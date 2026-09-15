package com.tftdeck.reader.data

import android.content.Context
import com.tftdeck.reader.ingame.LobbyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * 내 티어와 최근 전적.
 *
 * 덱 데이터와 달리 사람마다 다르고 한 판 끝날 때마다 바뀌므로, 하루 한 번 도는 수집기에
 * 넣을 수 없다. 앱이 직접 metatft의 공개 프로필 API를 부른다.
 *
 * 호출은 두 종류다.
 * - lookup_by_riotid(약 63KB): 최근 40판 등수·패치·서버 순위. 앱 화면과 판 종료 직후에만.
 * - rating_changes(약 17KB): 판 수와 LP 기록. 오버레이·게임 연동의 잦은 갱신은 이것만 쓴다.
 *
 * 라이엇 ID는 이 기기에만 저장하고, 조회할 때 metatft로만 보낸다.
 */
class ProfileRepository private constructor(private val context: Context) {

    private val json = metatftJson
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheFile get() = File(context.filesDir, CACHE_NAME)
    private val lock = Mutex()
    private val ratingLock = Mutex()

    private val _state = MutableStateFlow<ProfileState>(ProfileState.NotConfigured)
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    /** 마지막 rating_changes 결과. 17KB라 파일로 들고 있기보다 다시 받는 편이 단순하다. */
    @Volatile
    private var ratingSnapshot: RatingSnapshot? = null

    /** [ratingSnapshot]이 어느 계정의 것인지. ID를 바꾸면 이전 기록을 쓰지 않는다. */
    @Volatile
    private var ratingOwner: String = ""

    @Volatile
    private var lookupInFlight = false

    /**
     * 계정 세대. 연결 해제·계정 변경 때 올린다. 조회는 시작할 때의 세대를 기억했다가 네트워크가 끝난 뒤
     * 세대가 바뀌었으면 결과를 버린다 — 해제한 뒤에 옛 계정 요약·puuid·LP 기록이 다시 저장되지 않게.
     * [accountLock] 은 세대 확인과 저장을 해제 처리와 겹치지 않게 묶는다.
     */
    @Volatile
    private var accountGeneration = 0
    private val accountLock = Any()

    // -- 설정 ---------------------------------------------------------------

    /** "랄라붕#KR1" 형식. 비어 있으면 기능이 꺼진 것으로 본다. */
    var riotId: String
        get() = prefs.getString(KEY_RIOT_ID, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_RIOT_ID, value).apply()

    var region: String
        get() = prefs.getString(KEY_REGION, DEFAULT_REGION).orEmpty().ifBlank { DEFAULT_REGION }
        private set(value) = prefs.edit().putString(KEY_REGION, value).apply()

    val isConfigured: Boolean get() = riotId.contains("#")

    /**
     * metatft가 이 계정에 매긴 puuid. 진행 중 게임 조회(ActiveGameSource)에만 쓴다.
     * lolchess 등 다른 서비스의 puuid와 값이 달라서 서비스 이름을 붙인 키로 따로 둔다.
     */
    val metatftPuuid: String?
        get() = prefs.getString(KEY_METATFT_PUUID, null)?.takeIf { it.isNotBlank() }

    /** metatft 표기의 서버 코드(kr, na1 …). lookup 응답의 summoner_region. */
    val metatftRegion: String?
        get() = prefs.getString(KEY_METATFT_REGION, null)?.takeIf { it.isNotBlank() }

    /** 지금 계정의 마지막 rating_changes 결과. 없으면 null. */
    val lastRating: RatingSnapshot?
        get() = ratingSnapshot?.takeIf { ratingOwner.isNotEmpty() && ratingOwner.equals(riotId, ignoreCase = true) }

