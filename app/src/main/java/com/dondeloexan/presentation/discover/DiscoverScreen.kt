package com.dondeloexan.presentation.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.presentation.discover.components.SearchItemCard
import com.dondeloexan.presentation.navigation.BottomNavigationBar
import com.dondeloexan.presentation.theme.CinemaRed
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.PopcornYellow
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    navController: NavController,
    viewModel: DiscoverViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activePlatforms by viewModel.activePlatforms.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descubrir", color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DiscoverSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                onClear = viewModel::onClearSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            DiscoverContent(
                uiState = uiState,
                searchQuery = searchQuery,
                activePlatforms = activePlatforms,
                onItemClick = { contentId ->
                    navController.navigate("discover/$contentId")
                },
                onRetry = viewModel::onRetry
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("Buscar película o serie...", color = TextSecondary)
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, null, tint = TextSecondary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, "Limpiar", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PopcornYellow.copy(alpha = 0.5f),
            unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = PopcornYellow,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        ),
        textStyle = UbuntuTypography.bodyLarge
    )
}

@Composable
fun DiscoverContent(
    uiState: DiscoverUiState,
    searchQuery: String,
    activePlatforms: Set<String>,
    onItemClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        is DiscoverUiState.Initial -> InitialState()
        is DiscoverUiState.Loading -> LoadingState()
        is DiscoverUiState.Empty -> EmptyState(query = searchQuery)
        is DiscoverUiState.Error -> ErrorState(message = uiState.message, onRetry = onRetry)
        is DiscoverUiState.Success -> {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.results, key = { it.id }) { content ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        SearchItemCard(
                            content = content,
                            activePlatforms = activePlatforms,
                            onClick = { onItemClick(content.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InitialState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(com.dondeloexan.R.drawable.ic_popcorn),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = PopcornYellow.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Busca tu película o serie favorita",
                style = UbuntuTypography.titleMedium,
                color = TextSecondary
            )
            Text(
                "Te diremos dónde la echan en España",
                style = UbuntuTypography.bodySmall,
                color = TextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun LoadingState() {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(5) {
            ShimmerCard()
        }
    }
}

@Composable
fun ShimmerCard() {
    val shimmerColors = listOf(
        DarkSurface.copy(alpha = 0.6f),
        DarkSurface.copy(alpha = 0.2f),
        DarkSurface.copy(alpha = 0.6f)
    )
    val transition = androidx.compose.animation.core.rememberInfiniteTransition()
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)
        )
    )
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnim.value - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim.value, 0f)
    )

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = DarkSurface)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(width = 100.dp, height = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun EmptyState(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.SearchOff, null,
                modifier = Modifier.size(64.dp),
                tint = TextSecondary.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sin resultados para \"$query\"",
                style = UbuntuTypography.titleSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ErrorOutline, null,
                Modifier.size(48.dp),
                tint = CinemaRed.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = UbuntuTypography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}
