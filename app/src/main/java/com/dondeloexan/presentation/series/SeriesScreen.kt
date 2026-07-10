package com.dondeloexan.presentation.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.dondeloexan.presentation.navigation.BottomNavigationBar
import com.dondeloexan.presentation.theme.TextSecondary

@Composable
fun SeriesScreen(navController: NavController) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Mis Series", color = TextSecondary)
        }
        BottomNavigationBar(navController, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
