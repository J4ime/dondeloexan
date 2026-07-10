package com.dondeloexan.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DondeLoExanDarkColorScheme = darkColorScheme(
    primary = EleganteRose,
    onPrimary = Color.White,
    primaryContainer = EleganteRoseDark,
    onPrimaryContainer = Color.White,
    secondary = EleganteRoseLight,
    onSecondary = Color.Black,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary.copy(alpha = 0.3f)
)

@Composable
fun DondeLoExanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DondeLoExanDarkColorScheme,
        typography = UbuntuTypography,
        content = content
    )
}
