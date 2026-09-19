package com.tftdeck.reader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tftdeck.reader.data.DeckRepository
import com.tftdeck.reader.data.FeedState
import com.tftdeck.reader.data.IconInterceptor
import com.tftdeck.reader.data.IconPack
import com.tftdeck.reader.data.StatsRepository
import com.tftdeck.reader.sync.DailySyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class TftApp : Application(), ImageLoaderFactory {

    /** 앱 수명 동안 도는 작업(캐시 로드, 아이콘 선로딩)용. 하나가 실패해도 나머지는 계속된다. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 캐시(없으면 동봉 스냅샷)를 먼저 올린다. 첫 화면이 비지 않도록.
        // 동봉 스냅샷으로 시작했으면(설치 직후 등) 주기 작업의 15분 대기 없이 곧바로 한 번 받는다(N3).
        appScope.launch {
            val decks = DeckRepository.get(this@TftApp)
            decks.load()
            if ((decks.state.value as? FeedState.Ready)?.fromBundle == true) {
                DailySyncWorker.syncNow(this@TftApp)
            }
        }
        // 도감 통계도 캐시(없으면 동봉 스냅샷)를 먼저 올린다. 도감 탭을 처음 열 때 비지 않도록.
        appScope.launch {
            StatsRepository.get(this@TftApp).load()
        }

        // 아이콘 팩을 올리고(첫 실행이면 동봉 팩을 푼다), 피드가 준비되면 아이콘을 미리 디코드해 둔다.
        appScope.launch {
            val icons = IconPack.get(this@TftApp)
            icons.load()
            icons.warmUp(this@TftApp)
        }

        DailySyncWorker.schedule(this)
    }

    /**
     * 모든 AsyncImage 와 오버레이가 함께 쓰는 ImageLoader.
     *
     * - IconInterceptor: 아이콘 URL 을 로컬 팩 파일로 바꾸고 종류별 고정 크기로 디코드한다.
     * - respectCacheHeaders(false): CDragon 은 max-age 3600 이라 한 시간마다 304 왕복이 생기는데,
     *   측정상 304 도 전체 다운로드만큼 느렸다. 팩에 없는 아이콘도 한 번 받으면 디스크 캐시로 끝낸다.
     * - maxRequestsPerHost 16: OkHttp 기본 5면 한 화면 아이콘 수십 개가 줄을 선다(측정 5병렬 1.47 s, 16병렬 1.00 s).
     * - crossfade 끔: 로컬 디코드는 즉시 끝나 페이드가 깜빡임처럼 보인다.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .okHttpClient {
                OkHttpClient.Builder()
                    .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
                    .build()
            }
            .components { add(IconInterceptor(IconPack.get(this@TftApp))) }
            .crossfade(false)
            .build()
}
