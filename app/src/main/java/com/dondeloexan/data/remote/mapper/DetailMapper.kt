package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.ImdbDetailSeasonDto
import com.dondeloexan.data.remote.dto.ImdbEpisodeDto
import com.dondeloexan.data.remote.dto.ImdbSeasonDetailDto
import com.dondeloexan.data.remote.dto.TmdbEpisodeDto
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.domain.model.detail.Episode
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail

fun TmdbSeasonDto.toSeason(): Season = Season(
    seasonNumber = seasonNumber,
    name = name,
    episodeCount = episodeCount,
    airDate = airDate,
    overview = overview,
    posterPath = posterPath,
    id = id
)

fun TmdbEpisodeDto.toEpisode(): Episode = Episode(
    episodeNumber = episodeNumber,
    name = name,
    overview = overview,
    airDate = airDate,
    stillPath = stillPath,
    voteAverage = voteAverage,
    seasonNumber = seasonNumber,
    episodeType = episodeType
)

fun TmdbTvSeasonDetailDto.toSeasonDetail(): SeasonDetail = SeasonDetail(
    seasonNumber = seasonNumber,
    episodes = episodes.map { it.toEpisode() },
    name = name,
    overview = overview,
    airDate = airDate
)

fun ImdbDetailSeasonDto.toSeason(): Season = Season(
    seasonNumber = seasonNumber ?: 0,
    name = label ?: "Temporada ${seasonNumber ?: 0}",
    episodeCount = 0,
    id = null
)

fun ImdbEpisodeDto.toEpisode(): Episode = Episode(
    episodeNumber = episodeNumber ?: 0,
    name = name.orEmpty(),
    overview = overview,
    airDate = airDate,
    stillPath = stillPath,
    voteAverage = voteAverage,
    seasonNumber = seasonNumber ?: 0
)

fun ImdbSeasonDetailDto.toSeasonDetail(): SeasonDetail = SeasonDetail(
    seasonNumber = seasonNumber ?: 0,
    episodes = episodes.map { it.toEpisode() },
    name = name,
    overview = overview,
    airDate = airDate
)