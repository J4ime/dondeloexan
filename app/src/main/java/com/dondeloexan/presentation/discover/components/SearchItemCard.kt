package com.dondeloexan.presentation.discover.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseLight
import com.dondeloexan.presentation.theme.RatingHigh
import com.dondeloexan.presentation.theme.RatingLow
import com.dondeloexan.presentation.theme.RatingMedium
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.request.CachePolicy

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchItemCard(
    content: ContentPreview,
    isLiked: Boolean = false,
    isWatched: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onWatchedClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onFavoriteClick
            )
    ) {
        if (content.coverUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(content.coverUrl)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeBadge(content.type.name)
                Spacer(Modifier.weight(1f))
                if (content.ratingFa != null) {
                    RatingBadgeLarge(rating = content.ratingFa)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = content.title,
                style = UbuntuTypography.titleMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )

            if (content.year != null || content.directors.isNotEmpty()) {
                Text(
                    text = buildString {
                        content.year?.let { append(it) }
                        if (content.directors.isNotEmpty()) {
                            if (isNotEmpty()) append("  ·  ")
                            append("Dir. ${content.directors.take(2).joinToString(", ")}")
                        }
                    },
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (content.streamingPlatforms.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                PlatformLogoRow(
                    platforms = content.streamingPlatforms,
                    maxVisible = 4
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isLiked) EleganteRose else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onWatchedClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    if (isWatched) Icons.Filled.Check else Icons.Outlined.CheckCircleOutline,
                    contentDescription = "Visto",
                    tint = if (isWatched) EleganteRose else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val label = if (type == "SERIES") "SERIE" else "PELÍCULA"
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = EleganteRose.copy(alpha = 0.85f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = UbuntuTypography.labelSmall,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun RatingBadgeLarge(rating: Float, modifier: Modifier = Modifier) {
    val badgeColor = when {
        rating >= 7.0f -> RatingHigh
        rating >= 5.0f -> RatingMedium
        else -> RatingLow
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("★", color = badgeColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format("%.1f", rating),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PlatformLogoRow(
    platforms: List<StreamingAvailability>,
    maxVisible: Int = 4
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = platform.platformName.take(2),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp
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
                fontSize = 11.sp
            )
        }
    }
}
