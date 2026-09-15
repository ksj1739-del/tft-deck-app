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
 * 도감 통계(챔피언·특성·아이템·증강)의 단일 진실 공급원.
 *
 * 덱 저장소와 같은 원칙이다: 파일 캐시 → 앱 동봉 스냅샷 순으로 읽고,
 * 작은 version.json만 먼저 받아 해시가 바뀐 파일만 내려받는다.
 * 다른 점은 파일이 넷이라 해시를 파일마다 따로 비교한다는 것.
 * 패키지끼리 코드를 공유하지 않기로 했으므로 HTTP 코드는 DeckRepository에서 복제했다.
 */
class StatsRepository private constructor(private val context: Context) {

    private val cacheDir: File get() = File(context.filesDir, CACHE_DIR)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * load와 sync를 직렬화한다.
     * 느리게 끝난 load가 방금 받은 새 데이터를 옛 캐시로 덮어쓰는 일을 막는다.
     */
    private val lock = Mutex()

    private val _state = MutableStateFlow<StatsState>(StatsState.Loading)
    val state: StateFlow<StatsState> = _state.asStateFlow()

    /** 지금 메모리에 올라온 파일마다 그 내용의 해시. 동기화 때 무엇을 받을지 정한다. */
    @Volatile
    private var loadedHashes: Map<String, String> = emptyMap()

    // -- 로드 ---------------------------------------------------------------

    /**
     * 캐시를 먼저 읽고, 없거나 깨진 파일만 앱에 동봉한 스냅샷으로 떨어진다.
     * 넷 다 없으면 Missing. 하나라도 있으면 있는 것만으로 화면을 채운다.
     *
     * 앱 업데이트로 동봉 스냅샷이 캐시보다 새로우면 캐시를 버린다. 두면 새로 넣은 도감이 옛 캐시에 가린다.
     * 이미 Ready면 건너뛴다(앱 시작과 화면이 동시에 불러도 한 번만 읽는다).
     */
    suspend fun load(force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!force && _state.value is StatsState.Ready) return@withContext

            val bundledVersion = readBundled(VERSION)?.let(StatsParser::parseVersion)
            var cachedVersion = readCached(VERSION)?.let(StatsParser::parseVersion)
            if (cachedVersion != null && bundledVersion != null && isStale(cachedVersion, bundledVersion)) {
                runCatching { cacheDir.deleteRecursively() }
                cachedVersion = null
            }
            val hashes = HashMap<String, String>()
            var fromCache = 0

            // 파일마다 어디서 읽었는지에 따라 그 출처의 해시를 기록해야
            // 동기화가 '이미 최신'을 잘못 판단하지 않는다.
            fun <T : Any> pick(name: String, parse: (String) -> T?): T? {
                val cached = readCached(name)?.let(parse)
                if (cached != null) {
                    cachedVersion?.files?.get(name)?.let { hashes[name] = it }
                    fromCache++
                    return cached
                }
                val bundled = readBundled(name)?.let(parse)
                if (bundled != null) {
                    bundledVersion?.files?.get(name)?.let { hashes[name] = it }
                }
                return bundled
            }

            val champions = pick(CHAMPIONS, StatsParser::parseChampions)
            val traits = pick(TRAITS, StatsParser::parseTraits)
            val items = pick(ITEMS, StatsParser::parseItems)
            val augments = pick(AUGMENTS, StatsParser::parseAugments)

            if (champions == null && traits == null && items == null && augments == null) {
                loadedHashes = emptyMap()
                _state.value = StatsState.Missing
                return@withContext
            }

