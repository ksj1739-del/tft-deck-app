package com.tftdeck.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * FloaTFT 디자인 시스템 색 토큰(design-system/floatft/MASTER.md §3).
 * 게임 위에서 빠르게 읽는 앱이라 다크 한 가지로 고정한다. 값마다 대비를 문서에서 계산해 두었다.
 *
 * 공용 배지·칩(Components.kt)은 앱 테마 밖(오버레이 창)에서도 같은 색이 나오도록 colorScheme 대신 이 값을 직접 읽는다.
 */
object FloaColors {
    // 무채색 그래파이트 바탕 3단(MASTER 규칙 3): Background < SurfaceContainer < SurfaceContainerHighest.
    val Background = Color(0xFF0C0E12)

    /** 바탕보다 한 단 밝은 면. colorScheme.surface·surfaceContainer(앱바·내비게이션·메뉴·설정 묶음). 바탕 대비 1.17. */
    val SurfaceContainer = Color(0xFF1B1F26)

    /**
     * 한 단 더 밝은 면 = 덱 카드 면(colorScheme.surfaceContainerHighest). 바탕 대비 1.27, SurfaceContainer 대비 1.09.
     * 이보다 밝으면 카드 안의 자리 표시(outlineVariant)·글자 배지가 카드 면과 섞이고 outline 도 3:1 에 가까워진다.
     */
    val SurfaceContainerHighest = Color(0xFF22262E)

    /** 오버레이 패널·티어 카드 바탕(오버레이 대비 계산의 기준). 앱 화면의 surface 역할은 [SurfaceContainer] 가 맡는다. */
    val Surface = Color(0xFF171A20)

    /** 흐린 영역·입력칸·미선택 칩 채움(colorScheme.surfaceVariant). 바탕 대비 1.23. */
    val SurfaceVariant = Color(0xFF1F232A)

    /** 시트·메뉴·대화상자·오버레이 머리줄(colorScheme.surfaceContainerHigh). */
    val SurfaceElevated = Color(0xFF242932)

    /**
     * 가장 밝은 면(colorScheme.surfaceBright). 글자 배지 채움 — 카드 면(1.17)·오버레이 머리줄(1.12)·바탕(1.48)
     * 어디에 얹어도 한 단 밝게 보인다. 위 글자: onSurfaceVariant 5.11, primary 4.71.
     */
    val SurfaceBright = Color(0xFF2C313B)

    val OnSurface = Color(0xFFE8EBF0)
    val OnSurfaceVariant = Color(0xFF9AA3AF)

    // 포인트 색은 파랑 하나. 누를 수 있음·선택됨에만 쓴다(바탕 6.97, 면 5.96, 카드 5.47, 올린면 5.27).
    // 채움 위 글자는 흰색(2.77)이 아니라 OnPrimary(6.88)를 쓴다.
    val Primary = Color(0xFF5B9BFF)
    val OnPrimary = Color(0xFF06101F)
    val PrimaryContainer = Color(0xFF172A47)
    val OnPrimaryContainer = Color(0xFFCFE1FF)

    // 오버레이의 강조 글자·아이콘·포커스 링(바탕 9.83, 카드 7.72, 올린면 7.43).
    val Secondary = Color(0xFF8DBBFF)

    /** 선택 표시 한 모양(MASTER 규칙 6): 선택된 칩·탭 표시기 채움과 그 위 글자(11.11). */
    val SecondaryContainer = Color(0xFF1D2B42)
    val OnSecondaryContainer = Color(0xFFD6E4FF)

    // 강조 채움. 위에 흰 글자는 3.67:1 이라 어두운 글자를 얹는다.
    val Accent = Color(0xFFF43F5E)
    val AccentText = Color(0xFFFB7185)

    val Outline = Color(0xFF6B7482)
    val OutlineVariant = Color(0xFF2A2F38)
    val Error = Color(0xFFF87171)

    /** 1등·3성에만 쓴다(MASTER 규칙 3). */
    val Gold = Color(0xFFFBBF24)

    /** 좋음·나쁨. 색만으로 뜻을 전하지 않도록 늘 ▲/▼ 와 함께 쓴다(MASTER 규칙 3). */
    val Positive = Color(0xFF4ADE80)
    val Negative = Color(0xFFF87171)

    // 등급 팔레트. 등급 배지에만 쓴다. 채움 위 어두운 글자, 테두리형의 색 글자 모두 4.5:1 을 넘는다(MASTER §3).
    // S 는 Negative(빨강)와, C 는 Positive(초록)와 같은 색이 되지 않게 분홍·연두로 옮겼다(2026-09-19, V10).
    val TierS = Color(0xFFF472B6)
    val TierA = Color(0xFFFB923C)
    val TierB = Color(0xFFFACC15)
    val TierC = Color(0xFFA3E635)
    val TierD = Color(0xFF94A3B8)
    val NoGrade = Color(0xFF8D9AB0)
}

