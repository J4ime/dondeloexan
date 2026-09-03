package com.dondeloexan.presentation.platforms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.domain.repository.PlatformRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlatformsViewModel(
    private val repository: PlatformRepository
) : ViewModel() {

    val activePlatforms: StateFlow<Set<String>> = repository.activePlatforms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch { repository.sanitizeInvalid() }
    }

    fun togglePlatform(platform: String) {
        viewModelScope.launch { repository.toggle(platform) }
    }
}
