package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = AppleBlue,
    secondary = AppleIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEBFD),
    onSecondaryContainer = AppleIndigo,
    tertiary = ApplePurple,
    onTertiary = Color.White,
    background = AppleLightBackground,
    onBackground = AppleLightTextPrimary,
    surface = AppleLightSurface,
    onSurface = AppleLightTextPrimary,
    surfaceVariant = AppleLightSurfaceVariant,
    onSurfaceVariant = AppleLightTextSecondary,
    outline = AppleLightOutline,
    outlineVariant = Color(0xFFE5E5EA)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003875),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = AppleIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2B5C),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = ApplePurple,
    onTertiary = Color.White,
    background = AppleDarkBackground,
    onBackground = AppleDarkTextPrimary,
    surface = AppleDarkSurface,
    onSurface = AppleDarkTextPrimary,
    surfaceVariant = AppleDarkSurfaceVariant,
    onSurfaceVariant = AppleDarkTextSecondary,
    outline = AppleDarkOutline,
    outlineVariant = Color(0xFF2C2C2E)
)

@Composable
fun BlazeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