private val FloaDarkColors = darkColorScheme(
    primary = FloaColors.Primary,
    onPrimary = FloaColors.OnPrimary,
    primaryContainer = FloaColors.PrimaryContainer,
    onPrimaryContainer = FloaColors.OnPrimaryContainer,
    inversePrimary = Color(0xFF2563EB),
    secondary = FloaColors.Secondary,
    onSecondary = FloaColors.Background,
    secondaryContainer = FloaColors.SecondaryContainer,
    onSecondaryContainer = FloaColors.OnSecondaryContainer,
    tertiary = FloaColors.Accent,
    onTertiary = FloaColors.Background,
    tertiaryContainer = Color(0xFF3F1A24),
    onTertiaryContainer = Color(0xFFFECDD3),
    background = FloaColors.Background,
    onBackground = FloaColors.OnSurface,
    // 그래파이트 3단: background < surface(=surfaceContainer) < surfaceContainerHighest(카드 면).
    // surface 를 바탕과 같게 두면 surface 를 카드 색으로 쓴 곳이 테두리만 남은 빈 상자가 된다(L6·V9).
    surface = FloaColors.SurfaceContainer,
    onSurface = FloaColors.OnSurface,
    surfaceVariant = FloaColors.SurfaceVariant,
    onSurfaceVariant = FloaColors.OnSurfaceVariant,
    // 지정하지 않으면 M3 기본 틴트가 NavigationBar·Chip 배경에 섞여 토큰과 다른 색이 된다.
    surfaceTint = FloaColors.Primary,
    outline = FloaColors.Outline,
    outlineVariant = FloaColors.OutlineVariant,
    error = FloaColors.Error,
    onError = FloaColors.Background,
    errorContainer = Color(0xFF3D1A1E),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = FloaColors.OnSurface,
    inverseOnSurface = FloaColors.Surface,
    surfaceDim = FloaColors.Background,
    surfaceBright = FloaColors.SurfaceBright,
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF101216),
    // 앱바(surface)와 내비게이션 바(surfaceContainer)가 같은 면이 되게 묶는다.
    surfaceContainer = FloaColors.SurfaceContainer,
    // 대화상자·검색 후보 쪽 면. 카드 면보다 조금 밝다(1.04) — 스크림·그림자와 함께 떠 보인다.
    surfaceContainerHigh = FloaColors.SurfaceElevated,
    surfaceContainerHighest = FloaColors.SurfaceContainerHighest,
    scrim = Color(0xFF000000),
)

// 통계 앱이라 숫자가 세로로 정렬되도록 모든 글자에 고정폭 숫자를 켠다(글자 모양은 그대로).
private const val TabularNumbers = "tnum"

/**
 * 한국어를 어절 단위로 줄바꿈한다(MASTER 규칙 10, V15). WordBreak.Phrase 는 API 33 이상에서만 듣고 그 아래는 기본 동작이다.
 * 어절 규칙은 글자의 언어로 고르므로 기기 언어가 한국어가 아니어도 같게 끊기도록 ko 를 함께 준다.
 */
private val PhraseLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Simple,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)
private val Korean = LocaleList("ko")

// 글자 여섯 단계(MASTER 규칙 1): 11 · 12 · 14 · 16 · 20 · 28sp. 반 단계(10.5·11.5·13.5) 금지.
private val Heading20 = TextStyle(
    fontSize = 20.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = TabularNumbers,
)
private val Display28 = TextStyle(
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = TabularNumbers,
)

private val AppTypography = Typography(
    // 쓰지 않는 display·headline 칸도 여섯 단계 안으로 묶는다. 비워 두면 M3 기본값(24~57sp)이 새어 나온다(V4).
    displayLarge = Display28,
    displayMedium = Display28,
    displaySmall = Display28,
    headlineLarge = Heading20,
    headlineMedium = Heading20,
    headlineSmall = Heading20,
    titleLarge = Heading20,
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TabularNumbers,
        lineBreak = PhraseLineBreak,
        localeList = Korean,
    ),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TabularNumbers),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TabularNumbers,
        lineBreak = PhraseLineBreak,
        localeList = Korean,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TabularNumbers,
        lineBreak = PhraseLineBreak,
        localeList = Korean,
    ),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers),
    // 11sp 는 세 낱말 이하 라벨(배지·칩·수치 라벨)에만. 문장에는 12sp 이상을 쓴다.
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TabularNumbers,
    ),
)

// 모서리(MASTER 규칙 4): 배지 6, 칩·버튼 8, 카드 12, 시트·대화상자 16.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * 앱 테마. 예전 호출부가 darkTheme 을 넘기더라도 FloaTFT 는 다크 한 가지만 쓴다.
 * 시스템 바 색은 액티비티 창에만 맞춘다. 서비스(오버레이 창)처럼 액티비티가 아닌 곳에서 감싸도 죽지 않는다.
 */
@Composable
fun TftDeckTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(colorScheme = FloaDarkColors, typography = AppTypography, shapes = AppShapes, content = content)
}
