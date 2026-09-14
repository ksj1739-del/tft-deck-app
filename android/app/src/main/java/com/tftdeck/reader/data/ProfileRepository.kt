package com.tftdeck.reader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * 내 티어와 최근 전적.
 *
 * 덱 데이터와 달리 사람마다 다르고 한 판 끝날 때마다 바뀌므로, 하루 한 번 도는 수집기에
 * 넣을 수 없다. 앱이 직접 metatft의 공개 프로필 API를 부른다.
 *
 * 라이엇 ID는 이 기기에만 저장하고, 조회할 때 metatft로만 보낸다.
 */
class ProfileRepository private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheFile get() = File(context.filesDir, CACHE_NAME)
    private val lock = Mutex()

    private val _state = MutableStateFlow<ProfileState>(ProfileState.NotConfigured)
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    // -- 설정 ---------------------------------------------------------------

    /** "랄라붕#KR1" 형식. 비어 있으면 기능이 꺼진 것으로 본다. */
    var riotId: String
        get() = prefs.getString(KEY_RIOT_ID, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_RIOT_ID, value).apply()

    var region: String
        get() = prefs.getString(KEY_REGION, DEFAULT_REGION).orEmpty().ifBlank { DEFAULT_REGION }
        private set(value) = prefs.edit().putString(KEY_REGION, value).apply()

    val isConfigured: Boolean get() = riotId.contains("#")

    /** 설정 화면에서 저장. 저장하면 곧바로 한 번 조회한다. */
    suspend fun configure(newRiotId: String, newRegion: String) {
        riotId = newRiotId.trim()
        region = newRegion.trim().uppercase()
        if (!isConfigured) {
            clearCache()
            _state.value = ProfileState.NotConfigured
            return
        }
        refresh(force = true)
    }

    fun clear() {
        prefs.edit().remove(KEY_RIOT_ID).remove(KEY_REGION).apply()
        clearCache()
        _state.value = ProfileState.NotConfigured
    }

    private fun clearCache() = runCatching { cacheFile.delete() }

    // -- 로드 / 조회 ---------------------------------------------------------

    /**
     * 저장된 요약을 먼저 올린다. 오버레이가 즉시 뭔가 보여 줄 수 있도록.
     * 아직 받아 둔 게 없으면 바로 한 번 조회한다 — 연결해 뒀는데 빈 화면이 뜨면 안 된다.
     */
    suspend fun load() {
        if (!isConfigured) {
            _state.value = ProfileState.NotConfigured
            return
        }
        val cached = withContext(Dispatchers.IO) { readCache() }
        if (cached != null) {
            _state.value = ProfileState.Ready(cached)
        } else {
            refresh(force = true)
        }
    }

    /**
     * metatft에서 다시 받아온다.
     * [force]가 아니면 [MIN_INTERVAL_MS] 안에 다시 부르지 않는다 — 한 판에 여러 번 부를 이유가 없다.
     */
    suspend fun refresh(force: Boolean = false): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                _state.value = ProfileState.NotConfigured
                return@withContext false
            }

            val current = (_state.value as? ProfileState.Ready)?.profile
            if (!force && current != null &&
                System.currentTimeMillis() - current.fetchedAt < MIN_INTERVAL_MS
            ) {
                return@withContext true
            }

            _state.value = ProfileState.Loading(current)
            try {
                val (name, tag) = riotId.split("#", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                if (name.isBlank() || tag.isBlank()) {
                    _state.value = ProfileState.Failed("라이엇 ID는 '이름#태그' 형식이어야 합니다", current)
                    return@withContext false
                }

                val url = buildString {
                    append(BASE)
                    append(region.uppercase()).append('/')
                    append(URLEncoder.encode(name, "UTF-8")).append('/')
                    append(URLEncoder.encode(tag, "UTF-8"))
                    append("?source=full_profile")
                }
                val body = httpGet(url)
                val parsed = json.decodeFromString<ProfileResponse>(body)
                val profile = parsed.toPlayerProfile()

                if (profile == null) {
                    _state.value = ProfileState.Failed("이 세트의 랭크 기록이 없습니다", current)
                    return@withContext false
                }

                writeCache(profile)
                _state.value = ProfileState.Ready(profile)
                true
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("404") == true -> "소환사를 찾지 못했습니다. ID와 지역을 확인해 주세요"
                    else -> e.message ?: "조회 실패"
                }
                // 실패해도 이전 요약은 그대로 보여 준다.
                _state.value = ProfileState.Failed(message, current)
                false
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Referer", "https://www.metatft.com/")
            setRequestProperty("User-Agent", "TftDeckReader")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val stream = if (conn.contentEncoding?.contains("gzip", true) == true) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(): PlayerProfile? = runCatching {
        if (!cacheFile.exists()) return null
        json.decodeFromString<PlayerProfile>(cacheFile.readText())
    }.getOrNull()

    private fun writeCache(profile: PlayerProfile) = runCatching {
        cacheFile.writeText(json.encodeToString(PlayerProfile.serializer(), profile))
    }

    companion object {
        private const val BASE = "https://api.metatft.com/public/profile/lookup_by_riotid/"
        private const val PREFS = "tft_profile"
        private const val KEY_RIOT_ID = "riot_id"
        private const val KEY_REGION = "region"
        private const val CACHE_NAME = "profile.json"
        private const val MIN_INTERVAL_MS = 3 * 60 * 1000L
        const val DEFAULT_REGION = "KR"

        /** metatft가 받는 서버 코드. 한국 사용자가 주 대상이라 KR을 앞에 둔다. */
        val REGIONS = listOf("KR", "NA", "EUW", "EUNE", "JP", "BR", "OCE", "TR", "RU", "LAN", "LAS", "SG", "TW", "VN")

        @Volatile
        private var instance: ProfileRepository? = null

        fun get(context: Context): ProfileRepository =
            instance ?: synchronized(this) {
                instance ?: ProfileRepository(context.applicationContext).also { instance = it }
            }
    }
}

