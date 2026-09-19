package com.tftdeck.reader.overlay

import android.view.WindowManager

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
 * 검색 중 키보드를 피해 올린 창 y. 창 아래(영역 위 [areaTop] + y + [height], 화면 좌표)가 키보드 위([imeTop], 화면 좌표)를
 * 넘으면 넘친 만큼 올린다. 영역 위(0)보다 위로는 올리지 않는다 — 가로 화면처럼 키보드 위 공간이 창보다 작으면 창 위쪽
 * (검색창과 첫 후보들)이 보이는 데서 멈춘다. 넘치지 않으면 그대로 둔다(내리는 것은 검색이 끝날 때 한 번).
 * 키보드 위치를 화면 기준으로 받으므로 같은 상태에서 몇 번을 불러도 같은 값이다.
 */
internal fun liftAboveIme(y: Int, height: Int, areaTop: Int, imeTop: Int): Int {
    val overflow = areaTop + y + height - imeTop
    return if (overflow <= 0) y else (y - overflow).coerceAtLeast(0).coerceAtMost(y)
}

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
