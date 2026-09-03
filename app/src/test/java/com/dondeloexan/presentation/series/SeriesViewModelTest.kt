package com.dondeloexan.presentation.series

import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.WatchedCount
import com.dondeloexan.data.local.dao.TvShowLastWatched
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.TmdbApiException
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.RefreshCoordinator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {

    private val tvShowDao: TvShowDao = mockk()
    private val tvShowProgressDao: TvShowProgressDao = mockk()
    private val tmdbApi: TmdbApi = mockk()
    private val refreshCoordinator: RefreshCoordinator = mockk()
    private val discoverRepository: DiscoverRepository = mockk()
    private val feedbackManager: FeedbackManager = mockk()
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SeriesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    private fun stubSeries(series: List<TvShowEntity>, counts: List<WatchedCount>, lastWatched: List<TvShowLastWatched> = emptyList()) {
        coEvery { tvShowDao.getAllFlow() } returns flowOf(series)
        coEvery { tvShowProgressDao.getWatchedCounts() } returns flowOf(counts)
        coEvery { tvShowProgressDao.getLastWatchedAtByShow() } returns flowOf(lastWatched)
        viewModel = SeriesViewModel(tvShowDao, tvShowProgressDao, tmdbApi, refreshCoordinator, discoverRepository, feedbackManager)
    }

    private suspend fun TestScope.stateValue(flow: StateFlow<List<SeriesWithProgress>>): List<SeriesWithProgress> {
        val job = launch { flow.collect { } }
        advanceUntilIdle()
        mainDispatcher.scheduler.advanceUntilIdle()
        advanceUntilIdle()
        job.cancel()
        return flow.value
    }

    @Test
    fun `serie al dia sin liked aparece en agenda de estrenos`() = runTest {
        val show = TvShowEntity(
            id = 1,
            title = "Stuart no consigue salvar el universo",
            liked = false,
            status = WatchStatus.YA_VISTA,
            totalEpisodes = 10,
            releasedEpisodes = 10,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(show), listOf(WatchedCount(1, 10)))

        val agenda = stateValue(viewModel.upcomingAgenda)
        assert(agenda.size == 1)
        assert(agenda[0].show.title == show.title)
    }

    @Test
    fun `serie al dia sin liked no aparece en pendientes`() = runTest {
        val show = TvShowEntity(
            id = 1,
            title = "Stuart no consigue salvar el universo",
            liked = false,
            status = WatchStatus.YA_VISTA,
            releasedEpisodes = 10,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(show), listOf(WatchedCount(1, 10)))

        val pending = stateValue(viewModel.pending)
        assert(pending.isEmpty())
    }

    @Test
    fun `serie terminada sin liked aparece en terminadas`() = runTest {
        val show = TvShowEntity(
            id = 2,
            title = "Serie Terminada",
            liked = false,
            status = WatchStatus.YA_VISTA,
            releasedEpisodes = 8,
            totalEpisodes = 8,
            seriesStatus = "Ended",
            inProduction = false
        )
        stubSeries(listOf(show), listOf(WatchedCount(2, 8)))

        val finished = stateValue(viewModel.finished)
        assert(finished.size == 1)
        assert(finished[0].show.title == show.title)
    }

    @Test
    fun `serie sin liked y sin episodios vistos aparece en pendientes`() = runTest {
        val show = TvShowEntity(
            id = 3,
            title = "Serie Pendiente",
            liked = false,
            status = WatchStatus.POR_VER,
            releasedEpisodes = 0,
            totalEpisodes = 10,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(show), listOf(WatchedCount(3, 0)))

        val pending = stateValue(viewModel.pending)
        assert(pending.size == 1)
        assert(pending[0].show.title == show.title)
    }

    @Test
    fun `serie en curso sin liked pero no al dia aparece en en curso`() = runTest {
        val show = TvShowEntity(
            id = 4,
            title = "Serie En Curso",
            liked = false,
            status = WatchStatus.POR_VER,
            releasedEpisodes = 10,
            totalEpisodes = 24,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(show), listOf(WatchedCount(4, 3)))

        val inProgress = stateValue(viewModel.inProgress)
        assert(inProgress.size == 1)
        assert(inProgress[0].show.title == show.title)
    }

    @Test
    fun `deleteSeries borra la serie y emite feedback`() = runTest {
        val show = TvShowEntity(
            id = 5,
            title = "Serie a eliminar",
            status = WatchStatus.POR_VER
        )
        coEvery { tvShowDao.delete(show) } returns Unit
        coEvery { feedbackManager.emit("Serie eliminada") } returns Unit
        stubSeries(emptyList(), emptyList())

        viewModel.deleteSeries(show)
        mainDispatcher.scheduler.advanceUntilIdle()
        advanceUntilIdle()

        coVerify { tvShowDao.delete(show) }
        verify { feedbackManager.emit("Serie eliminada") }
    }

    @Test
    fun `marcar como vista marca la serie aunque falle el detalle de TMDB`() = runTest {
        val show = TvShowEntity(
            id = 100,
            title = "Serie sin detalle TMDB",
            status = WatchStatus.POR_VER,
            tmdbId = 324182
        )
        coEvery { tmdbApi.getTvDetailLight(324182) } throws TmdbApiException("TMDB HTTP 404 en tv/324182")
        coEvery { tvShowDao.update(any()) } returns Unit
        coEvery { feedbackManager.emit("Serie marcada como vista") } returns Unit
        stubSeries(listOf(show), emptyList())

        viewModel.toggleWatched(show)
        mainDispatcher.scheduler.advanceUntilIdle()
        advanceUntilIdle()

        coVerify { tvShowDao.update(match { it.status == WatchStatus.YA_VISTA }) }
        verify { feedbackManager.emit("Serie marcada como vista") }
    }

    @Test
    fun `en curso se ordena por el ultimo capitulo realmente visto`() = runTest {
        val antigua = TvShowEntity(
            id = 21, title = "Vista hace tiempo", status = WatchStatus.POR_VER,
            releasedEpisodes = 10, totalEpisodes = 24, seriesStatus = "Returning Series", inProduction = true
        )
        val reciente = TvShowEntity(
            id = 22, title = "Vista recientemente", status = WatchStatus.POR_VER,
            releasedEpisodes = 10, totalEpisodes = 24, seriesStatus = "Returning Series", inProduction = true
        )
        stubSeries(
            listOf(antigua, reciente),
            listOf(WatchedCount(21, 3), WatchedCount(22, 3)),
            listOf(TvShowLastWatched(21, lastWatchedAt = 1000L), TvShowLastWatched(22, lastWatchedAt = 5000L))
        )

        val inProgress = stateValue(viewModel.inProgress)

        assert(inProgress.size == 2)
        assert(inProgress[0].show.id == 22L)
        assert(inProgress[1].show.id == 21L)
    }
}