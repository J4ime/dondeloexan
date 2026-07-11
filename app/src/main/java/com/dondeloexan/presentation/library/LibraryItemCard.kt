package com.dondeloexan.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.RatingHigh
import com.dondeloexan.presentation.theme.RatingLow
import com.dondeloexan.presentation.theme.RatingMedium
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.request.CachePolicy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun LibraryItemCard(
    posterUrl: String?,
    title: String,
    year: Int?,
    ratingFa: Float?,
    streamingPlatforms: List<StreamingAvailability>,
    watchedCount: Int = 0,
    totalEpisodes: Int? = null,
    nextEpisodeAirDate: String? = null,
    nextEpisodeNumber: Int? = null,
    nextEpisodeSeasonNumber: Int? = null,
    seriesStatus: String? = null,
    inProduction: Boolean? = null,
    numberOfSeasons: Int? = null,
    isLiked: Boolean,
    isWatched: Boolean,
    onLikeClick: () -> Unit,
    onWatchedClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (posterUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .crossfade(300)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = "Poster",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(com.dondeloexan.R.drawable.placeholder_poster)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Movie, null,
                    tint = TextSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 80f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = UbuntuTypography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (ratingFa != null) {
                    RatingBadgeSmall(rating = ratingFa)
                }
            }

            if (year != null) {
                Text(
                    text = year.toString(),
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            val isFinished = totalEpisodes != null && totalEpisodes > 0 && watchedCount >= totalEpisodes
            val isFinalEpisode = totalEpisodes != null && totalEpisodes > 0 &&
                    nextEpisodeAirDate != null && inProduction == false &&
                    !isFinished

            if (isFinalEpisode || isFinished) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isFinalEpisode) FinalEpisodeBadge()
                    if (isFinished) FinishedBadge()
                }
            }

            if (totalEpisodes != null && totalEpisodes > 0) {
                Spacer(Modifier.height(6.dp))
                val progress = (watchedCount.toFloat() / totalEpisodes).coerceIn(0f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$watchedCount/$totalEpisodes episodios",
                            style = UbuntuTypography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = UbuntuTypography.labelSmall,
                            color = if (progress >= 1f) EleganteRose else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = EleganteRose,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }

            if (nextEpisodeAirDate != null && seriesStatus != "Ended" && seriesStatus != "Canceled") {
                Spacer(Modifier.height(4.dp))
                NextEpisodeLabel(
                    airDate = nextEpisodeAirDate,
                    season = nextEpisodeSeasonNumber,
                    episode = nextEpisodeNumber
                )
            }

            if (streamingPlatforms.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                PlatformBadgeRow(platforms = streamingPlatforms)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    if (isLiked) Icons.Filled.Add else Icons.Outlined.Add,
                    contentDescription = "Favorito",
                    tint = if (isLiked) EleganteRose else TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onWatchedClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    if (isWatched) Icons.Filled.Check else Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Visto",
                    tint = if (isWatched) EleganteRose else TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun FinalEpisodeBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFFF8F00).copy(alpha = 0.85f)
    ) {
        Text(
            text = "Capítulo Final",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = UbuntuTypography.labelSmall,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FinishedBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = RatingHigh.copy(alpha = 0.85f)
    ) {
        Text(
            text = "Terminada",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = UbuntuTypography.labelSmall,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RatingBadgeSmall(rating: Float) {
    val badgeColor = when {
        rating >= 7.0f -> RatingHigh
        rating >= 5.0f -> RatingMedium
        else -> RatingLow
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("★", color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format("%.1f", rating),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlatformBadgeRow(platforms: List<StreamingAvailability>, maxVisible: Int = 3) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        platforms.take(maxVisible).forEach { platform ->
            if (platform.logoUrl != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(platform.logoUrl)
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = platform.platformName,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = platform.platformName.take(2),
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                }
            }
        }

        val remaining = platforms.size - maxVisible
        if (remaining > 0) {
            Text(
                text = "+$remaining",
                style = UbuntuTypography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun NextEpisodeLabel(
    airDate: String,
    season: Int?,
    episode: Int?
) {
    val label = remember(airDate) {
        try {
            val date = LocalDate.parse(airDate)
            val now = LocalDate.now()
            val days = ChronoUnit.DAYS.between(now, date)
            when {
                days < 0 -> null
                days == 0L -> "¡Hoy nuevo episodio!"
                days == 1L -> "Mañana nuevo episodio"
                days <= 7 -> "Próximo en $days días"
                else -> "Próximo: ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
            }
        } catch (_: Exception) { null }
    }
    val seasonEpisode = if (season != null && episode != null) "T${season}E$episode" else null

    if (label != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = EleganteRose
            )
            Text(
                buildString {
                    append(label)
                    if (seasonEpisode != null) append(" · $seasonEpisode")
                },
                style = UbuntuTypography.labelSmall,
                color = EleganteRose,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
