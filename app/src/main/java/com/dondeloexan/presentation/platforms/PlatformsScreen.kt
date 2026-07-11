package com.dondeloexan.presentation.platforms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import org.koin.androidx.compose.koinViewModel

private val allPlatforms = listOf(
    "Netflix", "Prime Video", "Disney+", "HBO Max",
    "Movistar+", "Apple TV+", "Paramount+", "SkyShowtime",
    "Filmin", "Atresplayer", "Mitele", "RTVE Play"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformsScreen(
    navController: NavController,
    viewModel: PlatformsViewModel = koinViewModel()
) {
    val activePlatforms by viewModel.activePlatforms.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Mis Plataformas", color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            windowInsets = WindowInsets(top = 0)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Selecciona tus servicios de streaming",
                style = UbuntuTypography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            HorizontalDivider(color = DarkSurfaceVariant)

            LazyColumn {
                items(allPlatforms) { platform ->
                    PlatformToggle(
                        name = platform,
                        isActive = platform in activePlatforms,
                        onToggle = { viewModel.togglePlatform(platform) }
                    )
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun PlatformToggle(name: String, isActive: Boolean, onToggle: (Boolean) -> Unit) {
    val platformColors = mapOf(
        "Netflix" to "#E50914",
        "Prime Video" to "#00A8E1",
        "Disney+" to "#113CC3",
        "HBO Max" to "#5822B4",
        "Movistar+" to "#00A8E1",
        "Apple TV+" to "#555555",
        "Paramount+" to "#0064FF",
        "SkyShowtime" to "#000000",
        "Filmin" to "#E30613",
        "Atresplayer" to "#1E8C45",
        "Mitele" to "#00BFFF",
        "RTVE Play" to "#E1251B"
    )

    val accentColor = platformColors[name]?.let {
        Color(android.graphics.Color.parseColor(it))
    } ?: TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accentColor.copy(alpha = if (isActive) 0.2f else 0.05f)
        ) {
            Text(
                text = name.take(2).uppercase(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = UbuntuTypography.labelLarge,
                color = accentColor.copy(alpha = if (isActive) 1f else 0.5f)
            )
        }

        Text(
            text = name,
            style = UbuntuTypography.bodyLarge,
            color = if (isActive) TextPrimary else TextSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )

        Switch(
            checked = isActive,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EleganteRose,
                checkedTrackColor = EleganteRose.copy(alpha = 0.3f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}
