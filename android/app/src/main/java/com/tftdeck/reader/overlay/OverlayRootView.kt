package com.tftdeck.reader.overlay

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.FrameLayout

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
 * 회전으로 크기가 바뀔 때마다 온다), 키보드 인셋이 바뀜([onImeInsetsChanged], API 30 이상).
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

    /**
     * 이 창의 인셋이 바뀌었고 키보드가 창 아래를 가리고 있다. 가린 높이 값은 넘기지 않는다 — 창을 옮긴 직후에도 옛 틀 기준
     * 값이 한 번 더 와서 믿을 수 없다. 서비스가 화면 기준 키보드 위치로 다시 잰다.
     */
    var onImeInsetsChanged: () -> Unit = {}

    /** 이번 검색에서 창이 포커스를 한 번이라도 받았는지. 받기 전의 '포커스 없음'은 끝낼 신호가 아니다. */
    private var focusedWhileSearching = false

    init {
        // 이 창의 인셋은 창 틀 기준이라 IME 아래쪽 값은 '창 아래가 키보드에 가린 높이'다(SOFT_INPUT_ADJUST_NOTHING 이어도 온다).
        // 키보드가 뜨거나 창이 커져 가리기 시작한 때를 알리는 데만 쓴다. API 30 미만은 ADJUST_NOTHING 창에 IME 인셋이 오지 않아 올리지 않는다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setOnApplyWindowInsetsListener { view, insets ->
                val ime = WindowInsets.Type.ime()
                if (insets.isVisible(ime) && insets.getInsets(ime).bottom > 0) onImeInsetsChanged()
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