    /** 설정 화면에서 저장. 저장하면 곧바로 한 번 조회한다. */
    suspend fun configure(newRiotId: String, newRegion: String) {
        val nextId = newRiotId.trim()
        val nextRegion = newRegion.trim().uppercase()
        val changed = !nextId.equals(riotId, ignoreCase = true) || !nextRegion.equals(region, ignoreCase = true)
        synchronized(accountLock) {
            // 계정이 바뀌면 진행 중인 옛 계정 조회가 끝나도 결과를 저장하지 않게 세대를 함께 올린다.
            if (changed) accountGeneration++
            riotId = nextId
            region = nextRegion
        }
        if (changed) {
            // 다른 계정의 요약·로비가 새 ID 이름으로 잠깐이라도 보이면 안 된다.
            forgetAccountData()
            clearCache()
            _state.value = ProfileState.Loading(null)
        }
        if (!isConfigured) {
            clearCache()
            _state.value = ProfileState.NotConfigured
            return
        }
        refresh(force = true)
    }

    fun clear() {
        synchronized(accountLock) {
            accountGeneration++
            prefs.edit().remove(KEY_RIOT_ID).remove(KEY_REGION).apply()
        }
        forgetAccountData()
        clearCache()
        _state.value = ProfileState.NotConfigured
    }

