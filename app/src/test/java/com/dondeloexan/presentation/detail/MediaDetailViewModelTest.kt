package com.dondeloexan.presentation.detail

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.repository.DiscoverRepository
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
class MediaDetailViewModelTest {

    private val discoverRepository: DiscoverRepository = mockk()
    private val tmdbApi: TmdbApi = mockk()
    private val imdbApi: BalloonerismmApi = mockk()
    private val movieDao: MovieDao = mockk()
    private val tvShowDao: TvShowDao = mockk()
    private val tvShowProgressDao: TvShowProgressDao = mockk()

    private lateinit var viewModel: MediaDetailViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = MediaDetailViewModel(
            discoverRepository = discoverRepository,
            tmdbApi = tmdbApi,
            imdbApi = imdbApi,
            movieDao = movieDao,
            tvShowDao = tvShowDao,
            tvShowProgressDao = tvShowProgressDao
        )
    }

    @Test
    fun `loadContent calls loadCollection for movie with collectionTmdbId`() = runTest {
        val content = Content(
            id = "tmdb-1",
            source = ContentSource.TMDB,
            tmdbId = 1,
            title = "Test Movie",
            type = ContentType.MOVIE,
            collectionTmdbId = 10
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        coEvery { discoverRepository.getCollectionMovies(10) } returns emptyList()

        viewModel.loadContent("tmdb-1")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.isCollectionLoading == false)
        assert(state.collectionMovies != null)
        assert(state.collectionMovies!!.isEmpty())
        coVerify { discoverRepository.getCollectionMovies(10) }
    }

    @Test
    fun `loadCollection filters out current movie from collection movies`() = runTest {
        val content = Content(
            id = "tmdb-5",
            source = ContentSource.TMDB,
            tmdbId = 5,
            title = "Test Movie",
            type = ContentType.MOVIE,
            collectionTmdbId = 10
        )
        val collectionMovies = listOf(
            ContentPreview(id = "tmdb-5", title = "Test Movie", source = ContentSource.TMDB, tmdbId = 5, type = ContentType.MOVIE),
            ContentPreview(id = "tmdb-6", title = "Other Movie", source = ContentSource.TMDB, tmdbId = 6, type = ContentType.MOVIE)
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        coEvery { discoverRepository.getCollectionMovies(10) } returns collectionMovies

        viewModel.loadContent("tmdb-5")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.collectionMovies!!.size == 1)
        assert(state.collectionMovies!![0].title == "Other Movie")
    }

    @Test
    fun `loadCollection is not called for series content`() = runTest {
        val content = Content(
            id = "tmdb-2",
            source = ContentSource.TMDB,
            tmdbId = 2,
            title = "Test Series",
            type = ContentType.SERIES
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { tvShowDao.getByContentId(any()) } returns null
        coEvery { tvShowDao.getByTmdbId(any()) } returns null
        coEvery { tmdbApi.getTvDetail(any()) } returns mockk()
        coEvery { tmdbApi.getTvSeason(any(), any()) } returns mockk()

        viewModel.loadContent("tmdb-2")

        advanceUntilIdle()

        assert(viewModel.uiState.value.collectionMovies == null)
    }

    @Test
    fun `loadCollection settles loading flag after completion`() = runTest {
        val content = Content(
            id = "tmdb-3",
            source = ContentSource.TMDB,
            tmdbId = 3,
            title = "Test Movie",
            type = ContentType.MOVIE,
            collectionTmdbId = 10
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        coEvery { discoverRepository.getCollectionMovies(10) } returns emptyList()

        viewModel.loadContent("tmdb-3")
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(!finalState.isCollectionLoading)
        assert(finalState.collectionMovies != null)
    }

    @Test
    fun `loadSimilar is called for movie content`() = runTest {
        val content = Content(
            id = "tmdb-4",
            source = ContentSource.TMDB,
            tmdbId = 4,
            title = "Test Movie",
            type = ContentType.MOVIE
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        coEvery { discoverRepository.getRecommendations(any(), any()) } returns emptyList()

        viewModel.loadContent("tmdb-4")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(!state.isSimilarLoading)
        assert(state.similarContent != null)
        coVerify { discoverRepository.getRecommendations("tmdb-4", ContentType.MOVIE) }
    }

    @Test
    fun `loadSimilar sets similarContent on success`() = runTest {
        val content = Content(
            id = "tmdb-7",
            source = ContentSource.TMDB,
            tmdbId = 7,
            title = "Test Movie",
            type = ContentType.MOVIE
        )
        val similar = listOf(
            ContentPreview(id = "tmdb-8", title = "Similar 1", source = ContentSource.TMDB, tmdbId = 8, type = ContentType.MOVIE),
            ContentPreview(id = "tmdb-9", title = "Similar 2", source = ContentSource.TMDB, tmdbId = 9, type = ContentType.MOVIE)
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        coEvery { discoverRepository.getRecommendations(any(), any()) } returns similar

        viewModel.loadContent("tmdb-7")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.similarContent!!.size == 2)
        assert(state.similarContent!![0].title == "Similar 1")
    }

    @Test
    fun `loadSimilar sets error state on exception`() = runTest {
        val content = Content(
            id = "tmdb-10",
            source = ContentSource.TMDB,
            tmdbId = 10,
            title = "Test Movie",
            type = ContentType.MOVIE
        )
        coEvery { discoverRepository.getDetail(any(), any()) } returns flowOf(
            com.dondeloexan.domain.model.DataResult.Success(content)
        )
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        coEvery { discoverRepository.getRecommendations(any(), any()) } throws RuntimeException("Error")

        viewModel.loadContent("tmdb-10")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(!state.isSimilarLoading)
        assert(state.similarContent!!.isEmpty())
    }
}
