package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResponse
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResult
import com.dondeloexan.data.remote.dto.TmdbWatchProvidersResponse
import com.dondeloexan.data.remote.filmaffinity.FilmaffinityScraper
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiscoverRepositoryMovieWatchStateTest {

    private val tmdbApi: TmdbApi = mockk()
    private val imdbApi: BalloonerismmApi = mockk()
    private val omdbApi: OmdbApi = mockk()
    private val userPlatformDao: UserPlatformDao = mockk()
    private val movieDao: MovieDao = mockk()
    private val tvShowDao: TvShowDao = mockk()
    private val tvShowProgressDao: TvShowProgressDao = mockk()
    private val userPreferencesDataStore: UserPreferencesDataStore = mockk()
    private val filmaffinityScraper: FilmaffinityScraper = mockk()
    private val criticReviewDao: CriticReviewDao = mockk()
    private val wikidataApi: WikidataApi = mockk()
    private val faMovieDataDao: FaMovieDataDao = mockk()

    private lateinit var repo: DiscoverRepositoryImpl

    private val matrixContent = Content(
        id = "tmdb-603",
        source = ContentSource.TMDB,
        tmdbId = 603,
        imdbId = "tt0133093",
        title = "The Matrix",
        type = ContentType.MOVIE
    )

    @BeforeEach
    fun setUp() {
        repo = DiscoverRepositoryImpl(
            imdbApi = imdbApi,
            tmdbApi = tmdbApi,
            omdbApi = omdbApi,
            wikidataApi = wikidataApi,
            userPlatformDao = userPlatformDao,
            movieDao = movieDao,
            tvShowDao = tvShowDao,
            tvShowProgressDao = tvShowProgressDao,
            userPreferencesDataStore = userPreferencesDataStore,
            filmaffinityScraper = filmaffinityScraper,
            criticReviewDao = criticReviewDao,
            faMovieDataDao = faMovieDataDao
        )
    }

    @Test
    fun `setMovieWatched does not alter liked flag`() = runTest {
        coEvery { movieDao.getByContentId("tmdb-603") } returns MovieEntity(
            id = 1, contentId = "tmdb-603", tmdbId = 603, title = "The Matrix",
            liked = true, status = WatchStatus.POR_VER
        )
        coEvery { movieDao.getByTmdbId(603) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        val slot = slot<MovieEntity>()
        coEvery { movieDao.update(capture(slot)) } returns Unit

        repo.setMovieWatched(matrixContent, true)

        val updated = slot.captured
        assert(updated.status == WatchStatus.YA_VISTA)
        assert(updated.liked)
    }

    @Test
    fun `setMovieFavorite only toggles liked and keeps watched state`() = runTest {
        var current = MovieEntity(
            id = 1, contentId = "tmdb-603", tmdbId = 603, title = "The Matrix",
            liked = false, status = WatchStatus.YA_VISTA, watchedAt = 1000L
        )
        coEvery { movieDao.getByContentId("tmdb-603") } answers { current }
        coEvery { movieDao.getByTmdbId(603) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        val slot = slot<MovieEntity>()
        coEvery { movieDao.update(capture(slot)) } answers { current = slot.captured; Unit }

        val state = repo.setMovieFavorite(matrixContent, true)

        assert(state.isFavorite)
        val updated = slot.captured
        assert(updated.liked)
        assert(updated.status == WatchStatus.YA_VISTA)
        assert(updated.watchedAt == 1000L)
    }

    @Test
    fun `setMovieFavorite on unknown movie inserts as pending not watched`() = runTest {
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        val slot = slot<MovieEntity>()
        coEvery { movieDao.insert(capture(slot)) } returns 1L

        repo.setMovieFavorite(matrixContent, true)

        val inserted = slot.captured
        assert(inserted.liked)
        assert(inserted.status == WatchStatus.POR_VER)
        assert(inserted.watchedAt == null)
    }

    @Test
    fun `getMovieWatchState reports inLibrary`() = runTest {
        coEvery { movieDao.getByContentId(any()) } returns null
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null

        val missing = repo.getMovieWatchState(matrixContent)
        assert(!missing.inLibrary)
        assert(!missing.isWatched)
        assert(!missing.isFavorite)

        coEvery { movieDao.getByContentId(any()) } returns MovieEntity(
            id = 1, contentId = "tmdb-603", title = "The Matrix", liked = true, status = WatchStatus.YA_VISTA
        )
        val present = repo.getMovieWatchState(matrixContent)
        assert(present.inLibrary)
        assert(present.isWatched)
        assert(present.isFavorite)
    }

    @Test
    fun `addMovieToLibrary inserts pending liked false when missing`() = runTest {
        var current: MovieEntity? = null
        coEvery { movieDao.getByContentId(any()) } answers { current }
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null
        val slot = slot<MovieEntity>()
        coEvery { movieDao.insert(capture(slot)) } answers { current = slot.captured; 1L }

        val state = repo.addMovieToLibrary(matrixContent)

        assert(state.inLibrary)
        val inserted = slot.captured
        assert(inserted.status == WatchStatus.POR_VER)
        assert(!inserted.liked)
        assert(inserted.watchedAt == null)
    }

    @Test
    fun `addMovieToLibrary does not duplicate when existing`() = runTest {
        coEvery { movieDao.getByContentId(any()) } returns MovieEntity(
            id = 1, contentId = "tmdb-603", title = "The Matrix", status = WatchStatus.POR_VER
        )
        coEvery { movieDao.getByTmdbId(any()) } returns null
        coEvery { movieDao.getByImdbId(any()) } returns null

        repo.addMovieToLibrary(matrixContent)

        coVerify(exactly = 0) { movieDao.insert(any()) }
    }

    @Test
    fun `addSeriesToLibrary inserts series as pending`() = runTest {
        coEvery { tvShowDao.getByContentId(any()) } returns null
        coEvery { tvShowDao.getByTmdbId(any()) } returns null
        coEvery { tvShowDao.getByImdbId(any()) } returns null
        val slot = slot<TvShowEntity>()
        coEvery { tvShowDao.insert(capture(slot)) } returns 1L

        val added = repo.addSeriesToLibrary(
            matrixContent.copy(type = ContentType.SERIES, tmdbId = 1396)
        )

        assert(added)
        val inserted = slot.captured
        assert(inserted.status == WatchStatus.POR_VER)
        assert(!inserted.liked)
    }

    @Test
    fun `fetchSearchPage filters irrelevant results`() = runTest {
        val results = listOf(
            TmdbMultiSearchResult(id = 603, mediaType = "movie", title = "The Matrix", posterPath = "/m.jpg", voteAverage = 8.7f),
            TmdbMultiSearchResult(id = 606, mediaType = "movie", title = "The Matrix Reloaded", posterPath = null, voteAverage = 7.2f),
            TmdbMultiSearchResult(id = 238, mediaType = "movie", title = "The Godfather", posterPath = "/g.jpg", voteAverage = 8.7f),
            TmdbMultiSearchResult(id = 1399, mediaType = "tv", title = "Matrix Origins", posterPath = null, voteAverage = 6.5f)
        )
        coEvery { tmdbApi.searchMulti("matrix", any(), 1) } returns TmdbMultiSearchResponse(
            page = 1, totalResults = 4, totalPages = 1, results = results
        )
        coEvery { tmdbApi.getMovieWatchProviders(any()) } returns TmdbWatchProvidersResponse(id = 0, results = null)
        coEvery { tmdbApi.getTvWatchProviders(any()) } returns TmdbWatchProvidersResponse(id = 0, results = null)

        val page = repo.fetchSearchPage("matrix", 1)

        assert(page.none { it.title == "The Godfather" })
        assert(page.any { it.title == "The Matrix" })
        assert(page.any { it.title == "The Matrix Reloaded" })
        assert(page.any { it.title == "Matrix Origins" })
    }

    @Test
    fun `fetchSearchPage drops titles that miss a query token`() = runTest {
        val results = listOf(
            TmdbMultiSearchResult(id = 1, mediaType = "movie", title = "Matrix", posterPath = null),
            TmdbMultiSearchResult(id = 2, mediaType = "movie", title = "The", posterPath = null),
            TmdbMultiSearchResult(id = 3, mediaType = "movie", title = "Reloaded Matrix", posterPath = null)
        )
        coEvery { tmdbApi.searchMulti("the matrix", any(), 1) } returns TmdbMultiSearchResponse(
            page = 1, totalResults = 3, totalPages = 1, results = results
        )
        coEvery { tmdbApi.getMovieWatchProviders(any()) } returns TmdbWatchProvidersResponse(id = 0, results = null)

        val page = repo.fetchSearchPage("the matrix", 1)

        assert(page.none { it.title == "Matrix" })
        assert(page.none { it.title == "The" })
        assert(page.none { it.title == "Reloaded Matrix" })
    }
}