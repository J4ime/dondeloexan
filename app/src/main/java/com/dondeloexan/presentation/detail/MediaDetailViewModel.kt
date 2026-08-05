package com.dondeloexan.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.detail.CastSocialInfo
import com.dondeloexan.domain.model.detail.CascadeProposal
import com.dondeloexan.domain.model.detail.EpisodeToggleResult
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import com.dondeloexan.domain.usecase.MediaDetailUseCases
import com.dondeloexan.domain.usecase.SeriesState
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val content: Content? = null,
    val error: String? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Int = 0,
    val seasonDetail: SeasonDetail? = null,
    val watchedEpisodes: Set<String> = emptySet(),
    val cascadeProposal: CascadeProposal? = null,
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null,
    val isMovieWatched: Boolean? = null,
    val isMovieFavorite: Boolean? = null,
    val criticReviews: List<CriticReview>? = null,
    val isCriticReviewsLoading: Boolean = false,
    val collectionMovies: List<ContentPreview>? = null,
    val isCollectionLoading: Boolean = false,
    val similarContent: List<ContentPreview>? = null,
    val isSimilarLoading: Boolean = false,
    val seriesRelationships: List<ContentPreview>? = null,
    val isSeriesRelationshipsLoading: Boolean = false,
    val seriesRelationshipTargetIds: Set<String> = emptySet()
)

