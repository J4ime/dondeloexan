package com.dondeloexan.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dondeloexan.domain.model.BackupState
import com.dondeloexan.presentation.navigation.Route
import com.dondeloexan.presentation.settings.components.SettingsGroupHeader
import com.dondeloexan.presentation.settings.components.SettingsItem
import com.dondeloexan.presentation.settings.components.UpdateAvailableDialog
import com.dondeloexan.presentation.settings.components.UpdateCheckTrailing
import com.dondeloexan.presentation.theme.DarkBackground
import com.dondeloexan.presentation.theme.DarkSurface
import com.dondeloexan.presentation.theme.EleganteRose
import com.dondeloexan.presentation.theme.TextPrimary
import com.dondeloexan.presentation.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val updateState by viewModel.updateState.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.performExport(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.performImport(it) }
    }

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateCheckState.UpToDate -> {
                snackbarHostState.showSnackbar("Ya tienes la última versión instalada")
                viewModel.onUpToDateMessageShown()
            }
            is UpdateCheckState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.onErrorMessageShown()
            }
            is UpdateCheckState.InstallLaunched -> {
                snackbarHostState.showSnackbar("Descarga completada. Finaliza la instalación desde la pantalla del sistema.")
                viewModel.onInstallLaunchedMessageShown()
            }
            else -> {}
        }
    }

    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupState.ExportSuccess -> {
                snackbarHostState.showSnackbar("Copia de seguridad exportada correctamente")
                viewModel.onBackupMessageShown()
            }
            is BackupState.ImportSuccess -> {
                snackbarHostState.showSnackbar("${state.count} datos restaurados correctamente")
                viewModel.onBackupMessageShown()
            }
            is BackupState.Error -> {
                snackbarHostState.showSnackbar("Error: ${state.message}")
                viewModel.onBackupMessageShown()
            }
            else -> {}
        }
    }

    if (updateState is UpdateCheckState.UpdateAvailable) {
        val release = (updateState as UpdateCheckState.UpdateAvailable).release
        UpdateAvailableDialog(
            release = release,
            onDismiss = { viewModel.onUpdateDialogDismissed() },
            onDownload = { url -> viewModel.startSilentUpdate(url) }
        )
    }

    if (updateState is UpdateCheckState.NeedsInstallPermission) {
        val stateUrl = (updateState as UpdateCheckState.NeedsInstallPermission).downloadUrl
        val permissionGranted = viewModel.hasInstallPermission()
        AlertDialog(
            onDismissRequest = { viewModel.onUpdateDialogDismissed() },
            confirmButton = {
                TextButton(onClick = {
                    if (permissionGranted) {
                        viewModel.startSilentUpdate(stateUrl)
                    } else {
                        viewModel.requestInstallPermission()
                    }
                }) {
                    Text(
                        if (permissionGranted) "Reintentar" else "Abrir ajustes",
                        color = EleganteRose
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onUpdateDialogDismissed() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            title = {
                Text(
                    if (permissionGranted) "Instalación lista" else "Permiso necesario",
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    if (permissionGranted)
                        "El permiso ya está concedido. Pulsa 'Reintentar' para continuar con la instalación."
                    else
                        "Para instalar la actualización, Android requiere que habilites " +
                                "'Instalar apps desconocidas' para DondeLoExan. ¿Abrir ajustes?",
                    color = TextSecondary
                )
            },
            containerColor = DarkSurface
        )
    }

    if (updateState is UpdateCheckState.Downloading) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Descargando actualización", color = TextPrimary) },
            text = {
                Column {
                    Text("Descargando e instalando la nueva versión...", color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = EleganteRose
                    )
                }
            },
            containerColor = DarkSurface
        )
    }

    if (backupState is BackupState.Importing) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Restaurando datos", color = TextPrimary) },
            text = {
                Column {
                    Text("Importando copia de seguridad...", color = TextSecondary)
                    LinearProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp),
                        color = EleganteRose
                    )
                }
            },
            containerColor = DarkSurface
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Ajustes", color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                windowInsets = WindowInsets(top = 0)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item { SettingsGroupHeader("Plataformas") }
                item {
                    SettingsItem(
                        icon = Icons.Outlined.LiveTv,
                        title = "Mis Plataformas",
                        subtitle = "Gestiona tus servicios de streaming",
                        onClick = { navController.navigate(Route.SettingsPlatforms.route) }
                    )
                }

                item { SettingsGroupHeader("Datos") }
                item {
                    SettingsItem(
                        icon = Icons.Outlined.FileUpload,
                        title = "Exportar copia de seguridad",
                        subtitle = "Guarda tus datos en un archivo",
                        onClick = { exportLauncher.launch("dondeloexan_backup.json") }
                    )
                }
                item {
                    SettingsItem(
                        icon = Icons.Outlined.FileDownload,
                        title = "Importar copia de seguridad",
                        subtitle = "Restaura datos desde un archivo",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }

                item { SettingsGroupHeader("Herramientas") }
                item {
                    SettingsItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Registro de errores",
                        subtitle = "Consulta los logs del sistema",
                        onClick = { navController.navigate(Route.SettingsLogs.route) }
                    )
                }

                item { SettingsGroupHeader("Actualización") }
                item {
                    SettingsItem(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "Buscar actualizaciones",
                        subtitle = "Versión actual: ${viewModel.currentVersion}",
                        trailing = {
                            UpdateCheckTrailing(state = updateState)
                        },
                        onClick = { viewModel.checkForUpdates() }
                    )
                }

                item { SettingsGroupHeader("Información") }
                item {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "Acerca de DondeLoExan",
                        subtitle = "v${viewModel.currentVersion} · Creado con ❤",
                        onClick = { navController.navigate(Route.SettingsAbout.route) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
