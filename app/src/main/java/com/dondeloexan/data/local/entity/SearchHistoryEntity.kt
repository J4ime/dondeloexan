package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    @ColumnInfo(name = "searched_at") val searchedAt: Long = System.currentTimeMillis()
)
