package com.tftdeck.reader.overlay

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

/**
 * 오버레이 창의 뿌리 뷰. ComposeView 를 감싼다.
 *
 * 창은 검색하는 동안에만 포커스를 받는다(키보드). 포커스를 쥔 채 남으면 게임의 뒤로 가기·키 입력이 오버레이로 오므로,
 * 검색을 끝낼 신호를 여기서 모두 받아 서비스에 넘긴다([onEndSearch]):
 *  - 뒤로 가기: 키보드(IME)보다 먼저 받는다(dispatchKeyEventPreIme). IME 가 먼저 받으면 키보드만 내려가고
 *    창은 포커스를 쥔 채 남는다. 입력칸 밖에 포커스가 있을 때를 위해 dispatchKeyEvent 에서도 받는다.
 *  - 키보드가 스스로 내려감: 키보드 앱의 내리기 버튼·IME 쪽 뒤로 제스처 등. 이번 검색에서 키보드가 한 번 보였다가
 *    사라지면 [IME_HIDE_RECHECK_MS] 뒤에 다시 보고 그래도 없으면 끝낸다(회전 등 잠깐 숨는 것은 거른다). API 30 이상.
 *  - 창 밖 누름: FLAG_WATCH_OUTSIDE_TOUCH 로 오는 ACTION_OUTSIDE. 그 터치 자체는 뒤의 게임으로 간다(삼키지 않는다 —
 *    게임 누름 하나를 잃는 것도 오조작이다). 키보드 창은 오버레이보다 위라 키보드를 누르는 것은 여기로 오지 않는다.
 *  - 창 안 빈 곳 누름: 후보·칩·버튼처럼 누름을 받는 요소에 닿지 않은 누름. 창 안이라 게임으로 넘길 수 없고 끝내기 신호로만 쓴다.
 *  - 포커스를 잃음: 알림창을 내리는 등 다른 창이 포커스를 가져가면 검색을 접는다.
 *  - 검색 줄의 '완료' 버튼: [requestEndSearch].
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

    /** 이번 검색에서 키보드가 한 번이라도 보였는지. 보이기 전의 '키보드 없음'(검색을 막 시작함)은 끝낼 신호가 아니다. */
    private var imeShownWhileSearching = false

    /** 키보드가 사라지고 [IME_HIDE_RECHECK_MS] 뒤에도 여전히 없으면 검색을 끝낸다. */
    private val endIfImeStillHidden = Runnable {
        if (isSearching() && imeShownWhileSearching && !imeVisibleNow()) {
            imeShownWhileSearching = false
            onEndSearch()
        }
    }

    init {
        // 이 창의 인셋은 창 틀 기준이라 IME 아래쪽 값은 '창 아래가 키보드에 가린 높이'다(SOFT_INPUT_ADJUST_NOTHING 이어도 온다).
        // 가린 높이는 키보드가 뜨거나 창이 커져 가리기 시작한 때를 알리는 데만 쓰고, 보임 여부는 검색 중 키보드가 스스로
        // 내려갔는지 보는 데 쓴다. 리스너는 늘 달려 있다. API 30 미만은 ADJUST_NOTHING 창에 IME 인셋이 오지 않아
        // 창을 올리지도, 키보드 내려감을 알아채지도 못한다 — 그때는 검색 줄의 '완료'가 공통 탈출구다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setOnApplyWindowInsetsListener { view, insets ->
                val ime = WindowInsets.Type.ime()
                val imeVisible = insets.isVisible(ime)
                if (imeVisible && insets.getInsets(ime).bottom > 0) onImeInsetsChanged()
                trackIme(imeVisible)
                view.onApplyWindowInsets(insets)
            }
        }
    }

    /** 검색 중 키보드가 '보임 → 안 보임'으로 바뀌면 잠깐 뒤 다시 확인하도록 건다. */
    private fun trackIme(visible: Boolean) {
        when {
            !isSearching() -> {
                imeShownWhileSearching = false
                removeCallbacks(endIfImeStillHidden)
            }
            visible -> {
                imeShownWhileSearching = true
                removeCallbacks(endIfImeStillHidden)
            }
            imeShownWhileSearching -> {
                removeCallbacks(endIfImeStillHidden)
                postDelayed(endIfImeStillHidden, IME_HIDE_RECHECK_MS)
            }
        }
    }

    private fun imeVisibleNow(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true

    /** 검색 줄의 '완료' 버튼. 검색 중일 때만 끝낸다(두 번 눌려도 안전하다). */
    fun requestEndSearch() {
        if (isSearching()) onEndSearch()
    }

    /**
     * WRAP_CONTENT 창은 먼저 대화상자 폭(config_prefDialogWidth, 폰은 320dp)으로 재고, 뿌리가 '더 넓어야 한다'
     * (MEASURED_STATE_TOO_SMALL)고 할 때만 화면 폭으로 다시 잰다(ViewRootImpl.measureHierarchy). 그대로 두면 창이 늘
     * 320dp 에 묶여 티어 카드를 켠 목록 머리줄에서 접기·앱 열기·닫기 버튼이 밀려 사라지고, 넓게 보기(380dp)도 되지 않았다.
     * 마지막(화면 폭) 측정에서는 이 표시를 보지 않는다.
     *
     * 이 표시는 이번 폭을 다 쓴 때(내용이 더 넓기를 바라 잘린 때)만 붙인다. 늘 붙이면 들어맞는 크기인데도 매번 세 번씩
     * 다시 재어(대화상자 폭 → 중간 → 화면 폭) 창을 옮길 때마다 패널 전체를 세 번 측정했다.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST &&
            measuredWidth >= MeasureSpec.getSize(widthMeasureSpec)
        ) {
            setMeasuredDimension(measuredWidthAndState or MEASURED_STATE_TOO_SMALL, measuredHeightAndState)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onWindowLayout()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(endIfImeStillHidden)
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean =
        consumeBack(event) || super.dispatchKeyEventPreIme(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        consumeBack(event) || consumeStrayConfirm(event) || super.dispatchKeyEvent(event)

    /** 검색 중 뒤로 가기는 누름·뗌을 모두 삼키고, 뗄 때 검색을 끝낸다. */
    private fun consumeBack(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK || !isSearching()) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onEndSearch()
        return true
    }

    /**
     * 검색이 끝난 뒤 늦게 도착한 Enter·확인 키는 삼킨다. 하드웨어 Enter 로 검색을 끝내면 뗌이 뒤따라 와서, 입력칸이 사라진
     * 자리의 다음 포커스(머리줄 구간 칩)를 눌러 구간이 바뀌고 앱에도 저장됐다. 평소 창은 포커스를 받지 않아 키가 오지 않으므로
     * 이것으로 잃는 동작은 없다. 검색 중 Enter 는 입력칸이 받는다(TokenSearchField).
     */
    private fun consumeStrayConfirm(event: KeyEvent): Boolean =
        !isSearching() && event.keyCode in CONFIRM_KEYS

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (isSearching()) onEndSearch()
            return true
        }
        val handled = super.dispatchTouchEvent(event)
        // Compose 는 누름을 받는 요소(후보·칩·입력칸·버튼·목록)에 닿았을 때만 true 를 돌려준다. 닿지 않은 첫 누름은 창 안의
        // 빈 곳(패널 여백, 짧은 패널 아래 티어 카드 옆 등)이다.
        if (!handled && event.actionMasked == MotionEvent.ACTION_DOWN && isSearching()) onEndSearch()
        return handled
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        when {
            !isSearching() -> focusedWhileSearching = false
            hasWindowFocus -> {
                // 검색을 시작해 창이 처음 포커스를 받았다. 키보드는 이 뒤에 뜬다 — 지난 검색의 '키보드 보였음'을 지운다.
                if (!focusedWhileSearching) imeShownWhileSearching = false
                focusedWhileSearching = true
            }
            focusedWhileSearching -> {
                focusedWhileSearching = false
                onEndSearch()
            }
        }
    }

    private companion object {
        /** 키보드가 사라진 뒤 다시 확인할 때까지. 회전·키보드 바꿈으로 잠깐 숨는 것을 거른다. */
        const val IME_HIDE_RECHECK_MS = 150L

        /** 검색 밖에서 삼킬 확인 키. */
        val CONFIRM_KEYS = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER)
    }
}

/** 이 뷰를 담은 오버레이 창의 뿌리. 오버레이 창 밖(앱 화면·미리보기)이면 null. */
internal fun View.overlayRoot(): OverlayRootView? {
    var view: View? = this
    while (view != null) {
        if (view is OverlayRootView) return view
        view = view.parent as? View
    }
    return null
}
