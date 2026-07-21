package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content_id") val contentId: String? = null,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int? = null,
    @ColumnInfo(name = "imdb_id") val imdbId: String? = null,
    val title: String,
    val year: Int? = null,
    @ColumnInfo(name = "release_date") val releaseDate: String? = null,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "rating_tmdb") val ratingTmdb: Float? = null,
    @ColumnInfo(name = "rating_imdb") val ratingImdb: Float? = null,
    @ColumnInfo(name = "certification") val certification: String? = null,
    val status: WatchStatus = WatchStatus.POR_VER,
    val liked: Boolean = false,
    @ColumnInfo(name = "streaming_platforms") val streamingPlatforms: String? = null,
    @ColumnInfo(name = "watched_at") val watchedAt: Long? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_refreshed_at") val lastRefreshedAt: Long? = null
)
