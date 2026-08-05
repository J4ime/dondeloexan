package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.ImdbDetailSeasonDto
import com.dondeloexan.data.remote.dto.ImdbEpisodeDto
import com.dondeloexan.data.remote.dto.ImdbSeasonDetailDto
import com.dondeloexan.data.remote.dto.TmdbEpisodeDto
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import org.junit.jupiter.api.Test

class DetailMapperTest {

    @Test
    fun `TmdbSeasonDto toSeason maps all fields`() {
        val dto = TmdbSeasonDto(
            airDate = "2020-01-01",
            episodeCount = 10,
            id = 5,
            name = "Temporada 1",
            overview = "Desc",
            posterPath = "/poster.jpg",
            seasonNumber = 1
        )

        val season = dto.toSeason()

        assert(season.seasonNumber == 1)
        assert(season.name == "Temporada 1")
        assert(season.episodeCount == 10)
        assert(season.airDate == "2020-01-01")
        assert(season.overview == "Desc")
        assert(season.posterPath == "/poster.jpg")
        assert(season.id == 5)
    }

    @Test
    fun `TmdbEpisodeDto toEpisode maps all fields`() {
        val dto = TmdbEpisodeDto(
            airDate = "2020-01-05",
            episodeNumber = 2,
            id = 12,
            name = "Piloto",
            overview = "Sinopsis",
            stillPath = "/still.jpg",
            voteAverage = 7.8f,
            seasonNumber = 1,
            episodeType = "finale"
        )

        val episode = dto.toEpisode()

        assert(episode.episodeNumber == 2)
        assert(episode.name == "Piloto")
        assert(episode.overview == "Sinopsis")
        assert(episode.airDate == "2020-01-05")
        assert(episode.stillPath == "/still.jpg")
        assert(episode.voteAverage == 7.8f)
        assert(episode.seasonNumber == 1)
        assert(episode.episodeType == "finale")
    }

    @Test
    fun `TmdbTvSeasonDetailDto toSeasonDetail maps episodes`() {
        val dto = TmdbTvSeasonDetailDto(
            internalId = "id1",
            airDate = "2020-01-01",
            episodes = listOf(
                TmdbEpisodeDto(episodeNumber = 1, id = 1, name = "E1", seasonNumber = 1),
                TmdbEpisodeDto(episodeNumber = 2, id = 2, name = "E2", seasonNumber = 1)
            ),
            name = "Temporada 1",
            overview = "Desc",
            id = 3,
            seasonNumber = 1
        )

        val detail = dto.toSeasonDetail()

        assert(detail.seasonNumber == 1)
        assert(detail.episodes.size == 2)
        assert(detail.episodes[0].episodeNumber == 1)
        assert(detail.episodes[1].name == "E2")
    }

    @Test
    fun `ImdbDetailSeasonDto toSeason maps label and seasonNumber`() {
        val dto = ImdbDetailSeasonDto(seasonNumber = 2, label = "Segunda temporada")

        val season = dto.toSeason()

        assert(season.seasonNumber == 2)
        assert(season.name == "Segunda temporada")
    }

    @Test
    fun `ImdbEpisodeDto toEpisode maps fields with defaults`() {
        val dto = ImdbEpisodeDto(
            episodeNumber = 3,
            name = "E3",
            seasonNumber = 1
        )

        val episode = dto.toEpisode()

        assert(episode.episodeNumber == 3)
        assert(episode.name == "E3")
        assert(episode.seasonNumber == 1)
        assert(episode.episodeType == null)
    }

    @Test
    fun `ImdbSeasonDetailDto toSeasonDetail maps episodes`() {
        val dto = ImdbSeasonDetailDto(
            internalId = "x",
            seasonNumber = 1,
            episodes = listOf(
                ImdbEpisodeDto(episodeNumber = 1, name = "One", seasonNumber = 1),
                ImdbEpisodeDto(episodeNumber = 2, name = "Two", seasonNumber = 1)
            )
        )

        val detail = dto.toSeasonDetail()

        assert(detail.seasonNumber == 1)
        assert(detail.episodes.size == 2)
        assert(detail.episodes[1].name == "Two")
    }
}