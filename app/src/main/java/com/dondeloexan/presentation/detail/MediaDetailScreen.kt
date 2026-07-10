package com.dondeloexan.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.RatingHigh
import com.dondeloexan.presentation.theme.RatingLow
import com.dondeloexan.presentation.theme.RatingMedium
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import com.dondeloexan.util.AppLogger
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    contentId: String,
    onBack: () -> Unit,
    viewModel: MediaDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(contentId) {
        viewModel.loadContent(contentId)
    }

    Scaffold(
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EleganteRose)
                }
            }
            uiState.error != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "Error", color = TextSecondary)
                }
            }
            uiState.content != null -> {
                DetailContent(
                    content = uiState.content!!,
                    seasons = uiState.seasons,
                    selectedSeason = uiState.selectedSeason,
                    seasonDetail = uiState.seasonDetail,
                    watchedEpisodes = uiState.watchedEpisodes,
                    onSeasonSelected = viewModel::selectSeason,
                    onToggleEpisode = viewModel::toggleEpisodeWatched,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    content: Content,
    seasons: List<TmdbSeasonDto>,
    selectedSeason: Int,
    seasonDetail: TmdbTvSeasonDetailDto?,
    watchedEpisodes: Set<String>,
    onSeasonSelected: (Int) -> Unit,
    onToggleEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            HeroSection(content)
        }

        item {
            RatingRow(content)
        }

        item {
            TechnicalInfoSection(content)
        }

        if (content.streamingPlatforms.isNotEmpty()) {
            item {
                StreamingSection(content)
            }
        }

        if (content.type == ContentType.SERIES && seasons.isNotEmpty()) {
            item {
                SeasonsSection(
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    seasonDetail = seasonDetail,
                    watchedEpisodes = watchedEpisodes,
                    onSeasonSelected = onSeasonSelected,
                    onToggleEpisode = onToggleEpisode
                )
            }
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
        content.ratingFa?.let { "FA" to it.toString() },
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
private fun TechnicalInfoSection(content: Content) {
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

        val fields = listOfNotNull(
            "Título Original" to content.originalTitle,
            "Año" to content.year?.toString(),
            "Duración" to content.durationMinutes?.let { "${it} min" },
            "País" to content.countries.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Dirección" to content.directors.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Guion" to content.writers.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Reparto" to content.cast.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Música" to content.music.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Fotografía" to content.cinematography.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Compañías" to content.productionCompanies.takeIf { it.isNotEmpty() }?.joinToString(", "),
            "Género" to content.genres.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )

        fields.forEach { (label, value) ->
            if (value != null) {
                FieldRow(label, value)
            }
        }

        if (!content.synopsis.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
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
private fun StreamingSection(content: Content) {
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content.streamingPlatforms.forEach { platform ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (platform.logoUrl != null) {
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
                        color = TextSecondary,
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
private fun SeasonsSection(
    seasons: List<TmdbSeasonDto>,
    selectedSeason: Int,
    seasonDetail: TmdbTvSeasonDetailDto?,
    watchedEpisodes: Set<String>,
    onSeasonSelected: (Int) -> Unit,
    onToggleEpisode: (Int) -> Unit
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

        Spacer(Modifier.height(8.dp))

        if (seasonDetail != null) {
            seasonDetail.episodes.forEach { episode ->
                val episodeKey = "S${selectedSeason}E${episode.episodeNumber}"
                val isWatched = watchedEpisodes.contains(episodeKey)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleEpisode(episode.episodeNumber) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isWatched) Icons.Filled.Check else Icons.Outlined.CheckCircleOutline,
                        contentDescription = if (isWatched) "Visto" else "Marcar visto",
                        tint = if (isWatched) EleganteRose else TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${episode.episodeNumber}. ${episode.name}",
                            style = UbuntuTypography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
        }
    }
}
