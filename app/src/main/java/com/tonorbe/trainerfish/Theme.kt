package com.tonorbe.trainerfish

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Keys we store in SharedPreferences (gm_cosmetics.app_theme)
object AppThemeKeys {
    const val LIGHT = "light"
    const val DARK  = "dark"
}

// Light = current look (use default Material 3 light scheme)
private val TrainerFishLightColors = lightColorScheme()

// Dark = true-black / night-friendly
private val TrainerFishDarkColors = darkColorScheme(
    background       = Color(0xFF000000),
    surface          = Color(0xFF121212),
    surfaceVariant   = Color(0xFF1E1E1E),
    onBackground     = Color(0xFFE0E0E0),
    onSurface        = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFCCCCCC),
    primary          = Color(0xFF9CDCFE),
    secondary        = Color(0xFFCE9178)
)

@Composable
fun TrainerFishTheme(
    appThemeKey: String,
    content: @Composable () -> Unit
) {
    val colors = when (appThemeKey) {
        AppThemeKeys.DARK -> TrainerFishDarkColors
        else              -> TrainerFishLightColors   // includes legacy "silver" -> treat as light
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