// ---------------------------------------------------------------------------
// 화면이 쓰는 형태
// ---------------------------------------------------------------------------

sealed interface ProfileState {
    /** 라이엇 ID를 아직 입력하지 않았다. */
    data object NotConfigured : ProfileState

    data class Loading(val previous: PlayerProfile?) : ProfileState

    data class Ready(val profile: PlayerProfile) : ProfileState

    /** 실패해도 직전 요약이 있으면 계속 보여 준다. */
    data class Failed(val message: String, val previous: PlayerProfile?) : ProfileState

    val profileOrNull: PlayerProfile?
        get() = when (this) {
            is Ready -> profile
            is Loading -> previous
            is Failed -> previous
            NotConfigured -> null
        }
}

@Serializable
data class PlayerProfile(
    val riotId: String = "",
    val iconUrl: String? = null,
    val emblemUrl: String? = null,
    /** "다이아몬드 IV" */
    val tier: String = "",
    /** "36 LP" */
    val lp: String = "",
    val peak: String = "",
    val games: Int = 0,
    /** 최근 경기가 앞. */
    val recentPlacements: List<Int> = emptyList(),
    val averagePlacement: Double = 0.0,
    val top4Rate: Double = 0.0,
    val firstPlaces: Int = 0,
    val fetchedAt: Long = 0,
) {
    val averageText: String get() = String.format("%.2f", averagePlacement)
    val top4Text: String get() = "${(top4Rate * 100).toInt()}%"
}

// ---------------------------------------------------------------------------
// metatft 응답
// ---------------------------------------------------------------------------

@Serializable
private data class ProfileResponse(
    val summoner: SummonerRow = SummonerRow(),
    val ranked: RankedRow = RankedRow(),
    val matches: List<MatchRow> = emptyList(),
)

