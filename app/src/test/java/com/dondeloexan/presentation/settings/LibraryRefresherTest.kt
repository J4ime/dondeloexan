package com.dondeloexan.presentation.settings

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbEpisodeDto
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.library.LibraryNotificationManager
import com.dondeloexan.data.library.LibraryRefresher
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.util.RefreshCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LibraryRefresherTest {

    private val tvShowDao: TvShowDao = mockk()
    private val movieDao: MovieDao = mockk()
    private val tmdbApi: TmdbApi = mockk()
    private val omdbApi: OmdbApi = mockk()
    private val userPreferencesDataStore: UserPreferencesDataStore = mockk()
    private val notificationManager: LibraryNotificationManager = mockk()
    private val refreshCoordinator = RefreshCoordinator()

    private fun refresher() = LibraryRefresher(
        tvShowDao = tvShowDao,
        movieDao = movieDao,
        tmdbApi = tmdbApi,
        omdbApi = omdbApi,
        refreshCoordinator = refreshCoordinator,
        userPreferencesDataStore = userPreferencesDataStore,
        notificationManager = notificationManager
    )

    @Test
    fun `serie no liked recibe releasedEpisodes al refrescar`() = runTest {
        val show = TvShowEntity(
            id = 1,
            title = "Serie sin corazon",
            liked = false,
            status = WatchStatus.POR_VER,
            tmdbId = 100,
            totalEpisodes = 24,
            releasedEpisodes = null,
            streamingPlatforms = "Netflix",
            seriesStatus = "Returning Series",
            inProduction = true
        )

        coEvery { tvShowDao.getAll() } returns listOf(show)
        coEvery { tvShowDao.getById(1) } returns show
        coEvery { tvShowDao.update(any()) } returns Unit
        coEvery { movieDao.getAll() } returns emptyList()
        coEvery { userPreferencesDataStore.setLastLibraryUpdateTimestamp(any()) } returns Unit
        every { notificationManager.notifyChanges(any(), any()) } returns Unit
        coEvery { tmdbApi.getTvDetailLight(100) } returns TmdbTvDetailDto(
            id = 100,
            name = "Serie sin corazon",
            numberOfEpisodes = 24,
            numberOfSeasons = 3,
            status = "Returning Series",
            inProduction = true,
            nextEpisodeToAir = TmdbEpisodeDto(
                airDate = "2026-09-01", episodeNumber = 5, id = 2, name = "E5", seasonNumber = 3
            ),
            lastEpisodeToAir = TmdbEpisodeDto(
                airDate = "2026-08-20", episodeNumber = 4, id = 1, name = "E4", seasonNumber = 3
            ),
            seasons = listOf(
                TmdbSeasonDto(episodeCount = 10, id = 1, name = "T1", seasonNumber = 1),
                TmdbSeasonDto(episodeCount = 10, id = 2, name = "T2", seasonNumber = 2),
                TmdbSeasonDto(episodeCount = 10, id = 3, name = "T3", seasonNumber = 3)
            )
        )

        refresher().refresh()

        coVerify { tvShowDao.update(match { it.releasedEpisodes == 24 }) }
    }

    @Test
    fun `serie liked con nueva fecha de estreno emite notificacion`() = runTest {
        val show = TvShowEntity(
            id = 2,
            title = "Serie con corazon",
            liked = true,
            status = WatchStatus.POR_VER,
            tmdbId = 200,
            totalEpisodes = 10,
            releasedEpisodes = null,
            streamingPlatforms = "Disney+",
            seriesStatus = "Returning Series",
            inProduction = true
        )

        coEvery { tvShowDao.getAll() } returns listOf(show)
        coEvery { tvShowDao.getById(2) } returns show
        coEvery { tvShowDao.update(any()) } returns Unit
        coEvery { movieDao.getAll() } returns emptyList()
        coEvery { userPreferencesDataStore.setLastLibraryUpdateTimestamp(any()) } returns Unit
        every { notificationManager.notifyChanges(any(), any()) } returns Unit
        coEvery { tmdbApi.getTvDetailLight(200) } returns TmdbTvDetailDto(
            id = 200,
            name = "Serie con corazon",
            numberOfEpisodes = 10,
            status = "Returning Series",
            inProduction = true,
            nextEpisodeToAir = TmdbEpisodeDto(
                airDate = "2026-09-10", episodeNumber = 6, id = 2, name = "E6", seasonNumber = 1
            )
        )

        refresher().refresh()

        verify { notificationManager.notifyChanges(any(), any()) }
    }

    @Test
    fun `serie no liked no emite notificacion de nueva fecha`() = runTest {
        val show = TvShowEntity(
            id = 3,
            title = "Serie sin corazon con fecha",
            liked = false,
            status = WatchStatus.POR_VER,
            tmdbId = 300,
            totalEpisodes = 10,
            releasedEpisodes = null,
            streamingPlatforms = "Movistar+",
            seriesStatus = "Returning Series",
            inProduction = true
        )

        coEvery { tvShowDao.getAll() } returns listOf(show)
        coEvery { tvShowDao.getById(3) } returns show
        coEvery { tvShowDao.update(any()) } returns Unit
        coEvery { movieDao.getAll() } returns emptyList()
        coEvery { userPreferencesDataStore.setLastLibraryUpdateTimestamp(any()) } returns Unit
        every { notificationManager.notifyChanges(any(), any()) } returns Unit
        coEvery { tmdbApi.getTvDetailLight(300) } returns TmdbTvDetailDto(
            id = 300,
            name = "Serie sin corazon con fecha",
            numberOfEpisodes = 10,
            status = "Returning Series",
            inProduction = true,
            nextEpisodeToAir = TmdbEpisodeDto(
                airDate = "2026-09-10", episodeNumber = 7, id = 3, name = "E7", seasonNumber = 1
            )
        )

        refresher().refresh()

        verify(exactly = 0) { notificationManager.notifyChanges(any(), any()) }
    }
}