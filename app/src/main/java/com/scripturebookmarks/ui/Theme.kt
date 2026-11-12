package com.scripturebookmarks.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// JW Library-style purples. Tweak these to taste.
val JwPurple = Color(0xFF5A3F8E)
val JwDeepPurple = Color(0xFF3E2A6B)
val JwPlum = Color(0xFF8B6BB8)
val Paper = Color(0xFFF7F4F9)

private val LightColors = lightColorScheme(
    primary = JwPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = JwDeepPurple,
    secondary = JwPlum,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E9FA),
    onSecondaryContainer = JwDeepPurple,
    background = Paper,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBB5F0),
    onPrimary = Color(0xFF2B1B4D),
    primaryContainer = Color(0xFF4A3478),
    onPrimaryContainer = Color(0xFFE8DEF8),
    secondary = Color(0xFFB89AE0),
    onSecondary = Color(0xFF2B1B4D),
    secondaryContainer = Color(0xFF3B2B5E),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF14111B),
    surface = Color(0xFF1D1927),
)

@Composable
fun ScriptureTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
