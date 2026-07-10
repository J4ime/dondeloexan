package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content_id") val contentId: String? = null,
    @ColumnInfo(name = "film_affinity_id") val filmAffinityId: Int? = null,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int? = null,
    val title: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "rating_fa") val ratingFa: Float? = null,
    @ColumnInfo(name = "rating_tmdb") val ratingTmdb: Float? = null,
    @ColumnInfo(name = "rating_imdb") val ratingImdb: Float? = null,
    val status: WatchStatus = WatchStatus.POR_VER,
    val liked: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
