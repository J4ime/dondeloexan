package com.dondeloexan.presentation.series

import com.dondeloexan.domain.model.SeriesItem
import com.dondeloexan.domain.repository.SeriesRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    private val repository: SeriesRepository = mockk()
    private val feedbackManager: FeedbackManager = mockk()
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SeriesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        coEvery { feedbackManager.emit(any()) } returns Unit
    }

    private fun stubSeries(series: List<SeriesItem>) {
        every { repository.all } returns flowOf(series)
        viewModel = SeriesViewModel(repository, feedbackManager)
    }

    private suspend fun TestScope.stateValue(flow: StateFlow<List<SeriesItem>>): List<SeriesItem> {
        val job = launch { flow.collect { } }
        advanceUntilIdle()
        mainDispatcher.scheduler.advanceUntilIdle()
        advanceUntilIdle()
        job.cancel()
        return flow.value
    }

    @Test
    fun `serie al dia sin liked aparece en agenda de estrenos`() = runTest {
        val item = SeriesItem(
            id = 1,
            title = "Stuart no consigue salvar el universo",
            isLiked = false,
            isWatched = true,
            totalEpisodes = 10,
            releasedEpisodes = 10,
            watchedCount = 10,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(item))

        val agenda = stateValue(viewModel.upcomingAgenda)
        assert(agenda.size == 1)
        assert(agenda[0].title == item.title)
    }

    @Test
    fun `serie al dia sin liked no aparece en pendientes`() = runTest {
        val item = SeriesItem(
            id = 1,
            title = "Stuart no consigue salvar el universo",
            isLiked = false,
            isWatched = true,
            releasedEpisodes = 10,
            watchedCount = 10,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(item))

        val pending = stateValue(viewModel.pending)
        assert(pending.isEmpty())
    }

    @Test
    fun `serie terminada sin liked aparece en terminadas`() = runTest {
        val item = SeriesItem(
            id = 2,
            title = "Serie Terminada",
            isLiked = false,
            isWatched = true,
            releasedEpisodes = 8,
            totalEpisodes = 8,
            watchedCount = 8,
            seriesStatus = "Ended",
            inProduction = false
        )
        stubSeries(listOf(item))

        val finished = stateValue(viewModel.finished)
        assert(finished.size == 1)
        assert(finished[0].title == item.title)
    }

    @Test
    fun `serie sin liked y sin episodios vistos aparece en pendientes`() = runTest {
        val item = SeriesItem(
            id = 3,
            title = "Serie Pendiente",
            isLiked = false,
            isWatched = false,
            releasedEpisodes = 0,
            totalEpisodes = 10,
            watchedCount = 0,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(item))

        val pending = stateValue(viewModel.pending)
        assert(pending.size == 1)
        assert(pending[0].title == item.title)
    }

    @Test
    fun `serie en curso sin liked pero no al dia aparece en en curso`() = runTest {
        val item = SeriesItem(
            id = 4,
            title = "Serie En Curso",
            isLiked = false,
            isWatched = false,
            releasedEpisodes = 10,
            totalEpisodes = 24,
            watchedCount = 3,
            seriesStatus = "Returning Series",
            inProduction = true
        )
        stubSeries(listOf(item))

        val inProgress = stateValue(viewModel.inProgress)
        assert(inProgress.size == 1)
        assert(inProgress[0].title == item.title)
    }

    @Test
    fun `deleteSeries borra la serie y emite feedback`() = runTest {
        coEvery { repository.delete(5) } returns Unit
        stubSeries(emptyList())

        viewModel.deleteSeries(SeriesItem(id = 5, title = "Serie a eliminar"))
        advanceUntilIdle()

        coVerify { repository.delete(5) }
        verify { feedbackManager.emit("Serie eliminada") }
    }

    @Test
    fun `marcar como vista emite feedback`() = runTest {
        coEvery { repository.toggleWatched(100) } returns true
        stubSeries(emptyList())

        viewModel.toggleWatched(SeriesItem(id = 100, title = "Serie sin detalle TMDB"))
        advanceUntilIdle()

        coVerify { repository.toggleWatched(100) }
        verify { feedbackManager.emit("Serie marcada como vista") }
    }

    @Test
    fun `en curso se ordena por el ultimo capitulo realmente visto`() = runTest {
        val antigua = SeriesItem(
            id = 21, title = "Vista hace tiempo", isWatched = false,
            releasedEpisodes = 10, totalEpisodes = 24, watchedCount = 3,
            seriesStatus = "Returning Series", inProduction = true, lastWatchedAt = 1000L
        )
        val reciente = SeriesItem(
            id = 22, title = "Vista recientemente", isWatched = false,
            releasedEpisodes = 10, totalEpisodes = 24, watchedCount = 3,
            seriesStatus = "Returning Series", inProduction = true, lastWatchedAt = 5000L
        )
        stubSeries(listOf(antigua, reciente))

        val inProgress = stateValue(viewModel.inProgress)

        assert(inProgress.size == 2)
        assert(inProgress[0].id == 22L)
        assert(inProgress[1].id == 21L)
    }
}
