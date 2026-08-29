package com.dondeloexan.presentation.discover

import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
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
class DiscoverViewModelTest {

    private val discoverRepository: DiscoverRepository = mockk()
    private val userPlatformDao: UserPlatformDao = mockk()
    private val movieDao: MovieDao = mockk()
    private val tvShowDao: TvShowDao = mockk()
    private val tvShowProgressDao: TvShowProgressDao = mockk()
    private val blacklistDao: BlacklistDao = mockk()
    private val tmdbApi: TmdbApi = mockk()
    private val feedbackManager: FeedbackManager = mockk()
    private val faMovieDataDao: FaMovieDataDao = mockk()

    private lateinit var viewModel: DiscoverViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { userPlatformDao.getActiveFlow() } returns flowOf(emptyList())
        coEvery { blacklistDao.getAllFlow() } returns flowOf(emptyList())
        coEvery { movieDao.getLiked() } returns flowOf(emptyList())
        coEvery { tvShowDao.getLiked() } returns flowOf(emptyList())
        coEvery { movieDao.getByStatus(any()) } returns flowOf(emptyList())
        coEvery { tvShowDao.getByStatus(any()) } returns flowOf(emptyList())
        coEvery { movieDao.getAllFlow() } returns flowOf(emptyList())
        coEvery { tvShowDao.getAllFlow() } returns flowOf(emptyList())
        coEvery { faMovieDataDao.getByContentId(any()) } returns null
        coEvery { feedbackManager.emit(any()) } returns Unit
    }

    private fun createViewModel(): DiscoverViewModel = DiscoverViewModel(
        discoverRepository = discoverRepository,
        userPlatformDao = userPlatformDao,
        movieDao = movieDao,
        tvShowDao = tvShowDao,
        tvShowProgressDao = tvShowProgressDao,
        blacklistDao = blacklistDao,
        tmdbApi = tmdbApi,
        feedbackManager = feedbackManager,
        faMovieDataDao = faMovieDataDao
    )

    private fun searchPage(page: Int, count: Int = 10): List<ContentPreview> {
        return (1..count).map { i ->
            ContentPreview(
                id = "tmdb-${page * 100 + i}",
                source = ContentSource.TMDB,
                tmdbId = page * 100 + i,
                title = "Matrix Result ${page}-$i",
                type = ContentType.MOVIE,
                ratingImdb = 8.0f
            )
        }
    }

    @Test
    fun `loadNextPage during search paginates search results and never trending`() = runTest {
        coEvery { discoverRepository.fetchTrendingPage(any(), any()) } returns emptyList()
        coEvery { discoverRepository.searchPeople(any()) } returns emptyList()
        coEvery { discoverRepository.searchCompanies(any()) } returns emptyList()
        coEvery { discoverRepository.fetchSearchPage("matrix", 1) } returns searchPage(1)
        coEvery { discoverRepository.fetchSearchPage("matrix", 2) } returns searchPage(2)
        coEvery { discoverRepository.fetchSearchPage("matrix", 3) } returns emptyList()

        viewModel = createViewModel()
        advanceUntilIdle()

        clearMocks(discoverRepository, answers = false, recordedCalls = true)

        viewModel.onSearchQueryChanged("matrix")
        advanceUntilIdle()

        val initial = (viewModel.uiState.value as DiscoverUiState.Success).results
        assert(initial.isNotEmpty())

        viewModel.loadNextPage()
        advanceUntilIdle()

        coVerify { discoverRepository.fetchSearchPage("matrix", 2) }
        coVerify(exactly = 0) { discoverRepository.fetchTrendingPage(any(), any()) }

        val finalResults = (viewModel.uiState.value as DiscoverUiState.Success).results
        assert(finalResults.size > initial.size)
        assert(finalResults.all { it.title.startsWith("Matrix Result") })
    }

    @Test
    fun `search resets pagination when query changes`() = runTest {
        coEvery { discoverRepository.fetchTrendingPage(any(), any()) } returns emptyList()
        coEvery { discoverRepository.searchPeople(any()) } returns emptyList()
        coEvery { discoverRepository.searchCompanies(any()) } returns emptyList()
        coEvery { discoverRepository.fetchSearchPage(any(), 1) } returns searchPage(1, count = 5)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("matrix")
        advanceUntilIdle()

        val afterFirstSearch = (viewModel.uiState.value as DiscoverUiState.Success).results
        assert(afterFirstSearch.size == 5)

        viewModel.onSearchQueryChanged("inception")
        advanceUntilIdle()

        val afterSecondSearch = (viewModel.uiState.value as DiscoverUiState.Success).results
        assert(afterSecondSearch.size == 5)
        coVerify { discoverRepository.fetchSearchPage("inception", 1) }
    }
}