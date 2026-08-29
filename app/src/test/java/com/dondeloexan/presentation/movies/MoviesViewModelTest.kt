package com.dondeloexan.presentation.movies

import app.cash.turbine.test
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.RefreshCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {

    private val movieDao: MovieDao = mockk()
    private val tmdbApi: TmdbApi = mockk()
    private val refreshCoordinator: RefreshCoordinator = mockk()
    private val feedbackManager: FeedbackManager = mockk()

    private lateinit var viewModel: MoviesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { movieDao.getPendingFlow() } returns flowOf(emptyList())
        coEvery { movieDao.getWatchedMoviesFlow() } returns flowOf(emptyList())
        coEvery { movieDao.getFavoritesFlow() } returns flowOf(emptyList())
        viewModel = MoviesViewModel(movieDao, tmdbApi, refreshCoordinator, feedbackManager)
    }

    @Test
    fun `toggleLike when movie not liked only sets liked true and keeps pending status`() = runTest {
        val movie = MovieEntity(
            id = 1,
            title = "Test Movie",
            liked = false,
            status = WatchStatus.POR_VER
        )
        val slot = slot<MovieEntity>()
        coEvery { movieDao.update(capture(slot)) } returns Unit
        coEvery { feedbackManager.emit(any()) } returns Unit

        viewModel.toggleLike(movie)
        advanceUntilIdle()

        coVerify { movieDao.update(any()) }
        val updated = slot.captured
        assert(updated.liked)
        assert(updated.status == WatchStatus.POR_VER)
        assert(updated.watchedAt == null)
    }

    @Test
    fun `toggleLike when movie liked sets liked false and keeps watched status`() = runTest {
        val movie = MovieEntity(
            id = 2,
            title = "Test Movie",
            liked = true,
            status = WatchStatus.YA_VISTA,
            watchedAt = 1000L
        )
        val slot = slot<MovieEntity>()
        coEvery { movieDao.update(capture(slot)) } returns Unit
        coEvery { feedbackManager.emit(any()) } returns Unit

        viewModel.toggleLike(movie)
        advanceUntilIdle()

        coVerify { movieDao.update(any()) }
        val updated = slot.captured
        assert(!updated.liked)
        assert(updated.status == WatchStatus.YA_VISTA)
        assert(updated.watchedAt == 1000L)
    }

    @Test
    fun `toggleWatched keeps liked flag untouched`() = runTest {
        val movie = MovieEntity(
            id = 3,
            title = "Test Movie",
            liked = true,
            status = WatchStatus.POR_VER
        )
        val slot = slot<MovieEntity>()
        coEvery { movieDao.update(capture(slot)) } returns Unit
        coEvery { feedbackManager.emit(any()) } returns Unit

        viewModel.toggleWatched(movie)
        advanceUntilIdle()

        val updated = slot.captured
        assert(updated.status == WatchStatus.YA_VISTA)
        assert(updated.liked)
        assert(updated.watchedAt != null)
    }

    @Test
    fun `toggleWatched back to pending keeps liked flag untouched`() = runTest {
        val movie = MovieEntity(
            id = 4,
            title = "Test Movie",
            liked = true,
            status = WatchStatus.YA_VISTA,
            watchedAt = 5000L
        )
        val slot = slot<MovieEntity>()
        coEvery { movieDao.update(capture(slot)) } returns Unit
        coEvery { feedbackManager.emit(any()) } returns Unit

        viewModel.toggleWatched(movie)
        advanceUntilIdle()

        val updated = slot.captured
        assert(updated.status == WatchStatus.POR_VER)
        assert(updated.liked)
        assert(updated.watchedAt == null)
    }
}
