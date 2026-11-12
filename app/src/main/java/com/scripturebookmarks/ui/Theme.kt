package com.scripturebookmarks.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Approximate JW Library-style palette. Tweak these to taste.
val JwBlue = Color(0xFF4A6DA7)
val JwDeepBlue = Color(0xFF2F4A7A)
val JwPurple = Color(0xFF6A4C93)
val Paper = Color(0xFFF6F4EF)

private val LightColors = lightColorScheme(
    primary = JwBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5F3),
    onPrimaryContainer = JwDeepBlue,
    secondary = JwPurple,
    background = Paper,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB6E0),
    onPrimary = Color(0xFF14233F),
    primaryContainer = JwDeepBlue,
    secondary = Color(0xFFC3A9E8),
    background = Color(0xFF16181D),
    surface = Color(0xFF1E2128),
)

@Composable
fun ScriptureTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
