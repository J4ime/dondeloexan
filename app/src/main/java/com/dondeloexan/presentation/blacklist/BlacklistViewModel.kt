package com.dondeloexan.presentation.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.entity.BlacklistedEntity
import com.dondeloexan.presentation.feedback.FeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlacklistViewModel(
    private val blacklistDao: BlacklistDao,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    val items: StateFlow<List<BlacklistedEntity>> = blacklistDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun remove(item: BlacklistedEntity) {
        viewModelScope.launch {
            blacklistDao.deleteById(item.contentId)
            feedbackManager.emit("${item.title} desbloqueado")
        }
    }
}
