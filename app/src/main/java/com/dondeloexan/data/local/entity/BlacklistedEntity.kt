package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blacklist")
data class BlacklistedEntity(
    @PrimaryKey @ColumnInfo(name = "content_id") val contentId: String,
    val title: String,
    val type: String,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
