package com.dondeloexan.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbEpisodeDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.PersonInfo
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.Sentiment
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseLight
import com.dondeloexan.presentation.theme.SocialFacebook
import com.dondeloexan.presentation.theme.SocialTwitter
import com.dondeloexan.presentation.theme.SocialYoutube
import com.dondeloexan.presentation.theme.RatingHigh
import com.dondeloexan.presentation.theme.RatingLow
import com.dondeloexan.presentation.theme.RatingMedium
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import com.dondeloexan.data.remote.mapper.toCertificationDisplay
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaDetailScreen(
    contentId: String,
    contentType: ContentType = ContentType.MOVIE,
    onBack: () -> Unit,
    viewModel: MediaDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    LaunchedEffect(contentId, contentType) {
        viewModel.loadContent(contentId, contentType)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    uiState.content?.title ?: "Detalle",
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                        tint = TextPrimary
                    )
                }
            },
            actions = {
                if (uiState.content?.type == com.dondeloexan.domain.model.ContentType.MOVIE) {
                    val isWatched = uiState.isMovieWatched == true
                    IconButton(onClick = { viewModel.toggleMovieWatched() }) {
                        Icon(
                            if (isWatched) Icons.Filled.Check else Icons.Filled.Check,
                            contentDescription = if (isWatched) "Quitar de vistos" else "Marcar como vista",
                            tint = if (isWatched) EleganteRose else TextPrimary
                        )
                    }
                    if (isWatched) {
                        val isFavorite = uiState.isMovieFavorite == true
                        IconButton(onClick = { viewModel.toggleMovieFavorite() }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isFavorite) "Quitar de favoritas" else "Marcar como favorita",
                                tint = if (isFavorite) EleganteRose else TextPrimary
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            windowInsets = WindowInsets(top = 0)
        )

        when {
            uiState.isLoading -> {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EleganteRose)
                }
            }
            uiState.error != null -> {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "Error", color = TextSecondary)
                }
            }
            uiState.content != null -> {
                val content = uiState.content!!
                val pageCount = if (content.type == ContentType.SERIES) 2 else 1
                val pagerState = rememberPagerState(pageCount = { pageCount })

                LaunchedEffect(pagerState.currentPage) {
                    if (content.type == ContentType.SERIES && pagerState.currentPage == 1
                        && uiState.seasonDetail == null && uiState.seasons.isNotEmpty()) {
                        viewModel.selectSeason(uiState.selectedSeason)
                    }
                }

                if (content.type == ContentType.SERIES) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = DarkBackground,
                        contentColor = EleganteRose
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text("Ficha") }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text("Episodios") }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> FichaTab(content, viewModel)
                        1 -> if (content.type == ContentType.SERIES) {
                            EpisodiosTab(
                                seasons = uiState.seasons,
                                selectedSeason = uiState.selectedSeason,
                                seasonDetail = uiState.seasonDetail,
                                watchedEpisodes = uiState.watchedEpisodes,
                                lastWatchedEpisode = if (uiState.selectedSeason == uiState.lastWatchedSeason) uiState.lastWatchedEpisode else null,
                                onSeasonSelected = viewModel::selectSeason,
                                onToggleEpisode = viewModel::toggleEpisodeWatched,
                                onMarkSeasonToggle = viewModel::markSeasonWatched
                            )
                }
            }
        }
    }
}

    uiState.cascadeProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCascadeWatched() },
            title = {
                Text("Marcar episodios anteriores", color = TextPrimary)
            },
            text = {
                Text(
                    "Hay ${proposal.count} episodios anteriores no vistos en esta temporada. ¿Marcarlos tambien como vistos?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCascadeWatched() }) {
                    Text("Si, marcar todos", color = EleganteRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCascadeWatched() }) {
                    Text("Solo este", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            tonalElevation = 0.dp
        )
    }
}
}

@Composable
private fun CollectionSection(viewModel: MediaDetailViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isCollectionLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EleganteRose, modifier = Modifier.size(24.dp))
        }
        return
    }

    val movies = uiState.collectionMovies
    if (movies.isNullOrEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Precuelas / Secuelas",
            style = UbuntuTypography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                CollectionItemCard(movie)
            }
        }
    }
}

