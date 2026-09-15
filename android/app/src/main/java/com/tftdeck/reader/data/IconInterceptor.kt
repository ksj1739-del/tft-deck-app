package com.tftdeck.reader.data

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.size.Size

/**
 * Coil 요청의 아이콘 URL 을 아이콘 팩의 로컬 파일로 바꾸고, 종류별 고정 크기로 디코드하게 한다.
 *
 * 화면 코드(AsyncImage(model = url))는 그대로 두고 여기서만 바꾼다. 그래서 호출 지점을 고치지 않아도
 * 첫 실행부터 네트워크 없이 아이콘이 그려진다. 팩에 없는 아이콘은 원격(CDragon) 그대로 간다.
 *
 * 크기를 종류별로 고정하는 이유: 같은 아이콘을 11/19/24 dp 처럼 여러 크기로 그리면, 작게 샘플링돼
 * 캐시된 비트맵을 Coil 이 더 큰 요청에서 무효로 보고 다시 디코드한다. 한 크기로 묶으면 모든 호출
 * 지점이 메모리 캐시 한 항목을 함께 쓴다. (메모리 캐시 키는 변환이 없으면 크기를 포함하지 않는다.)
 */
class IconInterceptor(private val pack: IconPack) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data as? String ?: return chain.proceed(request)

        // 첫 실행에는 동봉 팩을 푸는 동안 잠깐 기다린다. 그사이 요청이 원격으로 새지 않도록.
        pack.awaitLoaded()

        val file = pack.resolve(data)
        if (file == null && !pack.isGameIcon(data)) {
            // 프로필 아이콘·티어 엠블럼 같은 다른 이미지는 크게 그려질 수 있어 크기를 건드리지 않는다.
            return chain.proceed(request)
        }

        val px = kind(data)
        val sized = chain.withSize(Size(px, px))
        if (file == null) return sized.proceed(request)

        val local = sized.proceed(request.newBuilder().data(file).build())
        // 팩 교체 순간이거나 파일이 깨졌으면 원격으로 한 번 더 시도한다. 빈 칸보다 늦게라도 그리는 편이 낫다.
        return if (local is ErrorResult) sized.proceed(request) else local
    }

    companion object {
        const val PX_CHAMPION = 128
        const val PX_TRAIT = 32
        const val PX_DEFAULT = 64

        /**
         * 경로로 종류별 한 변 픽셀을 정한다(챔피언 128, 특성 32, 아이템·증강·그 외 64).
         * 수집기 build_icons.py 의 icon_px() 와 같은 규칙이어야 팩 파일 크기와 디코드 크기가 맞는다.
         *
         * `/original-image/` 는 pet 소환물 초상(lol.qq chess.js originalImage, 128×128 확인)이다.
         * CDragon 상대 경로가 아니라 절대 URL 로 오지만 챔피언 칸에 같은 크기로 그려지므로 챔피언과 같이 둔다.
         */
        fun kind(path: String): Int = when {
            path.contains("/characters/", ignoreCase = true) -> PX_CHAMPION
            path.contains("/original-image/", ignoreCase = true) -> PX_CHAMPION
            path.contains("/traiticons/", ignoreCase = true) -> PX_TRAIT
            else -> PX_DEFAULT
        }
    }
}
