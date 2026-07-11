package com.dondeloexan.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Route(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Series : Route("series", "Series", Icons.Outlined.LiveTv)
    data object Movies : Route("movies", "Pelis", Icons.Outlined.Movie)
    data object Discover : Route("discover", "Descubrir", Icons.Outlined.Explore)
    data object Settings : Route("settings", "Ajustes", Icons.Outlined.Settings)

    data object SettingsPlatforms : Route("settings/platforms", "Mis Plataformas",      Icons.Outlined.Settings)
    data object SettingsLogs      : Route("settings/logs",     "Registro de errores",  Icons.Outlined.Settings)
    data object SettingsAbout     : Route("settings/about",     "Acerca de",            Icons.Outlined.Settings)
    data object SettingsBlacklist : Route("settings/blacklist", "Contenidos ocultos",   Icons.Outlined.Settings)
    data object SettingsAvailability : Route("settings/availability", "Disponibilidad", Icons.Outlined.Settings)

    companion object {
        val bottomNavItems by lazy { listOf(Series, Movies, Discover, Settings) }
    }
}
