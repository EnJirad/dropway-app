package com.pocketdev.dropway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    outline = Color(0xFF475569) // Crisp, dark slate border for maximum outdoor visibility
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
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFF94A3B8)
)

private val DropwayTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = TextPrimary
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    )
)

@Composable
fun DropwayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DropwayTypography,
        content = content
    )
}
