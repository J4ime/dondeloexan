package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tv_shows")
data class TvShowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content_id") val contentId: String? = null,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int? = null,
    @ColumnInfo(name = "imdb_id") val imdbId: String? = null,
    val title: String,
    val year: Int? = null,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "rating_tmdb") val ratingTmdb: Float? = null,
    @ColumnInfo(name = "rating_imdb") val ratingImdb: Float? = null,
    val status: WatchStatus = WatchStatus.POR_VER,
    val liked: Boolean = false,
    @ColumnInfo(name = "total_episodes") val totalEpisodes: Int? = null,
    @ColumnInfo(name = "streaming_platforms") val streamingPlatforms: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "next_episode_air_date") val nextEpisodeAirDate: String? = null,
    @ColumnInfo(name = "next_episode_number") val nextEpisodeNumber: Int? = null,
    @ColumnInfo(name = "next_episode_season") val nextEpisodeSeasonNumber: Int? = null,
    @ColumnInfo(name = "series_status") val seriesStatus: String? = null,
    @ColumnInfo(name = "in_production") val inProduction: Boolean? = null,
    @ColumnInfo(name = "num_seasons") val numberOfSeasons: Int? = null,
    @ColumnInfo(name = "last_watched_at") val lastWatchedAt: Long? = null,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null
)
