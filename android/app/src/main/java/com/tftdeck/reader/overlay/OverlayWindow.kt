package com.tftdeck.reader.overlay

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
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

/** 창 좌상단 자리(px). LayoutParams 의 x·y 와 같은 기준이다. */
internal data class OverlayPosition(val x: Int, val y: Int)

/**
 * 창이 화면 밖으로 나가지 않게 자리를 맞춘다: 0 ≤ x ≤ 영역 폭 − 창 폭, 0 ≤ y ≤ 영역 높이 − 창 높이.
 *
 * 창에 FLAG_LAYOUT_NO_LIMITS 가 있어 시스템은 창을 화면 안으로 밀어 주지 않는다(끌거나 커지면 그대로 밖으로 나가
 * 되찾을 수 없었다). 영역은 시스템이 이 창을 놓는 기준 — 지금 보이는 시스템 바와 컷아웃을 뺀 곳 — 이라
 * 맞춘 창은 상태 표시줄·내비게이션 막대 밑으로도 들어가지 않는다.
 * 창이 영역보다 크면(가로 화면에서 펼친 목록 등) 머리줄이 보이도록 위·왼쪽(0)에 붙인다.
 */
internal fun clampOverlayPosition(
    position: OverlayPosition,
    width: Int,
    height: Int,
    areaWidth: Int,
    areaHeight: Int,
): OverlayPosition {
    val maxX = (areaWidth - width).coerceAtLeast(0)
    val maxY = (areaHeight - height).coerceAtLeast(0)
    return OverlayPosition(position.x.coerceIn(0, maxX), position.y.coerceIn(0, maxY))
}

/**
 * 검색 중 키보드가 창 아래를 [imeOverlap] 만큼 가리면 그만큼 창을 올린다. 영역 위(0)보다 위로는 올리지 않는다 —
 * 가로 화면처럼 키보드 위 공간이 창보다 작으면 창 위쪽(검색창과 첫 후보들)이 보이는 데서 멈춘다.
 * 가린 게 없으면 그대로 둔다(올린 뒤 겹침이 0 이 되어도 다시 내리지 않는다 — 내리는 것은 검색이 끝날 때 한 번).
 */
internal fun liftAboveIme(y: Int, imeOverlap: Int): Int =
    if (imeOverlap <= 0) y else (y - imeOverlap).coerceAtLeast(0).coerceAtMost(y)

/**
 * 창이 떠 있는 방향별로 자리를 따로 기억한다. 게임(가로)에서 놓아 둔 자리가 홈 화면(세로)에서 한 번 옮겼다고
 * 흐트러지지 않게. 세로는 예전 키를 그대로 쓰고, 가로 키가 아직 없으면 예전 값에서 시작한다.
 */
internal fun overlayPositionKeys(landscape: Boolean): Pair<String, String> =
    if (landscape) "x_land" to "y_land" else "x" to "y"

/**
 * 검색하는 동안 머리줄을 접을 만큼 낮은 창인지. 가로 폰(높이 약 360~410dp)은 키보드가 화면의 60% 남짓을 덮어
 * 머리줄(약 38dp)까지 두면 후보가 한두 줄밖에 안 보인다. 세로(800dp 안팎)는 넉넉해 그대로 둔다.
 */
internal fun hideHeaderWhileSearching(availableHeightDp: Float): Boolean = availableHeightDp < SHORT_OVERLAY_AREA_DP

private const val SHORT_OVERLAY_AREA_DP = 480f

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
 *
 * 창 자리를 화면 안에 맞추는 데 필요한 신호도 여기서 넘긴다: 창을 다시 배치함([onWindowLayout] — 펼침·넓게 보기·
 * 회전으로 크기가 바뀔 때마다 온다), 키보드가 창 아래를 가림([onImeOverlap], API 30 이상).
 */
internal class OverlayRootView(context: Context) : FrameLayout(context) {

    /** 지금 검색 중인지. 서비스 상태를 그대로 읽는다. */
    var isSearching: () -> Boolean = { false }

    /** 검색을 끝내라는 신호. 서비스가 키보드를 내리고 FLAG_NOT_FOCUSABLE 을 되돌린다. */
    var onEndSearch: () -> Unit = {}

    /**
     * 창을 다시 배치했다(크기가 바뀌었거나 회전 등으로 다시 쟀다). 서비스가 지금 크기로 자리를 화면 안에 다시 맞춘다.
     * 크기가 같은 회전(접힌 칩)도 놓치지 않도록 onSizeChanged 가 아니라 배치마다 부른다 — 맞출 게 없으면 서비스가 창을 건드리지 않는다.
     */
    var onWindowLayout: () -> Unit = {}

    /** 키보드가 창 아래를 가린 높이(px). 가리지 않거나 키보드가 없으면 0. */
    var onImeOverlap: (Int) -> Unit = {}

    /** 이번 검색에서 창이 포커스를 한 번이라도 받았는지. 받기 전의 '포커스 없음'은 끝낼 신호가 아니다. */
    private var focusedWhileSearching = false

    init {
        // 이 창의 인셋은 창 틀 기준이라 IME 아래쪽 값이 곧 '창 아래가 키보드에 가린 높이'다(SOFT_INPUT_ADJUST_NOTHING 이어도 온다).
        // API 30 미만은 ADJUST_NOTHING 창에 IME 인셋이 오지 않아 올리지 않는다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setOnApplyWindowInsetsListener { view, insets ->
                val ime = WindowInsets.Type.ime()
                onImeOverlap(if (insets.isVisible(ime)) insets.getInsets(ime).bottom else 0)
                view.onApplyWindowInsets(insets)
            }
        }
    }

    /**
     * WRAP_CONTENT 창은 먼저 대화상자 폭(config_prefDialogWidth, 폰은 320dp)으로 재고, 뿌리가 '더 넓어야 한다'
     * (MEASURED_STATE_TOO_SMALL)고 할 때만 화면 폭으로 다시 잰다(ViewRootImpl.measureHierarchy). 그대로 두면 창이 늘
     * 320dp 에 묶여 티어 카드를 켠 목록 머리줄에서 접기·앱 열기·닫기 버튼이 밀려 사라지고, 넓게 보기(380dp)도 되지 않았다.
     * 마지막(화면 폭) 측정에서는 이 표시를 보지 않으므로 늘 붙여도 된다.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            setMeasuredDimension(measuredWidthAndState or MEASURED_STATE_TOO_SMALL, measuredHeightAndState)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onWindowLayout()
    }

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
