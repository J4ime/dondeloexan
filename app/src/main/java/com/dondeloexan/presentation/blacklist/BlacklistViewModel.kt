package com.dondeloexan.presentation.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.domain.model.BlacklistItem
import com.dondeloexan.domain.repository.BlacklistRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlacklistViewModel(
    private val repository: BlacklistRepository,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    val items: StateFlow<List<BlacklistItem>> = repository.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun remove(item: BlacklistItem) {
        viewModelScope.launch {
            repository.remove(item.contentId, item.title)
            feedbackManager.emit("${item.title} desbloqueado")
        }
    }
}
