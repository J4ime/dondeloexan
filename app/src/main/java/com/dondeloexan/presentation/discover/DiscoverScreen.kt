package com.dondeloexan.presentation.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import com.dondeloexan.presentation.components.BouncingDotsSpinner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.presentation.discover.components.SearchItemCard
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseDark
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import com.dondeloexan.presentation.feedback.FeedbackBanner
import com.dondeloexan.presentation.feedback.FeedbackManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun DiscoverScreen(
    navController: NavController,
    viewModel: DiscoverViewModel = koinViewModel(),
    isGridView: Boolean = false,
    onToggleGrid: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filmographyView by viewModel.filmographyView.collectAsState()
    val likedIds by viewModel.likedIds.collectAsState()
    val watchedIds by viewModel.watchedIds.collectAsState()
    val blacklistedIds by viewModel.blacklistedIds.collectAsState()
    val filterByPlatforms by viewModel.filterByPlatforms.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }

    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val feedbackManager: FeedbackManager = koinInject()
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        feedbackManager.events.collect { message -> feedbackMessage = message }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSearchFocused) EleganteRose.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 0.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    singleLine = true,
                    textStyle = UbuntuTypography.bodyLarge.copy(
                        color = Color.White,
                        fontSize = 18.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(EleganteRose),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Search, null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = viewModel::onClearSearch) {
                                    Icon(Icons.Outlined.Close, "Limpiar", tint = TextSecondary)
                                }
                            }
                        }
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface
            ) {
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = viewModel::togglePlatformFilter
                ) {
                    Icon(
                        Icons.Outlined.FilterList, null,
                        tint = if (filterByPlatforms) EleganteRose else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface
            ) {
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = onToggleGrid
                ) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Apps,
                        contentDescription = if (isGridView) "Vista lista" else "Vista cuadrícula",
                        tint = if (isGridView) EleganteRose else TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        FeedbackBanner(
            message = feedbackMessage,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        val activeFilmographyView = filmographyView
        if (activeFilmographyView != null) {
            FilmographyContent(
                view = activeFilmographyView,
                likedIds = likedIds,
                watchedIds = watchedIds,
                blacklistedIds = blacklistedIds,
                isGridView = isGridView,
                onItemClick = { contentId, contentType ->
                    navController.navigate("detail/$contentId/$contentType")
                },
                onFavoriteClick = viewModel::onToggleFavorite,
                onWatchedClick = viewModel::onToggleWatched,
                onBlacklistClick = viewModel::onToggleBlacklist,
                onLoadMore = viewModel::onFilmographyLoadMore,
                onBack = viewModel::onFilmographyBack
            )
        } else {
            DiscoverContent(
                uiState = uiState,
                isLoadingMore = isLoadingMore,
                searchQuery = searchQuery,
                likedIds = likedIds,
                watchedIds = watchedIds,
                blacklistedIds = blacklistedIds,
                isGridView = isGridView,
                onItemClick = { contentId, contentType ->
                    navController.navigate("detail/$contentId/$contentType")
                },
                onFavoriteClick = viewModel::onToggleFavorite,
                onWatchedClick = viewModel::onToggleWatched,
                onBlacklistClick = viewModel::onToggleBlacklist,
                onLoadNextPage = viewModel::loadNextPage,
                onRetry = viewModel::onRetry,
                onSelectEntity = viewModel::onSelectEntity
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DiscoverContent(
    uiState: DiscoverUiState,
    isLoadingMore: Boolean = false,
    searchQuery: String,
    likedIds: Set<String>,
    watchedIds: Set<String>,
    blacklistedIds: Set<String>,
    isGridView: Boolean = false,
    onItemClick: (String, String) -> Unit,
    onFavoriteClick: (ContentPreview) -> Unit,
    onWatchedClick: (ContentPreview) -> Unit,
    onBlacklistClick: (ContentPreview) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onSelectEntity: (FilmographyEntity) -> Unit = {}
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { isRefreshing = true }
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRetry()
            isRefreshing = false
        }
    }

    Box(Modifier.pullRefresh(pullRefreshState)) {
        when (uiState) {
            is DiscoverUiState.Initial -> InitialState()
            is DiscoverUiState.Loading -> LoadingState()
            is DiscoverUiState.Empty -> EmptyState(query = searchQuery)
            is DiscoverUiState.Error -> ErrorState(message = uiState.message, onRetry = onRetry)
            is DiscoverUiState.Success -> {
                if (isGridView) {
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = gridState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 1 && totalItems > 1
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) onLoadNextPage()
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        state = gridState,
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.results, key = { it.id }) { content ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn()
                            ) {
                                SearchItemCard(
                                    content = content,
                                    isLiked = likedIds.contains(content.id),
                                    isWatched = watchedIds.contains(content.id),
                                    isBlacklisted = blacklistedIds.contains(content.id),
                                    onFavoriteClick = { onFavoriteClick(content) },
                                    onWatchedClick = { onWatchedClick(content) },
                                    onBlacklistClick = { onBlacklistClick(content) },
                                    onClick = { onItemClick(content.id, content.type.name.lowercase()) }
                                )
                            }
                        }
                        if (isLoadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxCurrentLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    BouncingDotsSpinner()
                                }
                            }
                        }
                    }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 1 && totalItems > 1
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) onLoadNextPage()
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uiState.personSuggestions.isNotEmpty()) {
                            item(key = "__person_suggestions__") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Personas y compañías",
                                        style = UbuntuTypography.labelSmall,
                                        color = EleganteRose,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    uiState.personSuggestions.forEach { entity ->
                                        EntityRow(entity = entity, onClick = { onSelectEntity(entity) })
                                    }
                                    if (uiState.results.isNotEmpty()) {
                                        HorizontalDivider(
                                            color = DarkSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        items(uiState.results, key = { it.id }) { content ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 2 }
                            ) {
                                SearchItemCard(
                                    content = content,
                                    isLiked = likedIds.contains(content.id),
                                    isWatched = watchedIds.contains(content.id),
                                    isBlacklisted = blacklistedIds.contains(content.id),
                                    onFavoriteClick = { onFavoriteClick(content) },
                                    onWatchedClick = { onWatchedClick(content) },
                                    onBlacklistClick = { onBlacklistClick(content) },
                                    onClick = { onItemClick(content.id, content.type.name.lowercase()) }
                                )
                            }
                        }
                        if (isLoadingMore) {
                            item(key = "__loading_more__") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    BouncingDotsSpinner()
                                }
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = EleganteRose
        )
    }
}

