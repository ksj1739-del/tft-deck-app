package com.tftdeck.reader.data

import android.content.Context
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.tftdeck.reader.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * 수집기(collector/build_icons.py)가 만든 아이콘 WebP 팩을 기기에 풀어 두고,
 * 아이콘 URL 에 해당하는 로컬 파일을 찾아 준다.
 *
 * 왜 팩인가: CDragon 은 도쿄 edge 라 요청당 120~160 ms 가 걸리고, max-age 3600 이라 한 시간마다
 * 재검증이 붙는다(304 도 전체 다운로드만큼 느렸다). 아이콘 수백 개를 zip 한 파일(약 0.5 MB)로
 * 받아 두면 화면을 그릴 때 네트워크가 필요 없다. 같은 팩을 APK 에 동봉해 첫 실행부터 로컬로 그린다.
 *
 * 화면 코드는 그대로 URL 을 넘기고, 바꿔치기는 [IconInterceptor] 가 한다.
 *
 * filesDir 구성: icons/(현재 팩: *.webp + manifest.json), icons_tmp/(갱신 중 임시), icons_old/(교체 직전 백업).
 */
class IconPack private constructor(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true   // 수집기가 manifest 에 필드를 더해도 깨지지 않도록
        isLenient = true
        coerceInputValues = true
    }

    private val packDir: File get() = File(context.filesDir, DIR_CURRENT)
    private val stagingDir: File get() = File(context.filesDir, DIR_STAGING)
    private val retiredDir: File get() = File(context.filesDir, DIR_RETIRED)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** load 와 sync 가 같은 폴더를 동시에 만지지 않도록. */
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadStarted = AtomicBoolean(false)
    private val loaded = CompletableDeferred<Boolean>()

    /**
     * manifest 키(상대 경로 또는 절대 URL) → 로컬 파일. 인터셉터가 메인 스레드에서 매 요청 읽으므로
     * 디스크 확인은 만들 때 한 번만 하고, 갱신은 맵을 통째로 바꿔 끼운다.
     */
    @Volatile
    private var files: Map<String, File> = emptyMap()

    private val _state = MutableStateFlow(IconPackState())
    val state: StateFlow<IconPackState> = _state.asStateFlow()

    // -- 로드 ---------------------------------------------------------------

    /**
     * 기기에 풀어 둔 팩을 올린다. 없으면(첫 실행) 앱에 동봉한 팩을 풀어서 쓴다.
     * 여러 번 불러도 팩이 올라와 있으면 바로 끝난다. 실패해도 앱은 원격 아이콘으로 동작한다.
     */
    suspend fun load() {
        loadStarted.set(true)
        try {
            lock.withLock {
                if (_state.value.hash.isEmpty()) {
                    withContext(Dispatchers.IO) { loadLocked() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "아이콘 팩을 올리지 못해 원격으로 받습니다", e)
        } finally {
            loaded.complete(true)
        }
    }

    private fun loadLocked() {
        recoverInterruptedSwap()
        val local = readManifest(File(packDir, MANIFEST_NAME))
        val bundledText = readAsset(BUNDLED_MANIFEST)
        val bundled = bundledText?.let { parseManifest(it) }

        // 기기 팩이 없거나, 앱을 업데이트했는데 그동안 동기화를 못 해 동봉 팩이 더 새로우면 동봉 팩을 푼다.
        if (bundledText != null && bundled != null &&
            (local == null || (bundled.hash != local.hash && bundled.generatedAt > local.generatedAt))
        ) {
            val installed = runCatching {
                context.assets.open(BUNDLED_ZIP).use { installPack(it, bundled, bundledText) }
            }.onFailure { Log.w(TAG, "동봉 아이콘 팩을 풀지 못했습니다", it) }.isSuccess
            if (installed) {
                prefs.edit().putBoolean(KEY_FROM_BUNDLE, true).apply()
                activate(bundled)
                return
            }
        }
        if (local != null) activate(local)
    }

    /**
     * 팩이 올라올 때까지 잠깐 기다린다(인터셉터용). 이미 올라와 있으면 바로 돌아온다.
     * load() 를 부른 곳이 없으면 여기서 시작한다 — 누락돼도 요청마다 시간 초과까지 기다리는 일이 없게.
     */
    internal suspend fun awaitLoaded() {
        if (loaded.isCompleted) return
        if (loadStarted.compareAndSet(false, true)) scope.launch { load() }
        withTimeoutOrNull(LOAD_WAIT_MS) { loaded.await() }
    }

    // -- 조회 ---------------------------------------------------------------

    /**
     * 요청 모델(URL 문자열)에 해당하는 로컬 파일. 팩에 없으면 null(원격으로 받는다).
     * 절대 URL 이면 그대로 키로 찾고, assetBase(현재 피드 값 또는 기본값) 아래 URL 이면 접두사를 떼고 찾는다.
     */
    fun resolve(model: String): File? {
        val map = files
        if (map.isEmpty() || model.isEmpty()) return null
        map[model]?.let { return it }
        val key = manifestKeyOf(model, currentAssetBase()) ?: return null
        return map[key]
    }

    /** 게임 아이콘 경로인가. 팩에 없어도 종류별 고정 크기를 적용할 대상인지 가른다. */
    fun isGameIcon(model: String): Boolean = isGameIconUrl(model, currentAssetBase())

    private fun currentAssetBase(): String? =
        (DeckRepository.get(context).state.value as? FeedState.Ready)?.feed?.version?.assetBase

    // -- 동기화 -------------------------------------------------------------

    /**
     * 하루 한 번 호출된다(DailySyncWorker).
     *
     * manifest 만 받아 hash 가 지금 팩과 같으면 끝내고, 다를 때만 zip 을 한 번 받아 임시 폴더에 풀고
     * 검증(개수·해시)한 뒤 폴더째 교체한다. 실패하면 지금 팩을 그대로 둔다.
     */
    suspend fun sync(force: Boolean = false): IconSyncResult {
        // 워커는 빈 프로세스에서 깨어날 수 있다. 현재 hash 를 알아야 비교가 의미를 갖는다.
        load()
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val download = File(context.filesDir, DOWNLOAD_NAME)
                try {
                    val manifestText = httpGetText(BuildConfig.FEED_BASE_URL + REMOTE_DIR + MANIFEST_NAME)
                    val remote = parseManifest(manifestText)
                        ?: return@withContext IconSyncResult.Failed("아이콘 목록 형식을 읽지 못했습니다")

                    if (!force && remote.hash == _state.value.hash) {
                        markSynced()
                        _state.value = _state.value.copy(lastSyncedAt = lastSyncedAt())
                        return@withContext IconSyncResult.UpToDate
                    }

                    val zipName = remote.zip.takeIf { isPlainFileName(it) }
                        ?: return@withContext IconSyncResult.Failed("아이콘 팩 이름이 올바르지 않습니다")
                    httpDownload(BuildConfig.FEED_BASE_URL + REMOTE_DIR + zipName, download)
                    FileInputStream(download).use { installPack(it, remote, manifestText) }

                    prefs.edit().putBoolean(KEY_FROM_BUNDLE, false).apply()
                    markSynced()
                    activate(remote)
                    IconSyncResult.Updated(remote.count, remote.bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 기존 팩은 그대로 둔다. 앱이 아이콘을 잃는 일은 없다.
                    IconSyncResult.Failed(e.message ?: "네트워크 오류")
                } finally {
                    download.delete()
                    stagingDir.deleteRecursively()
                }
            }
        }
    }

    // -- 선로딩 -------------------------------------------------------------

    /**
     * 피드가 준비되면 피드가 쓰는 아이콘을 전부 미리 디코드해 메모리 캐시에 올린다(로컬이면 디코드만).
     * 목록·상세·오버레이가 처음 그릴 때 이미 캐시에 있게 해서, 탭을 바꿀 때 칸이 비는 순간을 없앤다.
     *
     * 한 번 올린 URL 은 다시 요청하지 않고 피드가 바뀌면 새 아이콘만 올린다. 팩이 바뀌면 파일이 새로 풀려
     * 캐시 키(경로+수정 시각)가 달라지므로 다시 올린다.
     *
     * 수집을 끝내지 않는 함수다. 앱 수명 스코프에서 launch 로 부른다.
     */
    suspend fun warmUp(context: Context) {
        val app = context.applicationContext
        val requested = HashSet<String>()
        var warmedHash: String? = null
        combine(DeckRepository.get(app).state, state) { feed, pack -> feed to pack.hash }
            .collect { (feedState, hash) ->
                val feed = (feedState as? FeedState.Ready)?.feed ?: return@collect
                if (hash != warmedHash) {
                    warmedHash = hash
                    requested.clear()
                }
                val loader = app.imageLoader
                for (path in iconPathsOf(feed)) {
                    val url = urlFor(feed.version.assetBase, path)
                    if (!requested.add(url)) continue
                    loader.enqueue(
                        ImageRequest.Builder(app)
                            .data(url)
                            .size(IconInterceptor.kind(url))
                            // AsyncImage 와 같은 정밀도로 디코드해야 화면 요청이 이 캐시 항목을 그대로 쓴다.
                            .precision(Precision.INEXACT)
                            // 시작 직후 수백 건의 키 계산(파일 수정 시각 조회)이 메인 스레드에 몰리지 않게.
                            .interceptorDispatcher(Dispatchers.IO)
                            .build()
                    )
                }
            }
    }

    // -- 파일 ---------------------------------------------------------------

    private fun activate(manifest: IconManifest) {
        val dir = packDir
        val map = HashMap<String, File>(manifest.files.size * 2)
        for ((key, name) in manifest.files) {
            if (!isPlainFileName(name)) continue
            val file = File(dir, name)
            if (file.isFile) map[normalizeKey(key)] = file
        }
        files = map
        _state.value = IconPackState(
            hash = manifest.hash,
            count = map.size,
            bytes = manifest.bytes,
            generatedAt = manifest.generatedAt,
            lastSyncedAt = lastSyncedAt(),
            fromBundle = prefs.getBoolean(KEY_FROM_BUNDLE, false),
        )
    }

    /** zip 을 임시 폴더에 풀어 검증하고, 통과하면 현재 팩 폴더와 바꾼다. 실패하면 예외(현재 팩은 그대로). */
    private fun installPack(zip: InputStream, manifest: IconManifest, manifestText: String) {
        val staging = stagingDir
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("아이콘 임시 폴더를 만들지 못했습니다")
        unpackAndVerify(zip, manifest, staging)
        // manifest 는 검증을 통과한 뒤에 쓴다. 임시 폴더에 manifest 가 있으면 완성본이라는 표시다.
        File(staging, MANIFEST_NAME).writeText(manifestText)
        swapIn(staging)
    }

    private fun swapIn(staging: File) {
        val current = packDir
        val retired = retiredDir
        retired.deleteRecursively()
        if (current.exists() && !current.renameTo(retired)) {
            throw IOException("기존 아이콘 폴더를 옮기지 못했습니다")
        }
        if (!staging.renameTo(current)) {
            retired.renameTo(current)   // 되돌린다
            throw IOException("새 아이콘 폴더로 바꾸지 못했습니다")
        }
        retired.deleteRecursively()
    }

    /** 교체 도중 프로세스가 죽었으면 완성된 쪽(manifest 가 있는 폴더)으로 되살린다. */
    private fun recoverInterruptedSwap() {
        val current = packDir
        if (!File(current, MANIFEST_NAME).isFile) {
            val survivor = listOf(stagingDir, retiredDir).firstOrNull { File(it, MANIFEST_NAME).isFile }
            if (survivor != null) {
                current.deleteRecursively()
                survivor.renameTo(current)
            }
        }
        stagingDir.deleteRecursively()
        retiredDir.deleteRecursively()
    }

    private fun readManifest(file: File): IconManifest? =
        runCatching { if (file.isFile) parseManifest(file.readText()) else null }.getOrNull()

    private fun readAsset(name: String): String? =
        runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()

    private fun parseManifest(text: String): IconManifest? =
        runCatching { json.decodeFromString(IconManifest.serializer(), text) }.getOrNull()
            ?.takeIf { it.schemaVersion == SCHEMA_VERSION && it.hash.isNotEmpty() && it.files.isNotEmpty() }

    private fun markSynced() = prefs.edit().putLong(KEY_SYNCED_AT, System.currentTimeMillis()).apply()

    private fun lastSyncedAt(): Long? = prefs.getLong(KEY_SYNCED_AT, 0L).takeIf { it > 0L }

    // -- 네트워크 -----------------------------------------------------------

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "TftDeckReader/${BuildConfig.VERSION_NAME}")
        }

    private fun httpGetText(url: String): String {
        val conn = openConnection(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return bodyStream(conn).bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownload(url: String, target: File) {
        val conn = openConnection(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            if (conn.contentLengthLong > MAX_ZIP_BYTES) throw IOException("아이콘 팩이 너무 큽니다")
            bodyStream(conn).use { input ->
                target.outputStream().use { output -> copyLimited(input, output, null, MAX_ZIP_BYTES) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun bodyStream(conn: HttpURLConnection): InputStream =
        if (conn.contentEncoding?.contains("gzip", true) == true) {
            GZIPInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }

    companion object {
        private const val TAG = "IconPack"

        /** 이 앱이 읽을 수 있는 manifest 형식. 수집기 SCHEMA_VERSION 과 같아야 한다. */
        const val SCHEMA_VERSION = 1
        const val DEFAULT_ASSET_BASE = "https://raw.communitydragon.org/latest/game/"

        private const val MANIFEST_NAME = "manifest.json"
        private const val REMOTE_DIR = "icons/"
        private const val BUNDLED_MANIFEST = "icons_manifest.json"
        private const val BUNDLED_ZIP = "icons.zip"
        private const val DIR_CURRENT = "icons"
        private const val DIR_STAGING = "icons_tmp"
        private const val DIR_RETIRED = "icons_old"
        private const val DOWNLOAD_NAME = "icons.zip.download"
        private const val PREFS = "icon_pack"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_FROM_BUNDLE = "from_bundle"

        /** CDragon 상대 경로는 모두 여기로 시작한다(데이터 계약 §5). */
        private const val RELATIVE_ROOT = "assets/"
        private const val LOAD_WAIT_MS = 2_500L
        private const val MAX_ZIP_BYTES = 32L * 1024 * 1024
        private const val MAX_UNPACKED_BYTES = 64L * 1024 * 1024
        private const val MAX_ENTRIES = 10_000
        private val HEX = "0123456789abcdef".toCharArray()

        @Volatile
        private var instance: IconPack? = null

        fun get(context: Context): IconPack =
            instance ?: synchronized(this) {
                instance ?: IconPack(context.applicationContext).also { instance = it }
            }

        /** lol.qq TFT 이미지(pet 소환물 초상 등). 데이터 계약상 CDragon 경로 밖에서 오는 유일한 아이콘 출처다. */
        private const val LOLQQ_TFT_IMAGE_PREFIX = "https://game.gtimg.cn/images/lol/act/img/tft/"

        /**
         * 게임 아이콘 URL 인가(팩에 없어도 종류별 고정 크기를 적용할 대상). CDragon 게임 에셋 경로와
         * lol.qq TFT 이미지만 해당한다. 프로필 아이콘·티어 엠블럼(CDragon plugins 경로)은 크게 그려질 수 있어 뺀다.
         */
        internal fun isGameIconUrl(model: String, assetBase: String?): Boolean =
            manifestKeyOf(model, assetBase) != null || model.startsWith(LOLQQ_TFT_IMAGE_PREFIX)

        /**
         * 요청 URL 을 manifest 키로 되돌린다. 게임 아이콘 경로가 아니면 null.
         * - assetBase(현재 피드 값, 없으면 기본값) 아래 URL → 접두사를 뗀 나머지.
         *   iconUrl() 이 절대 URL 을 assetBase 뒤에 붙인 경우도 나머지가 절대 URL 키가 되어 찾아진다.
         * - 슬래시로 시작하는 상대 경로(assetBase 가 아직 비었을 때 iconUrl("", path) 결과) → 앞 슬래시를 뗀다.
         */
        internal fun manifestKeyOf(model: String, assetBase: String?): String? {
            if (model.startsWith("https://") || model.startsWith("http://")) {
                var start = afterBase(model, assetBase)
                if (start < 0) start = afterBase(model, DEFAULT_ASSET_BASE)
                if (start < 0) return null
                return model.substring(start).trimStart('/').takeIf { it.isNotEmpty() }
            }
            return model.trimStart('/').takeIf { it.startsWith(RELATIVE_ROOT) }
        }

        /** model 이 base(끝 슬래시 무시) + "/" 로 시작하면 그 다음 위치, 아니면 -1. 요청마다 불리므로 할당하지 않는다. */
        private fun afterBase(model: String, base: String?): Int {
            if (base.isNullOrEmpty()) return -1
            var end = base.length
            while (end > 0 && base[end - 1] == '/') end--
            if (end == 0 || model.length <= end || model[end] != '/') return -1
            return if (model.regionMatches(0, base, 0, end)) end + 1 else -1
        }

        /** 화면의 iconUrl() 과 같은 규칙으로 URL 을 만든다(절대 URL 은 그대로). */
        internal fun urlFor(assetBase: String, path: String): String =
            if (isAbsolute(path)) path else assetBase.trimEnd('/') + "/" + path.trimStart('/')

        /** 선로딩할 아이콘: catalog 전체와 덱 유닛·아이템 아이콘(중복 제거, 순서 유지). */
        internal fun iconPathsOf(feed: DeckFeed): Set<String> {
            val out = LinkedHashSet<String>()
            fun add(icon: String?) {
                if (!icon.isNullOrBlank()) out.add(icon)
            }
            with(feed.catalog) {
                champions.forEach { add(it.icon) }
                traits.forEach { add(it.icon) }
                items.forEach { add(it.icon) }
                augments.forEach { add(it.icon) }
                // 소환물(pet)은 상점에 없어 champions 에 빠져 있다. 보드·빌드업 칸이 이 아이콘을 쓴다.
                pets.forEach { add(it.icon) }
            }
            for (deck in feed.decks) {
                for (member in deck.units) {
                    add(member.icon)
                    member.items.forEach { add(it.icon) }
                    member.itemsBackup.forEach { add(it.icon) }
                }
            }
            return out
        }

        /**
         * zip 을 [into] 에 풀고 manifest 와 맞는지 검증한다. 맞지 않으면 IOException.
         * - 항목 이름은 파일명만 허용(경로 탈출 차단), 항목 수·풀린 총량에 상한
         * - manifest.files 의 파일이 모두 있고 항목 수가 count 와 같아야 한다
         * - [packHash] 가 manifest.hash 와 같아야 한다. raw.githubusercontent.com 은 파일마다 5분씩
         *   캐시하므로 manifest 와 zip 이 서로 다른 커밋에서 올 수 있다. 섞이면 받지 않고 다음에 다시 시도한다.
         */
        internal fun unpackAndVerify(zip: InputStream, manifest: IconManifest, into: File) {
            val root = into.canonicalPath + File.separator
            val digests = HashMap<String, String>()
            var total = 0L
            ZipInputStream(zip.buffered()).use { entries ->
                while (true) {
                    val entry = entries.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (!isPlainFileName(name) || name == MANIFEST_NAME) {
                        throw IOException("허용하지 않는 zip 항목: $name")
                    }
                    val target = File(into, name)
                    if (!target.canonicalPath.startsWith(root)) throw IOException("경로 탈출 항목: $name")
                    if (name in digests) throw IOException("중복 zip 항목: $name")
                    if (digests.size >= MAX_ENTRIES) throw IOException("zip 항목이 너무 많습니다")
                    val sha1 = MessageDigest.getInstance("SHA-1")
                    target.outputStream().use { output ->
                        total += copyLimited(entries, output, sha1, MAX_UNPACKED_BYTES - total)
                    }
                    digests[name] = sha1.digest().toHex()
                }
            }
            val missing = manifest.files.values.toSet().count { it !in digests }
            if (missing > 0 || digests.size != manifest.count) {
                throw IOException("아이콘 수가 맞지 않습니다(zip ${digests.size}, 기대 ${manifest.count}, 누락 $missing)")
            }
            if (packHash(digests) != manifest.hash) throw IOException("아이콘 팩 해시가 manifest 와 다릅니다")
        }

        /**
         * 팩 해시. build_icons.py pack_hash() 와 같은 정의다:
         * 파일 이름순으로 "이름 + 공백 + 내용 sha1(hex)" 줄을 LF 로 이은 UTF-8 문자열의 sha1(hex).
         * 정의를 바꾸면 양쪽 SCHEMA_VERSION 을 함께 올려야 한다.
         */
        internal fun packHash(digests: Map<String, String>): String {
            val text = digests.keys.sorted().joinToString("\n") { "$it ${digests.getValue(it)}" }
            return MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).toHex()
        }

        internal fun isPlainFileName(name: String): Boolean =
            name.isNotEmpty() && name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it.code == 0 }

        private fun isAbsolute(path: String): Boolean =
            path.startsWith("https://") || path.startsWith("http://")

        private fun normalizeKey(key: String): String = if (isAbsolute(key)) key else key.trimStart('/')

        /** budget 바이트를 넘으면 예외. digest 가 있으면 복사하면서 함께 계산한다. */
        private fun copyLimited(input: InputStream, output: OutputStream, digest: MessageDigest?, budget: Long): Long {
            val buffer = ByteArray(16 * 1024)
            var copied = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                copied += read
                if (copied > budget) throw IOException("아이콘 팩 크기가 상한을 넘었습니다")
                digest?.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
            return copied
        }

        private fun ByteArray.toHex(): String {
            val out = CharArray(size * 2)
            for (i in indices) {
                val v = this[i].toInt() and 0xff
                out[i * 2] = HEX[v ushr 4]
                out[i * 2 + 1] = HEX[v and 0x0f]
            }
            return String(out)
        }
    }
}

/** data/icons/manifest.json (데이터 계약 §5.8). */
@Serializable
data class IconManifest(
    val schemaVersion: Int = 0,
    val hash: String = "",
    val generatedAt: String = "",
    val count: Int = 0,
    /** 내려받는 zip 의 바이트 수. */
    val bytes: Long = 0,
    val zip: String = "icons.zip",
    /** 원본 경로 또는 절대 URL → zip 안의 파일명. */
    val files: Map<String, String> = emptyMap(),
)

data class IconPackState(
    val hash: String = "",
    /** 기기에 실제로 있는 아이콘 수. */
    val count: Int = 0,
    val bytes: Long = 0,
    val generatedAt: String = "",
    val lastSyncedAt: Long? = null,
    /** 아직 새 팩을 한 번도 받지 못하고 앱에 동봉한 팩을 쓰는 중. */
    val fromBundle: Boolean = false,
) {
    val isReady: Boolean get() = hash.isNotEmpty()
}

sealed interface IconSyncResult {
    data object UpToDate : IconSyncResult
    data class Updated(val count: Int, val bytes: Long) : IconSyncResult
    data class Failed(val reason: String) : IconSyncResult
}
