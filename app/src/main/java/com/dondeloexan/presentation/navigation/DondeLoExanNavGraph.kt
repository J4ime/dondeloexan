package com.dondeloexan.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.dondeloexan.BuildConfig
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.presentation.detail.MediaDetailScreen
import com.dondeloexan.presentation.discover.DiscoverScreen
import com.dondeloexan.presentation.feedback.FeedbackBanner
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.presentation.library.LibraryItemCard
import com.dondeloexan.presentation.movies.MoviesViewModel
import com.dondeloexan.presentation.platforms.PlatformsScreen
import com.dondeloexan.presentation.series.SeriesViewModel
import com.dondeloexan.presentation.settings.LogViewerScreen
import com.dondeloexan.presentation.settings.SettingsScreen
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseDark
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private fun pageToBottomNav(page: Int): Int = when (page) {
    in 0..3 -> 0
    in 4..5 -> 1
    6 -> 2
    else -> 3
}

private fun bottomNavToPage(bottomNavIndex: Int): Int = when (bottomNavIndex) {
    0 -> 0
    1 -> 4
    2 -> 6
    else -> 7
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DondeLoExanNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val seriesViewModel = koinViewModel<SeriesViewModel>()
    val moviesViewModel = koinViewModel<MoviesViewModel>()
    val feedbackManager: FeedbackManager = koinInject()

    val pending by seriesViewModel.pending.collectAsState()
    val inProgress by seriesViewModel.inProgress.collectAsState()
    val finished by seriesViewModel.finished.collectAsState()
    val upcoming by seriesViewModel.upcomingAgenda.collectAsState()
    val pendingMovies by moviesViewModel.pendingMovies.collectAsState()
    val watchedMovies by moviesViewModel.watchedMovies.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 8 }
    )
    val scope = rememberCoroutineScope()

    var isGridView by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        feedbackManager.events.collect { message -> feedbackMessage = message }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                BottomNavigationBar(
                    currentPage = pageToBottomNav(pagerState.currentPage),
                    onPageChange = { bottomNavIndex ->
                        scope.launch {
                            if (currentRoute != "main") {
                                navController.navigate("main") {
                                    popUpTo("main") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            pagerState.animateScrollToPage(bottomNavToPage(bottomNavIndex))
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
                    MainPagerContent(
                        pagerState = pagerState,
                        scope = scope,
                        isGridView = isGridView,
                        onToggleGrid = { isGridView = !isGridView },
                        feedbackMessage = feedbackMessage,
                        navController = navController,
                        seriesViewModel = seriesViewModel,
                        moviesViewModel = moviesViewModel,
                        pending = pending,
                        inProgress = inProgress,
                        finished = finished,
                        upcoming = upcoming,
                        pendingMovies = pendingMovies,
                        watchedMovies = watchedMovies
                    )
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPagerContent(
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    isGridView: Boolean,
    onToggleGrid: () -> Unit,
    feedbackMessage: String?,
    navController: NavController,
    seriesViewModel: SeriesViewModel,
    moviesViewModel: MoviesViewModel,
    pending: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    inProgress: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    finished: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    upcoming: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    pendingMovies: List<com.dondeloexan.data.local.entity.MovieEntity>,
    watchedMovies: List<com.dondeloexan.data.local.entity.MovieEntity>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FeedbackBanner(
            message = feedbackMessage,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        val currentPage = pagerState.currentPage
        if (currentPage in 0..3) {
            TabRow(
                selectedTabIndex = currentPage,
                containerColor = DarkBackground,
                contentColor = EleganteRose
            ) {
                Tab(
                    selected = currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = {
                        Icon(
                            Icons.Outlined.Schedule, "Pendientes",
                            tint = if (currentPage == 0) EleganteRose else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = {
                        Icon(
                            Icons.Outlined.PlayCircle, "En curso",
                            tint = if (currentPage == 1) EleganteRose else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = {
                        Icon(
                            Icons.Outlined.CalendarMonth, "Agenda",
                            tint = if (currentPage == 2) EleganteRose else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    icon = {
                        Icon(
                            Icons.Outlined.CheckCircle, "Terminadas",
                            tint = if (currentPage == 3) EleganteRose else TextSecondary
                        )
                    }
                )
            }
        }

        if (currentPage in 4..5) {
            TabRow(
                selectedTabIndex = currentPage - 4,
                containerColor = DarkBackground,
                contentColor = EleganteRose
            ) {
                Tab(
                    selected = currentPage == 4,
                    onClick = { scope.launch { pagerState.animateScrollToPage(4) } },
                    icon = {
                        Icon(
                            Icons.Outlined.StarBorder, "Pendientes",
                            tint = if (currentPage == 4) EleganteRose else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = currentPage == 5,
                    onClick = { scope.launch { pagerState.animateScrollToPage(5) } },
                    icon = {
                        Icon(
                            Icons.Outlined.CheckCircle, "Vistas",
                            tint = if (currentPage == 5) EleganteRose else TextSecondary
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> SeriesPendingTab(
                        series = pending,
                        navController = navController,
                        viewModel = seriesViewModel
                    )
                    1 -> SeriesInProgressTab(
                        series = inProgress,
                        isGridView = isGridView,
                        navController = navController,
                        viewModel = seriesViewModel
                    )
                    2 -> SeriesAgendaTab(
                        series = upcoming,
                        isGridView = isGridView,
                        navController = navController,
                        viewModel = seriesViewModel
                    )
                    3 -> SeriesFinishedTab(
                        series = finished,
                        isGridView = isGridView,
                        navController = navController,
                        viewModel = seriesViewModel
                    )
                    4 -> MoviesPendingTab(
                        movies = pendingMovies,
                        isGridView = isGridView,
                        navController = navController,
                        viewModel = moviesViewModel
                    )
                    5 -> MoviesWatchedTab(
                        movies = watchedMovies,
                        isGridView = isGridView,
                        navController = navController,
                        viewModel = moviesViewModel
                    )
                    6 -> DiscoverScreen(navController = navController)
                    7 -> SettingsScreen(navController = navController)
                }
            }

            if (currentPage <= 5) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(56.dp),
                    onClick = onToggleGrid
                ) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Apps,
                        contentDescription = if (isGridView) "Vista lista" else "Vista cuadrícula",
                        tint = if (isGridView) EleganteRose else TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// ── Series Tab Content Composables ──

@Composable
private fun SeriesPendingTab(
    series: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No tienes series pendientes", style = UbuntuTypography.titleMedium, color = TextSecondary)
                Text("Las series que añadas aparecerán aquí", style = UbuntuTypography.bodySmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = s.status.name == "YA_VISTA",
                    onLikeClick = { viewModel.toggleLike(s) },
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesInProgressTab(
    series: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No tienes series en curso", style = UbuntuTypography.titleMedium, color = TextSecondary)
                Text("Añade series desde la pestaña Descubrir", style = UbuntuTypography.bodySmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = s.status.name == "YA_VISTA",
                    onLikeClick = { viewModel.toggleLike(s) },
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = s.status.name == "YA_VISTA",
                    onLikeClick = { viewModel.toggleLike(s) },
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesAgendaTab(
    series: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No hay próximos estrenos", style = UbuntuTypography.titleMedium, color = TextSecondary)
                Text("Los estrenos de tus series aparecerán aquí", style = UbuntuTypography.bodySmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = s.status.name == "YA_VISTA",
                    onLikeClick = { viewModel.toggleLike(s) },
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = s.status.name == "YA_VISTA",
                    onLikeClick = { viewModel.toggleLike(s) },
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesFinishedTab(
    series: List<com.dondeloexan.presentation.series.SeriesWithProgress>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No tienes series terminadas", style = UbuntuTypography.titleMedium, color = TextSecondary)
                Text("Las series que completes al 100% aparecerán aquí", style = UbuntuTypography.bodySmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = true,
                    showLikeButton = false,
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    releasedEpisodes = s.releasedEpisodes,
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = true,
                    showLikeButton = false,
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = { navController.navigate("detail/${s.contentId ?: ""}/series") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

// ── Movies Tab Content Composables ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoviesPendingTab(
    movies: List<com.dondeloexan.data.local.entity.MovieEntity>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: MoviesViewModel
) {
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No tienes películas pendientes", style = UbuntuTypography.titleMedium, color = TextSecondary)
                Text("Busca y guarda desde la pestaña Descubrir", style = UbuntuTypography.bodySmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(movies, key = { it.id }) { movie ->
                LibraryItemCard(
                    posterUrl = movie.posterUrl,
                    title = movie.title,
                    year = movie.year,
                    streamingPlatforms = movie.streamingPlatforms.toStreamingPlatforms(),
                    releaseDate = movie.releaseDate,
                    isWatched = movie.status.name == "YA_VISTA",
                    watchedAt = movie.watchedAt,
                    onDeleteClick = { viewModel.deleteMovie(movie) },
                    onWatchedClick = { viewModel.toggleWatched(movie) },
                    onClick = { navController.navigate("detail/${movie.contentId ?: ""}/movie") },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(movies, key = { it.id }) { movie ->
                LibraryItemCard(
                    posterUrl = movie.posterUrl,
                    title = movie.title,
                    year = movie.year,
                    streamingPlatforms = movie.streamingPlatforms.toStreamingPlatforms(),
                    releaseDate = movie.releaseDate,
                    isWatched = movie.status.name == "YA_VISTA",
                    watchedAt = movie.watchedAt,
                    onDeleteClick = { viewModel.deleteMovie(movie) },
                    onWatchedClick = { viewModel.toggleWatched(movie) },
                    onClick = { navController.navigate("detail/${movie.contentId ?: ""}/movie") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoviesWatchedTab(
    movies: List<com.dondeloexan.data.local.entity.MovieEntity>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: MoviesViewModel
) {
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No has visto ninguna película", style = UbuntuTypography.titleMedium, color = TextSecondary)
                Text("Busca y guarda desde la pestaña Descubrir", style = UbuntuTypography.bodySmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(movies, key = { it.id }) { movie ->
                LibraryItemCard(
                    posterUrl = movie.posterUrl,
                    title = movie.title,
                    year = movie.year,
                    streamingPlatforms = movie.streamingPlatforms.toStreamingPlatforms(),
                    releaseDate = movie.releaseDate,
                    isWatched = movie.status.name == "YA_VISTA",
                    watchedAt = movie.watchedAt,
                    onDeleteClick = { viewModel.deleteMovie(movie) },
                    onWatchedClick = { viewModel.toggleWatched(movie) },
                    onClick = { navController.navigate("detail/${movie.contentId ?: ""}/movie") },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(movies, key = { it.id }) { movie ->
                LibraryItemCard(
                    posterUrl = movie.posterUrl,
                    title = movie.title,
                    year = movie.year,
                    streamingPlatforms = movie.streamingPlatforms.toStreamingPlatforms(),
                    releaseDate = movie.releaseDate,
                    isWatched = movie.status.name == "YA_VISTA",
                    watchedAt = movie.watchedAt,
                    onDeleteClick = { viewModel.deleteMovie(movie) },
                    onWatchedClick = { viewModel.toggleWatched(movie) },
                    onClick = { navController.navigate("detail/${movie.contentId ?: ""}/movie") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
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
            Text("DondeLoExan v${BuildConfig.VERSION_NAME}", color = TextSecondary, style = UbuntuTypography.bodyLarge)
        }
    }
}
