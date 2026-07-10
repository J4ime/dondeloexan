package com.dondeloexan.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val content: Content? = null,
    val error: String? = null,
    val seasons: List<TmdbSeasonDto> = emptyList(),
    val selectedSeason: Int = 0,
    val seasonDetail: TmdbTvSeasonDetailDto? = null,
    val watchedEpisodes: Set<Int> = emptySet()
)

class MediaDetailViewModel(
    private val discoverRepository: DiscoverRepository,
    private val tmdbApi: TmdbApi,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadContent(contentId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)

            try {
                discoverRepository.getDetail(contentId).collect { result ->
                    when (result) {
                        is DataResult.Loading -> {}
                        is DataResult.Success -> {
                            val content = result.data
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                content = content,
                                error = null
                            )

                            if (content.type == com.dondeloexan.domain.model.ContentType.SERIES) {
                                loadSeasons(content.tmdbId)
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
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error desconocido"
                )
            }
        }
    }

    private suspend fun loadSeasons(tmdbId: Int?) {
        if (tmdbId == null) return

        try {
            val tvDetail = tmdbApi.getTvDetail(tmdbId)
            val seasons = tvDetail.seasons?.filter { it.seasonNumber > 0 }
                ?: emptyList()

            _uiState.value = _uiState.value.copy(seasons = seasons)

            if (seasons.isNotEmpty()) {
                selectSeason(seasons.first().seasonNumber)
            }

            val tvShow = tvShowDao.getByContentId("tmdb-$tmdbId")
            if (tvShow != null) {
                val progress = tvShowProgressDao.getByTvShowId(tvShow.id)
                val watchedSet = progress.map { it.episode }.toSet()
                _uiState.value = _uiState.value.copy(watchedEpisodes = watchedSet)
            }
        } catch (e: Exception) {
            AppLogger.e("DetailVM", "Error loading seasons", e)
        }
    }

    fun selectSeason(seasonNumber: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber)

            val tmdbId = _uiState.value.content?.tmdbId ?: return@launch
            try {
                val seasonDetail = tmdbApi.getTvSeason(tmdbId, seasonNumber)
                _uiState.value = _uiState.value.copy(seasonDetail = seasonDetail)
            } catch (e: Exception) {
                AppLogger.e("DetailVM", "Error loading season $seasonNumber", e)
            }
        }
    }

    fun toggleEpisodeWatched(episodeNumber: Int) {
        viewModelScope.launch {
            val tmdbId = _uiState.value.content?.tmdbId ?: return@launch
            val seasonNumber = _uiState.value.selectedSeason
            val contentId = "tmdb-$tmdbId"
            val currentWatched = _uiState.value.watchedEpisodes.toMutableSet()

            val tvShow = tvShowDao.getByContentId(contentId)
            if (tvShow == null) return@launch

            if (currentWatched.contains(episodeNumber)) {
                currentWatched.remove(episodeNumber)
                tvShowProgressDao.deleteEpisode(tvShow.id, seasonNumber, episodeNumber)
            } else {
                currentWatched.add(episodeNumber)
                tvShowProgressDao.insert(
                    TvShowProgressEntity(
                        tvShowId = tvShow.id,
                        season = seasonNumber,
                        episode = episodeNumber
                    )
                )
            }

            _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched)
        }
    }
}
