package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.entity.BlacklistedEntity
import com.dondeloexan.domain.model.BlacklistItem
import com.dondeloexan.domain.repository.BlacklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlacklistRepositoryImpl(
    private val blacklistDao: BlacklistDao
) : BlacklistRepository {

    override val items: Flow<List<BlacklistItem>> =
        blacklistDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun remove(contentId: String, title: String) {
        blacklistDao.deleteById(contentId)
    }

    private fun BlacklistedEntity.toDomain(): BlacklistItem =
        BlacklistItem(contentId = contentId, title = title, type = type)
}
