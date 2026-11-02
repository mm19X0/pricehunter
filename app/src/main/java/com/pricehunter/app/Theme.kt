package com.pricehunter.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF0F9D78),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F8),
    onSurface = Color(0xFF172033),
    onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun PriceHunterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Light,
        content = content
    )
}
