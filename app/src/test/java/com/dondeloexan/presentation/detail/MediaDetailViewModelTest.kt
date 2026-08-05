package com.dondeloexan.presentation.detail

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.Sentiment
import com.dondeloexan.domain.model.detail.CascadeProposal
import com.dondeloexan.domain.model.detail.EpisodeToggleResult
import com.dondeloexan.domain.model.detail.MovieWatchState
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import com.dondeloexan.domain.usecase.MediaDetailUseCases
import com.dondeloexan.domain.usecase.SeriesState
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

    private val useCases: MediaDetailUseCases = mockk()

    private lateinit var viewModel: MediaDetailViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = MediaDetailViewModel(useCases = useCases)
    }

    private fun movieContent(id: String = "tmdb-1", tmdbId: Int = 1, collectionId: Int? = null) = Content(
        id = id,
        source = ContentSource.TMDB,
        tmdbId = tmdbId,
        title = "Test Movie",
        type = ContentType.MOVIE,
        collectionTmdbId = collectionId
    )

    private fun seriesContent(id: String = "tmdb-2", tmdbId: Int = 2) = Content(
        id = id,
        source = ContentSource.TMDB,
        tmdbId = tmdbId,
        title = "Test Series",
        type = ContentType.SERIES
    )

    private fun stubMovieContentFlow(content: Content) {
        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()
        coEvery { useCases.getSimilar(content) } returns emptyList()
        coEvery { useCases.getCriticReviews(content) } returns emptyList()
        coEvery { useCases.getFaId(content) } returns null
        coEvery { useCases.loadMovieState(content) } returns MovieWatchState()
    }

    @Test
    fun `loadContent calls loadCollection for movie with collectionTmdbId`() = runTest {
        val content = movieContent(collectionId = 10)
        stubMovieContentFlow(content)
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()

        viewModel.loadContent("tmdb-1")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.isCollectionLoading == false)
        assert(state.collectionMovies != null)
        assert(state.collectionMovies!!.isEmpty())
        coVerify { useCases.getCollectionMovies(content) }
    }

    @Test
    fun `loadCollection exposes filtered collection movies`() = runTest {
        val content = movieContent(id = "tmdb-5", tmdbId = 5, collectionId = 10)
        val collectionMovies = listOf(
            ContentPreview(id = "tmdb-5", title = "Test Movie", source = ContentSource.TMDB, tmdbId = 5, type = ContentType.MOVIE),
            ContentPreview(id = "tmdb-6", title = "Other Movie", source = ContentSource.TMDB, tmdbId = 6, type = ContentType.MOVIE)
        )
        stubMovieContentFlow(content)
        coEvery { useCases.getCollectionMovies(content) } returns collectionMovies

        viewModel.loadContent("tmdb-5")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.collectionMovies!!.size == 2)
        assert(state.collectionMovies!!.any { it.title == "Other Movie" })
    }

    @Test
    fun `loadCollection is not called for series content`() = runTest {
        val content = seriesContent()
        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.loadSeriesState(content) } returns SeriesState(
            seasons = emptyList(),
            tracking = SeriesTracking(),
            selectedSeason = 0,
            seasonDetail = null
        )
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()
        coEvery { useCases.getSimilar(content) } returns emptyList()
        coEvery { useCases.getCriticReviews(content) } returns emptyList()
        coEvery { useCases.getFaId(content) } returns null

        viewModel.loadContent("tmdb-2")

        advanceUntilIdle()

        assert(viewModel.uiState.value.collectionMovies == null)
    }

    @Test
    fun `loadCollection settles loading flag after completion`() = runTest {
        val content = movieContent(id = "tmdb-3", tmdbId = 3, collectionId = 10)
        stubMovieContentFlow(content)

        viewModel.loadContent("tmdb-3")
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assert(!finalState.isCollectionLoading)
        assert(finalState.collectionMovies != null)
    }

    @Test
    fun `loadSimilar is called for movie content`() = runTest {
        val content = movieContent(id = "tmdb-4", tmdbId = 4)
        stubMovieContentFlow(content)
        coEvery { useCases.getSimilar(content) } returns emptyList()

        viewModel.loadContent("tmdb-4")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(!state.isSimilarLoading)
        assert(state.similarContent != null)
        coVerify { useCases.getSimilar(content) }
    }

    @Test
    fun `loadSimilar sets similarContent on success`() = runTest {
        val content = movieContent(id = "tmdb-7", tmdbId = 7)
        val similar = listOf(
            ContentPreview(id = "tmdb-8", title = "Similar 1", source = ContentSource.TMDB, tmdbId = 8, type = ContentType.MOVIE),
            ContentPreview(id = "tmdb-9", title = "Similar 2", source = ContentSource.TMDB, tmdbId = 9, type = ContentType.MOVIE)
        )
        stubMovieContentFlow(content)
        coEvery { useCases.getSimilar(content) } returns similar

        viewModel.loadContent("tmdb-7")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.similarContent!!.size == 2)
        assert(state.similarContent!![0].title == "Similar 1")
    }

    @Test
    fun `loadSimilar sets error state on exception`() = runTest {
        val content = movieContent(id = "tmdb-10", tmdbId = 10)
        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()
        coEvery { useCases.getSimilar(content) } throws RuntimeException("Error")
        coEvery { useCases.getCriticReviews(content) } returns emptyList()
        coEvery { useCases.getFaId(content) } returns null
        coEvery { useCases.loadMovieState(content) } returns MovieWatchState()

        viewModel.loadContent("tmdb-10")

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(!state.isSimilarLoading)
        assert(state.similarContent!!.isEmpty())
    }

    @Test
    fun `Matrix Revolutions loads critic reviews and collection`() = runTest {
        val content = Content(
            id = "tmdb-603",
            source = ContentSource.TMDB,
            tmdbId = 603,
            title = "The Matrix Revolutions",
            year = 2003,
            type = ContentType.MOVIE,
            collectionTmdbId = 2344
        )

        val reviews = listOf(
            CriticReview(
                author = "Pablo Kurt",
                publication = "FilmAffinity",
                text = "Fascinados por la primera entrega...",
                sentiment = Sentiment.NEGATIVE
            ),
            CriticReview(
                author = "Roger Ebert",
                publication = "rogerebert.com",
                text = "Mi admiración...",
                sentiment = Sentiment.NEUTRAL
            ),
            CriticReview(
                author = "Peter Travers",
                publication = "Rolling Stone",
                text = "A riesgo de no hacerle justicia...",
                sentiment = Sentiment.NEGATIVE
            ),
            CriticReview(
                author = "A. O. Scott",
                publication = "The New York Times",
                text = "Toda su grandilocuencia...",
                sentiment = Sentiment.NEGATIVE
            ),
            CriticReview(
                author = "David Denby",
                publication = "The New Yorker",
                text = "En el mejor de los casos...",
                sentiment = Sentiment.NEUTRAL
            )
        )

        val collection = listOf(
            ContentPreview(
                id = "tmdb-603", title = "The Matrix Revolutions",
                source = ContentSource.TMDB, tmdbId = 603, type = ContentType.MOVIE,
                year = 2003, coverUrl = "https://image.tmdb.org/t/p/w500/revolutions.jpg"
            ),
            ContentPreview(
                id = "tmdb-604", title = "The Matrix",
                source = ContentSource.TMDB, tmdbId = 604, type = ContentType.MOVIE,
                year = 1999, coverUrl = "https://image.tmdb.org/t/p/w500/matrix.jpg"
            ),
            ContentPreview(
                id = "tmdb-605", title = "The Matrix Reloaded",
                source = ContentSource.TMDB, tmdbId = 605, type = ContentType.MOVIE,
                year = 2003, coverUrl = "https://image.tmdb.org/t/p/w500/reloaded.jpg"
            )
        )

        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.getCollectionMovies(content) } returns collection
        coEvery { useCases.getSimilar(content) } returns emptyList()
        coEvery { useCases.getCriticReviews(content) } returns reviews
        coEvery { useCases.getFaId(content) } returns null
        coEvery { useCases.loadMovieState(content) } returns MovieWatchState()

        viewModel.loadContent("tmdb-603")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.criticReviews!!.size == 5)
        assert(state.criticReviews!![0].author == "Pablo Kurt")
        assert(state.criticReviews!![0].publication == "FilmAffinity")
        assert(state.criticReviews!![1].author == "Roger Ebert")
        assert(state.criticReviews!![4].author == "David Denby")
        assert(state.collectionMovies!!.size == 3)
        assert(state.collectionMovies!!.any { it.title == "The Matrix" })
        assert(state.collectionMovies!!.any { it.title == "The Matrix Reloaded" })
        assert(state.collectionMovies!!.any { it.title == "The Matrix Revolutions" })
        assert(!state.isCriticReviewsLoading)
        assert(!state.isCollectionLoading)
    }

    @Test
    fun `toggleMovieWatched updates state via use cases`() = runTest {
        val content = movieContent()
        stubMovieContentFlow(content)

        viewModel.loadContent("tmdb-1")
        advanceUntilIdle()

        coEvery { useCases.toggleMovieWatched(content) } returns MovieWatchState(isWatched = true)

        viewModel.toggleMovieWatched()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.isMovieWatched == true)
        coVerify { useCases.toggleMovieWatched(content) }
    }

    @Test
    fun `toggleEpisodeWatched triggers cascade proposal`() = runTest {
        val content = seriesContent()
        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.loadSeriesState(content) } returns SeriesState(
            seasons = listOf(Season(seasonNumber = 1, name = "T1", episodeCount = 3)),
            tracking = SeriesTracking(),
            selectedSeason = 1,
            seasonDetail = null
        )
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()
        coEvery { useCases.getSimilar(content) } returns emptyList()
        coEvery { useCases.getCriticReviews(content) } returns emptyList()
        coEvery { useCases.getFaId(content) } returns null

        coEvery {
            useCases.toggleEpisode(any(), any(), any(), any(), any())
        } returns EpisodeToggleResult.NeedsCascade(
            CascadeProposal(season = 1, targetEpisode = 3, count = 2)
        )

        viewModel.loadContent("tmdb-2")
        advanceUntilIdle()
        viewModel.toggleEpisodeWatched(3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.cascadeProposal != null)
        assert(state.cascadeProposal!!.count == 2)
    }

    @Test
    fun `toggleEpisodeWatched applies watched on no cascade`() = runTest {
        val content = seriesContent()
        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.loadSeriesState(content) } returns SeriesState(
            seasons = listOf(Season(seasonNumber = 1, name = "T1", episodeCount = 1)),
            tracking = SeriesTracking(),
            selectedSeason = 1,
            seasonDetail = null
        )
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()
        coEvery { useCases.getSimilar(content) } returns emptyList()
        coEvery { useCases.getCriticReviews(content) } returns emptyList()
        coEvery { useCases.getFaId(content) } returns null

        coEvery {
            useCases.toggleEpisode(any(), any(), any(), any(), any())
        } returns EpisodeToggleResult.Applied(
            SeriesTracking(exists = true, watchedEpisodes = setOf("S1E1"))
        )

        viewModel.loadContent("tmdb-2")
        advanceUntilIdle()
        viewModel.toggleEpisodeWatched(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.watchedEpisodes.contains("S1E1"))
        assert(state.cascadeProposal == null)
    }

    @Test
    fun `confirmCascadeWatched applies cascade and clears proposal`() = runTest {
        val content = seriesContent()
        coEvery { useCases.getDetail(any(), any()) } returns flowOf(DataResult.Success(content))
        coEvery { useCases.loadSeriesState(content) } returns SeriesState(
            seasons = listOf(Season(seasonNumber = 1, name = "T1", episodeCount = 3)),
            tracking = SeriesTracking(),
            selectedSeason = 1,
            seasonDetail = null
        )
        coEvery { useCases.getCollectionMovies(content) } returns emptyList()
        coEvery { useCases.getSimilar(content) } returns emptyList()
        coEvery { useCases.getCriticReviews(content) } returns emptyList()
        coEvery { useCases.getFaId(content) } returns null

        coEvery {
            useCases.toggleEpisode(any(), any(), any(), any(), any())
        } returns EpisodeToggleResult.NeedsCascade(
            CascadeProposal(season = 1, targetEpisode = 3, count = 2)
        )
        coEvery {
            useCases.confirmCascade(any(), any(), any(), any())
        } returns SeriesTracking(exists = true, watchedEpisodes = setOf("S1E1", "S1E2", "S1E3"))

        viewModel.loadContent("tmdb-2")
        advanceUntilIdle()
        viewModel.toggleEpisodeWatched(3)
        advanceUntilIdle()
        viewModel.confirmCascadeWatched()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assert(state.watchedEpisodes.contains("S1E1"))
        assert(state.watchedEpisodes.contains("S1E2"))
        assert(state.watchedEpisodes.contains("S1E3"))
        assert(state.cascadeProposal == null)
    }
}