    /** 계정에 딸린 기기 안 기록(puuid, LP 기록, 지난 게임 로비)을 지운다. */
    private fun forgetAccountData() {
        prefs.edit().remove(KEY_METATFT_PUUID).remove(KEY_METATFT_REGION).apply()
        ratingSnapshot = null
        ratingOwner = ""
        LobbyRepository.get(context).clear()
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
            // 이미 더 새로운 상태(조회 중·결과)가 올라와 있으면 캐시로 덮지 않는다.
            _state.update { current -> if (current is ProfileState.NotConfigured) ProfileState.Ready(cached) else current }
        } else {
            refresh(force = true)
        }
    }

    /**
     * metatft lookup을 다시 받는다(약 63KB).
     * [force]가 아니면 [MIN_INTERVAL_MS] 안에 다시 부르지 않는다 — 한 판에 여러 번 부를 이유가 없다.
     * 실제로 받아 왔으면 LP 기록(rating_changes)도 이어서 확인해 최근 판별 ±LP를 채운다.
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        val outcome = lock.withLock { withContext(Dispatchers.IO) { refreshLocked(force) } }
        if (outcome == RefreshOutcome.Fetched) fetchRatingChanges(force = false)
        return outcome != RefreshOutcome.Failed
    }

    private fun refreshLocked(force: Boolean): RefreshOutcome {
        // 조회를 시작할 때의 계정. 네트워크가 끝난 뒤 세대가 바뀌었으면(해제·변경) 결과를 버린다.
        val (generation, accountId, accountRegion) = synchronized(accountLock) {
            Triple(accountGeneration, riotId, region)
        }
        if (!accountId.contains("#")) {
            _state.value = ProfileState.NotConfigured
            return RefreshOutcome.Failed
        }

        val current = _state.value.profileOrNull
        if (!force && current != null &&
            System.currentTimeMillis() - current.fetchedAt < MIN_INTERVAL_MS
        ) {
            return RefreshOutcome.Skipped
        }

        lookupInFlight = true
        _state.value = ProfileState.Loading(current)
        try {
            val (name, tag) = splitRiotId(accountId) ?: run {
                _state.value = ProfileState.Failed("라이엇 ID는 '이름#태그' 형식이어야 합니다", current)
                return RefreshOutcome.Failed
            }

            // source를 빼면 서버 순위(server_rank)가 오지 않는다. app_profile은 같은 63KB에
            // server_rank 한 줄만 더 붙는다(2026-09-15 실측). full_profile(211KB)은 쓰지 않는다.
            val url = LOOKUP_BASE + accountRegion.uppercase() + "/" +
                encodePathSegment(name) + "/" + encodePathSegment(tag) + "?source=app_profile"
            val body = httpGet(url)
            val parsed = json.decodeFromString<ProfileResponse>(body)
            val profile = parsed.toPlayerProfile(accountRegion, System.currentTimeMillis(), lastRating?.changes)

            synchronized(accountLock) {
                // 받는 사이에 연결을 해제했거나 계정을 바꿨으면 옛 계정 결과를 저장하지도 보여 주지도 않는다.
                if (generation != accountGeneration) return RefreshOutcome.Skipped
                if (profile == null) {
                    _state.value = ProfileState.Failed("이 세트의 랭크 기록이 없습니다", current)
                    return RefreshOutcome.Failed
                }

                // 진행 중 게임 조회용. 서비스별 키로만 둔다.
                prefs.edit()
                    .putString(KEY_METATFT_PUUID, parsed.summoner.puuid)
                    .putString(KEY_METATFT_REGION, parsed.summoner.summonerRegion)
                    .apply()

                writeCache(profile)
                _state.value = ProfileState.Ready(profile)
            }
            return RefreshOutcome.Fetched
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("404") == true -> "소환사를 찾지 못했습니다. ID와 지역을 확인해 주세요"
                else -> e.message ?: "조회 실패"
            }
            // 실패해도 이전 요약은 그대로 보여 준다. 그사이 연결을 해제했으면 해제 상태를 덮지 않는다.
            synchronized(accountLock) {
                if (generation == accountGeneration) _state.value = ProfileState.Failed(message, current)
            }
            return RefreshOutcome.Failed
        } finally {
            lookupInFlight = false
        }
    }

    /**
     * 판 수와 LP 기록만 받는다(약 17KB). 오버레이를 펼칠 때, TFT가 앞에 있는 동안, 판 종료 감시에 쓴다.
     * [force]가 아니면 [RATING_MIN_INTERVAL_MS] 안에서는 직전 결과를 그대로 돌려준다.
     * 받아 오면 티어·LP와 최근 판별 ±LP를 요약에 반영한다. 실패하면 null.
     */
    suspend fun fetchRatingChanges(force: Boolean = false): RatingSnapshot? = ratingLock.withLock {
        val (generation, id, accountRegion) = synchronized(accountLock) {
            Triple(accountGeneration, riotId, region)
        }
        if (!id.contains("#")) return@withLock null
        val cached = lastRating
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.fetchedAt < RATING_MIN_INTERVAL_MS) {
            return@withLock cached
        }
        val (name, tag) = splitRiotId(id) ?: return@withLock null

        // 사용자가 직접 누른 갱신이면 돌고 있다는 표시를 한다. 자동 갱신은 조용히 한다.
        if (force) {
            _state.update { st -> if (st is ProfileState.Ready) ProfileState.Loading(st.profile) else st }
        }

        val url = RATING_BASE + accountRegion.uppercase() + "/" +
            encodePathSegment(name) + "/" + encodePathSegment(tag) + "?queue=" + RANKED_QUEUE
        val body = withContext(Dispatchers.IO) { runCatching { httpGet(url) }.getOrNull() }
        if (body == null) {
            if (force && !lookupInFlight) {
                _state.update { st ->
                    if (st is ProfileState.Loading) ProfileState.Failed("전적을 갱신하지 못했습니다", st.previous) else st
                }
            }
            return@withLock null
        }

        val snapshot = withContext(Dispatchers.Default) { runCatching { parseRatingChanges(body, now) }.getOrNull() }
        if (snapshot == null) {
            // 응답은 왔는데 이번 세트 기록이 없다. 오류로 보이지 않게 이전 상태로 되돌린다.
            if (force && !lookupInFlight) {
                _state.update { st -> if (st is ProfileState.Loading && st.previous != null) ProfileState.Ready(st.previous) else st }
            }
            return@withLock null
        }

        synchronized(accountLock) {
            // 받는 사이에 연결을 해제했거나 계정을 바꿨으면 옛 계정 LP 기록을 남기지 않는다.
            if (generation != accountGeneration) return@withLock null
            ratingSnapshot = snapshot
            ratingOwner = id
        }
        applyRating(snapshot)
        snapshot
    }

    private suspend fun applyRating(snapshot: RatingSnapshot) {
        var updated: PlayerProfile? = null
        _state.update { st ->
            val profile = st.profileOrNull ?: return@update st
            val next = profile.withRating(snapshot)
            updated = next
            when (st) {
                // lookup이 도는 중이면 그 결과가 곧 덮어쓰므로 조회 중 표시를 유지한다.
                is ProfileState.Loading -> if (lookupInFlight) ProfileState.Loading(next) else ProfileState.Ready(next)
                is ProfileState.Ready, is ProfileState.Failed -> ProfileState.Ready(next)
                ProfileState.NotConfigured -> st
            }
        }
        updated?.let { profile -> withContext(Dispatchers.IO) { writeCache(profile) } }
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
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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

    private enum class RefreshOutcome { Skipped, Fetched, Failed }

    companion object {
        private const val LOOKUP_BASE = "https://api.metatft.com/public/profile/lookup_by_riotid/"
        private const val RATING_BASE = "https://api.metatft.com/public/profile/rating_changes/"
        private const val PREFS = "tft_profile"
        private const val KEY_RIOT_ID = "riot_id"
        private const val KEY_REGION = "region"
        private const val KEY_METATFT_PUUID = "metatft_puuid"
        private const val KEY_METATFT_REGION = "metatft_region"
        private const val CACHE_NAME = "profile.json"
        private const val MIN_INTERVAL_MS = 3 * 60 * 1000L
        private const val RATING_MIN_INTERVAL_MS = 3 * 60 * 1000L
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

/**
 * 캐시 파일에도 그대로 쓰인다. 새 필드는 전부 기본값이 있어야 이전 버전 캐시가 읽힌다.
 */
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
    /** 최근 경기 목록(최대 40판) 안의 이번 세트 랭크 판 수. 평균·TOP4의 분모. */
    val games: Int = 0,
    /** 최근 경기가 앞. */
    val recentPlacements: List<Int> = emptyList(),
    val averagePlacement: Double = 0.0,
    val top4Rate: Double = 0.0,
    val firstPlaces: Int = 0,
    val fetchedAt: Long = 0,
    /** 조회한 서버(KR …). */
    val region: String = "",
    /** 이번 세트 랭크 전체 판 수(ranked.num_games). */
    val seasonGames: Int = 0,
    val ratingNumeric: Int? = null,
    /** 서버 순위. metatft server_rank{rank,total}. */
    val serverRank: Int? = null,
    val serverTotal: Int? = null,
    /** rank / total. 0.0104면 '상위 1.0%'. */
    val percentile: Double? = null,
    /** [recentPlacements]와 같은 순서의 경기 종료 시각(ms). ±LP 짝짓기에 쓴다. */
    val recentMatchTimes: List<Long> = emptyList(),
    /** [recentPlacements]와 같은 순서의 LP 변화. 짝을 못 찾은 판은 null. */
    val recentLpChanges: List<Int?> = emptyList(),
    /** 가장 최근 판의 패치("18.2"). */
    val currentPatch: String? = null,
    val currentPatchGames: Int = 0,
    val currentPatchAvg: Double = 0.0,
    /** 경기 목록이 40판에서 잘렸는지. 잘렸으면 현재 패치 판 수는 '이상'으로 읽어야 한다. */
    val matchesTruncated: Boolean = false,
    val latestMatchId: String? = null,
    val latestMatchUrl: String? = null,
    /** LP 기록(rating_changes)으로 티어를 마지막으로 갱신한 시각. */
    val ratingFetchedAt: Long = 0,
) {
    val averageText: String get() = String.format(Locale.US, "%.2f", averagePlacement)
    val top4Text: String get() = "${(top4Rate * 100).toInt()}%"

    /** "상위 1.0%". 순위가 없으면 null. */
    val percentileText: String?
        get() = percentile?.let { p ->
            val pct = p * 100
            val digits = if (pct < 0.1) "%.2f" else "%.1f"
            "상위 " + String.format(Locale.US, digits, pct) + "%"
        }

    /** "KR 6,397위". 순위가 없으면 null. */
    val serverRankText: String?
        get() = serverRank?.let { rank -> listOf(region, String.format(Locale.US, "%,d위", rank)).filter { it.isNotBlank() }.joinToString(" ") }

    /** [at] 근처(±15분)에 끝난 최근 판의 등수. 판 종료 알림 문구에 쓴다. */
    fun placementNear(at: Long): Int? {
        val index = recentMatchTimes.indices.minByOrNull { abs(recentMatchTimes[it] - at) } ?: return null
        if (abs(recentMatchTimes[index] - at) > LP_PAIR_WINDOW_MS) return null
        return recentPlacements.getOrNull(index)
    }
}

/** rating_changes 한 행. [createdAt]은 metatft가 기록한 시각(ms, UTC). */
data class RatingChange(val numGames: Int, val ratingNumeric: Int, val createdAt: Long)

/** rating_changes 조회 결과. 최신 행 기준 판 수·점수와 이번 세트의 전체 기록. */
data class RatingSnapshot(
    val numGames: Int,
    val ratingNumeric: Int,
    val ratingText: String,
    val changes: List<RatingChange>,
    val fetchedAt: Long = 0,
)

/**
 * LP 기록과 경기를 짝짓는 허용 간격. metatft는 LP를 약 15분 격자로 기록한다(109건 분석).
 */
const val LP_PAIR_WINDOW_MS = 15 * 60 * 1000L

/** "+36", "−35", "±0". 빼기는 하이픈이 아니라 수학 기호(U+2212)로 써서 숫자와 폭을 맞춘다. */
fun formatLpDelta(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "±0"
}

// ---------------------------------------------------------------------------
// metatft 응답
// ---------------------------------------------------------------------------

internal val metatftJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

@Serializable
private data class ProfileResponse(
    val summoner: SummonerRow = SummonerRow(),
    val ranked: RankedRow = RankedRow(),
    val matches: List<MatchRow> = emptyList(),
    @SerialName("server_rank") val serverRank: ServerRankRow? = null,
)

@Serializable
private data class SummonerRow(
    @SerialName("riot_id") val riotId: String = "",
    @SerialName("profile_icon_id") val profileIconId: Int? = null,
    @SerialName("summoner_level") val level: Int? = null,
    val puuid: String = "",
    @SerialName("summoner_region") val summonerRegion: String = "",
)

@Serializable
private data class RankedRow(
    @SerialName("num_games") val numGames: Int = 0,
    @SerialName("rating_text") val ratingText: String = "",
    @SerialName("rating_numeric") val ratingNumeric: Int? = null,
    @SerialName("peak_rating") val peakRating: String = "",
)

@Serializable
private data class ServerRankRow(
    val rank: Int? = null,
    val total: Int? = null,
)

@Serializable
private data class MatchRow(
    val placement: Int = 0,
    @SerialName("queue_id") val queueId: Int = 0,
    @SerialName("tft_set") val tftSet: String = "",
    @SerialName("match_timestamp") val matchTimestamp: Long = 0,
    val patch: String = "",
    @SerialName("riot_match_id") val riotMatchId: String = "",
    @SerialName("match_data_url") val matchDataUrl: String = "",
)

@Serializable
private data class RatingChangesResponse(
    @SerialName("rating_changes") val ratingChanges: List<RatingRow> = emptyList(),
)

@Serializable
private data class RatingRow(
    @SerialName("num_games") val numGames: Int = 0,
    @SerialName("rating_text") val ratingText: String = "",
    @SerialName("rating_numeric") val ratingNumeric: Int = 0,
    @SerialName("created_timestamp") val createdTimestamp: String = "",
    @SerialName("tft_set_name") val tftSetName: String = "",
    @SerialName("queue_id") val queueId: Int? = null,
)

/** 랭크 게임 큐 id. 일반전이 섞이면 평균 등수가 의미를 잃는다. */
private const val RANKED_QUEUE = 1100
private const val RECENT_COUNT = 8

/** lookup이 돌려주는 최근 경기 수. 이만큼 왔으면 목록이 잘린 것으로 본다. */
private const val LOOKUP_MATCH_LIMIT = 40

private fun ProfileResponse.toPlayerProfile(region: String, now: Long, changes: List<RatingChange>?): PlayerProfile? {
    // 지난 세트 기록이 섞이지 않도록, 가장 많이 플레이한 최신 세트만 쓴다.
    val rankedMatches = matches.filter { it.queueId == RANKED_QUEUE && it.placement > 0 }
    val set = rankedMatches.groupingBy { it.tftSet }.eachCount().maxByOrNull { it.value }?.key
    val current = rankedMatches.filter { it.tftSet == set }.sortedByDescending { it.matchTimestamp }
    if (current.isEmpty()) return null

    val (tier, lp) = splitRating(this.ranked.ratingText)
    val recent = current.take(RECENT_COUNT)
    val recentTimes = recent.map { it.matchTimestamp }
    val latest = current.first()
    val patch = latest.patch.takeIf { it.isNotBlank() }
    val patchGames = if (patch == null) emptyList() else current.filter { it.patch == patch }
    val rank = serverRank?.rank?.takeIf { it > 0 }
    val total = serverRank?.total?.takeIf { it > 0 }

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
        recentPlacements = recent.map { it.placement },
        averagePlacement = current.sumOf { it.placement }.toDouble() / current.size,
        top4Rate = current.count { it.placement <= 4 }.toDouble() / current.size,
        firstPlaces = current.count { it.placement == 1 },
        fetchedAt = now,
        region = region.uppercase(),
        seasonGames = this.ranked.numGames,
        ratingNumeric = this.ranked.ratingNumeric,
        serverRank = rank,
        serverTotal = total,
        percentile = if (rank != null && total != null) rank.toDouble() / total else null,
        recentMatchTimes = recentTimes,
        recentLpChanges = changes?.let { pairLpChanges(recentTimes, it) } ?: emptyList(),
        currentPatch = patch,
        currentPatchGames = patchGames.size,
        currentPatchAvg = if (patchGames.isEmpty()) 0.0 else patchGames.sumOf { it.placement }.toDouble() / patchGames.size,
        matchesTruncated = matches.size >= LOOKUP_MATCH_LIMIT,
        latestMatchId = latest.riotMatchId.takeIf { it.isNotBlank() },
        latestMatchUrl = latest.matchDataUrl.takeIf { it.isNotBlank() },
    )
}

