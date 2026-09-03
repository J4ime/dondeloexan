package com.dondeloexan.domain.repository

import kotlinx.coroutines.flow.Flow

interface AvailabilityRepository {
    val selectedTypes: Flow<Set<String>>
    suspend fun toggle(type: String)
}
