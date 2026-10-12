package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

/**
 * Géneros de TMDB que no son ficción: documental (99, incluye los "making of"),
 * informativos (10763), telerrealidad (10764) y programas de entrevistas (10767).
 * La filmografía de una persona solo debe mostrar películas y series de ficción.
 */
private val NON_FICTION_GENRE_IDS = setOf(99, 10763, 10764, 10767)

/**
 * true si el crédito es una película o serie de ficción. Si TMDB no informa de
 * géneros se conserva el crédito para no descartarlo por falta de dato.
 */
fun TmdbPersonCredit.isFictionFilmographyEntry(): Boolean =
    genreIds.isNullOrEmpty() || genreIds.none { it in NON_FICTION_GENRE_IDS }

fun TmdbPersonCredit.toContentPreview(forceType: ContentType? = null): ContentPreview = ContentPreview(
    id = "tmdb-$id",
    source = ContentSource.TMDB,
    tmdbId = id,
    title = title ?: name ?: "Sin título",
    type = forceType ?: if (mediaType == "tv") ContentType.SERIES else ContentType.MOVIE,
    year = (releaseDate ?: firstAirDate)?.substringBefore("-")?.toIntOrNull(),
    releaseDate = releaseDate ?: firstAirDate,
    coverUrl = posterPath?.let { "$TMDB_IMAGE_BASE$it" },
    ratingImdb = voteAverage,
    directors = emptyList(),
    streamingPlatforms = emptyList()
)
