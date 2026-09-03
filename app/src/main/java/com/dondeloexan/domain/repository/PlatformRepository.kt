package com.dondeloexan.domain.repository

import kotlinx.coroutines.flow.Flow

interface PlatformRepository {
    val activePlatforms: Flow<Set<String>>
    suspend fun toggle(name: String)
    suspend fun sanitizeInvalid()
}
