package com.dondeloexan.data.library

import com.dondeloexan.data.sync.SyncManager
import com.dondeloexan.domain.repository.MovieRepository
import com.dondeloexan.domain.repository.SeriesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LibraryRefreshCoordinatorTest {

    private val libraryRefresher: LibraryRefresher = mockk()
    private val seriesRepository: SeriesRepository = mockk()
    private val movieRepository: MovieRepository = mockk()
    private val syncManager: SyncManager = mockk()

    private val coordinator = LibraryRefreshCoordinator(
        libraryRefresher = libraryRefresher,
        seriesRepository = seriesRepository,
        movieRepository = movieRepository,
        syncManager = syncManager
    )

    @Test
    fun `refreshAll refresca biblioteca, series, peliculas y sube el catalogo`() = runTest {
        coEvery { libraryRefresher.refresh() } returns LibraryRefresher.RefreshResult(3, 2)
        coEvery { seriesRepository.refreshData() } returns Unit
        coEvery { movieRepository.refreshPlatforms() } returns Unit
        coEvery { syncManager.syncCatalog() } returns Unit

        val result = coordinator.refreshAll()

        assert(result.seriesUpdated == 3 && result.moviesUpdated == 2)
        coVerify(exactly = 1) { libraryRefresher.refresh() }
        coVerify(exactly = 1) { seriesRepository.refreshData() }
        coVerify(exactly = 1) { movieRepository.refreshPlatforms() }
        coVerify(exactly = 1) { syncManager.syncCatalog() }
    }

    @Test
    fun `refreshAll continua con el resto de pasos si uno falla`() = runTest {
        coEvery { libraryRefresher.refresh() } throws RuntimeException("red caída")
        coEvery { seriesRepository.refreshData() } returns Unit
        coEvery { movieRepository.refreshPlatforms() } returns Unit
        coEvery { syncManager.syncCatalog() } returns Unit

        val result = coordinator.refreshAll()

        assert(result.seriesUpdated == 0 && result.moviesUpdated == 0)
        coVerify(exactly = 1) { seriesRepository.refreshData() }
        coVerify(exactly = 1) { movieRepository.refreshPlatforms() }
        coVerify(exactly = 1) { syncManager.syncCatalog() }
    }
}
