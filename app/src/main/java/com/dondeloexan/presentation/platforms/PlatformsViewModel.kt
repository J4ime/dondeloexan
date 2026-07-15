package com.dondeloexan.presentation.platforms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.data.remote.TmdbProviderIds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlatformsViewModel(
    private val userPlatformDao: UserPlatformDao
) : ViewModel() {

    val activePlatforms: StateFlow<Set<String>> = userPlatformDao.getActiveFlow()
        .map { list -> list.map { it.platformName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            val all = userPlatformDao.getAll()
            for (platform in all) {
                if (!TmdbProviderIds.isValidPlatform(platform.platformName)) {
                    if (platform.isActive) {
                        userPlatformDao.upsert(platform.copy(isActive = false))
                    }
                }
            }
        }
    }

    fun togglePlatform(platform: String) {
        viewModelScope.launch {
            if (!TmdbProviderIds.isValidPlatform(platform)) return@launch
            val existing = userPlatformDao.getByName(platform)
            if (existing != null) {
                userPlatformDao.upsert(existing.copy(isActive = !existing.isActive))
            } else {
                userPlatformDao.upsert(UserPlatformEntity(platformName = platform, isActive = true))
            }
        }
    }
}
