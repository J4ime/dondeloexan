package com.dondeloexan.presentation.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.UbuntuTypography
import kotlinx.coroutines.delay

@Composable
fun FeedbackBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    var visible by remember(message) { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            visible = true
            delay(2000)
            visible = false
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EleganteRose.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message ?: "",
                    style = UbuntuTypography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
    }
}