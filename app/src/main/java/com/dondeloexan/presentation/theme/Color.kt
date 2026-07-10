package com.dondeloexan.presentation.theme

import androidx.compose.ui.graphics.Color

val PopcornYellow = Color(0xFFF5C518)
val CinemaRed = Color(0xFFE63946)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2D2D2D)

val TextPrimary = Color(0xFFF0F0F0)
val TextSecondary = Color(0xFFB0B0B0)

val RatingHigh = Color(0xFF4CAF50)
val RatingMedium = PopcornYellow
val RatingLow = CinemaRed

val PlatformActive = PopcornYellow.copy(alpha = 0.15f)
val PlatformActiveBorder = PopcornYellow.copy(alpha = 0.5f)
val PlatformInactiveBorder = TextSecondary.copy(alpha = 0.3f)
