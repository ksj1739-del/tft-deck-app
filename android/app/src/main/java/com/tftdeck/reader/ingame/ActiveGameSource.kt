package com.tftdeck.reader.ingame

import com.tftdeck.reader.BuildConfig
import com.tftdeck.reader.data.encodePathSegment
import com.tftdeck.reader.data.metatftJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/**
 * 진행 중인 게임의 로비.
 *
 * 2026-09-15 조사에서 키 없는 경로 세 곳(lolchess 관전 목록, metatft 리더보드 live, metatft spectate)
 * 모두 진행 중 게임이 0건이었다. 그래서 인터페이스만 두고 기본은 [NoopSource]다.
 * 데이터가 열려도 Riot 정책상 게임 중에는 상대 정보를 화면에 그리지 않는다 — 결과는
 * "지금 게임 중인지"를 점 하나로 보여 주는 데만 쓴다.
 */
interface ActiveGameSource {
    suspend fun current(region: String, puuid: String): ActiveLobby?
}

/** 진행 중 게임 요약. 다른 참가자의 puuid는 들고 있지 않는다(인원 수와 내 포함 여부만). */
data class ActiveLobby(
    val participantCount: Int,
    val includesMe: Boolean,
)

object NoopSource : ActiveGameSource {
    override suspend fun current(region: String, puuid: String): ActiveLobby? = null
}

/**
 * metatft 관전 조회. 게임 중 응답을 한 번도 관측하지 못해서(게임 밖에서는 `{}`) 필드를
 * 추정하지 않고 gameMode와 participants[].puuid만 읽는다. 모양이 다르면 null.
 */
class MetatftSpectateSource : ActiveGameSource {
    override suspend fun current(region: String, puuid: String): ActiveLobby? = withContext(Dispatchers.IO) {
        try {
            val url = SPECTATE_BASE + region.lowercase(Locale.ROOT) + "/" + encodePathSegment(puuid)
            parseSpectate(metatftGet(url), puuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val SPECTATE_BASE = "https://api.metatft.com/tft-spectate/summoner_by_puuid/"
    }
}

internal fun parseSpectate(body: String, puuid: String): ActiveLobby? {
    val text = body.trim()
    if (text.isEmpty() || text == "{}") return null
    val root = runCatching { metatftJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    val mode = (root["gameMode"] as? JsonPrimitive)?.contentOrNull ?: return null
    if (mode != "TFT") return null
    val participants = root["participants"] as? JsonArray ?: return null
    val puuids = participants.mapNotNull { ((it as? JsonObject)?.get("puuid") as? JsonPrimitive)?.contentOrNull }
    return ActiveLobby(participantCount = puuids.size, includesMe = puuids.any { it == puuid })
}

/**
 * 원격 플래그(flags.json)로 진행 중 게임 조회를 켤지 정한다.
 * 수집기가 매일 metatft 상위 플레이어 관전 목록이 비었는지 확인해 적는다.
 */
object LiveSpectate {

    private const val FLAGS_NAME = "flags.json"
    private const val FLAGS_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /** 하루에 한 번만 받는다. 파일이 없으면(404) 꺼진 것으로 기록한다. */
    suspend fun refreshFlagsIfStale(prefs: IngamePrefs, now: Long = System.currentTimeMillis()) {
        if (now - prefs.lastFlagsFetch < FLAGS_MAX_AGE_MS) return
        withContext(Dispatchers.IO) {
            try {
                val flags = parseFlags(metatftGet(BuildConfig.FEED_BASE_URL + FLAGS_NAME, referer = false))
                prefs.setLiveSpectate(flags.liveSpectateAvailable, flags.checkedAt, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                // 아직 배포되지 않은 파일. 데이터가 없다는 뜻이라 끄고 하루 뒤 다시 본다.
                prefs.setLiveSpectate(false, null, now)
            } catch (e: Exception) {
                // 네트워크 오류는 기록하지 않는다. 다음 감지 시작 때 다시 시도한다.
            }
        }
    }

    fun sourceFor(prefs: IngamePrefs): ActiveGameSource =
        if (prefs.liveSpectateAvailable.value) MetatftSpectateSource() else NoopSource
}

@Serializable
internal data class LiveFlags(
    val liveSpectateAvailable: Boolean = false,
    val checkedAt: String? = null,
)

internal fun parseFlags(body: String): LiveFlags = metatftJson.decodeFromString(LiveFlags.serializer(), body)
