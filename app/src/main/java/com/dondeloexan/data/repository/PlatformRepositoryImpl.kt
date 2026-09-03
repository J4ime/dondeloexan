package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.data.remote.TmdbProviderIds
import com.dondeloexan.domain.repository.PlatformRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlatformRepositoryImpl(
    private val userPlatformDao: UserPlatformDao
) : PlatformRepository {

    override val activePlatforms: Flow<Set<String>> =
        userPlatformDao.getActiveFlow().map { list -> list.map { it.platformName }.toSet() }

    override suspend fun toggle(name: String) {
        if (!TmdbProviderIds.isValidPlatform(name)) return
        val existing = userPlatformDao.getByName(name)
        if (existing != null) {
            userPlatformDao.upsert(existing.copy(isActive = !existing.isActive))
        } else {
            userPlatformDao.upsert(UserPlatformEntity(platformName = name, isActive = true))
        }
    }

    override suspend fun sanitizeInvalid() {
        val all = userPlatformDao.getAll()
        for (platform in all) {
            if (!TmdbProviderIds.isValidPlatform(platform.platformName) && platform.isActive) {
                userPlatformDao.upsert(platform.copy(isActive = false))
            }
        }
    }
}
