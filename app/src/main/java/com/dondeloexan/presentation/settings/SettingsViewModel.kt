package com.dondeloexan.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.BuildConfig
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.update.SilentUpdateManager
import com.dondeloexan.domain.model.BackupState
import com.dondeloexan.domain.model.GitHubRelease
import com.dondeloexan.domain.repository.BackupRepository
import com.dondeloexan.domain.repository.SettingsRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val silentUpdateManager: SilentUpdateManager,
    private val libraryRefresher: LibraryRefresher,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _libraryRefreshState = MutableStateFlow<LibraryRefreshState>(LibraryRefreshState.Idle)
    val libraryRefreshState: StateFlow<LibraryRefreshState> = _libraryRefreshState.asStateFlow()

    private val _lastLibraryUpdateDate = MutableStateFlow<String?>(null)
    val lastLibraryUpdateDate: StateFlow<String?> = _lastLibraryUpdateDate.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    init {
        viewModelScope.launch {
            userPreferencesDataStore.lastLibraryUpdateTimestamp.collect { timestamp ->
                _lastLibraryUpdateDate.value = timestamp?.let { formatDateTime(it) }
            }
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateCheckState.Checking) return

        _updateState.value = UpdateCheckState.Checking

        viewModelScope.launch {
            settingsRepository.checkForUpdate()
                .onSuccess { release ->
                    _updateState.value = if (release != null) {
                        UpdateCheckState.UpdateAvailable(release)
                    } else {
                        UpdateCheckState.UpToDate
                    }
                }
                .onFailure { error ->
                    _updateState.value = UpdateCheckState.Error(
                        error.message ?: "Error al comprobar actualización"
                    )
                }
        }
    }

    fun onUpdateDialogDismissed() {
        _updateState.value = UpdateCheckState.Idle
    }

    fun startSilentUpdate(downloadUrl: String) {
        if (!silentUpdateManager.canInstallApks()) {
            _updateState.value = UpdateCheckState.NeedsInstallPermission(downloadUrl)
            return
        }

        _updateState.value = UpdateCheckState.Downloading
        viewModelScope.launch {
            silentUpdateManager.downloadAndInstall(downloadUrl)
                .onSuccess {
                    _updateState.value = UpdateCheckState.InstallLaunched
                }
                .onFailure { error ->
                    AppLogger.e("SettingsVM", "Update failed", error)
                    _updateState.value = UpdateCheckState.Error(
                        "Error al descargar: ${error.message}"
                    )
                }
        }
    }

    fun requestInstallPermission() {
        silentUpdateManager.openInstallPermissionSettings()
    }

    fun hasInstallPermission(): Boolean = silentUpdateManager.canInstallApks()

    fun onUpToDateMessageShown() {
        _updateState.value = UpdateCheckState.Idle
    }

    fun onErrorMessageShown() {
        _updateState.value = UpdateCheckState.Idle
    }

    fun onInstallLaunchedMessageShown() {
        _updateState.value = UpdateCheckState.Idle
    }

    fun performExport(uri: Uri) {
        _backupState.value = BackupState.Exporting
        viewModelScope.launch {
            backupRepository.exportBackup(uri)
                .onSuccess { _backupState.value = BackupState.ExportSuccess(0) }
                .onFailure { _backupState.value = BackupState.Error(it.message ?: "Error al exportar") }
        }
    }

    fun performImport(uri: Uri) {
        _backupState.value = BackupState.Importing
        viewModelScope.launch {
            backupRepository.importBackup(uri)
                .onSuccess { count -> _backupState.value = BackupState.ImportSuccess(count) }
                .onFailure { _backupState.value = BackupState.Error(it.message ?: "Error al importar") }
        }
    }

    fun onBackupMessageShown() {
        _backupState.value = BackupState.Idle
    }

    fun refreshLibrary() {
        if (_libraryRefreshState.value is LibraryRefreshState.Refreshing) return
        _libraryRefreshState.value = LibraryRefreshState.Refreshing
        viewModelScope.launch {
            try {
                val result = libraryRefresher.refresh()
                val total = result.seriesUpdated + result.moviesUpdated
                _libraryRefreshState.value = LibraryRefreshState.Done(total)
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Library refresh error", e)
                _libraryRefreshState.value = LibraryRefreshState.Error(
                    e.message ?: "Error al actualizar biblioteca"
                )
            }
        }
    }

    fun onLibraryRefreshMessageShown() {
        _libraryRefreshState.value = LibraryRefreshState.Idle
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class UpdateAvailable(val release: GitHubRelease) : UpdateCheckState
    data object Downloading : UpdateCheckState
    data class NeedsInstallPermission(val downloadUrl: String) : UpdateCheckState
    data object InstallLaunched : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

sealed interface LibraryRefreshState {
    data object Idle : LibraryRefreshState
    data object Refreshing : LibraryRefreshState
    data class Done(val count: Int) : LibraryRefreshState
    data class Error(val message: String) : LibraryRefreshState
}