@Composable
private fun CollectionItemCard(movie: ContentPreview) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/movie/${movie.tmdbId}"))
            context.startActivity(intent)
        },
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (movie.coverUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(movie.coverUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = movie.title,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.PlayCircle, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.width(120.dp)) {
                Text(
                    movie.title,
                    style = UbuntuTypography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (movie.year != null) {
                    Text(
                        movie.year.toString(),
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                if (movie.ratingImdb != null) {
                    Text(
                        "★ ${String.format("%.1f", movie.ratingImdb)}",
                        style = UbuntuTypography.labelSmall,
                        color = RatingHigh,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarSection(viewModel: MediaDetailViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isSimilarLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EleganteRose, modifier = Modifier.size(24.dp))
        }
        return
    }

    val similar = uiState.similarContent
    if (similar.isNullOrEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Similares",
            style = UbuntuTypography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(similar) { item ->
                SimilarItemCard(item)
            }
        }
    }
}

@Composable
private fun SimilarItemCard(item: ContentPreview) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/${if (item.type == ContentType.SERIES) "tv" else "movie"}/${item.tmdbId}"))
            context.startActivity(intent)
        },
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.width(110.dp).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.coverUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(90.dp, 135.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp, 135.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.PlayCircle, null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                item.title,
                style = UbuntuTypography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.year != null) {
                Text(
                    item.year.toString(),
                    style = UbuntuTypography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
            if (item.ratingImdb != null) {
                Text(
                    "★ ${String.format("%.1f", item.ratingImdb)}",
                    style = UbuntuTypography.labelSmall,
                    color = RatingHigh,
                    fontSize = 10.sp
                )
            }
        }
}
}

@Composable
private fun FichaTab(content: Content, viewModel: MediaDetailViewModel) {
    val displayPlatforms = remember(content) {
        if (content.type == ContentType.MOVIE && content.releaseDate != null) {
            appendCinemaPlatform(content.releaseDate, content.streamingPlatforms)
        } else {
            content.streamingPlatforms
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { HeroSection(content) }
        item { RatingRow(content) }
        if (displayPlatforms.isNotEmpty()) {
            item { StreamingSection(displayPlatforms) }
        }
        item { TechnicalInfoSection(content, viewModel) }
        if (content.externalLinks != null) {
            item { ExternalLinksSection(content.externalLinks) }
        }
        item { CriticReviewsSection(viewModel) }
        item { CollectionSection(viewModel) }
        item { SimilarSection(viewModel) }
    }
}

@Composable
private fun EpisodiosTab(
    seasons: List<TmdbSeasonDto>,
    selectedSeason: Int,
    seasonDetail: TmdbTvSeasonDetailDto?,
    watchedEpisodes: Set<String>,
    lastWatchedEpisode: Int?,
    onSeasonSelected: (Int) -> Unit,
    onToggleEpisode: (Int) -> Unit,
    onMarkSeasonToggle: () -> Unit
) {
    val listState = rememberLazyListState()
    var dialogEpisodeNumber by remember { mutableStateOf(0) }
    var dialogIsWatched by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(seasonDetail, lastWatchedEpisode) {
        if (seasonDetail != null && lastWatchedEpisode != null) {
            val index = seasonDetail.episodes.indexOfFirst { it.episodeNumber == lastWatchedEpisode }
            if (index >= 0) {
                listState.animateScrollToItem(index + 1)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        if (seasons.isNotEmpty()) {
            item {
                SeasonsSectionHeader(
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    seasonDetail = seasonDetail,
                    watchedEpisodes = watchedEpisodes,
                    onSeasonSelected = onSeasonSelected,
                    onMarkSeasonToggle = onMarkSeasonToggle
                )
            }

            if (seasonDetail != null) {
                items(seasonDetail.episodes, key = { "ep-${selectedSeason}-${it.episodeNumber}" }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        selectedSeason = selectedSeason,
                        watchedEpisodes = watchedEpisodes,
                        onLongClick = { epNum, isWatched ->
                            dialogEpisodeNumber = epNum
                            dialogIsWatched = isWatched
                            showDialog = true
                        },
                        onToggle = onToggleEpisode
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    "Episodio $dialogEpisodeNumber",
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    if (dialogIsWatched) "¿Marcar como no visto?" else "¿Marcar como visto?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onToggleEpisode(dialogEpisodeNumber)
                    showDialog = false
                }) {
                    Text(
                        if (dialogIsWatched) "No visto" else "Visto",
                        color = EleganteRose
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            tonalElevation = 0.dp
        )
    }
}

private data class AirDateInfo(
    val daysUntil: Long,
    val formattedDate: String
)

private fun parseAirDate(airDate: String?): AirDateInfo? {
    if (airDate == null) return null
    return try {
        val date = LocalDate.parse(airDate)
        val now = LocalDate.now()
        val days = ChronoUnit.DAYS.between(now, date)
        AirDateInfo(
            daysUntil = days,
            formattedDate = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        )
    } catch (e: Exception) {
        AppLogger.e("MediaDetailScreen", "parseAirDate: $airDate", e)
        null
    }
}

@Composable
private fun EpisodeAirDateBadge(info: AirDateInfo?) {
    if (info == null) return

    val days = info.daysUntil

    if (days <= 0) {
        Text(
            "Estrenado el ${info.formattedDate}",
            style = UbuntuTypography.labelSmall,
            color = TextSecondary.copy(alpha = 0.5f),
            fontSize = 10.sp
        )
    } else {
        val (text, bgColor, textColor) = when {
            days == 0L -> Triple(
                "¡Se estrena HOY!",
                EleganteRose,
                Color.White
            )
            days == 1L -> Triple(
                "Se estrena mañana",
                EleganteRose,
                Color.White
            )
            days < 7L -> Triple(
                "Quedan $days días",
                EleganteRoseLight,
                Color.White
            )
            else -> Triple(
                "En $days días",
                Color.Transparent,
                EleganteRoseLight
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = bgColor,
            border = if (days >= 7L) BorderStroke(1.dp, EleganteRoseLight.copy(alpha = 0.5f)) else null
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = UbuntuTypography.labelSmall,
                color = textColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HeroSection(content: Content) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        if (content.backdropUrl != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(content.backdropUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = "Fondo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (content.coverUrl != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(content.coverUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = "Fondo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DarkSurfaceVariant)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DarkBackground.copy(alpha = 0.95f)
                        ),
                        startY = 120f
                    )
                )
        )
    }
}

@Composable
private fun RatingRow(content: Content) {
    val ratings = listOfNotNull(
        content.ratingTmdb?.let { "TMDB" to String.format("%.1f", it) },
        content.ratingImdb?.let { "IMDb" to String.format("%.1f", it) },
        content.ratingRt?.let { "RT" to "${it}%" },
        content.ratingMetacritic?.let { "MC" to it.toString() }
    )

    if (ratings.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ratings.take(5).forEach { (source, value) ->
            val color = when {
                value.toFloatOrNull() != null -> {
                    val v = value.toFloatOrNull() ?: 0f
                    val normalized = if (source == "RT") v / 10f else if (source == "MC") v / 10f else v
                    when {
                        normalized >= 7.0f -> RatingHigh
                        normalized >= 5.0f -> RatingMedium
                        else -> RatingLow
                    }
                }
                else -> TextSecondary
            }

            Box(
                modifier = Modifier
                    .background(
                        DarkSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        source,
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        value,
                        style = UbuntuTypography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TechnicalInfoSection(content: Content, viewModel: MediaDetailViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Ficha Técnica",
            style = UbuntuTypography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        val certificationDisplay = content.certification?.toCertificationDisplay()
        val fields = listOfNotNull(
            "Título Original" to content.originalTitle,
            "Año" to content.year?.toString(),
            "Duración" to content.durationMinutes?.let { "${it} min" },
            "País" to content.countries.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Dirección" to content.directors.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name },
            "Guion" to content.writers.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Reparto" to content.cast.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name },
            "Música" to content.music.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Fotografía" to content.cinematography.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Compañías" to content.productionCompanies.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Género" to content.genres.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )

        if (certificationDisplay != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Clasificación",
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(120.dp)
                )
                Icon(
                    imageVector = certificationDisplay.icon,
                    contentDescription = null,
                    tint = certificationDisplay.color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    certificationDisplay.label,
                    style = UbuntuTypography.bodySmall,
                    color = certificationDisplay.color,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        fields.forEach { (label, value) ->
            if (value != null) {
                FieldRow(label, value)
            }
        }

        if (content.cast.any { it.profilePath != null }) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text("Reparto", style = UbuntuTypography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val detailScope = rememberCoroutineScope()
            val castSocialInfo by viewModel.castSocialInfo.collectAsState()
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(content.cast.take(10)) { person ->
                    val hasLink = person.tmdbId != null
                    val socialInfo = person.tmdbId?.let { castSocialInfo[it] }
                    val borderColor = when (socialInfo?.type) {
                        SocialLinkType.INSTAGRAM -> EleganteRose
                        SocialLinkType.TWITTER -> SocialTwitter
                        SocialLinkType.FACEBOOK -> SocialFacebook
                        SocialLinkType.YOUTUBE -> SocialYoutube
                        else -> Color.Transparent
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(80.dp).then(
                            if (hasLink) Modifier.clickable {
                                detailScope.launch {
                                    viewModel.getPersonSocialUrl(person.tmdbId!!) { url ->
                                        if (url != null) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                }
                            } else Modifier
                        )
                    ) {
                        if (person.profilePath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("https://image.tmdb.org/t/p/w185${person.profilePath}")
                                    .crossfade(200)
                                    .build(),
                                contentDescription = person.name,
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(DarkSurfaceVariant)
                                    .then(
                                        if (borderColor != Color.Transparent) Modifier.border(2.dp, borderColor, CircleShape)
                                        else Modifier
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(person.name, style = UbuntuTypography.labelSmall, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (!content.synopsis.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text(
                "Sinopsis",
                style = UbuntuTypography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                content.synopsis,
                style = UbuntuTypography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = UbuntuTypography.bodySmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(120.dp)
        )
        Text(
            value,
            style = UbuntuTypography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExternalLinksSection(links: ExternalLinks) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items = listOfNotNull(
        links.imdbId?.let { Triple(it, Icons.Outlined.Star, "IMDb") },
        links.wikipediaUrl?.let { Triple(it, Icons.AutoMirrored.Outlined.MenuBook, "Wikipedia") },
        links.facebookId?.let { Triple(it, Icons.Outlined.People, "Facebook") },
        links.instagramId?.let { Triple(it, Icons.Outlined.CameraAlt, "Instagram") },
        links.twitterId?.let { Triple(it, Icons.Outlined.AlternateEmail, "Twitter") },
        links.youtubeId?.let { Triple(it, Icons.Outlined.PlayCircle, "YouTube") },
        links.homepage?.let { Triple(it, Icons.Outlined.Language, "Web") },
        links.filmaffinityUrl?.let { Triple(it, Icons.Outlined.Star, "FilmAffinity") }
    )

    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Enlaces",
            style = UbuntuTypography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(items) { (value, icon, label) ->
                val url = when (label) {
                    "IMDb" -> "https://www.imdb.com/title/$value/"
                    "Wikipedia" -> value
                    "Facebook" -> "https://facebook.com/$value/"
                    "Instagram" -> "https://instagram.com/$value/"
                    "Twitter" -> "https://x.com/$value/"
                    "YouTube" -> if (value.startsWith("http")) value else "https://www.youtube.com/watch?v=$value"
                    "Web" -> value
                    "FilmAffinity" -> value
                    else -> value
                }
                Surface(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(icon, contentDescription = label, tint = EleganteRose, modifier = Modifier.size(20.dp))
                        Text(label, style = UbuntuTypography.labelSmall, color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun appendCinemaPlatform(
    releaseDate: String,
    platforms: List<StreamingAvailability>
): List<StreamingAvailability> {
    val cinemaLabel = try {
        val date = LocalDate.parse(releaseDate)
        val now = LocalDate.now()
        val daysSinceRelease = ChronoUnit.DAYS.between(date, now)
        val daysUntilRelease = ChronoUnit.DAYS.between(now, date)
        val cinemaEnd = date.plusDays(90)

        when {
            daysSinceRelease in 0..90 -> "En cines → Fin: ${cinemaEnd.format(DateTimeFormatter.ofPattern("dd/MM"))}"
            daysUntilRelease > 0 -> "Estreno: ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
            else -> null
        }
    } catch (e: Exception) {
        AppLogger.e("MediaDetailScreen", "cinemaPlatform: $releaseDate", e)
        null
    }

    return if (cinemaLabel != null) {
        val cinemaPlatform = StreamingAvailability(
            platformName = cinemaLabel,
            platformId = null,
            logoUrl = null,
            availabilityType = com.dondeloexan.domain.model.AvailabilityType.SUBSCRIPTION
        )
        listOf(cinemaPlatform) + platforms
    } else {
        platforms
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamingSection(platforms: List<StreamingAvailability>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Dónde ver en España",
            style = UbuntuTypography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            platforms.forEach { platform ->
                Column(
                    modifier = Modifier.width(60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (platform.platformName == "Cine") {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    DarkSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "\uD83C\uDFAC",
                                fontSize = 20.sp
                            )
                        }
                    } else if (platform.logoUrl != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(platform.logoUrl)
                                .crossfade(200)
                                .build(),
                            contentDescription = platform.platformName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    DarkSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                platform.platformName.take(2),
                                style = UbuntuTypography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Text(
                        platform.platformName,
                        style = UbuntuTypography.labelSmall,
                        color = if (platform.platformName == "Cine") EleganteRose else TextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonsSectionHeader(
    seasons: List<TmdbSeasonDto>,
    selectedSeason: Int,
    seasonDetail: TmdbTvSeasonDetailDto?,
    watchedEpisodes: Set<String>,
    onSeasonSelected: (Int) -> Unit,
    onMarkSeasonToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))

        Text(
            "Temporadas",
            style = UbuntuTypography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        ScrollableTabRow(
            selectedTabIndex = seasons.indexOfFirst { it.seasonNumber == selectedSeason }
                .coerceAtLeast(0),
            containerColor = DarkBackground,
            contentColor = EleganteRose,
            edgePadding = 16.dp
        ) {
            seasons.forEach { season ->
                Tab(
                    selected = season.seasonNumber == selectedSeason,
                    onClick = { onSeasonSelected(season.seasonNumber) },
                    text = {
                        Text(
                            "T${season.seasonNumber}",
                            color = if (season.seasonNumber == selectedSeason) EleganteRose else TextSecondary
                        )
                    }
                )
            }
        }

        if (seasonDetail != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val allWatched = seasonDetail.episodes.isNotEmpty() &&
                    seasonDetail.episodes.all { ep ->
                        watchedEpisodes.contains("S${selectedSeason}E${ep.episodeNumber}")
                    }
                TextButton(onClick = onMarkSeasonToggle) {
                    Icon(
                        Icons.Outlined.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (allWatched) "Desmarcar todo" else "Marcar todo visto"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: TmdbEpisodeDto,
    selectedSeason: Int,
    watchedEpisodes: Set<String>,
    onLongClick: (Int, Boolean) -> Unit,
    onToggle: (Int) -> Unit
) {
    val episodeKey = "S${selectedSeason}E${episode.episodeNumber}"
    val isWatched = watchedEpisodes.contains(episodeKey)
    val airDateInfo = remember(episode.airDate) { parseAirDate(episode.airDate) }
    val isAired = airDateInfo == null || airDateInfo.daysUntil <= 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isAired) onToggle(episode.episodeNumber)
                },
                onLongClick = {
                    if (isAired) onLongClick(episode.episodeNumber, isWatched)
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isWatched) Icons.Filled.Check else Icons.Outlined.CheckCircleOutline,
            contentDescription = if (isWatched) "Visto" else "Marcar visto",
            tint = if (isWatched) EleganteRose else if (isAired) TextSecondary.copy(alpha = 0.4f) else TextSecondary.copy(alpha = 0.15f),
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${episode.episodeNumber}. ${episode.name}",
                    style = UbuntuTypography.bodyMedium,
                    color = if (isAired) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                EpisodeAirDateBadge(airDateInfo)
            }
            if (!episode.overview.isNullOrBlank()) {
                Text(
                    episode.overview,
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (episode.voteAverage != null && episode.voteAverage > 0f) {
            Text(
                String.format("%.1f", episode.voteAverage),
                style = UbuntuTypography.labelSmall,
                color = when {
                    episode.voteAverage >= 7.0f -> RatingHigh
                    episode.voteAverage >= 5.0f -> RatingMedium
                    else -> RatingLow
                }
            )
        }
    }
}

@Composable
private fun CriticReviewsSection(viewModel: MediaDetailViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isCriticReviewsLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EleganteRose, modifier = Modifier.size(24.dp))
        }
        return
    }

    val reviews = uiState.criticReviews
    if (reviews.isNullOrEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Crítica",
            style = UbuntuTypography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reviews.forEach { review ->
                CriticReviewCard(review)
            }
        }
    }
}

@Composable
private fun CriticReviewCard(review: CriticReview) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = when (review.sentiment) {
                            Sentiment.POSITIVE -> RatingHigh
                            Sentiment.NEGATIVE -> RatingLow
                            Sentiment.NEUTRAL -> RatingMedium
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            review.author,
                            style = UbuntuTypography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            review.publication,
                            style = UbuntuTypography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                if (review.rating != null) {
                    Text(
                        review.rating,
                        style = UbuntuTypography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                review.text,
                style = UbuntuTypography.bodySmall,
                color = TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (review.url != null) {
                Spacer(Modifier.height(4.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(review.url))
                        context.startActivity(intent)
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("Leer crítica completa", color = EleganteRose, fontSize = 11.sp)
                }
            }
        }
    }
}
