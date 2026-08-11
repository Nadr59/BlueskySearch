package com.ocrscreencapture.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Green500,
    onPrimary = Color.White,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = OnDark,
    onSurface = OnDark,
    onSurfaceVariant = Color(0xFF999999)
)

@Composable
fun OCRCaptureTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
