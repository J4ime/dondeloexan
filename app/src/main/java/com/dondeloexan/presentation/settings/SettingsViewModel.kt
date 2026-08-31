package com.dondeloexan.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.BuildConfig
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.sync.SessionState
import com.dondeloexan.data.sync.SyncSummary
import com.dondeloexan.data.update.SilentUpdateManager
import com.dondeloexan.domain.model.BackupState
import com.dondeloexan.domain.model.GitHubRelease
import com.dondeloexan.domain.repository.AccountRepository
import com.dondeloexan.domain.repository.BackupRepository
import com.dondeloexan.domain.repository.SettingsRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val silentUpdateManager: SilentUpdateManager,
    private val libraryRefresher: LibraryRefresher,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val accountRepository: AccountRepository,
    private val tmdbApi: TmdbApi
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _libraryRefreshState = MutableStateFlow<LibraryRefreshState>(LibraryRefreshState.Idle)
    val libraryRefreshState: StateFlow<LibraryRefreshState> = _libraryRefreshState.asStateFlow()

    private val _connectionTest = MutableStateFlow<String?>(null)
    val connectionTest: StateFlow<String?> = _connectionTest.asStateFlow()

    private val _lastLibraryUpdateDate = MutableStateFlow<String?>(null)
    val lastLibraryUpdateDate: StateFlow<String?> = _lastLibraryUpdateDate.asStateFlow()

    private val _session = MutableStateFlow<SessionState?>(null)
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    private val _accountAction = MutableStateFlow<AccountActionState>(AccountActionState.Idle)
    val accountAction: StateFlow<AccountActionState> = _accountAction.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    init {
        viewModelScope.launch {
            userPreferencesDataStore.lastLibraryUpdateTimestamp.collect { timestamp ->
                _lastLibraryUpdateDate.value = timestamp?.let { formatDateTime(it) }
            }
        }
        viewModelScope.launch {
            accountRepository.session.collect { _session.value = it }
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateCheckState.Checking) return

        _updateState.value = UpdateCheckState.Checking

        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    settingsRepository.checkForUpdate().getOrThrow()
                }
                _updateState.value = if (release != null) {
                    UpdateCheckState.UpdateAvailable(release)
                } else {
                    UpdateCheckState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Comprobación de actualización falló", e)
                _updateState.value = UpdateCheckState.Error(
                    e.message ?: "Error al comprobar actualización"
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
            try {
                withContext(Dispatchers.IO) {
                    silentUpdateManager.downloadAndInstall(downloadUrl).getOrThrow()
                }
                _updateState.value = UpdateCheckState.InstallLaunched
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Update failed", e)
                _updateState.value = UpdateCheckState.Error(
                    "Error al descargar: ${e.message}"
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
            try {
                withContext(Dispatchers.IO) {
                    backupRepository.exportBackup(uri).getOrThrow()
                }
                _backupState.value = BackupState.ExportSuccess(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Exportar falló", e)
                _backupState.value = BackupState.Error(e.message ?: "Error al exportar")
            }
        }
    }

    fun performImport(uri: Uri) {
        _backupState.value = BackupState.Importing
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    backupRepository.importBackup(uri).getOrThrow()
                }
                _backupState.value = BackupState.ImportSuccess(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Importar falló", e)
                _backupState.value = BackupState.Error(e.message ?: "Error al importar")
            }
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
                val result = withContext(Dispatchers.IO) { libraryRefresher.refresh() }
                val total = result.seriesUpdated + result.moviesUpdated
                _libraryRefreshState.value = LibraryRefreshState.Done(total)
            } catch (e: CancellationException) {
                throw e
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

    fun testTmdbConnection() {
        viewModelScope.launch {
            _connectionTest.value = "Comprobando..."
            AppLogger.i("SettingsVM", "Test conexión TMDB: iniciando (pasos: API TMDB + CDN imagen)")
            try {
                AppLogger.i("SettingsVM", "Test conexión: paso 1/2 llama trending/all/week")
                val trending = withContext(Dispatchers.IO) {
                    try {
                        withTimeout(12_000) { tmdbApi.getTrending() }
                    } catch (e: TimeoutCancellationException) {
                        throw SocketTimeoutException("TMDB API no respondió en 12s")
                    }
                }
                val sample = trending.results
                    .filter { it.mediaType in listOf("movie", "tv") }
                    .take(3)
                    .joinToString(", ") { it.title ?: it.name ?: "?" }
                val total = trending.results.count { it.mediaType in listOf("movie", "tv") }
                AppLogger.i("SettingsVM", "Test conexión: paso 1/2 OK ($total resultados; muestra: $sample)")

                AppLogger.i("SettingsVM", "Test conexión: paso 2/2 pingImage CDN")
                val bytes = withContext(Dispatchers.IO) { tmdbApi.pingImage() }
                AppLogger.i("SettingsVM", "Test conexión: paso 2/2 OK ($bytes bytes)")

                val responseText = if (bytes > 0) {
                    "TMDB OK · $total resultados (${sample}) · CDN $bytes bytes"
                } else {
                    "TMDB OK · $total resultados (${sample}) · CDN sin tamaño declarado"
                }
                AppLogger.i("SettingsVM", "Test conexión TMDB: fin OK -> $responseText")
                _connectionTest.value = responseText
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Test conexión TMDB falló", e)
                _connectionTest.value = "Error TMDB: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun login(email: String, password: String) {
        if (_accountAction.value is AccountActionState.Busy) return
        _accountAction.value = AccountActionState.Busy
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    accountRepository.login(email, password).getOrThrow()
                }
                _accountAction.value = AccountActionState.Success(
                    "Sesión iniciada · ${summary.total} datos sincronizados"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Login fallido (${email})", e)
                _accountAction.value = AccountActionState.Error(
                    e.message ?: "Error al iniciar sesión"
                )
            }
        }
    }

    fun logout() {
        if (_accountAction.value is AccountActionState.Busy) return
        _accountAction.value = AccountActionState.Busy
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { accountRepository.logout().getOrThrow() }
                _accountAction.value = AccountActionState.Success("Sesión cerrada")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Logout fallido", e)
                _accountAction.value = AccountActionState.Error(
                    e.message ?: "Error al cerrar la sesión"
                )
            }
        }
    }

    fun syncAccount() {
        if (_accountAction.value is AccountActionState.Busy) return
        _accountAction.value = AccountActionState.Busy
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    accountRepository.sync().getOrThrow()
                }
                _accountAction.value = AccountActionState.Success(summaryMessage(summary))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Sync fallido", e)
                _accountAction.value = AccountActionState.Error(
                    e.message ?: "Error al sincronizar"
                )
            }
        }
    }

    fun onAccountActionShown() {
        _accountAction.value = AccountActionState.Idle
    }

    private fun summaryMessage(summary: SyncSummary): String {
        val parts = buildList {
            if (summary.movies > 0) add("${summary.movies} películas")
            if (summary.tvShows > 0) add("${summary.tvShows} series")
            if (summary.tvShowProgress > 0) add("${summary.tvShowProgress} capítulos")
            if (summary.searchHistory > 0) add("${summary.searchHistory} búsquedas")
            if (summary.userPlatforms > 0) add("${summary.userPlatforms} plataformas")
        }
        return if (parts.isEmpty()) {
            "Sincronizado (nada que subir)"
        } else {
            "Sincronizado: ${parts.joinToString(", ")}"
        }
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

sealed interface AccountActionState {
    data object Idle : AccountActionState
    data object Busy : AccountActionState
    data class Success(val message: String) : AccountActionState
    data class Error(val message: String) : AccountActionState
}
