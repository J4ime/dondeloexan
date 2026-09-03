package com.dondeloexan.presentation.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.domain.repository.AvailabilityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AvailabilityViewModel(
    private val repository: AvailabilityRepository
) : ViewModel() {

    val selectedTypes: StateFlow<Set<String>> = repository.selectedTypes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggle(type: String) {
        viewModelScope.launch { repository.toggle(type) }
    }
}
