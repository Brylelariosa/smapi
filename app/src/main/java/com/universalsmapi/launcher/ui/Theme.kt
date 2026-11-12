package com.universalsmapi.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette: chalk paper, kelp green for the one primary action, brass and brick reserved for warnings and errors.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6B5E),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFEEF1EC),
    onBackground = Color(0xFF1D2A26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D2A26),
    surfaceVariant = Color(0xFFDDE4DE),
    onSurfaceVariant = Color(0xFF4A5A54),
    outline = Color(0xFFB8C4BC),
    error = Color(0xFFA5432F),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FC4B2),
    onPrimary = Color(0xFF0E1F1A),
    background = Color(0xFF131A17),
    onBackground = Color(0xFFE4EBE6),
    surface = Color(0xFF1C2622),
    onSurface = Color(0xFFE4EBE6),
    surfaceVariant = Color(0xFF2A3631),
    onSurfaceVariant = Color(0xFFA9B8B0),
    outline = Color(0xFF42514A),
    error = Color(0xFFF08A74),
)

/** Status colours that Material's scheme has no slot for. */
@Immutable
data class StatusColors(val ok: Color, val warn: Color, val bad: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(ok = Color.Unspecified, warn = Color.Unspecified, bad = Color.Unspecified)
}

private val LauncherTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val status = if (dark) {
        StatusColors(ok = Color(0xFF7FC4B2), warn = Color(0xFFE0B84A), bad = Color(0xFFF08A74))
    } else {
        StatusColors(ok = Color(0xFF2F6B5E), warn = Color(0xFF7A5B00), bad = Color(0xFFA5432F))
    }
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = LauncherTypography,
            content = content,
        )
    }
}