@Composable
fun InitialState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = EleganteRose.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Busca tu película o serie favorita",
                style = UbuntuTypography.titleMedium,
                color = TextSecondary
            )
            Text(
                "Te diremos dónde la echan en España",
                style = UbuntuTypography.bodySmall,
                color = TextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BouncingDotsSpinner()
    }
}

@Composable
fun EmptyState(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.SearchOff, null,
                modifier = Modifier.size(64.dp),
                tint = TextSecondary.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (query.isBlank()) "No hay resultados" else "Sin resultados para \"$query\"",
                style = UbuntuTypography.titleSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ErrorOutline, null,
                Modifier.size(48.dp),
                tint = EleganteRoseDark.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = UbuntuTypography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FilmographyContent(
    view: FilmographyView,
    likedIds: Set<String>,
    watchedIds: Set<String>,
    blacklistedIds: Set<String>,
    isGridView: Boolean,
    onItemClick: (String, String) -> Unit,
    onFavoriteClick: (ContentPreview) -> Unit,
    onWatchedClick: (ContentPreview) -> Unit,
    onBlacklistClick: (ContentPreview) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit
) {
    if (view.isLoading) {
        LoadingState()
    } else if (view.movies != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = EleganteRose, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "Filmografía de ${view.entity.name}",
                        style = UbuntuTypography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    if (view.totalCount > 0) {
                        Text(
                            "${view.movies.size}/${view.totalCount}",
                            style = UbuntuTypography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                FilmographyMoviesGrid(
                    movies = view.movies,
                    likedIds = likedIds,
                    watchedIds = watchedIds,
                    blacklistedIds = blacklistedIds,
                    isGridView = isGridView,
                    onItemClick = onItemClick,
                    onFavoriteClick = onFavoriteClick,
                    onWatchedClick = onWatchedClick,
                    onBlacklistClick = onBlacklistClick,
                    hasMore = view.hasMore,
                    onLoadMore = onLoadMore
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron películas", color = TextSecondary)
        }
    }
}

@Composable
private fun EntityRow(entity: FilmographyEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (entity.profilePath != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(if (entity.type == EntityType.PERSON) "https://image.tmdb.org/t/p/w185${entity.profilePath}" else "https://logo.tmdb.org/t/p/w92${entity.profilePath}")
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (entity.type == EntityType.PERSON) Icons.Filled.Person else Icons.Outlined.Business,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entity.name, style = UbuntuTypography.bodyMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (entity.type == EntityType.PERSON) (entity.role ?: "Persona") else "Compañía",
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary
                    )
                    if (entity.role != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = EleganteRose.copy(alpha = 0.2f)
                        ) {
                            Text(
                                entity.role,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = UbuntuTypography.labelSmall,
                                color = EleganteRose,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
            Icon(
                if (entity.type == EntityType.PERSON) Icons.Filled.Person else Icons.Outlined.Business,
                contentDescription = null,
                tint = EleganteRoseDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FilmographyMoviesGrid(
    movies: List<ContentPreview>,
    likedIds: Set<String>,
    watchedIds: Set<String>,
    blacklistedIds: Set<String>,
    isGridView: Boolean,
    onItemClick: (String, String) -> Unit,
    onFavoriteClick: (ContentPreview) -> Unit,
    onWatchedClick: (ContentPreview) -> Unit,
    onBlacklistClick: (ContentPreview) -> Unit,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron películas", color = TextSecondary)
        }
        return
    }

    if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(movies, key = { it.id }) { content ->
                SearchItemCard(
                    content = content,
                    isLiked = likedIds.contains(content.id),
                    isWatched = watchedIds.contains(content.id),
                    isBlacklisted = blacklistedIds.contains(content.id),
                    onFavoriteClick = { onFavoriteClick(content) },
                    onWatchedClick = { onWatchedClick(content) },
                    onBlacklistClick = { onBlacklistClick(content) },
                    onClick = { onItemClick(content.id, content.type.name.lowercase()) }
                )
            }
            if (hasMore) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onLoadMore) {
                            Text("Cargar más", color = EleganteRose)
                        }
                    }
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        val shouldLoadMore = remember {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= movies.size - 3
            }
        }
        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value && hasMore) onLoadMore()
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(movies, key = { it.id }) { content ->
                SearchItemCard(
                    content = content,
                    isLiked = likedIds.contains(content.id),
                    isWatched = watchedIds.contains(content.id),
                    isBlacklisted = blacklistedIds.contains(content.id),
                    onFavoriteClick = { onFavoriteClick(content) },
                    onWatchedClick = { onWatchedClick(content) },
                    onBlacklistClick = { onBlacklistClick(content) },
                    onClick = { onItemClick(content.id, content.type.name.lowercase()) }
                )
            }
        }
    }
}
