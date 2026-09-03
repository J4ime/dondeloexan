package com.dondeloexan.domain.model

data class MovieItem(
    val id: Long,
    val contentId: String? = null,
    val tmdbId: Int? = null,
    val title: String,
    val year: Int? = null,
    val releaseDate: String? = null,
    val posterUrl: String? = null,
    val ratingImdb: Float? = null,
    val isLiked: Boolean = false,
    val isWatched: Boolean = false,
    val watchedAt: Long? = null,
    val streamingPlatforms: List<StreamingAvailability> = emptyList()
)