/** LP 기록으로 티어·LP·최근 판별 ±LP만 새로 고친다. 등수 목록은 lookup 때만 바뀐다. */
internal fun PlayerProfile.withRating(snapshot: RatingSnapshot): PlayerProfile {
    val (newTier, newLp) = splitRating(snapshot.ratingText)
    return copy(
        tier = newTier.ifBlank { tier },
        lp = if (newTier.isBlank()) lp else newLp,
        emblemUrl = emblemUrl(newTier) ?: emblemUrl,
        ratingNumeric = snapshot.ratingNumeric,
        seasonGames = maxOf(seasonGames, snapshot.numGames),
        recentLpChanges = pairLpChanges(recentMatchTimes, snapshot.changes),
        ratingFetchedAt = snapshot.fetchedAt,
    )
}

/**
 * rating_changes 응답을 읽는다. 이번 세트(최신 행과 같은 세트)의 기록만 남긴다 —
 * 세트가 바뀌면 판 수가 0부터 다시 세어져 이웃 차이가 엉뚱해진다. 기록이 없으면 null.
 */
internal fun parseRatingChanges(body: String, fetchedAt: Long = System.currentTimeMillis()): RatingSnapshot? {
    val rows = metatftJson.decodeFromString<RatingChangesResponse>(body).ratingChanges
        .filter { it.queueId == null || it.queueId == RANKED_QUEUE }
        .map { it to parseMetatftTime(it.createdTimestamp) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val newest = rows.firstOrNull()?.first ?: return null
    val sameSet = rows.filter { it.first.tftSetName == newest.tftSetName }
    return RatingSnapshot(
        numGames = newest.numGames,
        ratingNumeric = newest.ratingNumeric,
        ratingText = newest.ratingText,
        changes = sameSet.map { (row, at) -> RatingChange(row.numGames, row.ratingNumeric, at) },
        fetchedAt = fetchedAt,
    )
}

/** metatft 시각 문자열. 시간대 표기가 없으면 UTC다(경기 종료 시각과 대조해 확인). */
internal fun parseMetatftTime(raw: String): Long {
    val text = raw.trim()
    if (text.isEmpty()) return 0L
    return runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(text).toEpochMilli() }
        .getOrDefault(0L)
}