            loadedHashes = hashes
            val version = (if (fromCache > 0) cachedVersion else bundledVersion)
                ?: cachedVersion ?: bundledVersion ?: StatsVersion()
            _state.value = StatsState.Ready(
                version = version,
                champions = champions ?: ChampionsFile(),
                traits = traits ?: TraitsFile(),
                items = items ?: ItemsFile(),
                augments = augments ?: AugmentsFile(),
                lastSyncedAt = lastSyncedAt(),
                fromBundle = fromCache == 0,
            )
        }
    }

    private fun readCached(name: String): String? = runCatching {
        File(cacheDir, "$name.json").takeIf { it.exists() }?.readText()
    }.getOrNull()

    private fun readBundled(name: String): String? = runCatching {
        context.assets.open("$ASSET_DIR/$name.json").bufferedReader().use { it.readText() }
    }.getOrNull()

    // -- 동기화 -------------------------------------------------------------

    /**
     * 하루 한 번(DailySyncWorker) 또는 사용자가 갱신을 누를 때 호출된다.
     *
     * 1) stats/version.json(수백 바이트)을 받는다.
     * 2) files.<이름> 해시가 지금 올라온 파일과 다른 것만 받는다.
     *    파싱이 끝나고 행이 있고, 파일 안의 해시가 version.json 이 약속한 해시와 같은 파일만
     *    임시 파일에 쓰고 교체한다.
     * 3) 마지막에 version.json을 저장한다. 받지 못한 파일은 옛 해시를 남겨 다음에 다시 받는다.
     *    하나라도 못 받았으면 패치·기준일 같은 메타는 바꾸지 않는다.
     *
     * 실패해도 기존 데이터는 그대로 둔다. 도감이 빈 화면이 되는 일은 없다.
     */
    suspend fun sync(force: Boolean = false): StatsSyncResult = lock.withLock {
        withContext(Dispatchers.IO) {
            val remote = try {
                StatsParser.parseVersion(httpGet(remoteUrl(VERSION)))
            } catch (e: Exception) {
                return@withContext StatsSyncResult.Failed(e.message ?: "네트워크 오류")
            } ?: return@withContext StatsSyncResult.Failed("도감 버전 파일이 비어 있습니다")

            val targets = filesToDownload(remote, loadedHashes, force)
            val current = _state.value as? StatsState.Ready
            var champions = current?.champions
            var traits = current?.traits
            var items = current?.items
            var augments = current?.augments
            val hashes = loadedHashes.toMutableMap()
            val updated = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for (name in targets) {
                val expected = remote.files[name].orEmpty()
                val body = try {
                    httpGet(remoteUrl(name))
                } catch (e: Exception) {
                    failed += name
                    continue
                }
                // 빈 파일·깨진 파일은 여기서 걸러진다. 멀쩡한 캐시를 덮어쓰지 않는다.
                // raw.githubusercontent 는 파일마다 따로 캐시해 새 version.json 과 옛 본체가 섞여 올 수 있다.
                // 옛 본체를 새 해시로 기록하면 다음에 해시가 바뀔 때까지 다시 받지 않으므로 여기서 거른다.
                val accepted: Boolean = when (name) {
                    CHAMPIONS -> StatsParser.parseChampions(body)
                        ?.takeIf { hashMatches(expected, it.version.contentHash) }
                        ?.also { champions = it } != null
                    TRAITS -> StatsParser.parseTraits(body)
                        ?.takeIf { hashMatches(expected, it.version.contentHash) }
                        ?.also { traits = it } != null
                    ITEMS -> StatsParser.parseItems(body)
                        ?.takeIf { hashMatches(expected, it.version.contentHash) }
                        ?.also { items = it } != null
                    AUGMENTS -> StatsParser.parseAugments(body)
                        ?.takeIf { hashMatches(expected, it.version.contentHash) }
                        ?.also { augments = it } != null
                    else -> false
                }
                if (!accepted || !writeCache(name, body)) {
                    failed += name
                    continue
                }
                hashes[name] = expected
                updated += name
            }

            loadedHashes = hashes
            val record = recordAfterSync(remote, current?.version, hashes, failed.isEmpty())
            writeCache(VERSION, StatsParser.json.encodeToString(StatsVersion.serializer(), record))

            val syncedAt = if (failed.isEmpty()) markSynced() else lastSyncedAt()
            if (champions != null || traits != null || items != null || augments != null) {
                _state.value = StatsState.Ready(
                    version = record,
                    champions = champions ?: ChampionsFile(),
                    traits = traits ?: TraitsFile(),
                    items = items ?: ItemsFile(),
                    augments = augments ?: AugmentsFile(),
                    lastSyncedAt = syncedAt,
                    fromBundle = current?.fromBundle == true && updated.isEmpty(),
                )
            }

            when {
                failed.isNotEmpty() -> StatsSyncResult.Failed("받지 못한 파일: ${failed.joinToString()}", updated)
                updated.isEmpty() -> StatsSyncResult.UpToDate
                else -> StatsSyncResult.Updated(updated)
            }
        }
    }

    /** 임시 파일에 쓰고 교체해서, 중간에 끊겨도 캐시가 깨지지 않게 한다. */
    private fun writeCache(name: String, body: String): Boolean = runCatching {
        val dir = cacheDir.apply { mkdirs() }
        val target = File(dir, "$name.json")
        val temp = File(dir, "$name.json.tmp")
        temp.writeText(body)
        if (!temp.renameTo(target)) {
            target.writeText(body)
            temp.delete()
        }
        true
    }.getOrDefault(false)

    private fun remoteUrl(name: String): String = BuildConfig.FEED_BASE_URL + REMOTE_DIR + "$name.json"

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

    private fun markSynced(): Long {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_SYNCED_AT, now).apply()
        return now
    }

    private fun lastSyncedAt(): Long? = prefs.getLong(KEY_SYNCED_AT, 0L).takeIf { it > 0L }

    companion object {
        const val VERSION = "version"
        const val CHAMPIONS = "champions"
        const val TRAITS = "traits"
        const val ITEMS = "items"
        const val AUGMENTS = "augments"

        /** version.json 을 뺀 본체 파일. 이 순서로 받는다. */
        val DATA_FILES = listOf(CHAMPIONS, TRAITS, ITEMS, AUGMENTS)

        private const val CACHE_DIR = "stats"
        private const val ASSET_DIR = "stats"
        private const val REMOTE_DIR = "stats/"
        private const val PREFS = "tft_stats"
        private const val KEY_SYNCED_AT = "synced_at"

        /**
         * 받아야 할 파일. 원격 해시가 있고, 지금 올라온 파일의 해시와 다르면 받는다.
         * 원격 목록에 없는 파일은 건드리지 않는다(수집기가 그 파일을 못 만든 날).
         */
        internal fun filesToDownload(
            remote: StatsVersion,
            loaded: Map<String, String>,
            force: Boolean,
        ): List<String> = DATA_FILES.filter { name ->
            val hash = remote.files[name]
            !hash.isNullOrBlank() && (force || hash != loaded[name])
        }

        /**
         * 받은 파일이 version.json 이 가리키는 그 내용인가. 수집기는 파일 안 version.contentHash 와
         * version.json 의 files.<이름> 에 같은 값을 쓴다(verify_stats.py 가 검사). 한쪽이 비어 있으면
         * 확인할 수 없어 받아들인다.
         */
        internal fun hashMatches(expected: String, actual: String): Boolean =
            expected.isBlank() || actual.isBlank() || expected == actual

        /** 캐시가 동봉 스냅샷보다 옛 것인가. 스키마가 낮거나, 같으면 생성 시각이 이르다. 모르는 값이면 false. */
        internal fun isStale(cached: StatsVersion, bundled: StatsVersion): Boolean =
            if (cached.schemaVersion != bundled.schemaVersion) {
                cached.schemaVersion < bundled.schemaVersion
            } else {
                FeedFreshness.isEarlier(cached.generatedAt, bundled.generatedAt)
            }

        /**
         * 동기화 뒤 저장할 version.json. 파일 해시는 실제로 올라온 것으로 적고, 메타(패치·기준일·생성 시각)는
         * 모든 파일을 받았을 때만 원격 것으로 바꾼다. 일부만 받았는데 새 메타를 쓰면 표는 옛 데이터인데
         * 신선도 줄과 '도감 데이터' 행만 새 날짜로 보이고, 앱을 다시 켜도 그대로 남는다.
         */
        internal fun recordAfterSync(
            remote: StatsVersion,
            shown: StatsVersion?,
            hashes: Map<String, String>,
            allReceived: Boolean,
        ): StatsVersion {
            val meta = if (allReceived || shown == null) remote else shown
            return meta.copy(files = hashes.filterKeys { it in DATA_FILES })
        }

        @Volatile
        private var instance: StatsRepository? = null

        fun get(context: Context): StatsRepository =
            instance ?: synchronized(this) {
                instance ?: StatsRepository(context.applicationContext).also { instance = it }
            }
    }
}

