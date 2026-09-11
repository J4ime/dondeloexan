package com.dondeloexan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeriesGraphSeasonRatingsDto(
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<SeriesGraphEpisodeRatingDto> = emptyList()
)

@Serializable
data class SeriesGraphEpisodeRatingDto(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("imdb_votes") val imdbVotes: Int? = null,
    @SerialName("community_avg") val communityAvg: Double? = null
)
