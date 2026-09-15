package com.tftdeck.reader.ingame

import android.content.Context
import com.tftdeck.reader.BuildConfig
import com.tftdeck.reader.data.encodePathSegment
import com.tftdeck.reader.data.metatftJson
import com.tftdeck.reader.data.splitRiotId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * 지난 게임 로비(8명의 라이엇 ID·등수·티어).
 *
 * Riot 정책상 로비 참가자 정보는 게임이 끝난 뒤에 보여 주는 것이 안전하다. 그래서 metatft가
 * 로비 종료 후에 만드는 경기 JSON만 읽는다. 다른 사람의 정보는 화면 표시용으로 최근 1경기만
 * 기기 파일에 두고, puuid는 아예 읽지 않는다. 어디로도 보내지 않는다.
 */
class LobbyRepository private constructor(private val context: Context) {

    private val cacheFile get() = File(context.filesDir, CACHE_NAME)
    private val lock = Mutex()

    private val _state = MutableStateFlow<LastLobby?>(null)
    val state: StateFlow<LastLobby?> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    /** 저장해 둔 최근 1경기를 올린다. 설정 화면이 빈 카드로 뜨지 않도록. */
    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            val cached = readCache()
            if (_state.value == null) _state.value = cached
            loaded = true
        }
    }

    /**
     * 가장 최근 경기의 로비를 받는다.
     * lookup(최근 경기 목록)에서 맨 앞 경기를 보고, 캐시와 같은 경기이거나 [newerThan]보다
     * 오래된 경기면 null. 새 경기면 경기 JSON을 한 번 받아 캐시를 바꾼다. 실패해도 null.
     */
    suspend fun fetchLatest(riotId: String, region: String, newerThan: Long = 0L): LastLobby? = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val (name, tag) = splitRiotId(riotId) ?: return@runCatching null
                val url = LOOKUP_BASE + region.uppercase() + "/" + encodePathSegment(name) + "/" + encodePathSegment(tag)
                val ref = parseLatestMatchRef(metatftGet(url)) ?: return@runCatching null
                fetchIfNew(ref, riotId, newerThan)
            }.getOrNull()
        }
    }

    /**
     * 경기 참조를 이미 알고 있을 때(방금 받은 전적 요약 등) lookup 없이 경기 JSON만 받는다.
     */
    suspend fun fetchMatch(ref: MatchRef, riotId: String, newerThan: Long = 0L): LastLobby? = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching { fetchIfNew(ref, riotId, newerThan) }.getOrNull()
        }
    }

    private fun fetchIfNew(ref: MatchRef, riotId: String, newerThan: Long): LastLobby? {
        if (ref.matchTimestamp in 1..newerThan) return null
        val cached = _state.value ?: readCache()
        if (cached != null && cached.matchId == ref.matchId && sameRiotId(cached.ownerRiotId, riotId)) return null
        // 응답에 적힌 주소라도 metatft 경기 저장소가 아니면 따라가지 않는다.
        if (!ref.matchDataUrl.startsWith(MATCH_HOST)) return null

        val body = metatftGet(ref.matchDataUrl, referer = false)
        val lobby = parseLobbyMatch(body, ref.matchId, riotId) ?: return null
        writeCache(lobby)
        _state.value = lobby
        loaded = true
        return lobby
    }

    /** 계정 연결을 해제하면 다른 사람 정보가 남지 않게 지운다. */
    fun clear() {
        runCatching { cacheFile.delete() }
        _state.value = null
    }

    private fun readCache(): LastLobby? = runCatching {
        if (!cacheFile.exists()) return null
        metatftJson.decodeFromString<LastLobby>(cacheFile.readText())
    }.getOrNull()?.takeIf { it.players.isNotEmpty() }

    private fun writeCache(lobby: LastLobby) = runCatching {
        val temp = File(context.filesDir, "$CACHE_NAME.tmp")
        temp.writeText(metatftJson.encodeToString(LastLobby.serializer(), lobby))
        if (!temp.renameTo(cacheFile)) {
            cacheFile.writeText(temp.readText())
            temp.delete()
        }
    }

    companion object {
        private const val LOOKUP_BASE = "https://api.metatft.com/public/profile/lookup_by_riotid/"
        private const val MATCH_HOST = "https://matches"
        const val CACHE_NAME = "last_lobby.json"

        @Volatile
        private var instance: LobbyRepository? = null

        fun get(context: Context): LobbyRepository =
            instance ?: synchronized(this) {
                instance ?: LobbyRepository(context.applicationContext).also { instance = it }
            }
    }
}

// ---------------------------------------------------------------------------
// 화면이 쓰는 형태
// ---------------------------------------------------------------------------

@Serializable
data class LastLobby(
    val matchId: String = "",
    /** 경기 종료 시각(ms). metatft info.game_datetime. */
    val endedAt: Long = 0,
    /** 이 로비를 받아 온 내 라이엇 ID. 계정을 바꾸면 이전 로비를 보여 주지 않는다. */
    val ownerRiotId: String = "",
    /** 등수순 8명. */
    val players: List<LobbyPlayer> = emptyList(),
) {
    val me: LobbyPlayer? get() = players.firstOrNull { it.isMe }
}

@Serializable
data class LobbyPlayer(
    val riotId: String = "",
    val placement: Int = 0,
    /** "다이아몬드 IV 42 LP". metatft가 경기 전에 관측한 값이라 결과 반영 전 티어다. 없으면 빈 문자열. */
    val tierText: String = "",
    val level: Int = 0,
    val isMe: Boolean = false,
)

