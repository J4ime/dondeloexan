package com.dondeloexan.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val movies: List<BackupMovie> = emptyList(),
    val tvShows: List<BackupTvShow> = emptyList(),
    val tvShowProgress: List<BackupProgress> = emptyList(),
    val searchHistory: List<BackupSearchEntry> = emptyList(),
    val userPlatforms: List<BackupPlatform> = emptyList()
)

@Serializable
data class BackupMovie(
    val filmAffinityId: Int? = null,
    val tmdbId: Int? = null,
    val title: String,
    val posterUrl: String? = null,
    val ratingFa: Float? = null,
    val ratingTmdb: Float? = null,
    val ratingImdb: Float? = null,
    val status: String = "POR_VER",
    val liked: Boolean = false,
    val addedAt: Long
)

@Serializable
data class BackupTvShow(
    val filmAffinityId: Int? = null,
    val tmdbId: Int? = null,
    val title: String,
    val posterUrl: String? = null,
    val ratingFa: Float? = null,
    val ratingTmdb: Float? = null,
    val ratingImdb: Float? = null,
    val status: String = "POR_VER",
    val totalEpisodes: Int? = null,
    val addedAt: Long
)

@Serializable
data class BackupProgress(
    val tvShowId: Long,
    val season: Int,
    val episode: Int,
    val watchedAt: Long
)

@Serializable
data class BackupSearchEntry(
    val query: String,
    val searchedAt: Long
)

@Serializable
data class BackupPlatform(
    val platformName: String,
    val isActive: Boolean = true
)
