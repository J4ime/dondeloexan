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
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResponse
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResult
import com.dondeloexan.data.remote.dto.TmdbWatchProvidersResponse
import com.dondeloexan.data.remote.filmaffinity.FilmaffinityScraper
import com.dondeloexan.domain.repository.TrackingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DiscoverRepositorySearchTest {

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

    private fun response(vararg results: TmdbMultiSearchResult) = TmdbMultiSearchResponse(
        page = 1,
        totalResults = results.size,
        totalPages = 1,
        results = results.toList()
    )

    @Test
    fun `busqueda encuentra por titulo original aunque el localizado no coincida`() = runTest {
        coEvery { tmdbApi.searchMulti(any(), any(), any()) } returns response(
            TmdbMultiSearchResult(id = 603, mediaType = "movie", title = "Matrix", originalTitle = "The Matrix")
        )
        coEvery { tmdbApi.getMovieWatchProviders(any()) } returns TmdbWatchProvidersResponse(id = 0, results = null)

        val result = repo.fetchSearchPage("the matrix", 1)

        assert(result.any { it.id == "tmdb-603" })
    }

    @Test
    fun `busqueda reintenta sin language cuando la localizada no devuelve nada`() = runTest {
        coEvery { tmdbApi.searchMulti("zzz", "es-ES", 1) } returns response()
        coEvery { tmdbApi.searchMulti("zzz", null, 1) } returns response(
            TmdbMultiSearchResult(id = 5, mediaType = "movie", title = "ZZZ")
        )
        coEvery { tmdbApi.getMovieWatchProviders(any()) } returns TmdbWatchProvidersResponse(id = 0, results = null)

        val result = repo.fetchSearchPage("zzz", 1)

        assert(result.any { it.id == "tmdb-5" })
        coVerify(exactly = 1) { tmdbApi.searchMulti("zzz", null, 1) }
    }
}
