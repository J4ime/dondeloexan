package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.OmdbDetailResponse

data class ExternalRatings(
    val imdbId: String?,
    val ratingImdb: Float?,
    val ratingRt: Int?,
    val ratingMetacritic: Int?,
    val imdbVotes: String?
)

fun OmdbDetailResponse.toRatings(): ExternalRatings = ExternalRatings(
    imdbId = imdbID,
    ratingImdb = imdbRating?.toFloatOrNull(),
    ratingRt = ratings?.find { it.source == "Rotten Tomatoes" }
        ?.value?.removeSuffix("%")?.toIntOrNull(),
    ratingMetacritic = metascore?.toIntOrNull(),
    imdbVotes = imdbVotes
)
