package com.dondeloexan.data.library

import com.dondeloexan.data.sync.SyncManager
import com.dondeloexan.domain.repository.MovieRepository
import com.dondeloexan.domain.repository.SeriesRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Orquestador único del refresco de biblioteca: trae datos de las APIs a la BD
 * local, reconcilia el estado de las series, refresca las plataformas de las
 * películas y sube el catálogo a la nube. Se usa desde el arranque de la app,
 * el botón de Ajustes y el worker, para que TODOS los caminos refresquen los
 * listados (series y películas) de la misma forma.
 */
class LibraryRefreshCoordinator(
    private val libraryRefresher: LibraryRefresher,
    private val seriesRepository: SeriesRepository,
    private val movieRepository: MovieRepository,
    private val syncManager: SyncManager
) {

    suspend fun refreshAll(): LibraryRefresher.RefreshResult {
        val result = runStep("libraryRefresher.refresh") { libraryRefresher.refresh() }
            ?: LibraryRefresher.RefreshResult(0, 0)
        runStep("seriesRepository.refreshData") { seriesRepository.refreshData() }
        runStep("movieRepository.refreshPlatforms") { movieRepository.refreshPlatforms() }
        runStep("syncManager.syncCatalog") { syncManager.syncCatalog() }
        return result
    }

    private suspend fun <T> runStep(name: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("LibraryRefresh", "$name falló", e)
            null
        }
}
