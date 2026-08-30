package com.dondeloexan.data.remote.mapper

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