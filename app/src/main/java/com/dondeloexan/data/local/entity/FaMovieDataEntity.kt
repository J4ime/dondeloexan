package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fa_movie_data")
data class FaMovieDataEntity(
    @PrimaryKey @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "fa_id") val faId: Int?,
    @ColumnInfo(name = "fa_rating") val faRating: Float?,
    @ColumnInfo(name = "platform_releases_json") val platformReleasesJson: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis()
)
