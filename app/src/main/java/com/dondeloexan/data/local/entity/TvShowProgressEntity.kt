package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tv_show_progress",
    foreignKeys = [
        ForeignKey(
            entity = TvShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["tv_show_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tv_show_id")]
)
data class TvShowProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tv_show_id") val tvShowId: Long,
    val season: Int,
    val episode: Int,
    @ColumnInfo(name = "watched_at") val watchedAt: Long = System.currentTimeMillis()
)
