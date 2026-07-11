package com.dondeloexan.presentation.availability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurfaceVariant
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography

private data class TypeOption(
    val type: AvailabilityType,
    val label: String,
    val description: String,
    val color: Color
)

private val availabilityOptions = listOf(
    TypeOption(AvailabilityType.SUBSCRIPTION, "Suscripción", "Incluido en tu plan", Color(0xFF4CAF50)),
    TypeOption(AvailabilityType.RENT, "Alquiler", "Pago por vision", Color(0xFFFFC107)),
    TypeOption(AvailabilityType.BUY, "Compra", "Compra permanente", Color(0xFFFF8F00)),
    TypeOption(AvailabilityType.FREE, "Gratis", "Contenido gratuito", Color(0xFF2196F3)),
    TypeOption(AvailabilityType.ADS, "Con anuncios", "Gratis con publicidad", Color(0xFF9C27B0))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel = remember { AvailabilityViewModel(context) }
    val selectedTypes by viewModel.selectedTypes.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Disponibilidad", color = TextPrimary) },
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
                "Elige como prefieres consumir contenido",
                style = UbuntuTypography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            HorizontalDivider(color = DarkSurfaceVariant)

            LazyColumn {
                items(availabilityOptions) { option ->
                    TypeToggle(
                        option = option,
                        isActive = option.type.name in selectedTypes,
                        onToggle = { viewModel.toggle(option.type.name) }
                    )
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun TypeToggle(option: TypeOption, isActive: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = option.color.copy(alpha = if (isActive) 0.25f else 0.08f)
        ) {
            Text(
                text = option.label.take(1),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = UbuntuTypography.labelLarge,
                color = option.color.copy(alpha = if (isActive) 1f else 0.4f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(option.label, style = UbuntuTypography.bodyLarge, color = TextPrimary)
            Text(
                option.description,
                style = UbuntuTypography.labelSmall,
                color = TextSecondary
            )
        }

        Switch(
            checked = isActive,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = option.color,
                checkedTrackColor = option.color.copy(alpha = 0.3f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}
