package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

fun TmdbPersonCredit.toContentPreview(): ContentPreview = ContentPreview(
    id = "tmdb-$id",
    source = ContentSource.TMDB,
    tmdbId = id,
    title = title ?: name ?: "Sin título",
    type = if (mediaType == "tv") ContentType.SERIES else ContentType.MOVIE,
    year = (releaseDate ?: firstAirDate)?.substringBefore("-")?.toIntOrNull(),
    releaseDate = releaseDate ?: firstAirDate,
    coverUrl = posterPath?.let { "$TMDB_IMAGE_BASE$it" },
    ratingImdb = voteAverage,
    directors = emptyList(),
    streamingPlatforms = emptyList()
)
