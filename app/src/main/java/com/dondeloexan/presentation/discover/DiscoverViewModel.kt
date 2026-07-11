package com.dondeloexan.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Initial)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _filterByPlatforms = MutableStateFlow(true)
    val filterByPlatforms: StateFlow<Boolean> = _filterByPlatforms.asStateFlow()

    init {
        loadTrending()
    }

    val activePlatforms: StateFlow<Set<String>> = userPlatformDao.getActiveFlow()
        .map { it.map { p -> p.platformName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val likedIds: StateFlow<Set<String>> = combine(
        movieDao.getLiked().map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getLiked().map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { movieLiked, tvLiked -> movieLiked + tvLiked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val watchedIds: StateFlow<Set<String>> = combine(
        movieDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { movieWatched, tvWatched -> movieWatched + tvWatched }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            searchJob?.cancel()
            loadTrending()
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
                            val liked = likedIds.value
                            var filtered = result.data.filter { it.id !in liked }

                            if (_filterByPlatforms.value) {
                                val userPlatforms = activePlatforms.value
                                if (userPlatforms.isNotEmpty()) {
                                    filtered = filtered.filter { preview ->
                                        preview.streamingPlatforms.any { platform ->
                                            val normalized = platform.platformName
                                                .replace("+", "").replace(" ", "").lowercase()
                                            userPlatforms.any { userP ->
                                                val normalizedUser = userP
                                                    .replace("+", "").replace(" ", "").lowercase()
                                                normalized.contains(normalizedUser) ||
                                                    normalizedUser.contains(normalized)
                                            }
                                        }
                                    }
                                }
                            }

                            _uiState.value = if (filtered.isEmpty()) {
                                DiscoverUiState.Empty(query)
                            } else {
                                DiscoverUiState.Success(filtered)
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
                if (e is kotlinx.coroutines.CancellationException) return@launch
                AppLogger.e("DiscoverVM", "Search error", e)
                _uiState.value = DiscoverUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun onToggleFavorite(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                when (preview.type) {
                    com.dondeloexan.domain.model.ContentType.MOVIE -> {
                        val existing = movieDao.getByContentId(preview.id)
                        if (existing != null) {
                            val newLiked = !existing.liked
                            movieDao.update(existing.copy(liked = newLiked))
                            feedbackManager.emit(
                                if (newLiked) "Película añadida"
                                else "Película quitada"
                            )
                        } else {
                            movieDao.insert(
                                MovieEntity(
                                    contentId = preview.id,
                                    tmdbId = preview.tmdbId,
                                    filmAffinityId = preview.filmAffinityId,
                                    title = preview.title,
                                    year = preview.year,
                                    releaseDate = preview.releaseDate,
                                    posterUrl = preview.coverUrl,
                                    ratingFa = preview.ratingFa,
                                    liked = true
                                )
                            )
                            feedbackManager.emit("Película añadida")
                        }
                    }
                    com.dondeloexan.domain.model.ContentType.SERIES -> {
                        val existing = tvShowDao.getByContentId(preview.id)
                        if (existing != null) {
                            val newLiked = !existing.liked
                            tvShowDao.update(existing.copy(liked = newLiked))
                            feedbackManager.emit(
                                if (newLiked) "Serie añadida"
                                else "Serie quitada"
                            )
                        } else {
                            tvShowDao.insert(
                                TvShowEntity(
                                    contentId = preview.id,
                                    tmdbId = preview.tmdbId,
                                    filmAffinityId = preview.filmAffinityId,
                                    title = preview.title,
                                    year = preview.year,
                                    posterUrl = preview.coverUrl,
                                    ratingFa = preview.ratingFa,
                                    totalEpisodes = preview.totalEpisodes,
                                    liked = true
                                )
                            )
                            feedbackManager.emit("Serie añadida")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Toggle favorite error", e)
            }
        }
    }

    fun onToggleWatched(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                when (preview.type) {
                    com.dondeloexan.domain.model.ContentType.MOVIE -> {
                        val existing = movieDao.getByContentId(preview.id)
                        if (existing != null) {
                            val wasWatched = existing.status == WatchStatus.YA_VISTA
                            val newStatus = if (wasWatched) WatchStatus.POR_VER else WatchStatus.YA_VISTA
                            movieDao.update(
                                existing.copy(
                                    status = newStatus,
                                    watchedAt = if (wasWatched) null else System.currentTimeMillis()
                                )
                            )
                            feedbackManager.emit(
                                if (!wasWatched) "Película marcada como vista"
                                else "Película quitada de vistos"
                            )
                        } else {
                            movieDao.insert(
                                MovieEntity(
                                    contentId = preview.id,
                                    tmdbId = preview.tmdbId,
                                    filmAffinityId = preview.filmAffinityId,
                                    title = preview.title,
                                    year = preview.year,
                                    releaseDate = preview.releaseDate,
                                    posterUrl = preview.coverUrl,
                                    ratingFa = preview.ratingFa,
                                    status = WatchStatus.YA_VISTA,
                                    watchedAt = System.currentTimeMillis()
                                )
                            )
                            feedbackManager.emit("Película marcada como vista")
                        }
                    }
                    com.dondeloexan.domain.model.ContentType.SERIES -> {
                        val existing = tvShowDao.getByContentId(preview.id)
                        if (existing != null) {
                            val wasWatched = existing.status == WatchStatus.YA_VISTA
                            val newStatus = if (wasWatched) WatchStatus.POR_VER else WatchStatus.YA_VISTA
                            tvShowDao.update(existing.copy(status = newStatus))
                            feedbackManager.emit(
                                if (!wasWatched) "Serie marcada como vista"
                                else "Serie quitada de vistos"
                            )
                        } else {
                            tvShowDao.insert(
                                TvShowEntity(
                                    contentId = preview.id,
                                    tmdbId = preview.tmdbId,
                                    filmAffinityId = preview.filmAffinityId,
                                    title = preview.title,
                                    year = preview.year,
                                    posterUrl = preview.coverUrl,
                                    ratingFa = preview.ratingFa,
                                    totalEpisodes = preview.totalEpisodes,
                                    status = WatchStatus.YA_VISTA
                                )
                            )
                            feedbackManager.emit("Serie marcada como vista")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Toggle watched error", e)
            }
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
        searchJob?.cancel()
        loadTrending()
    }

    fun onRetry() {
        val query = _searchQuery.value
        if (query.isBlank()) {
            loadTrending()
        } else {
            onSearchQueryChanged(query)
        }
    }

    fun togglePlatformFilter() {
        _filterByPlatforms.value = !_filterByPlatforms.value
        val query = _searchQuery.value
        if (query.isBlank()) {
            loadTrending()
        } else {
            onSearchQueryChanged(query)
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _uiState.value = DiscoverUiState.Loading
            try {
                discoverRepository.getTrending().collect { result ->
                    when (result) {
                        is DataResult.Loading -> _uiState.value = DiscoverUiState.Loading
                        is DataResult.Success -> {
                            val liked = likedIds.value
                            var filtered = result.data.filter { it.id !in liked }

                            if (_filterByPlatforms.value) {
                                val userPlatforms = activePlatforms.value
                                if (userPlatforms.isNotEmpty()) {
                                    filtered = filtered.filter { preview ->
                                        preview.streamingPlatforms.any { platform ->
                                            val normalized = platform.platformName
                                                .replace("+", "").replace(" ", "").lowercase()
                                            userPlatforms.any { userP ->
                                                val normalizedUser = userP
                                                    .replace("+", "").replace(" ", "").lowercase()
                                                normalized.contains(normalizedUser) ||
                                                    normalizedUser.contains(normalized)
                                            }
                                        }
                                    }
                                }
                            }

                            _uiState.value = if (filtered.isEmpty()) {
                                DiscoverUiState.Empty("")
                            } else {
                                DiscoverUiState.Success(filtered)
                            }
                        }
                        is DataResult.Error -> {
                            AppLogger.e("DiscoverVM", "Trending error", result.exception)
                            _uiState.value = DiscoverUiState.Error(
                                result.exception.message ?: "Error al carrar tendencias"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                AppLogger.e("DiscoverVM", "Trending error", e)
                _uiState.value = DiscoverUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}

sealed interface DiscoverUiState {
    data object Initial : DiscoverUiState
    data object Loading : DiscoverUiState
    data class Empty(val query: String) : DiscoverUiState
    data class Error(val message: String) : DiscoverUiState
    data class Success(val results: List<ContentPreview>) : DiscoverUiState
}
