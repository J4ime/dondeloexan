package com.dondeloexan.data.repository

import com.dondeloexan.data.remote.api.SeriesGraphApi
import com.dondeloexan.data.remote.dto.SeriesGraphEpisodeRatingDto
import com.dondeloexan.data.remote.dto.SeriesGraphSeasonRatingsDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SeriesRatingsRepositoryImplTest {

    private val api: SeriesGraphApi = mockk()
    private val repo = SeriesRatingsRepositoryImpl(api)

    @Test
    fun `mapea las notas IMDb por episodio`() = runTest {
        coEvery { api.getSeasonRatings(2316) } returns listOf(
            SeriesGraphSeasonRatingsDto(
                seasonNumber = 1,
                episodes = listOf(
                    SeriesGraphEpisodeRatingDto(
                        episodeNumber = 1, seasonNumber = 1, name = "Pilot",
                        airDate = "2005-03-24", imdbRating = 7.3, imdbVotes = 13496
                    )
                )
            )
        )

        val result = repo.getEpisodeRatings(2316)

        assert(result.size == 1)
        assert(result[0].seasonNumber == 1)
        assert(result[0].episodeNumber == 1)
        assert(result[0].imdbRating == 7.3)
    }

    @Test
    fun `cachea los ratings por serie`() = runTest {
        coEvery { api.getSeasonRatings(2316) } returns listOf(
            SeriesGraphSeasonRatingsDto(1, listOf(SeriesGraphEpisodeRatingDto(1, 1, imdbRating = 8.0)))
        )

        repo.getEpisodeRatings(2316)
        repo.getEpisodeRatings(2316)

        coVerify(exactly = 1) { api.getSeasonRatings(2316) }
    }

    @Test
    fun `si la api falla devuelve lista vacia`() = runTest {
        coEvery { api.getSeasonRatings(2316) } throws RuntimeException("red caída")

        val result = repo.getEpisodeRatings(2316)

        assert(result.isEmpty())
    }
}