/**
 * 판 수마다 처음 관측된 행 하나씩, 판 수 내림차순.
 * 같은 판 수가 여러 번 기록되는 경우가 있어(판 없이 다시 조회된 것) 중복을 정리한다.
 */
internal fun firstObservations(changes: List<RatingChange>): List<RatingChange> =
    changes.groupBy { it.numGames }
        .mapNotNull { (_, rows) -> rows.minByOrNull { it.createdAt } }
        .sortedByDescending { it.numGames }

/** [numGames]번째 판이 끝나며 처음 기록된 시각. */
internal fun firstObservedAt(changes: List<RatingChange>, numGames: Int): Long? =
    changes.filter { it.numGames == numGames }.minOfOrNull { it.createdAt }

/**
 * [numGames]번째 판 하나의 LP 변화: 그 판 수의 첫 기록 − 한 판 전의 마지막 기록.
 * 바로 앞 판 수의 기록이 없으면(여러 판이 한 기록에 묶임) 한 판 값으로 볼 수 없어 null.
 */
internal fun lpDeltaFor(changes: List<RatingChange>, numGames: Int): Int? {
    val first = changes.filter { it.numGames == numGames }.minByOrNull { it.createdAt } ?: return null
    val before = changes.filter { it.numGames == numGames - 1 }.maxByOrNull { it.createdAt } ?: return null
    return first.ratingNumeric - before.ratingNumeric
}

