package com.dondeloexan.presentation.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextSecondary

@Composable
fun BottomNavigationBar(
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.height(104.dp),
        containerColor = DarkSurface,
        tonalElevation = 0.dp
    ) {
        Route.bottomNavItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = currentPage == index,
                onClick = { onPageChange(index) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(36.dp)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EleganteRose,
                    unselectedIconColor = TextSecondary,
                    indicatorColor = EleganteRose.copy(alpha = 0.12f)
                )
            )
        }
    }
}
