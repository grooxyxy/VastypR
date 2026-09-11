package com.volxsy.vastypr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Skill: android-material3-design-system + android-compose-accessibility
// Dark default true untuk editor; tetap hormati system + sediakan toggle di Settings nanti.
private val DarkScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Paper,
    secondary = BrandAccent,
    background = Ink900,
    surface = Ink800,
    onBackground = Ink100,
    onSurface = Ink100,
)

private val LightScheme = lightColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Paper,
    secondary = BrandPrimary,
    background = Paper,
    surface = Paper,
    onBackground = Ink900,
    onSurface = Ink900,
)

@Composable
fun VastypRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = VastypRTypography,
        content = content,
    )
}
