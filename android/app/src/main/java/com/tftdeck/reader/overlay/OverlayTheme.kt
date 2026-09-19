package com.tftdeck.reader.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.theme.FloaColors
import com.tftdeck.reader.ui.tierColor

// 오버레이 전용 색. 게임 위에 떠 있어야 해서 앱 테마와 무관하게 어두운 반투명으로 고정한다.
internal val OverlayScrim = FloaColors.Surface.copy(alpha = 0.95f)
internal val OverlayHeader = FloaColors.SurfaceElevated
internal val OverlayBorder = FloaColors.Secondary.copy(alpha = 0.3f)
internal val OverlayText = FloaColors.OnSurface
internal val OverlayMuted = FloaColors.OnSurfaceVariant
internal val OverlayAccent = FloaColors.Secondary
internal val OverlayAmber = FloaColors.Gold

/**
 * 목록 줄 설명 글자. OnSurfaceVariant(바탕 대비 6.8, 흰 게임 화면이 비치면 6.0)는 7:1 에 못 미쳐 본문색을 78% 로 낮춰 쓴다:
 * 바탕 대비 9.3, 흰 화면이 5% 비쳐도 8.3. 별칭(본문색·굵게·12sp)과는 크기·굵기·밝기로 구분된다.
 */
internal val OverlaySubtext = FloaColors.OnSurface.copy(alpha = 0.78f)

/** 한 번 더 누르면 닫히는 상태의 닫기 버튼. 어두운 글자와 대비 약 7:1. */
internal val OverlayDanger = FloaColors.Negative
internal val OverlayOnDanger = FloaColors.Background

/** 오버레이용 코스트 테두리 색. 어두운 배경 위에서 읽히도록 앱 테마와 따로 둔다. */
internal fun costTint(cost: Int?): Color = when (cost) {
    1 -> Color(0xFF6E7C78)
    2 -> Color(0xFF3E9E86)
    3 -> Color(0xFF4E82B4)
    4 -> Color(0xFF9A5FB0)
    5 -> Color(0xFFC69A3C)
    else -> Color(0xFF4A5450)
}

/** 통계 등급은 등급색, 편집 등급으로 대신 보여 줄 때는 편집 등급색. 등급이 없으면 흐린 색. */
internal fun gradeTint(deck: Deck, bucket: String): Color {
    if (deck.isGlobalOnly) return OverlayAccent
    val grade = deck.gradeFor(bucket) ?: return OverlayMuted
    return if (deck.showsEditorialGrade(bucket)) tierColor(grade) else gradeColor(grade)
}

/** 오버레이는 칸이 좁아 metatft 전용 덱을 등급 글자 대신 'G' 한 글자로 구분한다. */
internal fun gradeText(deck: Deck, bucket: String): String =
    if (deck.isGlobalOnly) "G" else deck.gradeFor(bucket) ?: "-"

/**
 * 펼친 패널 높이 = 화면(창이 놓이는 영역) 높이 − 이 여유. 창 자리는 서비스가 화면 안으로 맞추므로(clampOverlayPosition)
 * 패널이 영역보다 작기만 하면 머리줄부터 바닥까지 다 보인다. 여유는 위아래 가장자리에 딱 붙지 않게 하는 몫이다.
 */
internal val PANEL_SCREEN_MARGIN = 8.dp
