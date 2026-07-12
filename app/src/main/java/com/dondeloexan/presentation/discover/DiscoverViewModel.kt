package com.dondeloexan.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.BlacklistDao
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val blacklistDao: BlacklistDao,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Initial)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _filterByPlatforms = MutableStateFlow(true)
    val filterByPlatforms: StateFlow<Boolean> = _filterByPlatforms.asStateFlow()

    val activePlatforms: StateFlow<Set<String>> = userPlatformDao.getActiveFlow()
        .map { it.map { p -> p.platformName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val blacklistedIds: StateFlow<Set<String>> = blacklistDao.getAllFlow()
        .map { list -> list.map { it.contentId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

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

    private var apiPage = 1
    private var hasMoreApiPages = true
    private var isFilling = false
    private val accumulatedResults = mutableListOf<ContentPreview>()
    private var searchJob: Job? = null
    private var trendingJob: Job? = null

    init {
        viewModelScope.launch {
            activePlatforms.drop(1).collect {
                if (_searchQuery.value.isBlank()) {
                    loadTrending()
                }
            }
        }
    }

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
            trendingJob?.cancel()
            apiPage = 1
            hasMoreApiPages = true
            accumulatedResults.clear()
            _uiState.value = DiscoverUiState.Loading

            fillUntil(targetCount = 20, query = query)
            isFilling = false

            _uiState.value = if (accumulatedResults.isEmpty()) {
                DiscoverUiState.Empty(query)
            } else {
                DiscoverUiState.Success(accumulatedResults.toList())
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
                                    title = preview.title,
                                    year = preview.year,
                                    releaseDate = preview.releaseDate,
                                    posterUrl = preview.coverUrl,
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
                                    title = preview.title,
                                    year = preview.year,
                                    posterUrl = preview.coverUrl,
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
                                    title = preview.title,
                                    year = preview.year,
                                    releaseDate = preview.releaseDate,
                                    posterUrl = preview.coverUrl,
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
                                    title = preview.title,
                                    year = preview.year,
                                    posterUrl = preview.coverUrl,
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

    fun onToggleBlacklist(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                blacklistDao.insert(
                    com.dondeloexan.data.local.entity.BlacklistedEntity(
                        contentId = preview.id,
                        title = preview.title,
                        type = preview.type.name
                    )
                )
                feedbackManager.emit("${preview.title} ocultado")
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Blacklist error", e)
            }
        }
    }

    fun loadNextPage() {
        if (isFilling) return
        val query = _searchQuery.value
        viewModelScope.launch {
            isFilling = true

            fillUntil(targetCount = accumulatedResults.size + 20, query = query)
            isFilling = false

            _uiState.value = if (accumulatedResults.isEmpty()) {
                DiscoverUiState.Empty(query)
            } else {
                DiscoverUiState.Success(accumulatedResults.toList())
            }
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
        searchJob?.cancel()
        apiPage = 1
        hasMoreApiPages = true
        accumulatedResults.clear()
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
        trendingJob?.cancel()
        trendingJob = viewModelScope.launch {
            apiPage = 1
            hasMoreApiPages = true
            accumulatedResults.clear()
            _uiState.value = DiscoverUiState.Loading

            fillUntil(targetCount = 20)
            isFilling = false

            _uiState.value = if (accumulatedResults.isEmpty()) {
                DiscoverUiState.Empty("")
            } else {
                DiscoverUiState.Success(accumulatedResults.toList())
            }
        }
    }

    private suspend fun fillUntil(targetCount: Int, query: String = "") {
        while (accumulatedResults.size < targetCount && hasMoreApiPages) {
            val pageResults = if (query.isBlank()) {
                try {
                    discoverRepository.fetchTrendingPage(apiPage)
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "fetchTrendingPage error", e)
                    emptyList()
                }
            } else {
                try {
                    discoverRepository.fetchSearchPage(query, apiPage)
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "fetchSearchPage error", e)
                    emptyList()
                }
            }

            if (pageResults.isEmpty()) {
                hasMoreApiPages = false
                break
            }

            apiPage++

            val liked = likedIds.value
            val blacklisted = blacklistedIds.value
            val watched = watchedIds.value

            val filtered = if (query.isBlank()) {
                pageResults.filter { it.id !in liked && it.id !in blacklisted }
            } else {
                pageResults.filter { it.id !in blacklisted && it.id !in liked && it.id !in watched }
            }

            val platformFiltered = if (_filterByPlatforms.value) {
                val userPlatforms = activePlatforms.value
                if (userPlatforms.isNotEmpty()) {
                    filtered.filter { preview ->
                        preview.streamingPlatforms.any { platform ->
                            userPlatforms.any { userP ->
                                platform.platformName.trim().equals(userP.trim(), ignoreCase = true)
                            }
                        }
                    }
                } else filtered
            } else filtered

            accumulatedResults.addAll(platformFiltered)
        }

        accumulatedResults.distinctBy { it.id }.let { dedup ->
            accumulatedResults.clear()
            accumulatedResults.addAll(dedup)
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
