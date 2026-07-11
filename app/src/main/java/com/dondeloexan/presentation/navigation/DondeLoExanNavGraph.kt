package com.dondeloexan.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.presentation.detail.MediaDetailScreen
import com.dondeloexan.presentation.discover.DiscoverScreen
import com.dondeloexan.presentation.feedback.FeedbackBanner
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.presentation.movies.MoviesScreen
import com.dondeloexan.presentation.platforms.PlatformsScreen
import com.dondeloexan.presentation.series.SeriesScreen
import com.dondeloexan.presentation.settings.LogViewerScreen
import com.dondeloexan.presentation.settings.SettingsScreen
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DondeLoExanNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val pagerState = rememberPagerState(
        initialPage = 2,
        pageCount = { Route.bottomNavItems.size }
    )
    val scope = rememberCoroutineScope()

    val feedbackManager: FeedbackManager = koinInject()
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        feedbackManager.events.collect { message ->
            feedbackMessage = message
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                BottomNavigationBar(
                    currentPage = pagerState.currentPage,
                    onPageChange = { page ->
                        scope.launch {
                            if (currentRoute != "main") {
                                navController.navigate("main") {
                                    popUpTo("main") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            pagerState.animateScrollToPage(page)
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "main",
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable("main") {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> SeriesScreen(navController = navController)
                            1 -> MoviesScreen(navController = navController)
                            2 -> DiscoverScreen(navController = navController)
                            3 -> SettingsScreen(navController = navController)
                        }
                    }
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

                composable(Route.SettingsBlacklist.route) {
                    com.dondeloexan.presentation.blacklist.BlacklistScreen(navController = navController)
                }

                composable(Route.SettingsAvailability.route) {
                    com.dondeloexan.presentation.availability.AvailabilityScreen(navController = navController)
                }

                composable(
                    route = "detail/{contentId}/{contentType}",
                    arguments = listOf(
                        navArgument("contentId") { type = NavType.StringType },
                        navArgument("contentType") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val contentId = backStackEntry.arguments?.getString("contentId") ?: return@composable
                    val contentTypeArg = backStackEntry.arguments?.getString("contentType") ?: "movie"
                    val contentType = if (contentTypeArg == "series") ContentType.SERIES else ContentType.MOVIE
                    MediaDetailScreen(
                        contentId = contentId,
                        contentType = contentType,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        FeedbackBanner(
            message = feedbackMessage,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            windowInsets = WindowInsets(top = 0)
        )
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("DondeLoExan v1.0.0", color = TextSecondary, style = UbuntuTypography.bodyLarge)
        }
    }
}