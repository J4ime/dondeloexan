package com.dondeloexan.presentation.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dondeloexan.presentation.settings.UpdateCheckState
import com.dondeloexan.presentation.theme.CinemaRed
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.PopcornYellow
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.Color

@Composable
fun UpdateCheckTrailing(state: UpdateCheckState, modifier: Modifier = Modifier) {
    when (state) {
        is UpdateCheckState.Idle -> {
            Text(
                "Comprobar",
                style = UbuntuTypography.labelMedium,
                color = PopcornYellow,
                modifier = modifier
            )
        }

        is UpdateCheckState.Checking -> {
            CircularProgressIndicator(
                modifier = modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = PopcornYellow
            )
        }

        is UpdateCheckState.UpToDate -> {
            Icon(
                Icons.Outlined.CheckCircle, contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = modifier.size(20.dp)
            )
        }

        is UpdateCheckState.Error -> {
            Icon(
                Icons.Outlined.ErrorOutline, contentDescription = null,
                tint = CinemaRed,
                modifier = modifier.size(20.dp)
            )
        }

        is UpdateCheckState.UpdateAvailable -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PopcornYellow.copy(alpha = 0.15f),
                modifier = modifier
            ) {
                Text(
                    "v${state.release.version.displayName}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = UbuntuTypography.labelSmall,
                    color = PopcornYellow
                )
            }
        }
    }
}
