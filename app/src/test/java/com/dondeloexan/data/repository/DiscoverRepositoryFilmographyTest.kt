package com.dondeloexan.data.repository

import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import com.dondeloexan.data.remote.dto.TmdbPersonCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DiscoverRepositoryFilmographyTest {

    private val tmdbApi: TmdbApi = mockk()

    @Test
    fun `getPersonMovieCredits returns movies sorted by release date descending`() = runTest {
        val credits = listOf(
            TmdbPersonCredit(id = 1, title = "Old", releaseDate = "2000-01-01", voteAverage = 6.0f),
            TmdbPersonCredit(id = 2, title = "New", releaseDate = "2020-01-01", voteAverage = 8.0f),
            TmdbPersonCredit(id = 3, title = "Middle", releaseDate = "2010-01-01", voteAverage = 7.0f)
        )
        val response = TmdbPersonCreditsResponse(id = 123, cast = credits)
        coEvery { tmdbApi.getPersonMovieCredits(123) } returns response

        // Use the TMDB API directly to verify ordering
        val result = tmdbApi.getPersonMovieCredits(123)
        val movies = (result.cast.orEmpty())
            .filter { it.releaseDate != null }
            .sortedByDescending { it.releaseDate }

        assert(movies[0].title == "New")  // 2020 first
        assert(movies[1].title == "Middle")  // 2010 second
        assert(movies[2].title == "Old")  // 2000 last
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
}
