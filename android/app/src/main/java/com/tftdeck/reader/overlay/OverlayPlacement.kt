package com.tftdeck.reader.overlay

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * 오버레이 창 플래그를 한 곳에서 합성한다. 가시성 토글·검색 전환·메뉴·드래그가 모두 이 값을 거치므로 서로의 플래그를 덮어쓰지 않는다.
 *
 *  - 평소: FLAG_NOT_FOCUSABLE — 포커스·키를 가져가지 않아 오버레이를 띄운 채 게임을 그대로 조작할 수 있다.
 *  - 숨김: 거기에 FLAG_NOT_TOUCHABLE — 보이지 않는 창이 다른 앱 조작을 막지 않게. 숨긴 창은 검색 중이라도 포커스를 받지 않는다.
 *  - 검색 중·⋯ 메뉴가 열린 동안(보일 때만): FLAG_NOT_FOCUSABLE 을 뺀다 — 검색은 키보드를 띄우려고, 메뉴는 뒤로 가기로 닫으려고.
 *    창 밖 터치는 그대로 뒤(게임)로 보내고(FLAG_NOT_TOUCH_MODAL), 눌렸다는 사실만 ACTION_OUTSIDE 로 받아 검색·메뉴를
 *    끝낸다(FLAG_WATCH_OUTSIDE_TOUCH).
 */
