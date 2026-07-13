package com.dondeloexan.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import java.util.UUID
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
    private val tvShowProgressDao: TvShowProgressDao,
    private val blacklistDao: BlacklistDao,
    private val tmdbApi: TmdbApi,
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val watchedIds: StateFlow<Set<String>> = combine(
        movieDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { movieWatched, tvWatched -> movieWatched + tvWatched }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

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

    private fun removeAndEmit(contentId: String) {
        accumulatedResults.removeAll { it.id == contentId }
        if (_uiState.value is DiscoverUiState.Success) {
            _uiState.value = DiscoverUiState.Success(accumulatedResults.toList())
        }
    }

    private suspend fun resolveContentForSave(preview: ContentPreview): SaveContentInfo {
        val uuid = UUID.randomUUID().toString()

        if (preview.type == com.dondeloexan.domain.model.ContentType.MOVIE) {
            val byTmdb = preview.tmdbId?.let { movieDao.getByTmdbId(it) }
            if (byTmdb != null) return SaveContentInfo(
                byTmdb.contentId ?: uuid, byTmdb.tmdbId, byTmdb.imdbId
            )
            val byImdb = preview.imdbId?.let { movieDao.getByImdbId(it) }
            if (byImdb != null) return SaveContentInfo(
                byImdb.contentId ?: uuid, byImdb.tmdbId, byImdb.imdbId
            )
            if (preview.tmdbId != null || preview.source != ContentSource.IMDB) {
                return SaveContentInfo(uuid, preview.tmdbId, preview.imdbId)
            }
            val rawImdbId = preview.id.removePrefix("imdb-")
            val resolvedTmdbId = discoverRepository.resolveTmdbId(rawImdbId, preview.type)
            return SaveContentInfo(uuid, resolvedTmdbId, rawImdbId)
        } else {
            val byTmdb = preview.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            if (byTmdb != null) return SaveContentInfo(
                byTmdb.contentId ?: uuid, byTmdb.tmdbId, byTmdb.imdbId
            )
            val byImdb = preview.imdbId?.let { tvShowDao.getByImdbId(it) }
            if (byImdb != null) return SaveContentInfo(
                byImdb.contentId ?: uuid, byImdb.tmdbId, byImdb.imdbId
            )
            if (preview.tmdbId != null || preview.source != ContentSource.IMDB) {
                return SaveContentInfo(uuid, preview.tmdbId, preview.imdbId)
            }
            val rawImdbId = preview.id.removePrefix("imdb-")
            val resolvedTmdbId = discoverRepository.resolveTmdbId(rawImdbId, preview.type)
            return SaveContentInfo(uuid, resolvedTmdbId, rawImdbId)
        }
    }

    private data class SaveContentInfo(
        val contentId: String,
        val tmdbId: Int?,
        val imdbId: String?
    )

    private suspend fun fetchPlatformsIfEmpty(preview: ContentPreview): List<StreamingAvailability> {
        if (preview.streamingPlatforms.isNotEmpty()) return preview.streamingPlatforms
        val tmdbId = preview.tmdbId ?: return emptyList()
        return try {
            val providers = if (preview.type == com.dondeloexan.domain.model.ContentType.SERIES) {
                tmdbApi.getTvWatchProviders(tmdbId)
            } else {
                tmdbApi.getMovieWatchProviders(tmdbId)
            }
            providers.results["ES"]?.toStreamingAvailability().orEmpty()
        } catch (_: Exception) { emptyList() }
    }

    fun onToggleFavorite(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                val info = resolveContentForSave(preview)
                val platforms = fetchPlatformsIfEmpty(preview)
                val platformsStr = platforms.toPlatformsString()
                AppLogger.d("DiscoverVM", "toggleFavorite ${preview.title}: platforms.size=${platforms.size}, platformsStr=${platformsStr != null}")
                when (preview.type) {
                    com.dondeloexan.domain.model.ContentType.MOVIE -> {
                        val existing = movieDao.getByContentId(info.contentId)
                            ?: movieDao.getByTmdbId(info.tmdbId ?: return@launch)
                        if (existing != null) {
                            val newLiked = !existing.liked
                            movieDao.update(existing.copy(
                                liked = newLiked,
                                streamingPlatforms = platformsStr ?: existing.streamingPlatforms,
                                releaseDate = preview.releaseDate ?: existing.releaseDate
                            ))
                            feedbackManager.emit(
                                if (newLiked) "Película añadida"
                                else "Película quitada"
                            )
                            if (newLiked) removeAndEmit(preview.id)
                        } else {
                            movieDao.insert(
                                MovieEntity(
                                    contentId = info.contentId,
                                    tmdbId = info.tmdbId,
                                    imdbId = info.imdbId,
                                    title = preview.title,
                                    year = preview.year,
                                    releaseDate = preview.releaseDate,
                                    posterUrl = preview.coverUrl,
                                    streamingPlatforms = platformsStr,
                                    liked = true
                                )
                            )
                            feedbackManager.emit("Película añadida")
                            removeAndEmit(preview.id)
                        }
                    }
                    com.dondeloexan.domain.model.ContentType.SERIES -> {
                        val existing = tvShowDao.getByContentId(info.contentId)
                            ?: tvShowDao.getByTmdbId(info.tmdbId ?: return@launch)
                        if (existing != null) {
                            val newLiked = !existing.liked
                            tvShowDao.update(existing.copy(
                                liked = newLiked,
                                streamingPlatforms = platformsStr ?: existing.streamingPlatforms
                            ))
                            feedbackManager.emit(
                                if (newLiked) "Serie añadida"
                                else "Serie quitada"
                            )
                            if (newLiked) removeAndEmit(preview.id)
                        } else {
                            tvShowDao.insert(
                                TvShowEntity(
                                    contentId = info.contentId,
                                    tmdbId = info.tmdbId,
                                    imdbId = info.imdbId,
                                    title = preview.title,
                                    year = preview.year,
                                    posterUrl = preview.coverUrl,
                                    totalEpisodes = preview.totalEpisodes,
                                    streamingPlatforms = platformsStr,
                                    liked = true
                                )
                            )
                            feedbackManager.emit("Serie añadida")
                            removeAndEmit(preview.id)
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
                val info = resolveContentForSave(preview)
                val platforms = fetchPlatformsIfEmpty(preview)
                val platformsStr = platforms.toPlatformsString()
                when (preview.type) {
                    com.dondeloexan.domain.model.ContentType.MOVIE -> {
                        val existing = movieDao.getByContentId(info.contentId)
                            ?: movieDao.getByTmdbId(info.tmdbId ?: return@launch)
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
                            if (!wasWatched) removeAndEmit(preview.id)
                        } else {
                            movieDao.insert(
                                MovieEntity(
                                    contentId = info.contentId,
                                    tmdbId = info.tmdbId,
                                    imdbId = info.imdbId,
                                    title = preview.title,
                                    year = preview.year,
                                    releaseDate = preview.releaseDate,
                                    posterUrl = preview.coverUrl,
                                    streamingPlatforms = platformsStr,
                                    status = WatchStatus.YA_VISTA,
                                    watchedAt = System.currentTimeMillis()
                                )
                            )
                            feedbackManager.emit("Película marcada como vista")
                            removeAndEmit(preview.id)
                        }
                    }
                    com.dondeloexan.domain.model.ContentType.SERIES -> {
                        val existing = tvShowDao.getByContentId(info.contentId)
                            ?: tvShowDao.getByTmdbId(info.tmdbId ?: return@launch)
                        val platformsStrFinal = platformsStr ?: existing?.streamingPlatforms
                        if (existing != null) {
                            val wasWatched = existing.status == WatchStatus.YA_VISTA
                            val newStatus = if (wasWatched) WatchStatus.POR_VER else WatchStatus.YA_VISTA
                            if (!wasWatched) {
                                // Mark all available episodes as watched
                                val totalEp = existing.totalEpisodes ?: preview.totalEpisodes ?: 0
                                if (totalEp > 0) {
                                    tvShowProgressDao.deleteByTvShowId(existing.id)
                                    val allEpisodes = (1..totalEp).map { epNum ->
                                        TvShowProgressEntity(
                                            tvShowId = existing.id,
                                            season = 1,
                                            episode = epNum
                                        )
                                    }
                                    tvShowProgressDao.insertAll(allEpisodes)
                                    tvShowDao.updateLastWatchedAt(existing.id, System.currentTimeMillis())
                                }
                            } else {
                                tvShowProgressDao.deleteByTvShowId(existing.id)
                            }
                            tvShowDao.update(existing.copy(status = newStatus))
                            feedbackManager.emit(
                                if (!wasWatched) "Serie marcada como vista"
                                else "Serie quitada de vistos"
                            )
                            if (!wasWatched) removeAndEmit(preview.id)
                        } else {
                            val newShowId = tvShowDao.insert(
                                TvShowEntity(
                                    contentId = info.contentId,
                                    tmdbId = info.tmdbId,
                                    imdbId = info.imdbId,
                                    title = preview.title,
                                    year = preview.year,
                                    posterUrl = preview.coverUrl,
                                    totalEpisodes = preview.totalEpisodes,
                                    streamingPlatforms = platformsStrFinal,
                                    status = WatchStatus.YA_VISTA
                                )
                            )
                            val totalEp = preview.totalEpisodes ?: 0
                            if (totalEp > 0) {
                                val allEpisodes = (1..totalEp).map { epNum ->
                                    TvShowProgressEntity(
                                        tvShowId = newShowId,
                                        season = 1,
                                        episode = epNum
                                    )
                                }
                                tvShowProgressDao.insertAll(allEpisodes)
                                tvShowDao.updateLastWatchedAt(newShowId, System.currentTimeMillis())
                            }
                            feedbackManager.emit("Serie marcada como vista")
                            removeAndEmit(preview.id)
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
                removeAndEmit(preview.id)
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
                    discoverRepository.fetchTrendingPage(apiPage, _filterByPlatforms.value)
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
                pageResults.filter { it.id !in liked && it.id !in blacklisted && it.id !in watched }
            } else {
                pageResults.filter { it.id !in blacklisted && it.id !in liked && it.id !in watched }
            }

            accumulatedResults.addAll(filtered)
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
