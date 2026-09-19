package com.tftdeck.reader.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tftdeck.reader.data.Deck
import com.tftdeck.reader.ui.components.GradeBadgeStyle
import com.tftdeck.reader.ui.gradeColor
import com.tftdeck.reader.ui.theme.FloaColors

// 오버레이 전용 색. 게임 위에 떠 있어서 앱 테마와 무관하게 고정한다.

/**
 * 패널·접힌 칩 바탕. 불투명 100%(MASTER 오버레이 추가 규칙) — 예전 95% 는 게임 화면을 보여 주는 실익 없이
 * 밝은 게임 보드가 글자 뒤에 무늬로 비쳤다(R11·V28). 배경 블러는 API 31 미만·게임 GPU 부하 때문에 쓰지 않는다.
 */
internal val OverlayScrim = FloaColors.Surface
internal val OverlayHeader = FloaColors.SurfaceElevated
internal val OverlayBorder = FloaColors.Secondary.copy(alpha = 0.3f)
internal val OverlayText = FloaColors.OnSurface
internal val OverlayMuted = FloaColors.OnSurfaceVariant
internal val OverlayAccent = FloaColors.Secondary
internal val OverlayAmber = FloaColors.Gold

/**
 * 목록 줄 설명·요약 운영 줄 글자. OnSurfaceVariant(패널 대비 약 6.7)는 7:1 에 못 미쳐 본문색을 78% 로 낮춰 쓴다:
 * 패널 대비 약 9.3. 별칭(본문색·굵게)과는 굵기·밝기로 구분된다. 패널이 불투명이라 뒤 게임 화면에 따라 대비가 흔들리지 않는다.
 */
internal val OverlaySubtext = FloaColors.OnSurface.copy(alpha = 0.78f)

/** 한 번 더 누르면 닫히는 상태의 닫기 버튼. 어두운 글자와 대비 약 7:1. */
internal val OverlayDanger = FloaColors.Negative
internal val OverlayOnDanger = FloaColors.Background

/**
 * 오버레이 글자(MASTER 규칙 1 · 오버레이 추가 규칙). 오버레이는 서비스 창에서 앱 테마(TftDeckTheme) 밖으로 그려서
 * MaterialTheme.typography 가 앱 값이 아니다 — 그래서 크기를 여기 한 곳에 두고, 오버레이 화면 코드는 `.sp` 를 직접 쓰지 않는다.
 *
 * 본문 최소 11sp. 그보다 작은 것은 얼굴 위 첫 글자([mark] 10sp)와 3성 별([star] 8sp) 둘뿐이다. 숫자는 고정폭(tnum).
 * 글자색은 호출부가 정한다(오버레이에는 앱 테마의 LocalContentColor 가 없다).
 */
internal object OverlayType {
    // 스타일보다 먼저 둔다 — object 초기화는 적힌 순서라 뒤에 두면 스타일을 만들 때 아직 비어 있다.
    /** 한국어를 어절 단위로 줄바꿈(MASTER 규칙 10). API 33 미만은 기본 동작이다. */
    private val phraseBreak = LineBreak(
        strategy = LineBreak.Strategy.Simple,
        strictness = LineBreak.Strictness.Normal,
        wordBreak = LineBreak.WordBreak.Phrase,
    )
    private val korean = LocaleList("ko")

    /** 별칭(목록 줄 첫 줄, 요약 첫 줄). 12sp Bold · 줄 높이 15. */
    val title = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)

    /** 덱 설명(최대 두 줄)·요약 운영 줄·빈 목록 안내. 12sp · 줄 높이 15 — 두 줄이어도 행이 크게 두꺼워지지 않는다. */
    val body = TextStyle(
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = TNUM,
        lineBreak = phraseBreak,
        localeList = korean,
    )

    /** 세 낱말 이하 라벨: 레벨 칩·레벨 캡션·'중국' 글자 배지·넓게 보기의 유닛 이름·티어 카드 글자. 11sp Medium. */
    val label = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM)

    /** 등급 글자·강조 숫자(티어 카드 1등 칩). 11sp Bold. */
    val badge = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)

    /** 얼굴 위 첫 글자 — 초상이 오기 전·못 받았을 때 코스트색 칸이 누구인지 알린다. 10sp. */
    val mark = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)

    /** 얼굴 위 3성 별. 8sp — 예전 6sp(약 0.8mm)는 게임 중에 읽히지 않았다(R10). */
    val star = TextStyle(fontSize = 8.sp, lineHeight = 10.sp)

    /** 티어 카드의 평균 등수 큰 숫자. 20sp Bold(글자 여섯 단계의 20). */
    val display = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)
}

/** 고정폭 숫자. 숫자가 바뀌어도 폭이 흔들리지 않는다. */
private const val TNUM = "tnum"

/** 오버레이용 코스트 테두리 색. 어두운 배경 위에서 읽히도록 앱 테마와 따로 둔다. */
internal fun costTint(cost: Int?): Color = when (cost) {
    1 -> Color(0xFF6E7C78)
    2 -> Color(0xFF3E9E86)
    3 -> Color(0xFF4E82B4)
    4 -> Color(0xFF9A5FB0)
    5 -> Color(0xFFC69A3C)
    else -> Color(0xFF4A5450)
}

/**
 * 등급 글자 색(접힌 칩·머리줄). 통계 등급·편집 등급 모두 [gradeColor] — 편집 등급은 색이 아니라 모양으로 가른다
 * ([overlayGradeStyle]). 옛 metatft 전용 덱('G')은 강조색, 등급이 없으면 흐린 색.
 */
internal fun gradeTint(deck: Deck, bucket: String): Color {
    if (deck.isGlobalOnly) return OverlayAccent
    val grade = deck.gradeFor(bucket) ?: return OverlayMuted
    return gradeColor(grade)
}

/** 오버레이는 칸이 좁아 metatft 전용 덱을 등급 글자 대신 'G' 한 글자로 구분한다. */
internal fun gradeText(deck: Deck, bucket: String): String =
    if (deck.isGlobalOnly) "G" else deck.gradeFor(bucket) ?: "-"

/**
 * 등급 배지 모양(MASTER 규칙 5, R2). 두 등급 체계(metatft 평균 등수 컷 / 중국 한정 덱끼리 매긴 반 S 반 A)를
 * 색이 아니라 모양으로 가른다.
 *  - 편집 등급으로 대신 보여 주는 덱 → Editorial(테두리형 + '편')
 *  - 중국 한정 덱 → Outlined. 뒤에 '중국' 글자 배지가 붙는다(OverlayGradeBadges)
 *  - 그 밖(metatft 조합 덱, 옛 metatft 전용 덱, metatft 비교가 없는 피드) → Filled
 */
internal fun overlayGradeStyle(deck: Deck, bucket: String, metatftCompared: Boolean): GradeBadgeStyle = when {
    deck.showsEditorialGrade(bucket) -> GradeBadgeStyle.Editorial
    metatftCompared && deck.isOnlyInChina -> GradeBadgeStyle.Outlined
    else -> GradeBadgeStyle.Filled
}

/**
 * 펼친 패널 높이 = 화면(창이 놓이는 영역) 높이 − 이 여유. 창 자리는 서비스가 화면 안으로 맞추므로(clampOverlayPosition)
 * 패널이 영역보다 작기만 하면 머리줄부터 바닥까지 다 보인다. 여유는 위아래 가장자리에 딱 붙지 않게 하는 몫이다.
 */
internal val PANEL_SCREEN_MARGIN = 8.dp
