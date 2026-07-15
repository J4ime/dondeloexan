package com.dondeloexan.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.BuildConfig
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.remote.api.TmdbApi
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

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val silentUpdateManager: SilentUpdateManager,
    private val tvShowDao: TvShowDao,
    private val tmdbApi: TmdbApi
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _seriesRefreshState = MutableStateFlow<SeriesRefreshState>(SeriesRefreshState.Idle)
    val seriesRefreshState: StateFlow<SeriesRefreshState> = _seriesRefreshState.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

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

    fun refreshSeries() {
        if (_seriesRefreshState.value is SeriesRefreshState.Refreshing) return
        _seriesRefreshState.value = SeriesRefreshState.Refreshing
        viewModelScope.launch {
            try {
                val liked = tvShowDao.getAllLiked()
                var count = 0
                for (show in liked) {
                    val tmdbId = show.tmdbId ?: continue
                    try {
                        val tvDetail = tmdbApi.getTvDetailLight(tmdbId)
                        val lastEp = tvDetail.lastEpisodeToAir
                        val seasons = tvDetail.seasons
                        val releasedEpisodes = if (lastEp != null && seasons != null) {
                            seasons.filter { it.seasonNumber > 0 }
                                .sumOf { season ->
                                    when {
                                        season.seasonNumber < lastEp.seasonNumber -> season.episodeCount
                                        season.seasonNumber == lastEp.seasonNumber -> lastEp.episodeNumber
                                        else -> 0
                                    }
                                }
                        } else tvDetail.numberOfEpisodes
                        tvShowDao.updateById(
                            id = show.id,
                            totalEpisodes = tvDetail.numberOfEpisodes,
                            releasedEpisodes = releasedEpisodes,
                            nextEpisodeAirDate = tvDetail.nextEpisodeToAir?.airDate,
                            nextEpisodeNumber = tvDetail.nextEpisodeToAir?.episodeNumber,
                            nextEpisodeSeasonNumber = tvDetail.nextEpisodeToAir?.seasonNumber,
                            seriesStatus = tvDetail.status,
                            inProduction = tvDetail.inProduction
                        )
                        count++
                    } catch (e: Exception) {
                        AppLogger.e("SettingsVM", "Refresh series ${show.id}", e)
                    }
                }
                _seriesRefreshState.value = SeriesRefreshState.Done(count)
            } catch (e: Exception) {
                AppLogger.e("SettingsVM", "Refresh series error", e)
                _seriesRefreshState.value = SeriesRefreshState.Error(
                    e.message ?: "Error al actualizar series"
                )
            }
        }
    }

    fun onSeriesRefreshMessageShown() {
        _seriesRefreshState.value = SeriesRefreshState.Idle
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

sealed interface SeriesRefreshState {
    data object Idle : SeriesRefreshState
    data object Refreshing : SeriesRefreshState
    data class Done(val count: Int) : SeriesRefreshState
    data class Error(val message: String) : SeriesRefreshState
}
