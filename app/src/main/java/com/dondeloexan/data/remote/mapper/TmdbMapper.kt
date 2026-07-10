package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.OmdbDetailResponse
import com.dondeloexan.data.remote.dto.TmdbCountryProviders
import com.dondeloexan.data.remote.dto.TmdbCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbMovieDto
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResult
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.StreamingAvailability

fun TmdbMovieDto.toDomain(
    omdbRating: OmdbDetailResponse?,
    platforms: List<StreamingAvailability>,
    credits: TmdbCreditsResponse? = null
): Content = Content(
    id = "tmdb-$id",
    source = ContentSource.TMDB,
    tmdbId = id,
    imdbId = imdbId ?: omdbRating?.imdbID,
    filmAffinityId = null,
    title = title,
    originalTitle = originalTitle,
    type = ContentType.MOVIE,
    year = releaseDate?.substringBefore("-")?.toIntOrNull(),
    durationMinutes = runtime,
    ratingTmdb = voteAverage,
    ratingImdb = omdbRating?.imdbRating?.toFloatOrNull(),
    ratingRt = omdbRating?.ratings
        ?.find { it.source == "Rotten Tomatoes" }
        ?.value?.removeSuffix("%")?.toIntOrNull(),
    ratingMetacritic = omdbRating?.metascore?.toIntOrNull(),
    synopsis = overview,
    coverUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
    directors = credits?.crew?.filter { it.job == "Director" }?.map { it.name }.orEmpty(),
    writers = credits?.crew?.filter { it.job == "Screenplay" }?.map { it.name }.orEmpty(),
    cast = credits?.cast?.map { it.name }.orEmpty(),
    music = credits?.crew?.filter { it.job == "Original Music Composer" }?.map { it.name }.orEmpty(),
    cinematography = credits?.crew?.filter { it.job == "Director of Photography" }?.map { it.name }.orEmpty(),
    productionCompanies = productionCompanies?.map { it.name }.orEmpty(),
    genres = genres?.map { it.name }.orEmpty(),
    countries = productionCountries?.map { it.name }.orEmpty(),
    streamingPlatforms = platforms,
    lastCachedAt = System.currentTimeMillis()
)

fun TmdbTvDetailDto.toDomain(
    omdbRating: OmdbDetailResponse?,
    platforms: List<StreamingAvailability>,
    credits: TmdbCreditsResponse? = null
): Content = Content(
    id = "tmdb-$id",
    source = ContentSource.TMDB,
    tmdbId = id,
    imdbId = omdbRating?.imdbID,
    filmAffinityId = null,
    title = name,
    originalTitle = originalName,
    type = ContentType.SERIES,
    year = firstAirDate?.substringBefore("-")?.toIntOrNull(),
    durationMinutes = episodeRunTime?.firstOrNull(),
    ratingTmdb = voteAverage,
    ratingImdb = omdbRating?.imdbRating?.toFloatOrNull(),
    ratingRt = omdbRating?.ratings
        ?.find { it.source == "Rotten Tomatoes" }
        ?.value?.removeSuffix("%")?.toIntOrNull(),
    ratingMetacritic = omdbRating?.metascore?.toIntOrNull(),
    synopsis = overview,
    coverUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
    directors = credits?.crew?.filter { it.job == "Director" }?.map { it.name }.orEmpty(),
    writers = credits?.crew?.filter { it.job == "Screenplay" }?.map { it.name }.orEmpty(),
    cast = credits?.cast?.map { it.name }.orEmpty(),
    music = credits?.crew?.filter { it.job == "Original Music Composer" }?.map { it.name }.orEmpty(),
    cinematography = credits?.crew?.filter { it.job == "Director of Photography" }?.map { it.name }.orEmpty(),
    productionCompanies = productionCompanies?.map { it.name }.orEmpty(),
    genres = genres?.map { it.name }.orEmpty(),
    countries = productionCountries?.map { it.name }.orEmpty(),
    streamingPlatforms = platforms,
    lastCachedAt = System.currentTimeMillis()
)

fun TmdbMultiSearchResult.toContentPreview(): ContentPreview = ContentPreview(
    id = "tmdb-$id",
    source = ContentSource.TMDB,
    title = title ?: name.orEmpty(),
    type = if (mediaType == "tv") ContentType.SERIES else ContentType.MOVIE,
    year = releaseDate?.substringBefore("-")?.toIntOrNull()
        ?: firstAirDate?.substringBefore("-")?.toIntOrNull(),
    coverUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    ratingFa = voteAverage?.let { it / 2f }
)

fun TmdbCountryProviders.toStreamingAvailability(): List<StreamingAvailability> {
    return listOfNotNull(
        flatrate?.map {
            StreamingAvailability(
                platformName = it.providerName,
                platformId = it.providerId,
                logoUrl = it.logoPath?.let { path ->
                    "https://image.tmdb.org/t/p/w92$path"
                },
                availabilityType = AvailabilityType.SUBSCRIPTION
            )
        },
        rent?.map {
            StreamingAvailability(
                platformName = it.providerName,
                platformId = it.providerId,
                logoUrl = it.logoPath?.let { path ->
                    "https://image.tmdb.org/t/p/w92$path"
                },
                availabilityType = AvailabilityType.RENT
            )
        },
        buy?.map {
            StreamingAvailability(
                platformName = it.providerName,
                platformId = it.providerId,
                logoUrl = it.logoPath?.let { path ->
                    "https://image.tmdb.org/t/p/w92$path"
                },
                availabilityType = AvailabilityType.BUY
            )
        },
        ads?.map {
            StreamingAvailability(
                platformName = it.providerName,
                platformId = it.providerId,
                logoUrl = it.logoPath?.let { path ->
                    "https://image.tmdb.org/t/p/w92$path"
                },
                availabilityType = AvailabilityType.ADS
            )
        },
        free?.map {
            StreamingAvailability(
                platformName = it.providerName,
                platformId = it.providerId,
                logoUrl = it.logoPath?.let { path ->
                    "https://image.tmdb.org/t/p/w92$path"
                },
                availabilityType = AvailabilityType.FREE
            )
        }
    ).flatten()
}
