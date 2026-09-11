package com.dondeloexan.domain.model

/**
 * Nota de un episodio concreto para el gráfico de ratings de una serie
 * (datos de SeriesGraph, métrica IMDb).
 */
data class EpisodeRating(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String? = null,
    val airDate: String? = null,
    val imdbRating: Double? = null,
    val imdbVotes: Int? = null
)
