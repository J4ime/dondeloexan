package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbCollectionDto
import com.dondeloexan.data.remote.dto.TmdbCollectionPartDto
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import com.dondeloexan.data.remote.dto.TmdbPersonCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResponse
import com.dondeloexan.data.remote.dto.TmdbTrendingResponse
import com.dondeloexan.data.remote.filmaffinity.FilmaffinityScraper
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.domain.model.ContentType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DiscoverRepositoryFilmographyTest {

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

    private val repo: DiscoverRepositoryImpl = DiscoverRepositoryImpl(
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

    @Test
    fun `getPersonMovieCredits returns movies sorted by release date descending`() = runTest {
        val credits = listOf(
            TmdbPersonCredit(id = 1, title = "Old", releaseDate = "2000-01-01", voteAverage = 6.0f),
            TmdbPersonCredit(id = 2, title = "New", releaseDate = "2020-01-01", voteAverage = 8.0f),
            TmdbPersonCredit(id = 3, title = "Middle", releaseDate = "2010-01-01", voteAverage = 7.0f)
        )
        val response = TmdbPersonCreditsResponse(id = 123, cast = credits)
        coEvery { tmdbApi.getPersonMovieCredits(123) } returns response

        val result = tmdbApi.getPersonMovieCredits(123)
        val movies = (result.cast.orEmpty())
            .filter { it.releaseDate != null }
            .sortedByDescending { it.releaseDate }

        assert(movies[0].title == "New")
        assert(movies[1].title == "Middle")
        assert(movies[2].title == "Old")
    }

    @Test
    fun `getPersonMovieCredits maps type to MOVIE even when mediaType is null`() = runTest {
        val credits = listOf(
            TmdbPersonCredit(id = 1, title = "Movie", releaseDate = "2020-01-01", mediaType = null)
        )
        coEvery { tmdbApi.getPersonMovieCredits(123) } returns TmdbPersonCreditsResponse(id = 123, cast = credits)

        val result = repo.getPersonMovieCredits(123)

        assert(result.size == 1)
        assert(result[0].type == ContentType.MOVIE)
    }

    @Test
    fun `getPersonTvCredits maps type to SERIES even when mediaType is null`() = runTest {
        val credits = listOf(
            TmdbPersonCredit(id = 1, name = "Series", firstAirDate = "2020-01-01", mediaType = null)
        )
        coEvery { tmdbApi.getPersonTvCredits(123) } returns TmdbPersonCreditsResponse(id = 123, cast = credits)

        val result = repo.getPersonTvCredits(123)

        assert(result.size == 1)
        assert(result[0].type == ContentType.SERIES)
    }

    @Test
    fun `searchPerson returns results`() = runTest {
        val results = listOf(
            TmdbPersonSearchResult(id = 1, name = "Steven Spielberg", knownForDepartment = "Directing")
        )
        coEvery { tmdbApi.searchPerson(any()) } returns TmdbPersonSearchResponse(
            page = 1, results = results, totalPages = 1, totalResults = 1
        )

        val response = tmdbApi.searchPerson("Spielberg")
        assert(response.results.size == 1)
        assert(response.results[0].name == "Steven Spielberg")
    }

    @Test
    fun `getCollectionMovies returns parts with posterPath mapped to ContentPreview`() = runTest {
        val parts = listOf(
            TmdbCollectionPartDto(id = 1, title = "Part I", posterPath = "/p1.jpg", releaseDate = "2020-01-01", voteAverage = 7.0f),
            TmdbCollectionPartDto(id = 2, title = "Part II", posterPath = "/p2.jpg", releaseDate = "2021-01-01", voteAverage = 8.0f)
        )
        val collection = TmdbCollectionDto(id = 10, name = "Test Collection", parts = parts)
        coEvery { tmdbApi.getCollection(10) } returns collection

        val result = repo.getCollectionMovies(10)

        assert(result.size == 2)
        assert(result[0].title == "Part I")
        assert(result[0].tmdbId == 1)
        assert(result[0].coverUrl == "https://image.tmdb.org/t/p/w500/p1.jpg")
        assert(result[1].title == "Part II")
    }

    @Test
    fun `getCollectionMovies filters out parts without posterPath`() = runTest {
        val parts = listOf(
            TmdbCollectionPartDto(id = 1, title = "With Poster", posterPath = "/p1.jpg", releaseDate = "2020-01-01"),
            TmdbCollectionPartDto(id = 2, title = "No Poster", posterPath = null, releaseDate = "2021-01-01")
        )
        val collection = TmdbCollectionDto(id = 10, name = "Test", parts = parts)
        coEvery { tmdbApi.getCollection(10) } returns collection

        val result = repo.getCollectionMovies(10)

        assert(result.size == 1)
        assert(result[0].title == "With Poster")
    }

    @Test
    fun `getCollectionMovies returns empty list when API throws`() = runTest {
        coEvery { tmdbApi.getCollection(any()) } throws RuntimeException("API error")

        val result = repo.getCollectionMovies(999)

        assert(result.isEmpty())
    }

    @Test
    fun `getRecommendations returns movie recommendations for tmdb movie`() = runTest {
        val results = listOf(
            TmdbMultiSearchResult(id = 101, title = "Rec 1", posterPath = "/r1.jpg", mediaType = "movie"),
            TmdbMultiSearchResult(id = 102, title = "Rec 2", posterPath = "/r2.jpg", mediaType = "movie")
        )
        val response = TmdbTrendingResponse(page = 1, totalResults = results.size, results = results)
        coEvery { tmdbApi.getMovieRecommendations(123) } returns response

        val result = repo.getRecommendations("tmdb-123", ContentType.MOVIE)

        assert(result.size == 2)
        assert(result[0].title == "Rec 1")
        assert(result[0].tmdbId == 101)
        assert(result[1].title == "Rec 2")
    }

    @Test
    fun `getRecommendations returns tv recommendations for tmdb series`() = runTest {
        val results = listOf(
                TmdbMultiSearchResult(id = 201, name = "TV Rec 1", posterPath = "/t1.jpg", mediaType = "tv")
        )
        val response = TmdbTrendingResponse(page = 1, totalResults = results.size, results = results)
        coEvery { tmdbApi.getTvRecommendations(456) } returns response

        val result = repo.getRecommendations("tmdb-456", ContentType.SERIES)

        assert(result.size == 1)
        assert(result[0].title == "TV Rec 1")
    }

    @Test
    fun `getRecommendations filters out results without posterPath`() = runTest {
        val results = listOf(
            TmdbMultiSearchResult(id = 301, title = "With Poster", posterPath = "/p.jpg", mediaType = "movie"),
                TmdbMultiSearchResult(id = 302, title = "No Poster", posterPath = null, mediaType = "movie")
        )
        val response = TmdbTrendingResponse(page = 1, totalResults = results.size, results = results)
        coEvery { tmdbApi.getMovieRecommendations(789) } returns response

        val result = repo.getRecommendations("tmdb-789", ContentType.MOVIE)

        assert(result.size == 1)
        assert(result[0].title == "With Poster")
    }

    @Test
    fun `getRecommendations takes only 5 results`() = runTest {
        val results = (1..10).map { i ->
                TmdbMultiSearchResult(id = i, title = "Rec $i", posterPath = "/p$i.jpg", mediaType = "movie")
        }
        val response = TmdbTrendingResponse(page = 1, totalResults = results.size, results = results)
        coEvery { tmdbApi.getMovieRecommendations(111) } returns response

        val result = repo.getRecommendations("tmdb-111", ContentType.MOVIE)

        assert(result.size == 5)
    }

    @Test
    fun `getRecommendations returns empty list when API throws`() = runTest {
        coEvery { tmdbApi.getMovieRecommendations(any()) } throws RuntimeException("API error")

        val result = repo.getRecommendations("tmdb-999", ContentType.MOVIE)

        assert(result.isEmpty())
    }

    @Test
    fun `getRecommendations returns empty list for unknown prefix`() = runTest {
        val result = repo.getRecommendations("unknown-123", ContentType.MOVIE)

        assert(result.isEmpty())
    }
}
