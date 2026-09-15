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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * FloaTFT 디자인 시스템 색 토큰(design-system/floatft/MASTER.md §2).
 * 게임 위에서 빠르게 읽는 앱이라 다크 한 가지로 고정한다. 값마다 대비를 문서에서 계산해 두었다.
 */
object FloaColors {
    val Background = Color(0xFF0F0F23)
    val Surface = Color(0xFF1E1C35)
    val SurfaceVariant = Color(0xFF27273B)
    val SurfaceElevated = Color(0xFF2A2745)
    val OnSurface = Color(0xFFE2E8F0)
    val OnSurfaceVariant = Color(0xFF94A3B8)

    // 채움·선택 상태 전용. 바탕 위 대비가 3.31:1 이라 글자색으로 쓰지 않는다.
    val Primary = Color(0xFF7C3AED)
    val PrimaryContainer = Color(0xFF3B2A6B)
    val OnPrimaryContainer = Color(0xFFC4B5FD)

    // 강조 글자·아이콘·포커스 링은 이 색을 쓴다(바탕 6.93, 카드 6.06).
    val Secondary = Color(0xFFA78BFA)

    // 강조 채움. 위에 흰 글자는 3.67:1 이라 어두운 글자를 얹는다.
    val Accent = Color(0xFFF43F5E)
    val AccentText = Color(0xFFFB7185)

    val Outline = Color(0xFF7C6BC0)
    val OutlineVariant = Color(0xFF3A3656)
    val Error = Color(0xFFF87171)
    val Gold = Color(0xFFFBBF24)
    val Positive = Color(0xFF4ADE80)
    val Negative = Color(0xFFF87171)

    // 등급 팔레트. 글자로도, 어두운 글자를 얹는 배지 채움으로도 4.5:1 을 넘는다.
    val TierS = Color(0xFFFB7185)
    val TierA = Color(0xFFFB923C)
    val TierB = Color(0xFFFACC15)
    val TierC = Color(0xFF4ADE80)
    val TierD = Color(0xFF94A3B8)
    val NoGrade = Color(0xFF8D9AB0)
}

private val FloaDarkColors = darkColorScheme(
    primary = FloaColors.Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = FloaColors.PrimaryContainer,
    onPrimaryContainer = FloaColors.OnPrimaryContainer,
    inversePrimary = Color(0xFF5B21B6),
    secondary = FloaColors.Secondary,
    onSecondary = FloaColors.Background,
    secondaryContainer = Color(0xFF312A55),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = FloaColors.Accent,
    onTertiary = FloaColors.Background,
    tertiaryContainer = Color(0xFF4A1D2B),
    onTertiaryContainer = Color(0xFFFECDD3),
    background = FloaColors.Background,
    onBackground = FloaColors.OnSurface,
    surface = FloaColors.Background,
    onSurface = FloaColors.OnSurface,
    surfaceVariant = FloaColors.SurfaceVariant,
    onSurfaceVariant = FloaColors.OnSurfaceVariant,
    // 지정하지 않으면 M3 기본 틴트가 NavigationBar·Chip 배경에 섞여 토큰과 다른 색이 된다.
    surfaceTint = FloaColors.Primary,
    outline = FloaColors.Outline,
    outlineVariant = FloaColors.OutlineVariant,
    error = FloaColors.Error,
    onError = FloaColors.Background,
    errorContainer = Color(0xFF4C1D24),
    onErrorContainer = Color(0xFFFECACA),
    inverseSurface = FloaColors.OnSurface,
    inverseOnSurface = FloaColors.Surface,
    surfaceDim = FloaColors.Background,
    surfaceBright = Color(0xFF2F2C4D),
    surfaceContainerLowest = Color(0xFF0A0A1A),
    surfaceContainerLow = Color(0xFF15142B),
    surfaceContainer = Color(0xFF1A1930),
    // 다이얼로그·메뉴는 카드보다 한 단계 밝게(MASTER §6), 카드(Highest)는 문서의 surface 값.
    surfaceContainerHigh = FloaColors.SurfaceElevated,
    surfaceContainerHighest = FloaColors.Surface,
    scrim = Color(0xFF000000),
)

// 통계 앱이라 숫자가 세로로 정렬되도록 모든 글자에 고정폭 숫자를 켠다(글자 모양은 그대로).
private const val TabularNumbers = "tnum"

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = TabularNumbers),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, fontFeatureSettings = TabularNumbers),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = TabularNumbers),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, fontFeatureSettings = TabularNumbers),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TabularNumbers),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers),
    labelMedium = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, fontFeatureSettings = TabularNumbers),
)

// 모서리: 칩·배지 8, 카드 12, 시트·다이얼로그 16 (MASTER §4)
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * 앱 테마. 예전 호출부가 darkTheme 을 넘기더라도 FloaTFT 는 다크 한 가지만 쓴다.
 */
@Composable
fun TftDeckTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(colorScheme = FloaDarkColors, typography = AppTypography, shapes = AppShapes, content = content)
}
