package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.ImdbCountryProviders
import com.dondeloexan.data.remote.dto.ImdbDetailSeasonDto
import com.dondeloexan.data.remote.dto.ImdbEpisodeDto
import com.dondeloexan.data.remote.dto.ImdbMovieDetailDto
import com.dondeloexan.data.remote.dto.ImdbSearchResult
import com.dondeloexan.data.remote.dto.ImdbSeasonDetailDto
import com.dondeloexan.data.remote.dto.ImdbTvDetailDto
import com.dondeloexan.data.remote.dto.OmdbDetailResponse
import com.dondeloexan.data.remote.dto.TmdbEpisodeDto
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
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
    certification = certificate?.rating,
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
    certification = certificate?.rating,
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
    imdbId = id,
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

fun ImdbDetailSeasonDto.toTmdbSeasonDto(): TmdbSeasonDto = TmdbSeasonDto(
    seasonNumber = seasonNumber ?: 0,
    airDate = null,
    episodeCount = 0,
    id = 0,
    name = label ?: "Temporada ${seasonNumber ?: 0}",
    overview = null,
    posterPath = null
)

fun ImdbEpisodeDto.toTmdb(): TmdbEpisodeDto = TmdbEpisodeDto(
    airDate = airDate,
    episodeNumber = episodeNumber ?: 0,
    id = 0,
    name = name.orEmpty(),
    overview = overview,
    stillPath = stillPath,
    voteAverage = voteAverage,
    seasonNumber = seasonNumber ?: 0
)

fun ImdbSeasonDetailDto.toTmdb(): TmdbTvSeasonDetailDto = TmdbTvSeasonDetailDto(
    internalId = internalId,
    airDate = airDate,
    episodes = episodes.map { it.toTmdb() },
    name = name,
    overview = overview,
    id = null,
    seasonNumber = seasonNumber ?: 0
)
