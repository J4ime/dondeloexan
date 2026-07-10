package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.FaRapidItemDetail
import com.dondeloexan.data.remote.dto.FaRapidSearchItem
import com.dondeloexan.data.remote.dto.FaRapidStreaming
import com.dondeloexan.data.remote.dto.OmdbDetailResponse
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.StreamingAvailability

fun FaRapidSearchItem.toContentPreview(): ContentPreview = ContentPreview(
    id = "fa-$id",
    source = ContentSource.FILMAFFINITY,
    title = title,
    type = when (type) {
        "series" -> ContentType.SERIES
        else -> ContentType.MOVIE
    },
    year = year,
    coverUrl = posterUrl,
    ratingFa = rating,
    filmAffinityId = id
)

fun FaRapidItemDetail.toDomain(
    omdbRating: OmdbDetailResponse? = null,
    platforms: List<StreamingAvailability>? = null
): Content = Content(
    id = "fa-$id",
    source = ContentSource.FILMAFFINITY,
    filmAffinityId = id,
    tmdbId = null,
    imdbId = omdbRating?.imdbID,
    title = title?.local ?: title?.original ?: "",
    originalTitle = title?.original?.takeIf { it != title?.local },
    type = when (type) { "series" -> ContentType.SERIES; else -> ContentType.MOVIE },
    year = year,
    durationMinutes = durationMinutes,
    ratingFa = rating,
    ratingTmdb = null,
    ratingImdb = omdbRating?.imdbRating?.toFloatOrNull(),
    ratingRt = omdbRating?.ratings
        ?.find { it.source == "Rotten Tomatoes" }
        ?.value?.removeSuffix("%")?.toIntOrNull(),
    ratingMetacritic = omdbRating?.ratings
        ?.find { it.source == "Metacritic" }
        ?.value?.removeSuffix("/100")?.toIntOrNull(),
    synopsis = synopsis,
    coverUrl = posterUrl,
    directors = credits?.directors.orEmpty(),
    cast = credits?.cast?.mapNotNull { it.name }.orEmpty(),
    genres = genres.orEmpty(),
    countries = countries.orEmpty(),
    streamingPlatforms = platforms ?: streaming?.flatMap { it.toStreamingAvailability() }.orEmpty(),
    lastCachedAt = System.currentTimeMillis()
)

fun FaRapidStreaming.toStreamingAvailability(): List<StreamingAvailability> {
    val availabilityType = when (type) {
        "subscription" -> AvailabilityType.SUBSCRIPTION
        "rental" -> AvailabilityType.RENT
        "purchase" -> AvailabilityType.BUY
        "free" -> AvailabilityType.FREE
        "ads" -> AvailabilityType.ADS
        else -> return emptyList()
    }
    return providers.map { name ->
        StreamingAvailability(
            platformName = name,
            availabilityType = availabilityType
        )
    }
}
