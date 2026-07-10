package com.dondeloexan.presentation.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.UbuntuTypography

@Composable
fun SettingsGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = UbuntuTypography.labelMedium,
        color = EleganteRose.copy(alpha = 0.7f),
        modifier = modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
        letterSpacing = 1.sp
    )
}
