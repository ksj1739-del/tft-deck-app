package com.tftdeck.reader.data

import android.content.Context
import com.tftdeck.reader.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 덱 데이터의 단일 진실 공급원.
 *
 * 통합 덱 v2도 1 MB 안팎이라 DB에 정규화하지 않는다.
 * 파일로 캐시하고 메모리에 인덱스를 얹는 편이 코드도 적고 검색도 즉시 끝난다.
 */
class DeckRepository private constructor(private val context: Context) {

    private val cacheFile: File get() = File(context.filesDir, CACHE_NAME)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val syncLock = Mutex()

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /**
     * 지금 원격에서 받는 중인지. 사용자가 누른 새로고침이든 하루 한 번 도는 워커든 [sync] 안에서 켜고 끈다 —
     * 덱 목록 배너('새로고침 중…')가 어느 쪽이 돌려도 같은 상태를 보여 주게.
     */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)

    /**
     * 마지막 동기화가 실패했으면 그 사유([SyncFailure]: 네트워크 없음/서버 오류/형식 오류), 성공하면 null.
     * 메모리에만 둔다(앱을 다시 켜면 비어 있다). 실패해도 화면의 데이터는 그대로다.
     */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // -- 로드 ---------------------------------------------------------------

    /**
     * 기기 캐시와 앱 동봉 스냅샷 중 더 새 데이터를 올린다. 첫 실행에도 네트워크 없이 화면이 채워진다.
     *
     * 캐시를 무조건 먼저 쓰면 앱을 업데이트했을 때 옛 버전이 받아 둔 데이터(1.0 의 v1 26덱)가 새로 넣은
     * 스냅샷(v2)을 가린다. 그래서 캐시를 쓴 앱 버전이 지금보다 낮으면 동봉본과 비교해 새것을 쓴다.
     * 지금 앱 버전이 쓴 캐시는 동기화 규칙상 동봉본보다 옛 것일 수 없으므로 비교 없이 쓴다(동봉 1 MB 파싱을 아낀다).
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null && prefs.getInt(KEY_CACHE_APP_VERSION, 0) >= BuildConfig.VERSION_CODE) {
            _state.value = ready(cached, lastSyncedAt(), fromBundle = false)
            return@withContext
        }

        val bundled = readBundled()
        _state.value = when {
            cached != null && (bundled == null || !FeedFreshness.isOlder(cached.version, bundled.version)) -> {
                // 앱을 올렸어도 캐시가 동봉본만큼 새롭다. 다음 실행부터는 비교하지 않는다.
                prefs.edit().putInt(KEY_CACHE_APP_VERSION, BuildConfig.VERSION_CODE).apply()
                ready(cached, lastSyncedAt(), fromBundle = false)
            }
            bundled != null -> {
                // 옛 캐시는 다시 쓸 일이 없다. 남겨 두면 실행할 때마다 비교만 반복한다.
                if (cached != null) runCatching { cacheFile.delete() }
                ready(bundled, null, fromBundle = true)
            }
            else -> FeedState.Error("덱 데이터를 읽지 못했습니다. 앱을 다시 설치해 주세요.")
        }
    }

    /**
     * 새 데이터를 화면에 올리기 전에 고정·숨김·오버레이 덱의 옛 id 를 새 덱 id 로 옮긴다.
     * 덱 목록을 metatft 조합 중심으로 다시 묶거나 metatft 가 클러스터를 다시 나누면 id 가 바뀐다.
     */
    private fun ready(feed: DeckFeed, syncedAt: Long?, fromBundle: Boolean): FeedState.Ready {
        val mapping = DeckIdMigration.mapping(feed.decks)
        if (mapping.isNotEmpty()) {
            pinnedDeckId?.let { old -> mapping[old]?.let { pinnedDeckId = it } }
            DeckPrefs.get(context).migrateIds(mapping)
        }
        return FeedState.Ready(feed, syncedAt, fromBundle)
    }

    private fun readCache(): DeckFeed? = runCatching {
        if (!cacheFile.exists()) return null
        FeedJson.decodeFeed(cacheFile.readText())
    }.getOrNull()?.takeIf { it.decks.isNotEmpty() }

    private fun readBundled(): DeckFeed? = runCatching {
        context.assets.open(CACHE_NAME).bufferedReader().use { reader ->
            FeedJson.decodeFeed(reader.readText())
        }
    }.getOrNull()?.takeIf { it.decks.isNotEmpty() }

    // -- 동기화 -------------------------------------------------------------

    /**
     * 하루 한 번 호출된다.
     *
     * 먼저 version.json(수백 바이트)만 받아 해시를 비교하고, 바뀐 경우에만
     * 본체를 내려받는다. 평상시 통신량은 사실상 0이다.
     *
     * 지금 보여 주는 데이터보다 옛 데이터는 받지 않는다([FeedFreshness]: schemaVersion 이 낮거나 generatedAt 이 이르면).
     * 원격 저장소에 새 스키마가 아직 올라가지 않았거나 옛 수집기가 다시 돌면 해시만 달라도 v2 화면을 v1 으로
     * 덮어쓰기 때문이다. 사용자가 누른 강제 갱신도 같은 규칙을 따른다.
     * raw.githubusercontent 는 파일마다 따로 캐시해 version.json 과 decks.json 이 다른 커밋에서 올 수 있어,
     * 본체를 받은 뒤에도 한 번 더 확인한다.
     *
     * 도는 동안 [syncing] 이 켜지고, 끝나면 [lastError] 에 실패 사유(성공이면 null)를 남긴다.
     */
    suspend fun sync(force: Boolean = false): SyncResult = syncLock.withLock {
        _syncing.value = true
        try {
            fetch(force).also { result -> _lastError.value = (result as? SyncResult.Failed)?.reason }
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun fetch(force: Boolean): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                val remote = FeedJson.decodeVersion(httpGet(BuildConfig.FEED_BASE_URL + VERSION_NAME))
                val current = currentVersion()

                val sameContent = current != null && remote.contentHash.isNotEmpty() &&
                    remote.contentHash == current.contentHash
                val olderThanShown = current != null && FeedFreshness.isOlder(remote, current)
                if ((!force && sameContent) || olderThanShown) {
                    markSynced()
                    _state.value = (_state.value as? FeedState.Ready)?.copy(lastSyncedAt = lastSyncedAt())
                        ?: _state.value
                    return@withContext SyncResult.UpToDate
                }

                val body = httpGet(BuildConfig.FEED_BASE_URL + CACHE_NAME)
                val feed = FeedJson.decodeFeed(body)
                if (feed.decks.isEmpty()) {
                    // 빈 응답으로 멀쩡한 캐시를 덮어쓰지 않는다.
                    return@withContext SyncResult.Failed(SyncFailure.FORMAT)
                }
                if (current != null && FeedFreshness.isOlder(feed.version, current)) {
                    // version.json 은 새 커밋인데 본체는 아직 옛 파일이 캐시에서 왔다(서버 쪽 사정). 다음 동기화에서 다시 받는다.
                    return@withContext SyncResult.Failed(SyncFailure.SERVER)
                }

                // 임시 파일에 쓰고 교체해서, 중간에 끊겨도 캐시가 깨지지 않게 한다.
                val temp = File(context.filesDir, "$CACHE_NAME.tmp")
                temp.writeText(body)
                if (!temp.renameTo(cacheFile)) {
                    cacheFile.writeText(body)
                    temp.delete()
                }
                prefs.edit().putInt(KEY_CACHE_APP_VERSION, BuildConfig.VERSION_CODE).apply()

                markSynced()
                _state.value = ready(feed, lastSyncedAt(), fromBundle = false)
                SyncResult.Updated(feed.version.patch, feed.decks.size)
            } catch (e: CancellationException) {
                // 취소(워커 중단·화면 종료)는 실패가 아니다. 사유를 남기지 않고 그대로 올려 보낸다.
                throw e
            } catch (e: Exception) {
                // 실패해도 기존 데이터는 그대로 둔다. 앱이 빈 화면이 되는 일은 없다.
                // 영문 예외 문구를 화면에 내보내지 않도록 한국어 사유 셋 중 하나로 바꾼다(N20).
                SyncResult.Failed(syncFailureReason(e))
            }
        }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "TftDeckReader/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw HttpStatusException(conn.responseCode)
            }
            val raw = if (conn.contentEncoding?.contains("gzip", true) == true) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return raw.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun currentVersion(): FeedVersion? = (_state.value as? FeedState.Ready)?.feed?.version

    private fun markSynced() = prefs.edit().putLong(KEY_SYNCED_AT, System.currentTimeMillis()).apply()

    private fun lastSyncedAt(): Long? =
        prefs.getLong(KEY_SYNCED_AT, 0L).takeIf { it > 0L }

    // -- 오버레이에 띄울 덱 --------------------------------------------------

    var pinnedDeckId: String?
        get() = prefs.getString(KEY_PINNED, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_PINNED) else putString(KEY_PINNED, value)
        }.apply()

    fun deckById(id: String?): Deck? =
        id?.let { (_state.value as? FeedState.Ready)?.feed?.decks?.firstOrNull { d -> d.id == it } }

    companion object {
        private const val CACHE_NAME = "decks.json"
        private const val VERSION_NAME = "version.json"
        private const val PREFS = "tft_deck_reader"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_PINNED = "pinned_deck"

        /** 캐시 파일을 마지막으로 쓰거나 확인한 앱 versionCode. 1.0 은 이 값을 남기지 않았다(0). */
        private const val KEY_CACHE_APP_VERSION = "cache_app_version"

        @Volatile
        private var instance: DeckRepository? = null

        fun get(context: Context): DeckRepository =
            instance ?: synchronized(this) {
                instance ?: DeckRepository(context.applicationContext).also { instance = it }
            }
    }
}

