package com.dondeloexan.presentation.discover.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.PlatformActive
import com.dondeloexan.presentation.theme.PlatformActiveBorder
import com.dondeloexan.presentation.theme.PlatformInactiveBorder
import com.dondeloexan.presentation.theme.PopcornYellow
import com.dondeloexan.presentation.theme.RatingHigh
import com.dondeloexan.presentation.theme.RatingLow
import com.dondeloexan.presentation.theme.RatingMedium
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.request.CachePolicy

@Composable
fun SearchItemCard(
    content: ContentPreview,
    activePlatforms: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PosterWithRatingOverlay(
                coverUrl = content.coverUrl,
                rating = content.ratingFa,
                modifier = Modifier.size(width = 100.dp, height = 150.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = content.title,
                    style = UbuntuTypography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                content.year?.let { year ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChip(year.toString())
                        if (content.genres.isNotEmpty()) {
                            MetaChip(content.genres.first())
                        }
                    }
                }

                if (content.directors.isNotEmpty()) {
                    Text(
                        text = "Dir. ${content.directors.take(2).joinToString(", ")}",
                        style = UbuntuTypography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                PlatformBadgeRow(
                    platforms = content.streamingPlatforms,
                    activePlatforms = activePlatforms
                )
            }
        }
    }
}

@Composable
fun PosterWithRatingOverlay(
    coverUrl: String?,
    rating: Float?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        if (coverUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
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
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (rating != null) {
            RatingBadge(rating = rating, modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp))
        }
    }
}

@Composable
fun RatingBadge(rating: Float, modifier: Modifier = Modifier) {
    val badgeColor = when {
        rating >= 7.0f -> RatingHigh
        rating >= 5.0f -> RatingMedium
        else -> RatingLow
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("★", color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format("%.1f", rating),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = UbuntuTypography.labelSmall,
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun PlatformBadgeRow(
    platforms: List<StreamingAvailability>,
    activePlatforms: Set<String>,
    maxVisible: Int = 3
) {
    if (platforms.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        platforms.take(maxVisible).forEach { platform ->
            val isActive = activePlatforms.any { userP ->
                platform.platformName.contains(userP, ignoreCase = true)
            }
            PlatformChip(name = platform.platformName, isActive = isActive)
        }

        val remaining = platforms.size - maxVisible
        if (remaining > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkSurfaceVariant
            ) {
                Text(
                    text = "+$remaining",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = UbuntuTypography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun PlatformChip(name: String, isActive: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) PlatformActive else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isActive) PlatformActiveBorder else PlatformInactiveBorder
        )
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = UbuntuTypography.labelSmall,
            color = if (isActive) PopcornYellow else TextSecondary,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}
