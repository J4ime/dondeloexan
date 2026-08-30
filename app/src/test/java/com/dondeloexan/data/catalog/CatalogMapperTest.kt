package com.dondeloexan.data.catalog

import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.detail.Episode
import com.dondeloexan.domain.model.detail.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CatalogMapperTest {

    // ── Season ↔ CatalogSeasonRow ─────────────────────────────────────────────

    @Test
    fun `season round trip preserva todos los campos`() {
        val season = Season(
            seasonNumber = 3,
            name = "Temporada 3",
            episodeCount = 10,
            airDate = "2013-06-09",
            overview = "El descenso continúa",
            posterPath = "/winter.jpg",
            id = 12345
        )

        val row = season.toCatalogSeasonRow("tmdb-1399")

        assertEquals("tmdb-1399", row.contentId)
        assertEquals(3, row.seasonNumber)
        assertEquals("Temporada 3", row.name)
        assertEquals(10, row.episodeCount)
        assertEquals("2013-06-09", row.airDate)
        assertEquals("El descenso continúa", row.overview)
        assertEquals("/winter.jpg", row.posterPath)
        assertEquals(12345, row.tmdbSeasonId)

        val restored = row.toSeason()
        assertEquals(season, restored)
    }

    @Test
    fun `season sin opcionales usa defaults`() {
        val season = Season(seasonNumber = 1, name = "S1")
        val restored = season.toCatalogSeasonRow("tmdb-1399").toSeason()
        assertEquals(season, restored)
        assertNull(restored.id)
    }

    // ── Episode ↔ CatalogEpisodeRow ───────────────────────────────────────────

    @Test
    fun `episode round trip preserva todos los campos`() {
        val episode = Episode(
            episodeNumber = 2,
            name = "La noche fría",
            overview = "Los walkers llegan",
            airDate = "2010-11-14",
            stillPath = "/still.jpg",
            voteAverage = 8.2f,
            seasonNumber = 1,
            episodeType = "standard"
        )

        val row = episode.toCatalogEpisodeRow("tmdb-1402")

        assertEquals("tmdb-1402", row.contentId)
        assertEquals(1, row.seasonNumber)
        assertEquals(2, row.episodeNumber)
        assertEquals("La noche fría", row.name)
        assertEquals("Los walkers llegan", row.overview)
        assertEquals("2010-11-14", row.airDate)
        assertEquals("/still.jpg", row.stillPath)
        assertEquals(8.2f, row.voteAverage ?: 0f, 0.001f)
        assertEquals("standard", row.episodeType)

        val restored = row.toEpisode()
        assertEquals(episode, restored)
    }

    @Test
    fun `episode sin opcionales usa defaults`() {
        val episode = Episode(episodeNumber = 1, name = "E1", seasonNumber = 2)
        val restored = episode.toCatalogEpisodeRow("imdb-tt0000001").toEpisode()
        assertEquals(episode, restored)
        assertNull(restored.voteAverage)
    }

    // ── ContentPreview ↔ CatalogListRow ───────────────────────────────────────

    @Test
    fun `list row round trip preserva preview y orden`() {
        val preview = ContentPreview(
            id = "tmdb-603",
            source = ContentSource.TMDB,
            tmdbId = 603,
            imdbId = "tt0137523",
            title = "Fight Club",
            type = ContentType.MOVIE,
            year = 1999,
            releaseDate = "1999-10-15",
            coverUrl = "https://image.tmdb.org/t/p/w500/fightclub.jpg",
            directors = listOf("David Fincher"),
            ratingImdb = 8.8f,
            genres = listOf("Drama", "Suspense"),
            totalEpisodes = null,
            voteCount = 1000,
            isAdult = true
        )

        val row = preview.toCatalogListRow("collection-10", "collection", 1)

        assertEquals("collection-10", row.contentId)
        assertEquals("collection", row.listType)
        assertEquals(1, row.pos)
        assertEquals("tmdb-603", row.relatedContentId)

        val restored = row.toContentPreview()
        assertEquals(preview, restored)
    }

    @Test
    fun `list row sin related_data cae a preview por defecto`() {
        val row = CatalogListRow(
            contentId = "collection-5",
            listType = "collection",
            pos = 0,
            relatedContentId = "tmdb-278",
            relatedData = null
        )

        val preview = row.toContentPreview()

        assertEquals("tmdb-278", preview.id)
        assertEquals(ContentSource.TMDB, preview.source)
        assertEquals(ContentType.MOVIE, preview.type)
        assertEquals("", preview.title)
    }
}