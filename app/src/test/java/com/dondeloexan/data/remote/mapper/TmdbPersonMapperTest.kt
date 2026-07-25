package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import org.junit.jupiter.api.Test

class TmdbPersonMapperTest {

    @Test
    fun `toContentPreview maps TmdbPersonCredit correctly`() {
        val credit = TmdbPersonCredit(
            id = 123,
            title = "Test Movie",
            releaseDate = "2020-06-15",
            posterPath = "/test.jpg",
            voteAverage = 7.5f
        )

        val preview = credit.toContentPreview()

        assert(preview.id == "tmdb-123")
        assert(preview.title == "Test Movie")
        assert(preview.year == 2020)
        assert(preview.releaseDate == "2020-06-15")
        assert(preview.coverUrl == "https://image.tmdb.org/t/p/w500/test.jpg")
        assert(preview.ratingImdb == 7.5f)
    }

    @Test
    fun `toContentPreview handles null fields gracefully`() {
        val credit = TmdbPersonCredit(
            id = 456,
            title = null,
            name = "A Person",
            releaseDate = null,
            posterPath = null
        )

        val preview = credit.toContentPreview()

        assert(preview.title == "A Person")
        assert(preview.year == null)
        assert(preview.coverUrl == null)
    }

    @Test
    fun `toContentPreview uses firstAirDate when releaseDate is null`() {
        val credit = TmdbPersonCredit(
            id = 789,
            title = "TV Show",
            firstAirDate = "2019-03-01",
            posterPath = null
        )

        val preview = credit.toContentPreview()

        assert(preview.releaseDate == "2019-03-01")
        assert(preview.year == 2019)
    }
}
