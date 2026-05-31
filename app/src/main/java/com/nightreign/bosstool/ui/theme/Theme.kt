package com.nightreign.bosstool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF1A1A1A),
    secondary = Color(0xFFB0A080),
    onSecondary = Color(0xFF1A1A1A),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFE8E2D0),
    surface = Color(0xFF1F1D17),
    onSurface = Color(0xFFE8E2D0),
    surfaceVariant = Color(0xFF2A271F),
    onSurfaceVariant = Color(0xFFB9B19B),
    error = Color(0xFFE57373),
)

@Composable
fun NightreignTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        typography = Typography(),
        content = content,
    )
}
