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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.presentation.library.LibraryItemCard
import com.dondeloexan.presentation.navigation.BottomNavigationBar
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseDark
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    navController: NavController,
    viewModel: SeriesViewModel = koinViewModel()
) {
    val series by viewModel.series.collectAsState()
    var isGridView by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Series", color = TextPrimary) },
                actions = {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Apps,
                            contentDescription = if (isGridView) "Vista lista" else "Vista cuadrícula",
                            tint = if (isGridView) EleganteRose else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { padding ->
        if (series.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                        "No tienes series guardadas",
                        style = UbuntuTypography.titleMedium,
                        color = TextSecondary
                    )
                    Text(
                        "Busca y guarda desde la pestaña Descubrir",
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(series, key = { it.id }) { show ->
                    LibraryItemCard(
                        posterUrl = show.posterUrl,
                        title = show.title,
                        year = show.year,
                        ratingFa = show.ratingFa,
                        streamingPlatforms = show.streamingPlatforms.toStreamingPlatforms(),
                        isLiked = show.liked,
                        isWatched = show.status.name == "YA_VISTA",
                        onLikeClick = { viewModel.toggleLike(show) },
                        onWatchedClick = { viewModel.toggleWatched(show) },
                        onDeleteClick = { viewModel.delete(show) },
                        onClick = {
                            val type = if (show.contentId?.startsWith("fa-") == true) "fa" else "tmdb"
                            navController.navigate("detail/${show.contentId ?: ""}/$type")
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(series, key = { it.id }) { show ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        LibraryItemCard(
                            posterUrl = show.posterUrl,
                            title = show.title,
                            year = show.year,
                            ratingFa = show.ratingFa,
                            streamingPlatforms = show.streamingPlatforms.toStreamingPlatforms(),
                            isLiked = show.liked,
                            isWatched = show.status.name == "YA_VISTA",
                            onLikeClick = { viewModel.toggleLike(show) },
                            onWatchedClick = { viewModel.toggleWatched(show) },
                            onDeleteClick = { viewModel.delete(show) },
                            onClick = {
                                val type = if (show.contentId?.startsWith("fa-") == true) "fa" else "tmdb"
                                navController.navigate("detail/${show.contentId ?: ""}/$type")
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
}
