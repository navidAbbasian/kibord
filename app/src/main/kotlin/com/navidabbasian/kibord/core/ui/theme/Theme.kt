package com.navidabbasian.kibord.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * توکن‌های تکمیلی خارج از Material3:
 * شیشه‌گرایی، رنگ‌های معنایی و پالت تیم‌ها — همگی تم‌آگاه.
 */
@Immutable
data class KiBordExtras(
    val isDark: Boolean,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassBorderStrong: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val gold: Color,
    val teamColors: List<Color>,
)

private val DarkExtras = KiBordExtras(
    isDark = true,
    glass = Color(0x1AFFFFFF),
    glassStrong = Color(0x2EFFFFFF),
    glassBorder = Color(0x38FFFFFF),
    glassBorderStrong = Color(0x55FFFFFF),
    success = SuccessGreen,
    warning = WarningAmber,
    danger = DangerRed,
    gold = GoldAccent,
    teamColors = teamColorsOnDark,
)

private val LightExtras = KiBordExtras(
    isDark = false,
    glass = Color(0xB3FFFFFF),
    glassStrong = Color(0xE6FFFFFF),
    glassBorder = Color(0x1A4A3A78),
    glassBorderStrong = Color(0x334A3A78),
    success = SuccessGreenDeep,
    warning = WarningAmberDeep,
    danger = DangerRedDeep,
    gold = Color(0xFFDFA943),
    teamColors = teamColorsOnLight,
)

val LocalKiBordExtras = staticCompositionLocalOf { DarkExtras }

/** رنگ اکسنت بازی فعال — هاب بنفش برند، هر بازی رنگ خودش را فراهم می‌کند */
val LocalGameAccent = staticCompositionLocalOf { VioletPrimary }

private val DarkScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = Color(0xFF2A1E52),
    primaryContainer = VioletDeep,
    onPrimaryContainer = Color(0xFFF1EBFF),
    secondary = WarningAmber,
    onSecondary = Color(0xFF473308),
    tertiary = SuccessGreen,
    onTertiary = Color(0xFF0A3D28),
    background = DarkBackground,
    onBackground = Color(0xFFF5F1FB),
    surface = DarkSurface,
    onSurface = Color(0xFFF5F1FB),
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFFC4BAD8),
    error = DangerRed,
    onError = Color(0xFF4E1B1B),
    outline = Color(0xFF5D5377),
)

private val LightScheme = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE5FF),
    onPrimaryContainer = Color(0xFF3A2B6B),
    secondary = WarningAmberDeep,
    onSecondary = Color.White,
    tertiary = SuccessGreenDeep,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF2B2438),
    surface = LightSurface,
    onSurface = Color(0xFF2B2438),
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = Color(0xFF6A6180),
    error = DangerRedDeep,
    onError = Color.White,
    outline = Color(0xFFCFC5E2),
)

@Composable
fun KiBordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalKiBordExtras provides if (darkTheme) DarkExtras else LightExtras
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = KiBordTypography,
            content = content
        )
    }
}

/** دسترسی کوتاه به توکن‌های تکمیلی تم */
val kiExtras: KiBordExtras
    @Composable get() = LocalKiBordExtras.current
