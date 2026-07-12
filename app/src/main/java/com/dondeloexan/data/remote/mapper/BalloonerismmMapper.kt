package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.ImdbCountryProviders
import com.dondeloexan.data.remote.dto.ImdbMovieDetailDto
import com.dondeloexan.data.remote.dto.ImdbSearchResult
import com.dondeloexan.data.remote.dto.ImdbTvDetailDto
import com.dondeloexan.data.remote.dto.OmdbDetailResponse
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.StreamingAvailability

fun ImdbMovieDetailDto.toDomain(
    omdbRating: OmdbDetailResponse?,
    platforms: List<StreamingAvailability>,
    externalLinks: ExternalLinks? = null
): Content = Content(
    id = "imdb-$id",
    source = ContentSource.IMDB,
    imdbId = id,
    title = title.orEmpty(),
    originalTitle = originalTitle,
    type = ContentType.MOVIE,
    year = releaseDate?.substringBefore("-")?.toIntOrNull(),
    releaseDate = releaseDate,
    durationMinutes = runtime,
    ratingImdb = omdbRating?.imdbRating?.toFloatOrNull(),
    ratingTmdb = voteAverage,
    ratingRt = omdbRating?.ratings
        ?.find { it.source == "Rotten Tomatoes" }
        ?.value?.removeSuffix("%")?.toIntOrNull(),
    ratingMetacritic = metascore ?: omdbRating?.metascore?.toIntOrNull(),
    synopsis = overview,
    coverUrl = posterPath,
    backdropUrl = backdropPath,
    productionCompanies = productionCompanies?.map { it.name.orEmpty() }.orEmpty(),
    genres = genres?.map { it.name }.orEmpty(),
    countries = productionCountries?.map { it.name.orEmpty() }.orEmpty(),
    streamingPlatforms = platforms,
    externalLinks = externalLinks,
    lastCachedAt = System.currentTimeMillis()
)

fun ImdbTvDetailDto.toDomain(
    omdbRating: OmdbDetailResponse?,
    platforms: List<StreamingAvailability>,
    externalLinks: ExternalLinks? = null
): Content = Content(
    id = "imdb-$id",
    source = ContentSource.IMDB,
    imdbId = id,
    title = name.orEmpty(),
    originalTitle = originalName,
    type = ContentType.SERIES,
    year = firstAirDate?.substringBefore("-")?.toIntOrNull(),
    releaseDate = firstAirDate,
    durationMinutes = episodeRunTime?.firstOrNull(),
    totalEpisodes = numberOfEpisodes,
    ratingImdb = omdbRating?.imdbRating?.toFloatOrNull(),
    ratingTmdb = voteAverage,
    ratingRt = omdbRating?.ratings
        ?.find { it.source == "Rotten Tomatoes" }
        ?.value?.removeSuffix("%")?.toIntOrNull(),
    ratingMetacritic = omdbRating?.metascore?.toIntOrNull(),
    synopsis = overview,
    coverUrl = posterPath,
    backdropUrl = backdropPath,
    productionCompanies = productionCompanies?.map { it.name.orEmpty() }.orEmpty(),
    genres = genres?.map { it.name }.orEmpty(),
    countries = productionCountries?.map { it.name.orEmpty() }.orEmpty(),
    streamingPlatforms = platforms,
    externalLinks = externalLinks,
    lastCachedAt = System.currentTimeMillis()
)

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