sealed interface FeedState {
    data object Loading : FeedState

    data class Ready(
        val feed: DeckFeed,
        val lastSyncedAt: Long?,
        /** 아직 한 번도 갱신하지 못하고 앱 동봉 스냅샷을 쓰는 중. */
        val fromBundle: Boolean,
    ) : FeedState

    data class Error(val message: String) : FeedState
}

sealed interface SyncResult {
    data object UpToDate : SyncResult
    data class Updated(val patch: String, val deckCount: Int) : SyncResult

    /** [reason] 은 [SyncFailure] 의 한국어 사유 중 하나다. */
    data class Failed(val reason: String) : SyncResult
}

/** 동기화 실패 사유. 화면(배너·스낵바)에 그대로 보이는 한국어 한 낱말이다. */
object SyncFailure {
    const val NETWORK = "네트워크 없음"
    const val SERVER = "서버 오류"
    const val FORMAT = "형식 오류"
}

/** 원격이 2xx 가 아닌 응답을 준 경우. [syncFailureReason] 이 '서버 오류'로 가른다. */
internal class HttpStatusException(val code: Int) : IllegalStateException("HTTP $code")

/**
 * 동기화 중 난 예외를 [SyncFailure] 사유로. 연결·시간 초과·끊김(IOException)은 '네트워크 없음',
 * 2xx 가 아닌 응답은 '서버 오류', JSON 이 계약과 다르면(SerializationException 등 IllegalArgumentException) '형식 오류'.
 * 나머지는 '서버 오류'로 본다.
 */
internal fun syncFailureReason(error: Throwable): String = when (error) {
    is HttpStatusException -> SyncFailure.SERVER
    is IOException -> SyncFailure.NETWORK
    is IllegalArgumentException -> SyncFailure.FORMAT
    else -> SyncFailure.SERVER
}
