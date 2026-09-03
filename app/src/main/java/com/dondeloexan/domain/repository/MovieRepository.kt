package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.MovieItem
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    val pending: Flow<List<MovieItem>>
    val watched: Flow<List<MovieItem>>
    val favorites: Flow<List<MovieItem>>
    suspend fun delete(id: Long)
    suspend fun toggleFavorite(id: Long): Boolean
    suspend fun toggleWatched(id: Long): Boolean
    suspend fun refreshPlatforms()
}
