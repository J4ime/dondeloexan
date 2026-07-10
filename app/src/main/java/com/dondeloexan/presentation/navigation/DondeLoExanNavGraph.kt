package com.dondeloexan.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dondeloexan.presentation.detail.MediaDetailScreen
import com.dondeloexan.presentation.discover.DiscoverScreen
import com.dondeloexan.presentation.movies.MoviesScreen
import com.dondeloexan.presentation.platforms.PlatformsScreen
import com.dondeloexan.presentation.series.SeriesScreen
import com.dondeloexan.presentation.settings.LogViewerScreen
import com.dondeloexan.presentation.settings.SettingsScreen
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography

@Composable
fun DondeLoExanNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Route.Discover.route,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable(Route.Series.route) {
            SeriesScreen(navController = navController)
        }

        composable(Route.Movies.route) {
            MoviesScreen(navController = navController)
        }

        composable(Route.Discover.route) {
            DiscoverScreen(navController = navController)
        }

        composable(Route.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Route.SettingsLogs.route) {
            LogViewerScreen(navController = navController)
        }

        composable(Route.SettingsPlatforms.route) {
            PlatformsScreen(navController = navController)
        }

        composable(Route.SettingsAbout.route) {
            AboutScreen(navController = navController)
        }

        composable(
            route = "detail/{contentId}/{contentType}",
            arguments = listOf(
                navArgument("contentId") { type = NavType.StringType },
                navArgument("contentType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contentId = backStackEntry.arguments?.getString("contentId") ?: return@composable
            MediaDetailScreen(
                contentId = contentId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acerca de", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("DondeLoExan v1.0.0", color = TextSecondary, style = UbuntuTypography.bodyLarge)
        }
    }
}
