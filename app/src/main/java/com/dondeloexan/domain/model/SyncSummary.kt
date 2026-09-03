package com.dondeloexan.domain.model

data class SyncSummary(
    val movies: Int,
    val tvShows: Int,
    val tvShowProgress: Int,
    val searchHistory: Int,
    val userPlatforms: Int,
    val blacklist: Int,
    val criticReviews: Int,
    val faMovieData: Int
) {
    val total: Int
        get() = movies + tvShows + tvShowProgress + searchHistory +
            userPlatforms + blacklist + criticReviews + faMovieData
}
