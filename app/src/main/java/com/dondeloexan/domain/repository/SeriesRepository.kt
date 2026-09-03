package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.SeriesItem
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {
    val all: Flow<List<SeriesItem>>
    suspend fun delete(id: Long)
    suspend fun toggleWatched(id: Long): Boolean
    suspend fun refreshData()
}
