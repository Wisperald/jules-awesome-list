package com.personal.clock.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.personal.clock.data.ThemeMode

// Calm palette: graphite / deep blue-grey surfaces with a muted teal accent.
// All text/background pairs meet WCAG AA (≥ 4.5:1) contrast.
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E6B70),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCBE7E8),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E6E8),
    onSecondaryContainer = Color(0xFF071F21),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    background = Color(0xFFF5F7F8),
    onBackground = Color(0xFF181C1E),
    surface = Color(0xFFF5F7F8),
    onSurface = Color(0xFF181C1E),
    surfaceVariant = Color(0xFFDDE3E6),
    onSurfaceVariant = Color(0xFF3F484B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF2F3),
    surfaceContainer = Color(0xFFE9EDEE),
    surfaceContainerHigh = Color(0xFFE3E7E9),
    surfaceContainerHighest = Color(0xFFDDE2E4),
    outline = Color(0xFF6F787B),
    outlineVariant = Color(0xFFBFC8CB),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FD1D5),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF1B4E52),
    onPrimaryContainer = Color(0xFFABEEF1),
    secondary = Color(0xFFB0CCCE),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF2C4345),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF101417),
    onBackground = Color(0xFFE0E3E5),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE0E3E5),
    surfaceVariant = Color(0xFF3F484B),
    onSurfaceVariant = Color(0xFFC2CBCE),
    surfaceContainerLowest = Color(0xFF0B0F11),
    surfaceContainerLow = Color(0xFF181C1F),
    surfaceContainer = Color(0xFF1C2023),
    surfaceContainerHigh = Color(0xFF262B2D),
    surfaceContainerHighest = Color(0xFF313538),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF3F484B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val baseTypography = Typography()

/** System font (Roboto on most devices); tabular digits keep changing numbers from jittering. */
private val AppTypography = baseTypography.copy(
    displayLarge = baseTypography.displayLarge.copy(fontFeatureSettings = "tnum"),
    displayMedium = baseTypography.displayMedium.copy(fontFeatureSettings = "tnum"),
    displaySmall = baseTypography.displaySmall.copy(fontFeatureSettings = "tnum"),
    headlineLarge = baseTypography.headlineLarge.copy(fontFeatureSettings = "tnum"),
    headlineMedium = baseTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
    headlineSmall = baseTypography.headlineSmall.copy(fontFeatureSettings = "tnum"),
    titleLarge = baseTypography.titleLarge.copy(fontFeatureSettings = "tnum"),
    bodyLarge = baseTypography.bodyLarge.copy(fontFeatureSettings = "tnum"),
    bodyMedium = baseTypography.bodyMedium.copy(fontFeatureSettings = "tnum"),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ClockTheme(
    theme: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(theme)
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
