package com.pocketdev.dropway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DropwayPrimary,
    onPrimary = Color.White,
    primaryContainer = DropwayPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = DropwayNavy,
    onSecondary = Color.White,
    background = DropwayBackground,
    onBackground = TextPrimary,
    surface = DropwayCard,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = DropwayPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF38BDF8),
    background = DropwayNavy,
    onBackground = Color(0xFFF8FAFC),
    surface = DropwaySurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

@Composable
fun DropwayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
