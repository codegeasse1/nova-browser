package com.nova.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    background = Color(0xFFF4F5F9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    onBackground = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E6EF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8C9EFF),
    onPrimary = Color(0xFF0A1130),
    background = Color(0xFF12141A),
    surface = Color(0xFF1D2026),
    onSurface = Color(0xFFE8E9ED),
    onBackground = Color(0xFFE8E9ED),
    surfaceVariant = Color(0xFF2A2E38)
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
