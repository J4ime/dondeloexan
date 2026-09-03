package com.dondeloexan.data.sync

import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.data.local.entity.WatchStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SyncDtosMapperTest {

    private val userId = "uuid-123"

    @Test
    fun `MovieEntity se mapea a DTO de usuario con booleanos 0 o 1 y status`() {
        val entity = MovieEntity(
            id = 9, contentId = "c1", tmdbId = 5, imdbId = "tt1",
            title = "Los cazafantasmas", year = 1984, releaseDate = "1984-06-08",
            posterUrl = "poster", ratingTmdb = 8.0f, ratingImdb = 7.8f,
            certification = "PG", status = WatchStatus.YA_VISTA, liked = true,
            streamingPlatforms = "[]", watchedAt = 1000L, addedAt = 2000L,
            lastRefreshedAt = 3000L, faId = 42
        )

        val dto = entity.toUserMovieSyncDto(userId)

        assertEquals(userId, dto.userId)
        assertEquals("c1", dto.contentId)
        assertEquals("YA_VISTA", dto.status)
        assertEquals(1, dto.liked)
        assertEquals(1000L, dto.watchedAt)
        assertEquals(2000L, dto.addedAt)
        assertEquals(3000L, dto.lastRefreshedAt)
        assertEquals(42, dto.faId)
    }

    @Test
    fun `MovieEntity se mapea a fila de catalogo para el merge global`() {
        val entity = MovieEntity(
            id = 9, contentId = "c1", tmdbId = 5, imdbId = "tt1",
            title = "Los cazafantasmas", year = 1984, releaseDate = "1984-06-08",
            posterUrl = "poster", ratingTmdb = 8.0f, ratingImdb = 7.8f,
            certification = "PG", status = WatchStatus.YA_VISTA, liked = true,
            streamingPlatforms = "[x]", watchedAt = 1000L, addedAt = 2000L,
            lastRefreshedAt = 3000L, faId = 42
        )

        val row = entity.toCatalogMovieRow(now = 777L)

        assertEquals("c1", row.contentId)
        assertEquals("Los cazafantasmas", row.title)
        assertEquals(8.0f, row.ratingTmdb)
        assertEquals(3000L, row.updatedAt)
        assertNull(row.ratingFilmaffinity)
    }

    @Test
    fun `TvShowEntity con liked false e in_production false mapea a 0`() {
        val entity = TvShowEntity(
            id = 7, title = "Serie", status = WatchStatus.POR_VER,
            liked = false, totalEpisodes = 10, inProduction = false,
            numberOfSeasons = 2, releasedEpisodes = 5
        )

        val dto = entity.toUserTvShowSyncDto(userId)

        assertEquals(0, dto.liked)
        assertEquals(0, dto.inProduction)
        assertEquals(10, dto.totalEpisodes)
        assertEquals(2, dto.numberOfSeasons)
        assertEquals(5, dto.releasedEpisodes)
        assertEquals(userId, dto.userId)
    }

    @Test
    fun `TvShowEntity se mapea a fila de catalogo sin duplicar la relacion de usuario`() {
        val entity = TvShowEntity(
            id = 8, contentId = "s1", title = "Serie", status = WatchStatus.POR_VER,
            liked = true, totalEpisodes = 10, inProduction = true,
            numberOfSeasons = 2, releasedEpisodes = 5
        )

        val row = entity.toCatalogTvShowRow(now = 9L)

        assertEquals("s1", row.contentId)
        assertEquals(1, row.inProduction)
        assertEquals(2, row.numSeasons)
        assertEquals(9L, row.updatedAt)
    }

    @Test
    fun `UserPlatformEntity desactivada mapea is_active a 0`() {
        val dto = UserPlatformEntity("Netflix", isActive = false).toSyncDto(userId)
        assertEquals(0, dto.isActive)
        assertEquals(userId, dto.userId)
        assertEquals("Netflix", dto.platformName)
    }

    @Test
    fun `TvShowEntity con ficha tecnica completa mapea todos los campos de catalogo sin nulls`() {
        val entity = TvShowEntity(
            id = 1, contentId = "s1", tmdbId = 1396, imdbId = "tt0903747",
            title = "Breaking Bad", year = 2008, posterUrl = "p",
            ratingTmdb = 9.5f, ratingImdb = 9.4f, certification = "TV-MA",
            status = WatchStatus.POR_VER, liked = true, totalEpisodes = 62,
            streamingPlatforms = "[Netflix]", addedAt = 1000L,
            seriesStatus = "Ended", inProduction = false, numberOfSeasons = 5,
            releasedEpisodes = 62, lastRefreshedAt = 2000L,
            originalTitle = "Breaking Bad", releaseDate = "2008-01-20",
            spanishReleaseDate = "2008-01-20", tvReleaseDate = "2008-01-20",
            durationMinutes = 47, ratingRt = 96, ratingMetacritic = 98,
            ratingFilmaffinity = 8.9f, synopsis = "Un profesor con cáncer...",
            backdropUrl = "b", directors = "Vince Gilligan", writers = "Vince Gilligan",
            castJson = "[{\"name\":\"Bryan Cranston\"}]", music = "Dave Porter",
            cinematography = "Michael Slovis", productionCompanies = "AMC",
            genres = "Drama,Crimen", countries = "US", externalLinks = "{\"imdb\":\"tt0903747\"}"
        )

        val row = entity.toCatalogTvShowRow()

        assertEquals("s1", row.contentId)
        assertEquals("Breaking Bad", row.originalTitle)
        assertEquals("2008-01-20", row.releaseDate)
        assertEquals("2008-01-20", row.spanishReleaseDate)
        assertEquals("2008-01-20", row.tvReleaseDate)
        assertEquals(47, row.durationMinutes)
        assertEquals(96, row.ratingRt)
        assertEquals(98, row.ratingMetacritic)
        assertEquals(8.9f, row.ratingFilmaffinity)
        assertEquals("Un profesor con cáncer...", row.synopsis)
        assertEquals("b", row.backdropUrl)
        assertEquals("Vince Gilligan", row.directors)
        assertEquals("Vince Gilligan", row.writers)
        assertEquals("[{\"name\":\"Bryan Cranston\"}]", row.castJson)
        assertEquals("Dave Porter", row.music)
        assertEquals("Michael Slovis", row.cinematography)
        assertEquals("AMC", row.productionCompanies)
        assertEquals("Drama,Crimen", row.genres)
        assertEquals("US", row.countries)
        assertEquals("{\"imdb\":\"tt0903747\"}", row.externalLinks)
    }
}