sealed interface StatsState {
    data object Loading : StatsState

    data class Ready(
        val version: StatsVersion,
        val champions: ChampionsFile,
        val traits: TraitsFile,
        val items: ItemsFile,
        val augments: AugmentsFile,
        val lastSyncedAt: Long?,
        /** 네 파일 모두 앱 동봉 스냅샷에서 읽었고 아직 한 번도 받지 못했다. */
        val fromBundle: Boolean = false,
    ) : StatsState {
        // 상세 화면이 id로 자주 찾는다. 파일당 한 번만 만든다.
        val championsById: Map<String, ChampionRow> by lazy { champions.champions.associateBy { it.id } }
        val traitsById: Map<String, TraitRow> by lazy { traits.traits.associateBy { it.id } }
        val itemsById: Map<String, ItemRow> by lazy { items.items.associateBy { it.id } }
        val augmentsById: Map<String, AugmentRow> by lazy { augments.augments.associateBy { it.id } }
    }

    /** 캐시도 동봉 스냅샷도 없다. 설정에서 갱신하도록 안내한다. */
    data object Missing : StatsState
}

sealed interface StatsSyncResult {
    data object UpToDate : StatsSyncResult
    data class Updated(val files: List<String>) : StatsSyncResult

    /** 일부만 받았으면 [updated]에 받은 파일이 들어 있다. */
    data class Failed(val reason: String, val updated: List<String> = emptyList()) : StatsSyncResult
}

