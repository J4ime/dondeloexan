package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.TmdbCollectionPartDto
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

    @Test
    fun `toContentPreview maps TmdbCollectionPartDto with title`() {
        val dto = TmdbCollectionPartDto(
            id = 101,
            title = "The Godfather",
            posterPath = "/poster.jpg",
            releaseDate = "1972-03-24",
            voteAverage = 8.7f,
            voteCount = 5000
        )

        val preview = dto.toContentPreview()

        assert(preview.id == "tmdb-101")
        assert(preview.title == "The Godfather")
        assert(preview.type == com.dondeloexan.domain.model.ContentType.MOVIE)
        assert(preview.year == 1972)
        assert(preview.releaseDate == "1972-03-24")
        assert(preview.coverUrl == "https://image.tmdb.org/t/p/w500/poster.jpg")
        assert(preview.ratingImdb == 8.7f)
        assert(preview.voteCount == 5000)
    }

    @Test
    fun `toContentPreview uses name when title is null for collection part`() {
        val dto = TmdbCollectionPartDto(
            id = 201,
            title = null,
            name = "The Godfather Part II",
            firstAirDate = "1974-12-20",
            voteAverage = 9.0f
        )

        val preview = dto.toContentPreview()

        assert(preview.title == "The Godfather Part II")
        assert(preview.year == 1974)
        assert(preview.releaseDate == "1974-12-20")
        assert(preview.ratingImdb == 9.0f)
    }

    @Test
    fun `toContentPreview uses firstAirDate when releaseDate is null for collection part`() {
        val dto = TmdbCollectionPartDto(
            id = 301,
            title = "Test",
            releaseDate = null,
            firstAirDate = "2024-01-15"
        )

        val preview = dto.toContentPreview()

        assert(preview.releaseDate == "2024-01-15")
        assert(preview.year == 2024)
    }

    @Test
    fun `toContentPreview returns null coverUrl when posterPath is null for collection part`() {
        val dto = TmdbCollectionPartDto(
            id = 401,
            title = "Test",
            posterPath = null
        )

        val preview = dto.toContentPreview()

        assert(preview.coverUrl == null)
    }
}
