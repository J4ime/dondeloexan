package com.dondeloexan.presentation.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dondeloexan.domain.model.GitHubRelease
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import com.dondeloexan.presentation.theme.UbuntuTypography

@Composable
fun UpdateAvailableDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = DarkSurface,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate, contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = EleganteRose
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nueva actualización disponible",
                    style = UbuntuTypography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    "v${release.version.displayName}",
                    style = UbuntuTypography.headlineSmall,
                    color = EleganteRose,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (release.changelog.isNotBlank()) {
                Column {
                    Text(
                        "Novedades:",
                        style = UbuntuTypography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = release.changelog,
                        style = UbuntuTypography.bodySmall,
                        color = TextSecondary,
                        maxLines = 10,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDownload(release.apkDownloadUrl ?: release.htmlUrl)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EleganteRose)
            ) {
                Text("Descargar APK", color = DarkBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
