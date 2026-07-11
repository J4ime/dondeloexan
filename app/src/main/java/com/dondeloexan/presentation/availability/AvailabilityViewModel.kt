package com.dondeloexan.presentation.availability

import android.content.Context
import androidx.lifecycle.ViewModel
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class AvailabilityViewModel(context: Context) : ViewModel() {

    private val dataStore = UserPreferencesDataStore(context)

    val selectedTypes: StateFlow<Set<String>> = dataStore.preferredAvailabilityTypes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggle(type: String) {
        viewModelScope.launch {
            dataStore.toggleAvailabilityType(type)
        }
    }
}
