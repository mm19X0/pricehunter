package com.pricehunter.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    secondary = Color(0xFF16A34A),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFF8FAFC)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0F172A),
    surface = Color(0xFF0F172A)
)

@Composable
fun PriceHunterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
