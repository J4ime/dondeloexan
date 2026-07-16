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
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private suspend fun enrichTvShowFromTmdb(entity: TvShowEntity) {
        val tmdbId = entity.tmdbId ?: return
        try {
            val tvDetail = tmdbApi.getTvDetailLight(tmdbId)
            val lastEp = tvDetail.lastEpisodeToAir
            val seasons = tvDetail.seasons
            val releasedEpisodes = if (lastEp != null && seasons != null) {
                seasons.filter { it.seasonNumber > 0 }
                    .sumOf { season ->
                        when {
                            season.seasonNumber < lastEp.seasonNumber -> season.episodeCount
                            season.seasonNumber == lastEp.seasonNumber -> lastEp.episodeNumber
                            else -> 0
                        }
                    }
            } else tvDetail.numberOfEpisodes
            tvShowDao.update(
                entity.copy(
                    totalEpisodes = tvDetail.numberOfEpisodes ?: entity.totalEpisodes,
                    releasedEpisodes = releasedEpisodes,
                    nextEpisodeAirDate = tvDetail.nextEpisodeToAir?.airDate,
                    nextEpisodeNumber = tvDetail.nextEpisodeToAir?.episodeNumber,
                    nextEpisodeSeasonNumber = tvDetail.nextEpisodeToAir?.seasonNumber,
                    seriesStatus = tvDetail.status,
                    inProduction = tvDetail.inProduction ?: entity.inProduction,
                    numberOfSeasons = tvDetail.numberOfSeasons ?: entity.numberOfSeasons
                )
            )
        } catch (e: Exception) {
            AppLogger.e("DiscoverVM", "enrichTvShowFromTmdb error for ${entity.title}", e)
        }
    }

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
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val watchedIds: StateFlow<Set<String>> = combine(
        movieDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { movieWatched, tvWatched -> movieWatched + tvWatched }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var currentPage = 1
    private var hasMorePages = true
    private var isFilling = false
    private var hasError = false
    private var cachedResults = listOf<ContentPreview>()
    private var searchJob: Job? = null
    private var trendingJob: Job? = null

    init {
        viewModelScope.launch {
            activePlatforms.drop(1).collect {
                if (_searchQuery.value.isBlank()) {
                    val currentState = _uiState.value
                    if (currentState !is DiscoverUiState.Success || currentState.results.isEmpty()) {
                        loadTrending()
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query

        if (query.isBlank() || query.length < 3) {
            if (query.isBlank()) {
                searchJob?.cancel()
                loadTrending()
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            trendingJob?.cancel()
            currentPage = 1
            hasMorePages = true
            cachedResults = emptyList()
            _uiState.value = DiscoverUiState.Loading

            val page = try {
                discoverRepository.fetchSearchPage(query, 1)
                    .filter { it.ratingImdb != null && it.ratingImdb >= 6.0f }
            } catch (e: Exception) {
                hasError = true
                emptyList()
            }

            cachedResults = page
            _uiState.value = if (cachedResults.isEmpty()) {
                DiscoverUiState.Empty(query)
            } else {
                DiscoverUiState.Success(cachedResults)
            }
        }
    }

    private fun removeAndEmit(contentId: String) {
        cachedResults = cachedResults.filter { it.id != contentId }
        if (_uiState.value is DiscoverUiState.Success) {
            _uiState.value = DiscoverUiState.Success(cachedResults)
        }
    }

    private suspend fun resolveContentForSave(preview: ContentPreview): SaveContentInfo {
        if (preview.type == com.dondeloexan.domain.model.ContentType.MOVIE) {
            val byTmdb = preview.tmdbId?.let { movieDao.getByTmdbId(it) }
            if (byTmdb != null) return SaveContentInfo(
                byTmdb.contentId ?: preview.id, byTmdb.tmdbId, byTmdb.imdbId
            )
            val byImdb = preview.imdbId?.let { movieDao.getByImdbId(it) }
            if (byImdb != null) return SaveContentInfo(
                byImdb.contentId ?: preview.id, byImdb.tmdbId, byImdb.imdbId
            )
            if (preview.tmdbId != null || preview.source != ContentSource.IMDB) {
                return SaveContentInfo(preview.id, preview.tmdbId, preview.imdbId)
            }
            val rawImdbId = preview.id.removePrefix("imdb-")
            val resolvedTmdbId = discoverRepository.resolveTmdbId(rawImdbId, preview.type)
            return SaveContentInfo(preview.id, resolvedTmdbId, rawImdbId)
        } else {
            val byTmdb = preview.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            if (byTmdb != null) return SaveContentInfo(
                byTmdb.contentId ?: preview.id, byTmdb.tmdbId, byTmdb.imdbId
            )
            val byImdb = preview.imdbId?.let { tvShowDao.getByImdbId(it) }
            if (byImdb != null) return SaveContentInfo(
                byImdb.contentId ?: preview.id, byImdb.tmdbId, byImdb.imdbId
            )
            if (preview.tmdbId != null || preview.source != ContentSource.IMDB) {
                return SaveContentInfo(preview.id, preview.tmdbId, preview.imdbId)
            }
            val rawImdbId = preview.id.removePrefix("imdb-")
            val resolvedTmdbId = discoverRepository.resolveTmdbId(rawImdbId, preview.type)
            return SaveContentInfo(preview.id, resolvedTmdbId, rawImdbId)
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
        } catch (e: Exception) {
            AppLogger.e("DiscoverVM", "fetchPlatformsIfEmpty for ${preview.title}", e)
            emptyList()
        }
    }

    fun onToggleFavorite(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                val info = resolveContentForSave(preview)
                val platforms = fetchPlatformsIfEmpty(preview)
                val platformsStr = platforms.toPlatformsString()
                AppLogger.d("DiscoverVM", "toggleFavorite ${preview.title}: platforms.size=${platforms.size}, platformsStr=${platformsStr != null}, preview=${platformsStr?.take(120)}")
                when (preview.type) {
                    com.dondeloexan.domain.model.ContentType.MOVIE -> {
                        val existing = movieDao.getByContentId(info.contentId)
                            ?: (info.tmdbId?.let { movieDao.getByTmdbId(it) }
                                ?: info.imdbId?.let { movieDao.getByImdbId(it) })
                        if (existing != null) {
                            val newLiked = !existing.liked
                            movieDao.update(existing.copy(
                                liked = newLiked,
                                streamingPlatforms = if (platformsStr.isNullOrEmpty() || platformsStr == "[]") existing.streamingPlatforms else platformsStr,
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
                            ?: (info.tmdbId?.let { tvShowDao.getByTmdbId(it) }
                                ?: info.imdbId?.let { tvShowDao.getByImdbId(it) })
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
                            tvShowDao.getByContentId(info.contentId)?.let { enrichTvShowFromTmdb(it) }
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
                            ?: (info.tmdbId?.let { movieDao.getByTmdbId(it) }
                                ?: info.imdbId?.let { movieDao.getByImdbId(it) })
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
                            ?: (info.tmdbId?.let { tvShowDao.getByTmdbId(it) }
                                ?: info.imdbId?.let { tvShowDao.getByImdbId(it) })
                        val platformsStrFinal = platformsStr ?: existing?.streamingPlatforms
                        if (existing != null) {
                            val wasWatched = existing.status == WatchStatus.YA_VISTA
                            if (wasWatched) {
                                tvShowProgressDao.deleteByTvShowId(existing.id)
                                tvShowDao.update(existing.copy(status = WatchStatus.POR_VER))
                                feedbackManager.emit("Serie quitada de vistos")
                            } else {
                                tvShowProgressDao.deleteByTvShowId(existing.id)
                                val tmdbId = existing.tmdbId ?: info.tmdbId
                                if (tmdbId != null) {
                                    try {
                                        val detail = tmdbApi.getTvDetailLight(tmdbId)
                                        val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                                        if (detail.numberOfEpisodes != null && detail.numberOfEpisodes > 0) {
                                            tvShowDao.update(existing.copy(totalEpisodes = detail.numberOfEpisodes))
                                        }
                                        val progressToInsert = mutableListOf<TvShowProgressEntity>()
                                        val today = LocalDate.now()
                                        for (season in seasons) {
                                            try {
                                                val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                                                for (ep in seasonDetail.episodes) {
                                                    val isAired = ep.airDate == null ||
                                                            try { !LocalDate.parse(ep.airDate).isAfter(today) } catch (_: Exception) { true }
                                                    if (isAired) {
                                                        progressToInsert.add(
                                                            TvShowProgressEntity(
                                                                tvShowId = existing.id,
                                                                season = season.seasonNumber,
                                                                episode = ep.episodeNumber
                                                            )
                                                        )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                AppLogger.e("DiscoverVM", "season ${season.seasonNumber} for ${existing.id}", e)
                                                for (epNum in 1..season.episodeCount) {
                                                    progressToInsert.add(
                                                        TvShowProgressEntity(
                                                            tvShowId = existing.id,
                                                            season = season.seasonNumber,
                                                            episode = epNum
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        tvShowProgressDao.insertAll(progressToInsert)
                                        tvShowDao.update(existing.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                                        feedbackManager.emit("Serie marcada como vista")
                                    } catch (e: Exception) {
                                        AppLogger.e("DiscoverVM", "mark watched error for ${existing.id}", e)
                                    }
                                } else {
                                    val totalEp = existing.totalEpisodes ?: preview.totalEpisodes ?: 0
                                    if (totalEp > 0) {
                                        val allEpisodes = (1..totalEp).map { epNum ->
                                            TvShowProgressEntity(tvShowId = existing.id, season = 1, episode = epNum)
                                        }
                                        tvShowProgressDao.insertAll(allEpisodes)
                                    }
                                    tvShowDao.update(existing.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                                    feedbackManager.emit("Serie marcada como vista")
                                }
                                removeAndEmit(preview.id)
                            }
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
                            val tmdbId = info.tmdbId
                            if (tmdbId != null) {
                                try {
                                    val detail = tmdbApi.getTvDetailLight(tmdbId)
                                    val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                                    val progressToInsert = mutableListOf<TvShowProgressEntity>()
                                    val today = LocalDate.now()
                                    for (season in seasons) {
                                        try {
                                            val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                                            for (ep in seasonDetail.episodes) {
                                                val isAired = ep.airDate == null ||
                                                        try { !LocalDate.parse(ep.airDate).isAfter(today) } catch (_: Exception) { true }
                                                if (isAired) {
                                                    progressToInsert.add(
                                                        TvShowProgressEntity(
                                                            tvShowId = newShowId,
                                                            season = season.seasonNumber,
                                                            episode = ep.episodeNumber
                                                        )
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.e("DiscoverVM", "season ${season.seasonNumber} for $newShowId", e)
                                            for (epNum in 1..season.episodeCount) {
                                                progressToInsert.add(
                                                    TvShowProgressEntity(
                                                        tvShowId = newShowId,
                                                        season = season.seasonNumber,
                                                        episode = epNum
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    tvShowProgressDao.insertAll(progressToInsert)
                                    tvShowDao.updateLastWatchedAt(newShowId, System.currentTimeMillis())
                                } catch (e: Exception) {
                                    AppLogger.e("DiscoverVM", "mark watched error for $newShowId", e)
                                }
                            } else {
                                val totalEp = preview.totalEpisodes ?: 0
                                if (totalEp > 0) {
                                    val allEpisodes = (1..totalEp).map { epNum ->
                                        TvShowProgressEntity(tvShowId = newShowId, season = 1, episode = epNum)
                                    }
                                    tvShowProgressDao.insertAll(allEpisodes)
                                    tvShowDao.updateLastWatchedAt(newShowId, System.currentTimeMillis())
                                }
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
        if (isFilling || !hasMorePages) return
        isFilling = true
        viewModelScope.launch {
            val page = fetchTrendingSinglePage(currentPage + 1)
            if (page.isNotEmpty()) {
                currentPage++
                cachedResults = cachedResults + page
                _uiState.value = DiscoverUiState.Success(cachedResults)
            } else {
                hasMorePages = false
            }
            isFilling = false
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
        searchJob?.cancel()
        currentPage = 1
        hasMorePages = true
        cachedResults = emptyList()
        loadTrending()
    }

    fun onRetry() {
        val query = _searchQuery.value
        if (query.isBlank()) {
            if (!hasMorePages) { loadTrending(); return }
            trendingJob?.cancel()
            trendingJob = viewModelScope.launch {
                val nextPage = currentPage + 1
                _uiState.value = DiscoverUiState.Loading
                val page = fetchTrendingSinglePage(nextPage)
                if (page.isNotEmpty()) {
                    currentPage = nextPage
                    cachedResults = page
                    _uiState.value = DiscoverUiState.Success(cachedResults)
                } else {
                    onClearSearch()
                }
            }
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

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        trendingJob?.cancel()
    }

    fun loadTrending() {
        trendingJob?.cancel()
        hasError = false
        trendingJob = viewModelScope.launch {
            currentPage = 1
            hasMorePages = true
            cachedResults = emptyList()
            _uiState.value = DiscoverUiState.Loading

            val page = fetchTrendingSinglePage(1)
            cachedResults = page
            hasMorePages = page.isNotEmpty()

            _uiState.value = when {
                cachedResults.isNotEmpty() -> DiscoverUiState.Success(cachedResults)
                hasError -> DiscoverUiState.Error("No se pudo conectar con el servidor, prueba a deslizar para reintentar")
                else -> DiscoverUiState.Empty("")
            }
        }
    }

    private suspend fun fetchTrendingSinglePage(page: Int): List<ContentPreview> {
        val filterByPlatforms = _filterByPlatforms.value
        val liked = buildSet {
            addAll(likedIds.value)
            movieDao.getLiked().first().forEach { m ->
                m.tmdbId?.let { add("tmdb-$it") }
            }
            tvShowDao.getLiked().first().forEach { s ->
                s.tmdbId?.let { add("tmdb-$it") }
            }
        }
        val watched = buildSet {
            addAll(watchedIds.value)
            movieDao.getByStatus(WatchStatus.YA_VISTA).first().forEach { m ->
                m.tmdbId?.let { add("tmdb-$it") }
            }
            tvShowDao.getByStatus(WatchStatus.YA_VISTA).first().forEach { s ->
                s.tmdbId?.let { add("tmdb-$it") }
            }
        }
        return try {
            val results = discoverRepository.fetchTrendingPage(page, filterByPlatforms)
            val blacklisted = blacklistedIds.value
            results.filter { it.id !in liked && it.id !in blacklisted && it.id !in watched }
                .filter { it.ratingImdb != null && it.ratingImdb >= 6.0f }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("DiscoverVM", "fetchTrendingPage error page=$page", e)
            hasError = true
            emptyList()
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