/** lookup 응답의 경기 한 건을 가리키는 참조. */
data class MatchRef(
    val matchId: String,
    val matchDataUrl: String,
    val placement: Int,
    val matchTimestamp: Long,
)

// ---------------------------------------------------------------------------
// 파싱 (Android 의존성 없이 단위 테스트한다)
// ---------------------------------------------------------------------------

/** lookup 응답에서 가장 최근 경기. 경기 JSON 주소가 없으면 null. */
internal fun parseLatestMatchRef(body: String): MatchRef? {
    val latest = metatftJson.decodeFromString<LookupJson>(body).matches
        .filter { it.riotMatchId.isNotBlank() && it.matchDataUrl.isNotBlank() }
        .maxByOrNull { it.matchTimestamp } ?: return null
    return MatchRef(latest.riotMatchId, latest.matchDataUrl, latest.placement, latest.matchTimestamp)
}

/**
 * matches3 경기 JSON → 로비. 참가자와 티어 목록(_metatft.participant_info)은 순서가 같다는
 * 보장이 없어 라이엇 ID로 짝을 맞춘다. puuid·유닛·MMR은 읽지 않는다.
 */
internal fun parseLobbyMatch(body: String, matchId: String, meRiotId: String): LastLobby? {
    val match = metatftJson.decodeFromString<MatchJson>(body)
    val participants = match.info.participants.filter { it.placement in 1..8 }
    if (participants.isEmpty()) return null

    val tiers = match.metatft.participantInfo.associate { normalizeRiotId(it.riotId) to it.ranked?.ratingText.orEmpty() }
    val players = participants.map { p ->
        val riotId = p.riotIdGameName.trim() + "#" + p.riotIdTagline.trim()
        LobbyPlayer(
            riotId = riotId,
            placement = p.placement,
            tierText = lobbyTierText(tiers[normalizeRiotId(riotId)].orEmpty()),
            level = p.level,
            isMe = sameRiotId(riotId, meRiotId),
        )
    }.sortedBy { it.placement }

    return LastLobby(
        matchId = match.metadata.matchId.ifBlank { matchId },
        endedAt = match.info.gameDatetime,
        ownerRiotId = meRiotId.trim(),
        players = players,
    )
}

/** 라이엇 ID는 대소문자를 구분하지 않는다. '#' 앞뒤 공백도 무시한다. */
internal fun normalizeRiotId(riotId: String): String =
    riotId.split("#", limit = 2).joinToString("#") { it.trim() }.lowercase(Locale.ROOT)

internal fun sameRiotId(a: String, b: String): Boolean =
    a.contains('#') && normalizeRiotId(a) == normalizeRiotId(b)

/**
 * "DIAMOND IV 42 LP" → "다이아몬드 IV 42 LP". 전적 카드(ProfileRepository)와 같은 한글 이름을 쓴다.
 * 모르는 영문 티어는 원문 그대로 둔다.
 */
internal fun lobbyTierText(raw: String): String {
    val words = raw.trim().split(' ').filter { it.isNotBlank() }
    val first = words.firstOrNull() ?: return ""
    val korean = LOBBY_TIER_KO[first.uppercase(Locale.ROOT)] ?: return words.joinToString(" ")
    return (listOf(korean) + words.drop(1)).joinToString(" ")
}

private val LOBBY_TIER_KO = mapOf(
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

@Serializable
private data class LookupJson(val matches: List<LookupMatch> = emptyList())

@Serializable
private data class LookupMatch(
    @SerialName("riot_match_id") val riotMatchId: String = "",
    @SerialName("match_data_url") val matchDataUrl: String = "",
    val placement: Int = 0,
    @SerialName("match_timestamp") val matchTimestamp: Long = 0,
)

@Serializable
private data class MatchJson(
    val metadata: MatchMeta = MatchMeta(),
    val info: MatchInfo = MatchInfo(),
    @SerialName("_metatft") val metatft: MetatftExtra = MetatftExtra(),
)

@Serializable
private data class MatchMeta(@SerialName("match_id") val matchId: String = "")

@Serializable
private data class MatchInfo(
    @SerialName("game_datetime") val gameDatetime: Long = 0,
    val participants: List<ParticipantRow> = emptyList(),
)

@Serializable
private data class ParticipantRow(
    val riotIdGameName: String = "",
    val riotIdTagline: String = "",
    val placement: Int = 0,
    val level: Int = 0,
)

@Serializable
private data class MetatftExtra(
    @SerialName("participant_info") val participantInfo: List<ParticipantInfoRow> = emptyList(),
)

@Serializable
private data class ParticipantInfoRow(
    @SerialName("riot_id") val riotId: String = "",
    val ranked: RankedInfo? = null,
)

@Serializable
private data class RankedInfo(@SerialName("rating_text") val ratingText: String = "")

// ---------------------------------------------------------------------------
// 네트워크
// ---------------------------------------------------------------------------

/** HTTP 상태 코드가 2xx가 아닐 때. 404(아직 배포 전 파일)를 네트워크 오류와 구분하려고 둔다. */
internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** 인게임 연동이 쓰는 GET. metatft API는 사이트 Referer를 붙여 부른다. */
internal fun metatftGet(url: String, referer: Boolean = true): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept-Encoding", "gzip")
        setRequestProperty("Accept", "application/json")
        if (referer) setRequestProperty("Referer", "https://www.metatft.com/")
        setRequestProperty("User-Agent", "TftDeckReader/${BuildConfig.VERSION_NAME}")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) throw HttpStatusException(code)
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