class MediaDetailViewModel(
    private val useCases: MediaDetailUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var seasonJob: Job? = null
    private val personSocialCache = mutableMapOf<Int, CastSocialInfo>()
    private val _castSocialInfo = MutableStateFlow<Map<Int, CastSocialInfo>>(emptyMap())
    val castSocialInfo: StateFlow<Map<Int, CastSocialInfo>> = _castSocialInfo.asStateFlow()

    fun getPersonSocialUrl(personId: Int, onUrl: (String?) -> Unit) {
        viewModelScope.launch {
            val cached = personSocialCache[personId]
            val social = if (cached != null) cached
            else {
                try {
                    useCases.getPersonSocialInfo(personId)?.also { personSocialCache[personId] = it }
                } catch (e: Exception) {
                    null
                }
            }
            onUrl(social?.url)
        }
    }

    private fun loadCastSocialInfo(cast: List<com.dondeloexan.domain.model.PersonInfo>) {
        cast.forEach { person ->
            val tmdbId = person.tmdbId ?: return@forEach
            if (personSocialCache.containsKey(tmdbId)) {
                val cached = personSocialCache[tmdbId]
                if (cached != null) {
                    _castSocialInfo.value = _castSocialInfo.value + (tmdbId to cached)
                }
                return@forEach
            }
            viewModelScope.launch {
                val social = try {
                    useCases.getPersonSocialInfo(tmdbId)
                } catch (e: Exception) {
                    AppLogger.w("DetailVM", "personExternalIds failed for tmdb=$tmdbId: ${e.message}")
                    null
                }
                if (social != null) {
                    personSocialCache[tmdbId] = social
                    _castSocialInfo.value = _castSocialInfo.value + (tmdbId to social)
                }
            }
        }
    }

    private fun loadCriticReviews(content: Content) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCriticReviewsLoading = true)
            try {
                val reviews = useCases.getCriticReviews(content)
                AppLogger.i("DetailVM", "loadCriticReviews: got ${reviews.size} reviews")
                if (reviews.isNotEmpty()) {
                    val faId = useCases.getFaId(content)
                    if (faId != null) {
                        val faUrl = "https://www.filmaffinity.com/es/film$faId.html"
                        val currentLinks = _uiState.value.content?.externalLinks
                        val updatedLinks = currentLinks?.copy(filmaffinityUrl = faUrl)
                            ?: ExternalLinks(filmaffinityUrl = faUrl)
                        _uiState.value = _uiState.value.copy(
                            content = _uiState.value.content?.copy(externalLinks = updatedLinks)
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    criticReviews = reviews,
                    isCriticReviewsLoading = false
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading critic reviews", e)
                _uiState.value = _uiState.value.copy(
                    criticReviews = emptyList(),
                    isCriticReviewsLoading = false
                )
            }
        }
    }

    private fun loadFaMovieData(content: Content) {
        if (content.type != ContentType.MOVIE) return
        viewModelScope.launch {
            try {
                val (rating, releases) = useCases.getFaMovieData(content)
                if (rating != null || releases.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content?.copy(
                            ratingFilmaffinity = rating,
                            platformReleaseDates = releases
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading FA movie data", e)
            }
        }
    }

    private fun loadCollection(content: Content) {
        if (content.type != ContentType.MOVIE) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCollectionLoading = true)
            try {
                val movies = useCases.getCollectionMovies(content)
                _uiState.value = _uiState.value.copy(
                    collectionMovies = movies,
                    isCollectionLoading = false
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading collection", e)
                _uiState.value = _uiState.value.copy(
                    collectionMovies = emptyList(),
                    isCollectionLoading = false
                )
            }
        }
    }

    private fun loadSimilar(content: Content) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSimilarLoading = true)
            try {
                val similar = useCases.getSimilar(content)
                _uiState.value = _uiState.value.copy(
                    similarContent = similar,
                    isSimilarLoading = false
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading similar", e)
                _uiState.value = _uiState.value.copy(
                    similarContent = emptyList(),
                    isSimilarLoading = false
                )
            }
        }
    }

    private fun loadSeriesRelationships(content: Content) {
        if (content.type != ContentType.SERIES) return
        val wikidataId = content.externalLinks?.wikidataId
        val imdbId = content.externalLinks?.imdbId ?: content.imdbId
        if (wikidataId == null && imdbId == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSeriesRelationshipsLoading = true)
            try {
                val (previews, excludeIds) = useCases.getSeriesRelationships(content)
                _uiState.value = _uiState.value.copy(
                    seriesRelationships = previews,
                    isSeriesRelationshipsLoading = false,
                    seriesRelationshipTargetIds = excludeIds
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading series relationships", e)
                _uiState.value = _uiState.value.copy(
                    seriesRelationships = emptyList(),
                    isSeriesRelationshipsLoading = false
                )
            }
        }
    }

    fun loadContent(contentId: String, contentType: ContentType = ContentType.MOVIE) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)

            try {
                useCases.getDetail(contentId, contentType).collect { result ->
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
                            } else {
                                viewModelScope.launch {
                                    val movieState = useCases.loadMovieState(content)
                                    _uiState.value = _uiState.value.copy(
                                        isMovieWatched = movieState.isWatched,
                                        isMovieFavorite = movieState.isFavorite
                                    )
                                }
                            }
                            loadCastSocialInfo(content.cast.take(10))
                            loadCriticReviews(content)
                            loadFaMovieData(content)
                            loadCollection(content)
                            loadSimilar(content)
                            loadSeriesRelationships(content)
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

    fun toggleMovieWatched() {
        viewModelScope.launch {
            val content = _uiState.value.content ?: return@launch
            val newState = useCases.toggleMovieWatched(content)
            _uiState.value = _uiState.value.copy(
                isMovieWatched = newState.isWatched,
                isMovieFavorite = newState.isFavorite
            )
        }
    }

    fun toggleMovieFavorite() {
        viewModelScope.launch {
            val content = _uiState.value.content ?: return@launch
            val newState = useCases.toggleMovieFavorite(content)
            _uiState.value = _uiState.value.copy(
                isMovieFavorite = newState.isFavorite,
                isMovieWatched = if (newState.isFavorite) true else _uiState.value.isMovieWatched
            )
        }
    }

    private suspend fun loadSeasons(content: Content) {
        try {
            val seriesState = useCases.loadSeriesState(content)
            _uiState.value = _uiState.value.copy(
                seasons = seriesState.seasons,
                watchedEpisodes = seriesState.tracking.watchedEpisodes,
                lastWatchedSeason = seriesState.tracking.lastWatchedSeason,
                lastWatchedEpisode = seriesState.tracking.lastWatchedEpisode,
                selectedSeason = seriesState.selectedSeason,
                seasonDetail = seriesState.seasonDetail
            )
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
                val seasonDetail = useCases.loadSeasonDetail(content, seasonNumber)
                _uiState.value = _uiState.value.copy(seasonDetail = seasonDetail)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DetailVM", "Error loading season $seasonNumber", e)
            }
        }
    }

    fun toggleEpisodeWatched(episodeNumber: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val content = currentState.content ?: return@launch
            val seasonNumber = currentState.selectedSeason
            val result = useCases.toggleEpisode(
                content = content,
                selectedSeason = seasonNumber,
                episodeNumber = episodeNumber,
                currentWatched = currentState.watchedEpisodes,
                seasonDetail = currentState.seasonDetail
            )
            when (result) {
                is EpisodeToggleResult.NeedsCascade -> {
                    _uiState.value = _uiState.value.copy(cascadeProposal = result.proposal)
                }
                is EpisodeToggleResult.Applied -> {
                    _uiState.value = _uiState.value.copy(
                        watchedEpisodes = result.tracking.watchedEpisodes,
                        cascadeProposal = null
                    )
                }
            }
        }
    }

    fun confirmCascadeWatched() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val proposal = currentState.cascadeProposal ?: return@launch
            val content = currentState.content ?: return@launch
            val tracking = useCases.confirmCascade(
                content = content,
                proposal = proposal,
                seasonDetail = currentState.seasonDetail,
                currentWatched = currentState.watchedEpisodes
            )
            _uiState.value = _uiState.value.copy(
                watchedEpisodes = tracking.watchedEpisodes,
                cascadeProposal = null
            )
        }
    }

    fun dismissCascadeWatched() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val proposal = currentState.cascadeProposal ?: return@launch
            val content = currentState.content ?: return@launch
            val tracking = useCases.dismissCascade(
                content = content,
                proposal = proposal,
                seasonDetail = currentState.seasonDetail
            )
            _uiState.value = _uiState.value.copy(
                watchedEpisodes = tracking.watchedEpisodes,
                cascadeProposal = null
            )
        }
    }

    fun markSeasonWatched() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val content = currentState.content ?: return@launch
            val tracking = useCases.toggleSeasonWatched(
                content = content,
                selectedSeason = currentState.selectedSeason,
                seasonDetail = currentState.seasonDetail
            )
            _uiState.value = _uiState.value.copy(
                watchedEpisodes = tracking.watchedEpisodes,
                cascadeProposal = null
            )
        }
    }
}