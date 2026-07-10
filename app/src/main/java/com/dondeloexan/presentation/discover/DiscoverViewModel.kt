package com.dondeloexan.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.repository.DiscoverRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    private val userPlatformDao: UserPlatformDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Initial)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    val activePlatforms: StateFlow<Set<String>> = userPlatformDao.getActiveFlow()
        .map { it.map { p -> p.platformName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _uiState.value = DiscoverUiState.Initial
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            _uiState.value = DiscoverUiState.Loading

            try {
                discoverRepository.search(query).collect { result ->
                    when (result) {
                        is DataResult.Loading -> _uiState.value = DiscoverUiState.Loading
                        is DataResult.Success -> {
                            _uiState.value = if (result.data.isEmpty()) {
                                DiscoverUiState.Empty(query)
                            } else {
                                DiscoverUiState.Success(result.data)
                            }
                        }
                        is DataResult.Error -> {
                            AppLogger.e("DiscoverVM", "Search error", result.exception)
                            _uiState.value = DiscoverUiState.Error(
                                result.exception.message ?: "Error al buscar"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DiscoverUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
        searchJob?.cancel()
        _uiState.value = DiscoverUiState.Initial
    }

    fun onRetry() {
        onSearchQueryChanged(_searchQuery.value)
    }
}

sealed interface DiscoverUiState {
    data object Initial : DiscoverUiState
    data object Loading : DiscoverUiState
    data class Empty(val query: String) : DiscoverUiState
    data class Error(val message: String) : DiscoverUiState
    data class Success(val results: List<ContentPreview>) : DiscoverUiState
}
