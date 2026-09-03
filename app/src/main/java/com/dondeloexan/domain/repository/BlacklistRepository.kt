package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.BlacklistItem
import kotlinx.coroutines.flow.Flow

interface BlacklistRepository {
    val items: Flow<List<BlacklistItem>>
    suspend fun remove(contentId: String, title: String)
}
