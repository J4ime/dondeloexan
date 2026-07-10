package com.dondeloexan.presentation.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.EleganteRoseDark
import com.dondeloexan.presentation.theme.EleganteRoseLight
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import com.dondeloexan.util.LogEntry
import com.dondeloexan.util.LogLevel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    navController: NavController,
    viewModel: LogViewerViewModel = koinViewModel()
) {
    val logs by viewModel.filteredLogs.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro de errores", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack, "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Outlined.Delete, "Limpiar", tint = TextPrimary)
                    }
                    IconButton(onClick = { viewModel.shareLogs(navController.context) }) {
                        Icon(Icons.Outlined.Share, "Compartir", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LogFilterRow(
                activeFilter = activeFilter,
                onFilterChange = viewModel::setFilter
            )

            HorizontalDivider(color = DarkSurfaceVariant, thickness = 1.dp)

            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay registros", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    items(logs, key = { "${it.timestamp}_${it.hashCode()}" }) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }

            HorizontalDivider(color = DarkSurfaceVariant, thickness = 1.dp)
            Surface(color = DarkBackground) {
                Text(
                    "${logs.size} registros · Último: ${logs.firstOrNull()?.formattedTime ?: "—"}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = UbuntuTypography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun LogEntryRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> Color(0xFF90CAF9)
        LogLevel.INFO -> Color(0xFF81C784)
        LogLevel.WARN -> EleganteRoseLight
        LogLevel.ERROR -> EleganteRoseDark
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                entry.formattedTime,
                style = UbuntuTypography.labelSmall,
                color = TextSecondary.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = levelColor.copy(alpha = 0.15f)
            ) {
                Text(
                    entry.level.name,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = UbuntuTypography.labelSmall,
                    color = levelColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                entry.tag,
                style = UbuntuTypography.labelSmall,
                color = TextSecondary.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            entry.message,
            style = UbuntuTypography.bodySmall,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
        entry.throwable?.let { trace ->
            Text(
                trace,
                style = UbuntuTypography.bodySmall,
                color =                 EleganteRoseDark.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
}

@Composable
fun LogFilterRow(
    activeFilter: LogLevel?,
    onFilterChange: (LogLevel?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = activeFilter == null,
            onClick = { onFilterChange(null) },
            label = { Text("Todos", fontSize = 11.sp) }
        )
        LogLevel.entries.forEach { level ->
            val chipColor = when (level) {
                LogLevel.ERROR -> EleganteRoseDark.copy(alpha = 0.15f)
                LogLevel.WARN -> EleganteRoseLight.copy(alpha = 0.15f)
                LogLevel.INFO -> Color(0xFF81C784).copy(alpha = 0.15f)
                LogLevel.DEBUG -> Color(0xFF90CAF9).copy(alpha = 0.15f)
            }
            FilterChip(
                selected = activeFilter == level,
                onClick = { onFilterChange(level) },
                label = { Text(level.name, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor
                )
            )
        }
    }
}