internal fun overlayWindowFlags(visible: Boolean, searching: Boolean, menuOpen: Boolean = false): Int {
    var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    flags = if (visible && (searching || menuOpen)) {
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

// -- 칩 자리 기준 배치(F1·F6) ------------------------------------------------------

/**
 * 접힌 칩이 놓인 화면 사분면(칩 가운데 기준). 펼친 패널은 칩 자리에서 화면 가운데 쪽으로 자란다 — 왼쪽 위 칩은 오른쪽 아래로,
 * 오른쪽 아래 칩은 왼쪽 위로. 머리줄은 칩과 같은 변(위·아래)에 두고 '접기'는 칩 쪽 끝에 둔다(오른쪽 칩이면 머리줄 좌우를
 * 거울로 뒤집는다). 그래서 칩을 누른 자리에 곧바로 '접기'가 온다.
 *
 * 창 gravity 도 칩 쪽 모서리로 잡는다([gravity]). 그러면 요약↔목록↔검색·넓게 보기로 창 크기가 바뀌어도 시스템이 그 모서리를
 * 제자리에 두므로 '접기'가 한 프레임도 흔들리지 않는다(왼쪽 위 gravity 로 두면 아래·오른쪽 칩에서 크기가 바뀔 때마다 창이
 * 먼저 옛 자리에 그려졌다가 옮겨졌다).
 */
enum class Quadrant(val isLeft: Boolean, val isTop: Boolean) {
    TopLeft(isLeft = true, isTop = true),
    TopRight(isLeft = false, isTop = true),
    BottomLeft(isLeft = true, isTop = false),
    BottomRight(isLeft = false, isTop = false);

    /** 창 gravity. 창 x·y 는 이 모서리 쪽 영역 변에서 창의 같은 변까지 잰 거리다([gravityOffset]). */
    internal val gravity: Int
        get() = (if (isTop) Gravity.TOP else Gravity.BOTTOM) or (if (isLeft) Gravity.LEFT else Gravity.RIGHT)
}

/** 칩 가운데가 영역 가운데보다 왼쪽·위면 왼쪽·위 사분면. 한가운데는 왼쪽·위로 본다. */
internal fun quadrantOf(anchor: IntOffset, chipSize: IntSize, areaSize: IntSize): Quadrant {
    val left = 2 * anchor.x + chipSize.width <= areaSize.width
    val top = 2 * anchor.y + chipSize.height <= areaSize.height
    return when {
        left && top -> Quadrant.TopLeft
        top -> Quadrant.TopRight
        left -> Quadrant.BottomLeft
        else -> Quadrant.BottomRight
    }
}

/**
 * 펼친 패널(창)의 왼쪽 위 자리. [anchor]·[area]·결과는 같은 좌표계(서비스는 창이 놓이는 영역의 왼쪽 위가 0,0)다.
 *
 * '접기' 버튼 가운데가 칩 가운데에 오도록 놓는다. [collapseCenter] 는 패널의 칩 쪽 모서리(왼쪽 위 사분면이면 왼쪽 위,
 * 오른쪽 아래면 오른쪽 아래)에서 '접기' 가운데까지의 거리(px, 양수)다. 칩 크기는 접힌 칩의 크기다 — 펼친 동안에는 창이
 * 패널이므로 서비스가 접혀 있을 때 잰 값을 넘긴다.
 *
 * 패널이 영역을 넘으면 넘치는 만큼만 안으로 민다. 칩 쪽 변은 여백 없이 맞춘다(가장자리에 붙인 칩도 '접기'가 칩 위에 온다).
 * 패널이 자라는 쪽 변에서는 [farMargin] 만큼 띄운다(가장자리에 딱 붙지 않게, 패널 높이 상한의 여유와 같은 8dp).
 * 패널이 영역보다 크면 칩 쪽 변에 붙여 머리줄이 화면 안에 남게 한다.
 */
internal fun expandedPlacement(
    anchor: IntOffset,
    chipSize: IntSize,
    panelSize: IntSize,
    area: IntRect,
    corner: Quadrant,
    collapseCenter: IntOffset,
    farMargin: Int = 0,
): IntOffset {
    val chipCenterX = anchor.x + chipSize.width / 2
    val chipCenterY = anchor.y + chipSize.height / 2
    val x = if (corner.isLeft) chipCenterX - collapseCenter.x else chipCenterX + collapseCenter.x - panelSize.width
    val y = if (corner.isTop) chipCenterY - collapseCenter.y else chipCenterY + collapseCenter.y - panelSize.height
    return IntOffset(
        placeOnAxis(x, panelSize.width, area.left, area.right, chipAtStart = corner.isLeft, farMargin = farMargin),
        placeOnAxis(y, panelSize.height, area.top, area.bottom, chipAtStart = corner.isTop, farMargin = farMargin),
    )
}

/** 한 축에서 패널을 [start]~[end] 안으로 맞춘다. 칩 쪽 변은 여백 없이, 반대쪽(자라는 쪽) 변은 [farMargin] 을 둔다. */
private fun placeOnAxis(natural: Int, size: Int, start: Int, end: Int, chipAtStart: Boolean, farMargin: Int): Int {
    val slack = end - start - size
    if (slack < 0) return if (chipAtStart) start else end - size
    val margin = farMargin.coerceIn(0, slack)
    val low = start + if (chipAtStart) 0 else margin
    val high = end - size - if (chipAtStart) margin else 0
    return natural.coerceIn(low, high)
}

/**
 * 창 왼쪽 위 자리(영역 기준 px)를 [corner] gravity 의 창 x·y 로 바꾼다. 오른쪽 gravity 의 x 는 영역 오른쪽 변에서 창 오른쪽
 * 변까지, 아래 gravity 의 y 는 영역 아래 변에서 창 아래 변까지다. 같은 식이 거꾸로(창 x·y → 왼쪽 위)도 성립한다.
 * 칩 쪽 모서리를 기준으로 잡았으므로 밀려나지 않은 패널은 크기가 바뀌어도 이 값이 그대로다.
 */
internal fun gravityOffset(position: IntOffset, size: IntSize, areaSize: IntSize, corner: Quadrant): IntOffset =
    IntOffset(
        if (corner.isLeft) position.x else areaSize.width - position.x - size.width,
        if (corner.isTop) position.y else areaSize.height - position.y - size.height,
    )

/**
 * 끌어서 옮긴 칩 자리. 펼친 창을 끌어도 칩 자리(앵커)를 창 좌표로 덮어쓰지 않고 끈 만큼 더한다(F6 — 예전에는 맨 위로 올라간
 * 목록 창의 y 가 칩 자리로 저장돼 접으면 칩이 맨 위에 가 있었다). 칩이 화면 밖으로 나가지 않게 칩 크기로 맞춘다.
 */
internal fun dragAnchor(anchor: IntOffset, dx: Int, dy: Int, chipSize: IntSize, areaSize: IntSize): IntOffset {
    val placed = clampOverlayPosition(
        OverlayPosition(anchor.x + dx, anchor.y + dy),
        chipSize.width,
        chipSize.height,
        areaSize.width,
        areaSize.height,
    )
    return IntOffset(placed.x, placed.y)
}

/**
 * API 30 미만에서 검색 중 창 y(S14). 그 버전은 키보드 위치(WindowMetrics)를 알 수 없어 가로 화면에서는 키보드가 화면 대부분을
 * 덮는 것으로 보고 맨 위(0)로 올린다. 세로는 키보드 위 공간이 넉넉해 그대로 둔다. 검색이 끝나면 칩 자리 기준으로 되돌린다.
 */
internal fun searchFallbackY(y: Int, landscape: Boolean): Int = if (landscape) 0 else y

/** 머리줄 높이(dp). 머리줄(OverlayHeader)과 창 자리 계산(서비스)이 같은 값을 쓴다. */
internal const val HEADER_HEIGHT_DP = 36

/** 머리줄 버튼(접기·←·⋯) 폭(dp). 누름 영역 최소 44×36dp(MASTER 규칙 9). */
internal const val HEADER_BUTTON_WIDTH_DP = 44

/** 펼친 패널이 자라는 쪽 화면 가장자리에서 띄우는 여백(dp). 패널 높이 상한의 여유(PANEL_SCREEN_MARGIN)와 같다. */
internal const val PANEL_FAR_MARGIN_DP = 8