/**
 * 도감 화면 설정(기기 로컬). 스코프는 여러 화면이 함께 보므로 흐름으로 들고 있는다.
 * 뷰모델이 화면마다 따로 만들어져도 같은 스코프를 보도록 싱글턴이다.
 */
class CodexPrefs private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _scope = MutableStateFlow(
        prefs.getString(KEY_SCOPE, null)?.takeIf { it.isNotBlank() } ?: StatScope.DEFAULT
    )
    val scope: StateFlow<String> = _scope.asStateFlow()

    fun setScope(value: String) {
        if (value.isBlank() || value == _scope.value) return
        _scope.value = value
        prefs.edit().putString(KEY_SCOPE, value).apply()
    }

    /** 도감 상단 탭(0 챔피언, 1 특성, 2 아이템, 3 증강). */
    var tab: Int
        get() = prefs.getInt(KEY_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_TAB, value).apply()

    /** 챔피언 정렬 키(grade / avg / pick / name). */
    var championSort: String?
        get() = prefs.getString(KEY_CHAMPION_SORT, null)
        set(value) = prefs.edit().putString(KEY_CHAMPION_SORT, value).apply()

    /** 아이템 탭 안의 탭(0 통계, 1 조합표). */
    var itemSubTab: Int
        get() = prefs.getInt(KEY_ITEM_SUB_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_ITEM_SUB_TAB, value).apply()

    /** 증강 탭 안의 탭(0 티어, 1 목록). */
    var augmentSubTab: Int
        get() = prefs.getInt(KEY_AUGMENT_SUB_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_AUGMENT_SUB_TAB, value).apply()

    companion object {
        const val NAME = "codex_prefs"
        private const val KEY_SCOPE = "scope"
        private const val KEY_TAB = "tab"
        private const val KEY_CHAMPION_SORT = "champion_sort"
        private const val KEY_ITEM_SUB_TAB = "item_sub_tab"
        private const val KEY_AUGMENT_SUB_TAB = "augment_sub_tab"

        @Volatile
        private var instance: CodexPrefs? = null

        fun get(context: Context): CodexPrefs =
            instance ?: synchronized(this) {
                instance ?: CodexPrefs(context.applicationContext).also { instance = it }
            }
    }
}
