package com.dondeloexan.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.data.remote.mapper.toTmdb
import com.dondeloexan.data.remote.mapper.toTmdbSeasonDto
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val content: Content? = null,
    val error: String? = null,
    val seasons: List<TmdbSeasonDto> = emptyList(),
    val selectedSeason: Int = 0,
    val seasonDetail: TmdbTvSeasonDetailDto? = null,
    val watchedEpisodes: Set<String> = emptySet(),
    val cascadeProposal: CascadeProposal? = null,
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null
)

data class CascadeProposal(
    val season: Int,
    val targetEpisode: Int,
    val count: Int
)

class MediaDetailViewModel(
    private val discoverRepository: DiscoverRepository,
    private val tmdbApi: TmdbApi,
    private val imdbApi: BalloonerismmApi,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var seasonJob: Job? = null

    fun loadContent(contentId: String, contentType: ContentType = ContentType.MOVIE) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)

            try {
                discoverRepository.getDetail(contentId, contentType).collect { result ->
                    when (result) {
                        is DataResult.Loading -> {}
                        is DataResult.Success -> {
                            val content = result.data
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                content = content,
                                error = null
                            )

                            if (content.type == ContentType.SERIES) {
                                loadSeasons(content)
                            }
                        }
                        is DataResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.exception.message ?: "Error al cargar detalle"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DetailVM", "Error loading content", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    private suspend fun loadSeasons(content: Content) {
        try {
            when (content.source) {
                ContentSource.TMDB -> {
                    val tmdbId = content.tmdbId ?: return
                    val tvDetail = tmdbApi.getTvDetail(tmdbId)
                    val seasons = tvDetail.seasons?.filter { it.seasonNumber > 0 } ?: emptyList()
                    _uiState.value = _uiState.value.copy(seasons = seasons)
                    if (seasons.isNotEmpty()) {
                        val targetSeason = _uiState.value.lastWatchedSeason
                            ?.let { s -> seasons.find { it.seasonNumber == s } }
                            ?: seasons.first()
                        selectSeason(targetSeason.seasonNumber)
                    }
                }
                ContentSource.IMDB -> {
                    val imdbId = content.imdbId ?: return
                    val tvDetail = imdbApi.getTvDetail(imdbId)
                    val seasons = tvDetail.seasons
                        ?.filter { (it.seasonNumber ?: 0) > 0 }
                        ?.map { it.toTmdbSeasonDto() }
                        ?: emptyList()
                    _uiState.value = _uiState.value.copy(seasons = seasons)
                    if (seasons.isNotEmpty()) {
                        val targetSeason = _uiState.value.lastWatchedSeason
                            ?.let { s -> seasons.find { it.seasonNumber == s } }
                            ?: seasons.first()
                        selectSeason(targetSeason.seasonNumber)
                    }
                }
            }

            val tvShow = tvShowDao.getByContentId(content.id) ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            if (tvShow != null) {
                val progress = tvShowProgressDao.getByTvShowId(tvShow.id)
                val watchedSet = progress.map { "S${it.season}E${it.episode}" }.toSet()
                val lastWatched = progress.maxByOrNull { it.watchedAt }
                _uiState.value = _uiState.value.copy(
                    watchedEpisodes = watchedSet,
                    lastWatchedSeason = lastWatched?.season,
                    lastWatchedEpisode = lastWatched?.episode
                )
            }
        } catch (e: Exception) {
            AppLogger.e("DetailVM", "Error loading seasons", e)
        }
    }

    fun selectSeason(seasonNumber: Int) {
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber, cascadeProposal = null)

            val content = _uiState.value.content ?: return@launch
            try {
                when (content.source) {
                    ContentSource.TMDB -> {
                        val tmdbId = content.tmdbId ?: return@launch
                        val seasonDetail = tmdbApi.getTvSeason(tmdbId, seasonNumber)
                        _uiState.value = _uiState.value.copy(seasonDetail = seasonDetail)
                    }
                    ContentSource.IMDB -> {
                        val imdbId = content.imdbId ?: return@launch
                        val seasonDetail = imdbApi.getTvSeason(imdbId, seasonNumber).toTmdb()
                        _uiState.value = _uiState.value.copy(seasonDetail = seasonDetail)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading season $seasonNumber", e)
            }
        }
    }

    fun toggleEpisodeWatched(episodeNumber: Int) {
        viewModelScope.launch {
            val content = _uiState.value.content ?: return@launch
            val seasonNumber = _uiState.value.selectedSeason
            val contentId = content.id
            val episodeKey = "S${seasonNumber}E${episodeNumber}"
            val currentWatched = _uiState.value.watchedEpisodes.toMutableSet()

            val tvShow = tvShowDao.getByContentId(contentId) ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            if (tvShow == null) return@launch

            if (currentWatched.contains(episodeKey)) {
                currentWatched.remove(episodeKey)
                tvShowProgressDao.deleteEpisode(tvShow.id, seasonNumber, episodeNumber)
                val lastWatched = tvShowProgressDao.getLastWatchedAt(tvShow.id)
                tvShowDao.updateLastWatchedAt(tvShow.id, lastWatched)
                if (tvShow.finishedAt != null && currentWatched.isEmpty()) {
                    tvShowDao.update(tvShow.copy(finishedAt = null, status = WatchStatus.POR_VER))
                }
                _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched, cascadeProposal = null)
            } else {
                val seasonDetail = _uiState.value.seasonDetail
                val unwatchedBefore = seasonDetail?.episodes
                    ?.map { it.episodeNumber }
                    ?.filter { it < episodeNumber && !currentWatched.contains("S${seasonNumber}E${it}") }
                    ?: emptyList()

                if (unwatchedBefore.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        cascadeProposal = CascadeProposal(
                            season = seasonNumber,
                            targetEpisode = episodeNumber,
                            count = unwatchedBefore.size
                        )
                    )
                } else {
                    currentWatched.add(episodeKey)
                    tvShowProgressDao.insert(
                        TvShowProgressEntity(
                            tvShowId = tvShow.id,
                            season = seasonNumber,
                            episode = episodeNumber
                        )
                    )
                    tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
                    checkAndMarkFinale(seasonNumber, episodeNumber)
                    _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched)
                }
            }
        }
    }

    fun confirmCascadeWatched() {
        viewModelScope.launch {
            val proposal = _uiState.value.cascadeProposal ?: return@launch
            val content = _uiState.value.content ?: return@launch
            val contentId = content.id
            val tvShow = tvShowDao.getByContentId(contentId) ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) } ?: return@launch
            val currentWatched = _uiState.value.watchedEpisodes.toMutableSet()
            val seasonDetail = _uiState.value.seasonDetail ?: return@launch

            val allItems = mutableListOf<TvShowProgressEntity>()

            val episodesToMark = seasonDetail.episodes
                .map { it.episodeNumber }
                .filter { it <= proposal.targetEpisode && !currentWatched.contains("S${proposal.season}E${it}") }

            episodesToMark.forEach { epNum ->
                val key = "S${proposal.season}E${epNum}"
                currentWatched.add(key)
                allItems.add(
                    TvShowProgressEntity(
                        tvShowId = tvShow.id,
                        season = proposal.season,
                        episode = epNum
                    )
                )
            }

            if (allItems.isNotEmpty()) {
                tvShowProgressDao.insertAll(allItems)
                tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
                checkAndMarkFinale(proposal.season, proposal.targetEpisode)
            }
            _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched, cascadeProposal = null)
        }
    }

    fun dismissCascadeWatched() {
        viewModelScope.launch {
            val proposal = _uiState.value.cascadeProposal ?: return@launch
            val content = _uiState.value.content ?: return@launch
            val contentId = content.id
            val tvShow = tvShowDao.getByContentId(contentId) ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) } ?: return@launch
            val currentWatched = _uiState.value.watchedEpisodes.toMutableSet()
            val targetKey = "S${proposal.season}E${proposal.targetEpisode}"

            currentWatched.add(targetKey)
            tvShowProgressDao.insert(
                TvShowProgressEntity(
                    tvShowId = tvShow.id,
                    season = proposal.season,
                    episode = proposal.targetEpisode
                )
            )
            tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
            checkAndMarkFinale(proposal.season, proposal.targetEpisode)
            _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched, cascadeProposal = null)
        }
    }

    private suspend fun checkAndMarkFinale(seasonNumber: Int, episodeNumber: Int) {
        val state = _uiState.value
        val seasonDetail = state.seasonDetail ?: return
        val content = state.content ?: return

        val isFinaleType = seasonDetail.episodes.any {
            it.episodeNumber == episodeNumber &&
                    (it.episodeType == "finale" || it.episodeType == "series_finale")
        }

        if (!isFinaleType) {
            val lastSeason = state.seasons.maxOfOrNull { it.seasonNumber } ?: return
            val lastEpCount = seasonDetail.episodes.size
            if (seasonNumber != lastSeason || episodeNumber != lastEpCount) return
            if (content.totalEpisodes != null && seasonNumber != lastSeason) return
        }

        try {
            val tvShow = tvShowDao.getByContentId(content.id) ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) } ?: return

            if (tvShow.inProduction == true
                || tvShow.seriesStatus in listOf("Returning Series", "In Production")
                || tvShow.nextEpisodeAirDate != null) {
                return
            }

            tvShowDao.update(tvShow.copy(
                status = WatchStatus.YA_VISTA,
                finishedAt = System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            AppLogger.e("DetailVM", "checkAndMarkFinale for ${content.id}", e)
        }
    }

    fun markSeasonWatched() {
        viewModelScope.launch {
            val seasonDetail = _uiState.value.seasonDetail ?: return@launch
            val episodeNumbers = seasonDetail.episodes.map { it.episodeNumber }
            val seasonNumber = _uiState.value.selectedSeason
            val content = _uiState.value.content ?: return@launch
            val contentId = content.id

            val tvShow = tvShowDao.getByContentId(contentId) ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) } ?: return@launch
            val currentWatched = _uiState.value.watchedEpisodes.toMutableSet()

            val alreadyWatched = episodeNumbers.all { epNum ->
                currentWatched.contains("S${seasonNumber}E${epNum}")
            }

            if (alreadyWatched) {
                episodeNumbers.forEach { epNum ->
                    val key = "S${seasonNumber}E${epNum}"
                    if (currentWatched.remove(key)) {
                        tvShowProgressDao.deleteEpisode(tvShow.id, seasonNumber, epNum)
                    }
                }
                val lastWatched = tvShowProgressDao.getLastWatchedAt(tvShow.id)
                tvShowDao.updateLastWatchedAt(tvShow.id, lastWatched)
            } else {
                val lastEp = episodeNumbers.maxOrNull() ?: 0
                episodeNumbers.forEach { epNum ->
                    val key = "S${seasonNumber}E${epNum}"
                    if (!currentWatched.contains(key)) {
                        currentWatched.add(key)
                        tvShowProgressDao.insert(
                            TvShowProgressEntity(
                                tvShowId = tvShow.id,
                                season = seasonNumber,
                                episode = epNum
                            )
                        )
                    }
                }
                tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
                if (lastEp > 0) checkAndMarkFinale(seasonNumber, lastEp)
            }

            _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched)
        }
    }
}
