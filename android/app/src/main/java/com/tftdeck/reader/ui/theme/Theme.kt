package com.tftdeck.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 옥(jade) 계열. 중국 원본과 TFT 헥사곤 보드 양쪽에 어울리면서
// 흔한 '게임 앱 남색+금색'과는 다른 자리를 잡는다.
private val Jade = Color(0xFF17705C)
private val JadeDark = Color(0xFF4FC2A3)
private val Amber = Color(0xFF8F5B0C)
private val AmberDark = Color(0xFFD9A441)

private val LightColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E7E1),
    onPrimaryContainer = Color(0xFF06231C),
    secondary = Amber,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E6CC),
    onSecondaryContainer = Color(0xFF3A2605),
    background = Color(0xFFF1F4F3),
    onBackground = Color(0xFF131A18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF131A18),
    surfaceVariant = Color(0xFFE8EDEB),
    onSurfaceVariant = Color(0xFF3A4A45),
    outline = Color(0xFFC2CFCB),
    outlineVariant = Color(0xFFDCE3E1),
    error = Color(0xFFBA1A1A),
    // 지정하지 않으면 M3 기본(보라) 팔레트가 NavigationBar·Chip 배경으로 새어나온다.
    surfaceTint = Jade,
    surfaceDim = Color(0xFFD8E0DD),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainer = Color(0xFFEDF2F0),
    surfaceContainerHigh = Color(0xFFE7EDEB),
    surfaceContainerHighest = Color(0xFFE0E8E5),
    inverseSurface = Color(0xFF2A3330),
    inverseOnSurface = Color(0xFFEFF4F2),
    inversePrimary = Color(0xFF4FC2A3),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = JadeDark,
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF15332C),
    onPrimaryContainer = Color(0xFFB8EEDD),
    secondary = AmberDark,
    onSecondary = Color(0xFF3A2605),
    secondaryContainer = Color(0xFF33280F),
    onSecondaryContainer = Color(0xFFF6E3BF),
    background = Color(0xFF0E1413),
    onBackground = Color(0xFFE6EDEA),
    surface = Color(0xFF161F1D),
    onSurface = Color(0xFFE6EDEA),
    surfaceVariant = Color(0xFF1D2825),
    onSurfaceVariant = Color(0xFFB7C6C1),
    outline = Color(0xFF36453F),
    outlineVariant = Color(0xFF263230),
    error = Color(0xFFFFB4AB),
    surfaceTint = JadeDark,
    surfaceDim = Color(0xFF0E1413),
    surfaceBright = Color(0xFF333C39),
    surfaceContainerLowest = Color(0xFF090E0D),
    surfaceContainerLow = Color(0xFF161F1D),
    surfaceContainer = Color(0xFF1A2321),
    surfaceContainerHigh = Color(0xFF242E2B),
    surfaceContainerHighest = Color(0xFF2F3936),
    inverseSurface = Color(0xFFE6EDEA),
    inverseOnSurface = Color(0xFF1A2321),
    inversePrimary = Color(0xFF17705C),
    scrim = Color(0xFF000000),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun TftDeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
