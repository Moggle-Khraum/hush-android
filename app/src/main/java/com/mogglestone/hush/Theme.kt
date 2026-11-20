package com.mogglestone.hush

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Classical palette: ivory and ink in light mode, midnight ink with brass in dark mode. */
private class Palette(
    val ivory: Color,
    val ink: Color,
    val inkSoft: Color,
    val hairline: Color,
    val brass: Color,
    val alarm: Color
)

private val LightPalette = Palette(
    ivory = Color(0xFFF6F1E4),
    ink = Color(0xFF1C2330),
    inkSoft = Color(0xFF5E6573),
    hairline = Color(0xFFD6CCB6),
    brass = Color(0xFF9C7A3C),
    alarm = Color(0xFF8E3B2E)
)

private val DarkPalette = Palette(
    ivory = Color(0xFF141A26),
    ink = Color(0xFFF3ECDD),
    inkSoft = Color(0xFFA3A9B5),
    hairline = Color(0xFF2F3746),
    brass = Color(0xFFC9A46A),
    alarm = Color(0xFFD98B74)
)

private val LocalPalette = compositionLocalOf { LightPalette }

@Composable @ReadOnlyComposable fun ivory(): Color = LocalPalette.current.ivory
@Composable @ReadOnlyComposable fun ink(): Color = LocalPalette.current.ink
@Composable @ReadOnlyComposable fun inkSoft(): Color = LocalPalette.current.inkSoft
@Composable @ReadOnlyComposable fun hairline(): Color = LocalPalette.current.hairline
@Composable @ReadOnlyComposable fun brass(): Color = LocalPalette.current.brass
@Composable @ReadOnlyComposable fun alarm(): Color = LocalPalette.current.alarm

@Composable
fun HushTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.ink, onPrimary = p.ivory, secondary = p.brass,
            background = p.ivory, onBackground = p.ink, surface = p.ivory, onSurface = p.ink,
            onSurfaceVariant = p.inkSoft, outline = p.hairline, error = p.alarm, onError = p.ivory
        )
    } else {
        lightColorScheme(
            primary = p.ink, onPrimary = p.ivory, secondary = p.brass,
            background = p.ivory, onBackground = p.ink, surface = p.ivory, onSurface = p.ink,
            onSurfaceVariant = p.inkSoft, outline = p.hairline, error = p.alarm, onError = p.ivory
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
