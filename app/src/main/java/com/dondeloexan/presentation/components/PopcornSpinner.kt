package com.dondeloexan.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dondeloexan.R

@Composable
fun PopcornSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val transition = rememberInfiniteTransition(label = "popcornLoop")

    val bucketPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bucketBounce"
    )
    val bucketScale = 0.93f + bucketPhase * 0.14f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        PopcornKernel(transition, 0, offsetX = -30.dp, maxY = -72.dp)
        PopcornKernel(transition, 1, offsetX = -14.dp, maxY = -88.dp)
        PopcornKernel(transition, 2, offsetX = 0.dp, maxY = -96.dp)
        PopcornKernel(transition, 3, offsetX = 16.dp, maxY = -84.dp)
        PopcornKernel(transition, 4, offsetX = 32.dp, maxY = -68.dp)

        Image(
            painter = painterResource(R.drawable.ic_popcorn),
            contentDescription = "Loading",
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = bucketScale
                    scaleY = bucketScale
                }
        )
    }
}

@Composable
private fun PopcornKernel(
    transition: androidx.compose.animation.core.InfiniteTransition,
    index: Int,
    offsetX: Dp,
    maxY: Dp
) {
    val totalDuration = 1500
    val staggerDelay = index * (totalDuration / 5)

    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(totalDuration, delayMillis = staggerDelay, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "kernel$index"
    )

    val scale = when {
        progress < 0.2f -> (progress / 0.2f) * 1.35f
        progress < 0.45f -> 1.35f - ((progress - 0.2f) / 0.25f) * 0.45f
        else -> (0.9f - ((progress - 0.45f) / 0.55f) * 0.1f).coerceAtLeast(0.5f)
    }

    val kernelAlpha = when {
        progress < 0.15f -> progress / 0.15f
        progress > 0.8f -> 1f - (progress - 0.8f) / 0.2f
        else -> 1f
    }

    val yFraction = if (progress < 0.55f) {
        (progress / 0.55f) * 1.15f
    } else {
        1.15f - ((progress - 0.55f) / 0.45f) * 0.15f
    }
    val yOffset = maxY * yFraction.coerceIn(0f, 1f)
    val xOffset = offsetX * progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                translationX = xOffset.toPx()
                translationY = yOffset.toPx()
                scaleX = scale
                scaleY = scale
                alpha = kernelAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = "\uD83C\uDF7F",
            fontSize = 18.sp
        )
    }
}
