package com.tftdeck.reader.overlay

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 앱 화면과 오버레이 서비스가 같은 프로세스 안에서 주고받는 상태.
 */
object OverlayState {

    /**
     * 앱 화면이 보이는 중인지. 보이는 동안에는 오버레이를 숨긴다.
     * 같은 정보가 앱 위에 한 번 더 떠서 앱을 가릴 이유가 없다. 앱을 나가면 다시 나타난다.
     */
    val appVisible = MutableStateFlow(false)

    /**
     * 오버레이가 실제로 떠 있는지. 서비스가 직접 갱신한다.
     * 화면 쪽 변수로만 들고 있으면 앱을 새로 열 때마다 꺼짐으로 초기화돼
     * 실제로는 떠 있는데 설정 스위치가 꺼져 보인다.
     */
    val running = MutableStateFlow(false)
}
