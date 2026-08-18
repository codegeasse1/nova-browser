package com.nova.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NovaPurple = Color(0xFF8B5CF6)
val NovaIndigo = Color(0xFF6366F1)
val NovaCyan = Color(0xFF22D3EE)
val NovaPink = Color(0xFFEC4899)

private val DarkColors = darkColorScheme(
    primary = NovaPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2D63),
    onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = NovaIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2F3460),
    onSecondaryContainer = Color(0xFFDEE0FF),
    tertiary = NovaCyan,
    background = Color(0xFF0E0D16),
    onBackground = Color(0xFFE9E9F2),
    surface = Color(0xFF15141F),
    onSurface = Color(0xFFE9E9F2),
    surfaceVariant = Color(0xFF23222F),
    onSurfaceVariant = Color(0xFFC9C8D6),
    outline = Color(0xFF8A8899),
)

private val LightColors = lightColorScheme(
    primary = NovaPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADFFF),
    onPrimaryContainer = Color(0xFF2C1B4F),
    secondary = NovaIndigo,
    secondaryContainer = Color(0xFFE0E3FF),
    onSecondaryContainer = Color(0xFF1A1F4F),
    tertiary = Color(0xFF0E7490),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1B1A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1A22),
    surfaceVariant = Color(0xFFEBEAF3),
    onSurfaceVariant = Color(0xFF4E4D5A),
    outline = Color(0xFF76747F),
)

private val IncognitoColors = darkColorScheme(
    primary = Color(0xFF9D8CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2E5C),
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFFB39DFF),
    background = Color(0xFF120F1D),
    onBackground = Color(0xFFF0EDFA),
    surface = Color(0xFF1A1526),
    onSurface = Color(0xFFF0EDFA),
    surfaceVariant = Color(0xFF2A2337),
    onSurfaceVariant = Color(0xFFCBC4D9),
    outline = Color(0xFF968EAA),
)

@Composable
fun NovaTheme(
    incognito: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        incognito -> IncognitoColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