@Serializable
private data class SummonerRow(
    @SerialName("riot_id") val riotId: String = "",
    @SerialName("profile_icon_id") val profileIconId: Int? = null,
    @SerialName("summoner_level") val level: Int? = null,
)

@Serializable
private data class RankedRow(
    @SerialName("num_games") val numGames: Int = 0,
    @SerialName("rating_text") val ratingText: String = "",
    @SerialName("peak_rating") val peakRating: String = "",
)

@Serializable
private data class MatchRow(
    val placement: Int = 0,
    @SerialName("queue_id") val queueId: Int = 0,
    @SerialName("tft_set") val tftSet: String = "",
    @SerialName("match_timestamp") val matchTimestamp: Long = 0,
)

/** 랭크 게임 큐 id. 일반전이 섞이면 평균 등수가 의미를 잃는다. */
private const val RANKED_QUEUE = 1100
private const val RECENT_COUNT = 8

private fun ProfileResponse.toPlayerProfile(): PlayerProfile? {
    // 지난 세트 기록이 섞이지 않도록, 가장 많이 플레이한 최신 세트만 쓴다.
    val ranked = matches.filter { it.queueId == RANKED_QUEUE && it.placement > 0 }
    val set = ranked.groupingBy { it.tftSet }.eachCount().maxByOrNull { it.value }?.key
    val current = ranked.filter { it.tftSet == set }.sortedByDescending { it.matchTimestamp }
    if (current.isEmpty()) return null

    val (tier, lp) = splitRating(this.ranked.ratingText)

    return PlayerProfile(
        riotId = summoner.riotId,
        iconUrl = summoner.profileIconId?.let {
            "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons/$it.jpg"
        },
        emblemUrl = emblemUrl(tier),
        tier = tier,
        lp = lp,
        peak = splitRating(this.ranked.peakRating).let { (t, l) -> if (t.isBlank()) "" else "$t $l".trim() },
        games = current.size,
        recentPlacements = current.take(RECENT_COUNT).map { it.placement },
        averagePlacement = current.sumOf { it.placement }.toDouble() / current.size,
        top4Rate = current.count { it.placement <= 4 }.toDouble() / current.size,
        firstPlaces = current.count { it.placement == 1 },
        fetchedAt = System.currentTimeMillis(),
    )
}

/** "DIAMOND IV 36 LP" -> ("다이아몬드 IV", "36 LP") */
private fun splitRating(raw: String): Pair<String, String> {
    if (raw.isBlank()) return "" to ""
    val lpIndex = raw.lastIndexOf(" LP")
    val (ratingPart, lpPart) = if (lpIndex > 0) {
        val head = raw.substring(0, lpIndex)
        val cut = head.lastIndexOf(' ')
        if (cut > 0) head.substring(0, cut) to head.substring(cut + 1) + " LP" else head to ""
    } else {
        raw to ""
    }

    val words = ratingPart.trim().split(' ')
    val korean = TIER_KO[words.firstOrNull()?.uppercase()] ?: words.firstOrNull().orEmpty()
    val division = words.getOrNull(1).orEmpty()
    return listOf(korean, division).filter { it.isNotBlank() }.joinToString(" ") to lpPart
}

private fun emblemUrl(koreanTier: String): String? {
    val english = TIER_KO.entries.firstOrNull { koreanTier.startsWith(it.value) }?.key ?: return null
    return "https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-static-assets/" +
        "global/default/images/ranked-mini-crests/${english.lowercase()}.png"
}

private val TIER_KO = mapOf(
    "IRON" to "아이언",
    "BRONZE" to "브론즈",
    "SILVER" to "실버",
    "GOLD" to "골드",
    "PLATINUM" to "플래티넘",
    "EMERALD" to "에메랄드",
    "DIAMOND" to "다이아몬드",
    "MASTER" to "마스터",
    "GRANDMASTER" to "그랜드마스터",
    "CHALLENGER" to "챌린저",
)
