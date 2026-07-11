package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.ImdbCountryProviders
import com.dondeloexan.data.remote.dto.ImdbSearchResult
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.StreamingAvailability

fun ImdbSearchResult.toContentPreview(): ContentPreview = ContentPreview(
    id = "imdb-$id",
    source = ContentSource.IMDB,
    title = title ?: name.orEmpty(),
    type = if (mediaType == "tv") ContentType.SERIES else ContentType.MOVIE,
    year = releaseDate?.substringBefore("-")?.toIntOrNull()
        ?: firstAirDate?.substringBefore("-")?.toIntOrNull(),
    releaseDate = releaseDate ?: firstAirDate,
    coverUrl = posterPath,
    ratingImdb = voteAverage,
    voteCount = voteCount,
    isAdult = adult
)

fun ImdbCountryProviders.toStreamingAvailability(): List<StreamingAvailability> {
    return listOfNotNull(
        flatrate?.map {
            StreamingAvailability(
                platformName = it.providerName.orEmpty(),
                platformId = it.providerId,
                logoUrl = it.logoPath,
                availabilityType = AvailabilityType.SUBSCRIPTION
            )
        },
        rent?.map {
            StreamingAvailability(
                platformName = it.providerName.orEmpty(),
                platformId = it.providerId,
                logoUrl = it.logoPath,
                availabilityType = AvailabilityType.RENT
            )
        },
        buy?.map {
            StreamingAvailability(
                platformName = it.providerName.orEmpty(),
                platformId = it.providerId,
                logoUrl = it.logoPath,
                availabilityType = AvailabilityType.BUY
            )
        },
        ads?.map {
            StreamingAvailability(
                platformName = it.providerName.orEmpty(),
                platformId = it.providerId,
                logoUrl = it.logoPath,
                availabilityType = AvailabilityType.ADS
            )
        },
        free?.map {
            StreamingAvailability(
                platformName = it.providerName.orEmpty(),
                platformId = it.providerId,
                logoUrl = it.logoPath,
                availabilityType = AvailabilityType.FREE
            )
        }
    ).flatten()
}
