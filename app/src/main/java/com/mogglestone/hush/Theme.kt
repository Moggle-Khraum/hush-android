package com.mogglestone.hush

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ivory and ink, with a brass accent and a muted red for "lifted" states.
val Ivory = Color(0xFFF6F1E4)
val Ink = Color(0xFF1C2330)
val InkSoft = Color(0xFF5E6573)
val Hairline = Color(0xFFD6CCB6)
val Brass = Color(0xFF9C7A3C)
val Alarm = Color(0xFF8E3B2E)

@Composable
fun HushTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Ivory,
            secondary = Brass,
            background = Ivory,
            onBackground = Ink,
            surface = Ivory,
            onSurface = Ink,
            onSurfaceVariant = InkSoft,
            outline = Hairline,
            error = Alarm,
            onError = Ivory
        ),
        content = content
    )
}
