package com.dondeloexan.presentation.blacklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(
    navController: NavController,
    viewModel: BlacklistViewModel = koinViewModel()
) {
    val items by viewModel.items.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Contenidos ocultos", color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            windowInsets = WindowInsets(top = 0)
        )

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.Block, null,
                    modifier = Modifier.size(64.dp),
                    tint = TextSecondary.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No hay contenidos ocultos", style = UbuntuTypography.titleSmall, color = TextSecondary)
                Text(
                    "Los contenidos que bloquees apareceran aqui",
                    style = UbuntuTypography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(items, key = { it.contentId }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = UbuntuTypography.bodyLarge, color = TextPrimary)
                            Text(item.type, style = UbuntuTypography.labelSmall, color = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.remove(item) }) {
                            Icon(Icons.Outlined.Close, "Desbloquear", tint = EleganteRose)
                        }
                    }
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}