/**
 * 최근 경기마다 LP 변화를 붙인다.
 * 경기 종료 시각과 LP 첫 기록 시각이 [windowMs] 안인 짝 중 가까운 것부터 하나씩 확정한다
 * (한 기록이 두 경기에 붙지 않도록). 짝이 없으면 null.
 */
internal fun pairLpChanges(
    matchTimes: List<Long>,
    changes: List<RatingChange>,
    windowMs: Long = LP_PAIR_WINDOW_MS,
): List<Int?> {
    if (matchTimes.isEmpty()) return emptyList()
    val observations = firstObservations(changes)
    val candidates = buildList {
        matchTimes.forEachIndexed { index, time ->
            if (time <= 0) return@forEachIndexed
            observations.forEach { row ->
                val diff = abs(time - row.createdAt)
                if (diff <= windowMs) add(Triple(diff, index, row.numGames))
            }
        }
    }.sortedWith(compareBy<Triple<Long, Int, Int>>({ it.first }, { it.second }))

    val result = MutableList<Int?>(matchTimes.size) { null }
    val usedMatches = HashSet<Int>()
    val usedGames = HashSet<Int>()
    for ((_, index, games) in candidates) {
        if (index in usedMatches || games in usedGames) continue
        usedMatches += index
        usedGames += games
        result[index] = lpDeltaFor(changes, games)
    }
    return result
}

/** "이름#태그" → (이름, 태그). 형식이 틀리면 null. */
internal fun splitRiotId(riotId: String): Pair<String, String>? {
    val parts = riotId.split("#", limit = 2)
    val name = parts[0].trim()
    val tag = parts.getOrElse(1) { "" }.trim()
    return if (name.isBlank() || tag.isBlank()) null else name to tag
}

/**
 * URL 경로 조각 인코딩. URLEncoder는 공백을 +로 바꾸는데 경로에서는 %20이어야 한다.
 * 원래 있던 +는 %2B로 바뀌므로 +만 치환해도 안전하다.
 */
internal fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

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
    val english = TIER_KO.entries.firstOrNull { koreanTier.isNotBlank() && koreanTier.startsWith(it.value) }?.key ?: return null
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
