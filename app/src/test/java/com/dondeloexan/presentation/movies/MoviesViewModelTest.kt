package com.dondeloexan.presentation.movies

import com.dondeloexan.domain.model.MovieItem
import com.dondeloexan.domain.repository.MovieRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {

    private val repository: MovieRepository = mockk()
    private val feedbackManager: FeedbackManager = mockk()

    private lateinit var viewModel: MoviesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { repository.pending } returns flowOf(emptyList())
        every { repository.watched } returns flowOf(emptyList())
        every { repository.favorites } returns flowOf(emptyList())
        coEvery { feedbackManager.emit(any()) } returns Unit
        viewModel = MoviesViewModel(repository, feedbackManager)
    }

    @Test
    fun `toggleLike when movie not liked marks as favorite`() = runTest {
        val movie = MovieItem(id = 1, title = "Test Movie", isLiked = false)
        coEvery { repository.toggleFavorite(1) } returns true

        viewModel.toggleLike(movie)
        advanceUntilIdle()

        coVerify { repository.toggleFavorite(1) }
        coVerify { feedbackManager.emit("Película marcada como favorita") }
    }

    @Test
    fun `toggleLike when movie liked removes favorite`() = runTest {
        val movie = MovieItem(id = 2, title = "Test Movie", isLiked = true)
        coEvery { repository.toggleFavorite(2) } returns false

        viewModel.toggleLike(movie)
        advanceUntilIdle()

        coVerify { repository.toggleFavorite(2) }
        coVerify { feedbackManager.emit("Película quitada de favoritas") }
    }

    @Test
    fun `toggleWatched marks as watched`() = runTest {
        val movie = MovieItem(id = 3, title = "Test Movie", isWatched = false)
        coEvery { repository.toggleWatched(3) } returns true

        viewModel.toggleWatched(movie)
        advanceUntilIdle()

        coVerify { repository.toggleWatched(3) }
        coVerify { feedbackManager.emit("Película marcada como vista") }
    }

    @Test
    fun `toggleWatched back to pending`() = runTest {
        val movie = MovieItem(id = 4, title = "Test Movie", isWatched = true)
        coEvery { repository.toggleWatched(4) } returns false

        viewModel.toggleWatched(movie)
        advanceUntilIdle()

        coVerify { repository.toggleWatched(4) }
        coVerify { feedbackManager.emit("Película quitada de vistos") }
    }

    @Test
    fun `deleteMovie calls repository and emits feedback`() = runTest {
        val movie = MovieItem(id = 9, title = "Test Movie")
        coEvery { repository.delete(9) } returns Unit

        viewModel.deleteMovie(movie)
        advanceUntilIdle()

        coVerify { repository.delete(9) }
        coVerify { feedbackManager.emit("Película eliminada") }
    }
}
