package com.dondeloexan.presentation.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.presentation.feedback.FeedbackBanner
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.presentation.library.LibraryItemCard
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseDark
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SeriesScreen(
    navController: NavController,
    viewModel: SeriesViewModel = koinViewModel()
) {
    val pending by viewModel.pending.collectAsState()
    val inProgress by viewModel.inProgress.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val upcoming by viewModel.upcomingAgenda.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(false) }

    val feedbackManager: FeedbackManager = koinInject()
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        feedbackManager.events.collect { message -> feedbackMessage = message }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FeedbackBanner(
            message = feedbackMessage,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = EleganteRose
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = "Pendientes",
                        tint = if (selectedTab == 0) EleganteRose else TextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = "En curso",
                        tint = if (selectedTab == 1) EleganteRose else TextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = "Agenda",
                        tint = if (selectedTab == 2) EleganteRose else TextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                icon = {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Terminadas",
                        tint = if (selectedTab == 3) EleganteRose else TextSecondary
                    )
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
            0 -> PendingTab(
                series = pending,
                navController = navController,
                viewModel = viewModel
            )
            1 -> InProgressTab(
                series = inProgress,
                isGridView = isGridView,
                navController = navController,
                viewModel = viewModel
            )
            2 -> AgendaTab(
                series = upcoming,
                navController = navController,
                viewModel = viewModel
            )
            3 -> FinishedTab(
                series = finished,
                isGridView = isGridView,
                navController = navController,
                viewModel = viewModel
            )
        }

        IconButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp),
            onClick = { isGridView = !isGridView }
        ) {
            Icon(
                if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Apps,
                contentDescription = if (isGridView) "Vista lista" else "Vista cuadrícula",
                tint = if (isGridView) EleganteRose else TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        }
    }
}

@Composable
private fun PendingTab(
    series: List<SeriesWithProgress>,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No tienes series pendientes",
                    style = UbuntuTypography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    "Las series que añadas aparecerán aquí",
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.6f)
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
                com.dondeloexan.presentation.library.LibraryItemCard(
                    posterUrl = s.posterUrl,
                    title = s.title,
                    year = s.year,
                    streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
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
                    onClick = {
                        navController.navigate("detail/${s.contentId ?: ""}/series")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }
}

@Composable
private fun InProgressTab(
    series: List<SeriesWithProgress>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No tienes series en curso",
                    style = UbuntuTypography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    "Añade series desde la pestaña Descubrir",
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
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
                    onClick = {
                        navController.navigate("detail/${s.contentId ?: ""}/series")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    LibraryItemCard(
                        posterUrl = s.posterUrl,
                        title = s.title,
                        year = s.year,
                        streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                        watchedCount = item.watchedCount,
                        totalEpisodes = item.totalEpisodes,
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
                        onClick = {
                            navController.navigate("detail/${s.contentId ?: ""}/series")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishedTab(
    series: List<SeriesWithProgress>,
    isGridView: Boolean,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No tienes series terminadas",
                    style = UbuntuTypography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    "Las series que completes al 100% aparecerán aquí",
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
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
                    nextEpisodeAirDate = s.nextEpisodeAirDate,
                    nextEpisodeNumber = s.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                    seriesStatus = s.seriesStatus,
                    inProduction = s.inProduction,
                    numberOfSeasons = s.numberOfSeasons,
                    isLiked = s.liked,
                    isWatched = true,
                    onLikeClick = { viewModel.toggleLike(s) },
                    onWatchedClick = { viewModel.toggleWatched(s) },
                    onClick = {
                        navController.navigate("detail/${s.contentId ?: ""}/series")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.65f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(series, key = { it.show.id }) { item ->
                val s = item.show
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    LibraryItemCard(
                        posterUrl = s.posterUrl,
                        title = s.title,
                        year = s.year,
                        streamingPlatforms = s.streamingPlatforms.toStreamingPlatforms(),
                        watchedCount = item.watchedCount,
                        totalEpisodes = item.totalEpisodes,
                        nextEpisodeAirDate = s.nextEpisodeAirDate,
                        nextEpisodeNumber = s.nextEpisodeNumber,
                        nextEpisodeSeasonNumber = s.nextEpisodeSeasonNumber,
                        seriesStatus = s.seriesStatus,
                        inProduction = s.inProduction,
                        numberOfSeasons = s.numberOfSeasons,
                        isLiked = s.liked,
                        isWatched = true,
                        onLikeClick = { viewModel.toggleLike(s) },
                        onWatchedClick = { viewModel.toggleWatched(s) },
                        onClick = {
                            navController.navigate("detail/${s.contentId ?: ""}/series")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaTab(
    series: List<SeriesWithProgress>,
    navController: NavController,
    viewModel: SeriesViewModel
) {
    if (series.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = EleganteRoseDark.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No hay próximos estrenos",
                    style = UbuntuTypography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    "Los estrenos de tus series aparecerán aquí",
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.6f)
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
                val show = item.show
                com.dondeloexan.presentation.library.LibraryItemCard(
                    posterUrl = show.posterUrl,
                    title = show.title,
                    year = show.year,
                    streamingPlatforms = show.streamingPlatforms.toStreamingPlatforms(),
                    watchedCount = item.watchedCount,
                    totalEpisodes = item.totalEpisodes,
                    nextEpisodeAirDate = show.nextEpisodeAirDate,
                    nextEpisodeNumber = show.nextEpisodeNumber,
                    nextEpisodeSeasonNumber = show.nextEpisodeSeasonNumber,
                    seriesStatus = show.seriesStatus,
                    inProduction = show.inProduction,
                    numberOfSeasons = show.numberOfSeasons,
                    isLiked = show.liked,
                    isWatched = show.status.name == "YA_VISTA",
                    onLikeClick = { viewModel.toggleLike(show) },
                    onWatchedClick = { viewModel.toggleWatched(show) },
                    onClick = {
                        navController.navigate("detail/${show.contentId ?: ""}/series")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }
}
