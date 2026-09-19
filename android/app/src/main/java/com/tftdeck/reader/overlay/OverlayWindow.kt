package com.tftdeck.reader.overlay

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * 오버레이 창 플래그를 한 곳에서 합성한다. 가시성 토글·검색 전환·드래그가 모두 이 값을 거치므로 서로의 플래그를 덮어쓰지 않는다.
 *
 *  - 평소: FLAG_NOT_FOCUSABLE — 포커스·키를 가져가지 않아 오버레이를 띄운 채 게임을 그대로 조작할 수 있다.
 *  - 숨김: 거기에 FLAG_NOT_TOUCHABLE — 보이지 않는 창이 다른 앱 조작을 막지 않게. 숨긴 창은 검색 중이라도 포커스를 받지 않는다.
 *  - 검색 중(보일 때만): FLAG_NOT_FOCUSABLE 을 빼서 키보드가 뜨게 한다. 창 밖 터치는 그대로 뒤(게임)로 보내고
 *    (FLAG_NOT_TOUCH_MODAL), 눌렸다는 사실만 ACTION_OUTSIDE 로 받아 검색을 끝낸다(FLAG_WATCH_OUTSIDE_TOUCH).
 */
internal fun overlayWindowFlags(visible: Boolean, searching: Boolean): Int {
    var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    flags = if (visible && searching) {
        flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    } else {
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }
    if (!visible) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    return flags
}

/**
 * 검색 중 키보드가 떠도 창을 밀거나 줄이지 않는다. 떠 있는 작은 창이라 밀면 머리줄이 잘리고,
 * 사용자가 옮겨 둔 자리가 기준이어야 한다. 키보드 상태는 입력칸이 직접 띄우고 내린다.
 */
internal const val OVERLAY_SOFT_INPUT_MODE = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

/**
 * 오버레이 창의 뿌리 뷰. ComposeView 를 감싼다.
 *
 * 창은 검색하는 동안에만 포커스를 받는다(키보드). 포커스를 쥔 채 남으면 게임의 뒤로 가기·키 입력이 오버레이로 오므로,
 * 검색을 끝낼 신호를 여기서 모두 받아 서비스에 넘긴다([onEndSearch]):
 *  - 뒤로 가기: 키보드(IME)보다 먼저 받는다(dispatchKeyEventPreIme). IME 가 먼저 받으면 키보드만 내려가고
 *    창은 포커스를 쥔 채 남는다. 입력칸 밖에 포커스가 있을 때를 위해 dispatchKeyEvent 에서도 받는다.
 *  - 창 밖 누름: FLAG_WATCH_OUTSIDE_TOUCH 로 오는 ACTION_OUTSIDE. 그 터치 자체는 뒤의 게임으로 간다.
 *    키보드 창은 오버레이보다 위라 키보드를 누르는 것은 여기로 오지 않는다.
 *  - 포커스를 잃음: 알림창을 내리는 등 다른 창이 포커스를 가져가면 검색을 접는다.
 */
internal class OverlayRootView(context: Context) : FrameLayout(context) {

    /** 지금 검색 중인지. 서비스 상태를 그대로 읽는다. */
    var isSearching: () -> Boolean = { false }

    /** 검색을 끝내라는 신호. 서비스가 키보드를 내리고 FLAG_NOT_FOCUSABLE 을 되돌린다. */
    var onEndSearch: () -> Unit = {}

    /** 이번 검색에서 창이 포커스를 한 번이라도 받았는지. 받기 전의 '포커스 없음'은 끝낼 신호가 아니다. */
    private var focusedWhileSearching = false

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean =
        consumeBack(event) || super.dispatchKeyEventPreIme(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        consumeBack(event) || super.dispatchKeyEvent(event)

    /** 검색 중 뒤로 가기는 누름·뗌을 모두 삼키고, 뗄 때 검색을 끝낸다. */
    private fun consumeBack(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK || !isSearching()) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onEndSearch()
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (isSearching()) onEndSearch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        when {
            !isSearching() -> focusedWhileSearching = false
            hasWindowFocus -> focusedWhileSearching = true
            focusedWhileSearching -> {
                focusedWhileSearching = false
                onEndSearch()
            }
        }
    }
}
