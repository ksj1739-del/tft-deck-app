package com.tftdeck.reader.data

import android.content.Context
import com.tftdeck.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
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

    // -- 로드 ---------------------------------------------------------------

    /**
     * 캐시를 먼저 읽고, 없거나 깨졌으면 앱에 동봉한 스냅샷으로 떨어진다.
     * 첫 실행에도 네트워크 없이 화면이 채워진다.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null) {
            _state.value = FeedState.Ready(cached, lastSyncedAt(), fromBundle = false)
            return@withContext
        }
        val bundled = readBundled()
        _state.value = if (bundled != null) {
            FeedState.Ready(bundled, null, fromBundle = true)
        } else {
            FeedState.Error("덱 데이터를 읽지 못했습니다. 앱을 다시 설치해 주세요.")
        }
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
     * schemaVersion 은 보지 않는다. 수집기가 v1으로 되돌아가도(장애 복구 등) 모델이 두 버전을
     * 모두 읽으므로, 버전으로 거르면 오히려 새 데이터를 못 받는 날이 생긴다.
     */
    suspend fun sync(force: Boolean = false): SyncResult = syncLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val remote = FeedJson.decodeVersion(httpGet(BuildConfig.FEED_BASE_URL + VERSION_NAME))
                val current = currentVersion()

                if (!force && current != null && remote.contentHash.isNotEmpty() &&
                    remote.contentHash == current.contentHash
                ) {
                    markSynced()
                    _state.value = (_state.value as? FeedState.Ready)?.copy(lastSyncedAt = lastSyncedAt())
                        ?: _state.value
                    return@withContext SyncResult.UpToDate
                }

                val body = httpGet(BuildConfig.FEED_BASE_URL + CACHE_NAME)
                val feed = FeedJson.decodeFeed(body)
                if (feed.decks.isEmpty()) {
                    // 빈 응답으로 멀쩡한 캐시를 덮어쓰지 않는다.
                    return@withContext SyncResult.Failed("받은 데이터에 덱이 없습니다")
                }

                // 임시 파일에 쓰고 교체해서, 중간에 끊겨도 캐시가 깨지지 않게 한다.
                val temp = File(context.filesDir, "$CACHE_NAME.tmp")
                temp.writeText(body)
                if (!temp.renameTo(cacheFile)) {
                    cacheFile.writeText(body)
                    temp.delete()
                }

                markSynced()
                _state.value = FeedState.Ready(feed, lastSyncedAt(), fromBundle = false)
                SyncResult.Updated(feed.version.patch, feed.decks.size)
            } catch (e: Exception) {
                // 실패해도 기존 데이터는 그대로 둔다. 앱이 빈 화면이 되는 일은 없다.
                SyncResult.Failed(e.message ?: "네트워크 오류")
            }
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
                throw IllegalStateException("HTTP ${conn.responseCode}")
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
    data class Failed(val reason: String) : SyncResult
}
