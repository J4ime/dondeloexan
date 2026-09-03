package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.data.remote.dto.TmdbTrendingResponse
import com.dondeloexan.data.remote.filmaffinity.FilmaffinityScraper
import com.dondeloexan.domain.repository.TrackingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DiscoverRepositoryDiscoverRecipesTest {

    private val tmdbApi: TmdbApi = mockk()
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
    private val trackingRepository: TrackingRepository = mockk(relaxed = true)

    private val repo: DiscoverRepositoryImpl = DiscoverRepositoryImpl(
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
        faMovieDataDao = faMovieDataDao,
        trackingRepository = trackingRepository
    )

    private val emptyMovie = TmdbTrendingResponse(page = 1, totalResults = 0, results = emptyList())
    private val emptyTv = TmdbTrendingResponse(page = 1, totalResults = 0, results = emptyList())
    private val emptyTrending = TmdbTrendingResponse(page = 1, totalResults = 0, results = emptyList())

    private fun stubDiscover() {
        coEvery { userPlatformDao.getActiveNames() } returns emptyList()
        coEvery { tmdbApi.discoverMovie(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyMovie
        coEvery { tmdbApi.discoverTv(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyTv
        coEvery { tmdbApi.getTrending() } returns emptyTrending
    }

    @Test
    fun `pagina 1 usa descubrir por popularidad`() = runTest {
        stubDiscover()

        repo.fetchTrendingPage(1, filterByPlatforms = false)

        coVerify {
            tmdbApi.discoverMovie(
                any(), any(), any(), any(), any(), any(),
                sortBy = "popularity.desc", voteCountGte = 100, any(), any()
            )
        }
        coVerify {
            tmdbApi.discoverTv(
                any(), any(), any(), any(), any(), any(),
                sortBy = "popularity.desc", voteCountGte = 100, any()
            )
        }
    }

    @Test
    fun `pagina 2 usa mejor valoradas sin limite de fecha y con minimo de votos mas alto`() = runTest {
        stubDiscover()

        repo.fetchTrendingPage(2, filterByPlatforms = false)

        coVerify {
            tmdbApi.discoverMovie(
                any(), any(), any(), any(), releaseDateGte = null, releaseDateLte = null,
                sortBy = "vote_average.desc", voteCountGte = 200, any(), any()
            )
        }
        coVerify {
            tmdbApi.discoverTv(
                any(), any(), any(), any(), firstAirDateGte = null, firstAirDateLte = null,
                sortBy = "vote_average.desc", voteCountGte = 200, any()
            )
        }
    }

    @Test
    fun `pagina 3 usa novedades por fecha de estreno`() = runTest {
        stubDiscover()

        repo.fetchTrendingPage(3, filterByPlatforms = false)

        coVerify {
            tmdbApi.discoverMovie(
                any(), any(), any(), any(), any(), any(),
                sortBy = "primary_release_date.desc", voteCountGte = 100, any(), any()
            )
        }
        coVerify {
            tmdbApi.discoverTv(
                any(), any(), any(), any(), any(), any(),
                sortBy = "first_air_date.desc", voteCountGte = 100, any()
            )
        }
    }

    @Test
    fun `pagina 4 usa tendencias de la semana en lugar de discover`() = runTest {
        stubDiscover()

        repo.fetchTrendingPage(4, filterByPlatforms = false)

        coVerify { tmdbApi.getTrending() }
        coVerify(exactly = 0) {
            tmdbApi.discoverMovie(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            tmdbApi.discoverTv(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `la rotacion se repite, pagina 5 vuelve a popularidad`() = runTest {
        stubDiscover()

        repo.fetchTrendingPage(5, filterByPlatforms = false)

        coVerify {
            tmdbApi.discoverMovie(
                any(), any(), any(), any(), any(), any(),
                sortBy = "popularity.desc", voteCountGte = 100, any(), any()
            )
        }
    }
}
