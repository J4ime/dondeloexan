package com.dondeloexan.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbPersonExternalIdsDto
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.data.remote.mapper.toTmdb
import com.dondeloexan.data.remote.mapper.toTmdbSeasonDto
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.PersonInfo
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.PlatformReleaseDate
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

data class CascadeProposal(
    val season: Int,
    val targetEpisode: Int,
    val count: Int
)

data class CastSocialInfo(val url: String, val type: SocialLinkType)
enum class SocialLinkType { INSTAGRAM, TWITTER, FACEBOOK, YOUTUBE }

class MediaDetailViewModel(
    private val discoverRepository: DiscoverRepository,
    private val tmdbApi: TmdbApi,
    private val imdbApi: BalloonerismmApi,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var seasonJob: Job? = null
    private val personSocialCache = mutableMapOf<Int, TmdbPersonExternalIdsDto>()
    private val _castSocialInfo = MutableStateFlow<Map<Int, CastSocialInfo>>(emptyMap())
    val castSocialInfo: StateFlow<Map<Int, CastSocialInfo>> = _castSocialInfo.asStateFlow()

    fun getPersonSocialUrl(personId: Int, onUrl: (String?) -> Unit) {
        viewModelScope.launch {
            val cached = personSocialCache[personId]
            val social = if (cached != null) cached
            else try {
                tmdbApi.getPersonExternalIds(personId).also { personSocialCache[personId] = it }
            } catch (e: Exception) {
                null
            }
            onUrl(social?.instagramId?.let { "https://instagram.com/$it/" }
                ?: social?.twitterId?.let { "https://x.com/$it/" }
                ?: social?.facebookId?.let { "https://facebook.com/$it/" }
                ?: social?.youtubeId?.let { "https://www.youtube.com/channel/$it" })
        }
    }

    private fun loadCastSocialInfo(cast: List<PersonInfo>) {
        cast.forEach { person ->
            val tmdbId = person.tmdbId ?: return@forEach
            viewModelScope.launch {
                val social = try {
                    tmdbApi.getPersonExternalIds(tmdbId)
                } catch (e: Exception) {
                    AppLogger.w("DetailVM", "personExternalIds failed for tmdb=$tmdbId: ${e.message}")
                    null
                }
                if (social != null) {
                    personSocialCache[tmdbId] = social
                    val url = social.instagramId?.let { "https://instagram.com/$it/" }
                        ?: social.twitterId?.let { "https://x.com/$it/" }
                        ?: social.facebookId?.let { "https://facebook.com/$it/" }
                        ?: social.youtubeId?.let { "https://www.youtube.com/channel/$it" }
                    val type = when {
                        social.instagramId != null -> SocialLinkType.INSTAGRAM
                        social.twitterId != null -> SocialLinkType.TWITTER
                        social.facebookId != null -> SocialLinkType.FACEBOOK
                        social.youtubeId != null -> SocialLinkType.YOUTUBE
                        else -> null
                    }
                    if (url != null && type != null) {
                        _castSocialInfo.value = _castSocialInfo.value + (tmdbId to CastSocialInfo(url, type))
                    }
                }
            }
        }
    }

    private fun loadCriticReviews(content: Content) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCriticReviewsLoading = true)
            try {
                val title = content.originalTitle ?: content.title
                val year = content.year
                AppLogger.i("DetailVM", "loadCriticReviews: calling getCriticReviews(id=${content.id}, title=$title, year=$year)")
                val reviews = discoverRepository.getCriticReviews(content.id, title, year)
                AppLogger.i("DetailVM", "loadCriticReviews: got ${reviews.size} reviews")
                if (reviews.isNotEmpty()) {
                    val faId = movieDao.getByContentId(content.id)?.faId
                        ?: tvShowDao.getByContentId(content.id)?.faId
                    AppLogger.i("DetailVM", "loadCriticReviews: faId from entity = $faId")
                    if (faId != null) {
                        val faUrl = "https://www.filmaffinity.com/es/film$faId.html"
                        val currentLinks = _uiState.value.content?.externalLinks
                        val updatedLinks = currentLinks?.copy(filmaffinityUrl = faUrl)
                            ?: ExternalLinks(filmaffinityUrl = faUrl)
                        _uiState.value = _uiState.value.copy(
                            content = _uiState.value.content?.copy(externalLinks = updatedLinks)
                        )
                        AppLogger.i("DetailVM", "loadCriticReviews: set FA URL = $faUrl")
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
                val title = content.originalTitle ?: content.title
                val (rating, releases) = discoverRepository.getFaMovieData(content.id, title, content.year)
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
        val collectionId = content.collectionTmdbId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCollectionLoading = true)
            try {
                val movies = discoverRepository.getCollectionMovies(collectionId)
                    .filter { it.tmdbId != content.tmdbId }
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
                val similar = discoverRepository.getRecommendations(content.id, content.type)
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
        AppLogger.d("DetailVM", "loadSeriesRelationships — wikidataId=$wikidataId, imdbId=$imdbId")
        if (wikidataId == null && imdbId == null) {
            AppLogger.d("DetailVM", "No wikidataId nor imdbId, skipping")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSeriesRelationshipsLoading = true)
            try {
                val (previews, excludeIds) = discoverRepository.getSeriesRelationships(wikidataId, imdbId)
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
                            } else {
                                val existing = movieDao.getByContentId(content.id)
                                    ?: content.tmdbId?.let { movieDao.getByTmdbId(it) }
                                    ?: content.imdbId?.let { movieDao.getByImdbId(it) }
                                _uiState.value = _uiState.value.copy(
                                    isMovieWatched = existing?.status == WatchStatus.YA_VISTA,
                                    isMovieFavorite = existing?.liked == true
                                )
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
            val wasWatched = _uiState.value.isMovieWatched ?: false
            val newStatus = if (wasWatched) WatchStatus.POR_VER else WatchStatus.YA_VISTA

            val existing = movieDao.getByContentId(content.id)
                ?: content.tmdbId?.let { movieDao.getByTmdbId(it) }
                ?: content.imdbId?.let { movieDao.getByImdbId(it) }

            if (existing != null) {
                movieDao.update(
                    existing.copy(
                        status = newStatus,
                        watchedAt = if (wasWatched) null else System.currentTimeMillis()
                    )
                )
            } else {
                movieDao.insert(
                    MovieEntity(
                        contentId = content.id,
                        tmdbId = content.tmdbId,
                        imdbId = content.imdbId,
                        title = content.title,
                        year = content.year,
                        releaseDate = content.releaseDate,
                        posterUrl = content.coverUrl,
                        ratingImdb = content.ratingImdb,
                        ratingTmdb = content.ratingTmdb,
                        status = WatchStatus.YA_VISTA,
                        watchedAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.value = _uiState.value.copy(isMovieWatched = !wasWatched)
        }
    }

    fun toggleMovieFavorite() {
        viewModelScope.launch {
            val content = _uiState.value.content ?: return@launch
            val wasFavorite = _uiState.value.isMovieFavorite ?: false
            val newLiked = !wasFavorite

            val existing = movieDao.getByContentId(content.id)
                ?: content.tmdbId?.let { movieDao.getByTmdbId(it) }
                ?: content.imdbId?.let { movieDao.getByImdbId(it) }

            if (existing != null) {
                movieDao.update(existing.copy(
                    liked = newLiked,
                    status = if (newLiked) WatchStatus.YA_VISTA else existing.status,
                    watchedAt = if (newLiked) (existing.watchedAt ?: System.currentTimeMillis()) else existing.watchedAt
                ))
            } else {
                movieDao.insert(
                    MovieEntity(
                        contentId = content.id,
                        tmdbId = content.tmdbId,
                        imdbId = content.imdbId,
                        title = content.title,
                        year = content.year,
                        releaseDate = content.releaseDate,
                        posterUrl = content.coverUrl,
                        ratingImdb = content.ratingImdb,
                        ratingTmdb = content.ratingTmdb,
                        liked = true,
                        status = WatchStatus.YA_VISTA,
                        watchedAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.value = _uiState.value.copy(isMovieFavorite = newLiked, isMovieWatched = if (newLiked) true else _uiState.value.isMovieWatched)
        }
    }

    private suspend fun loadSeasons(content: Content) {
        try {
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
            val progress = tvShowProgressDao.getByTvShowId(tvShow.id)
            val currentWatched = progress.map { "S${it.season}E${it.episode}" }.toMutableSet()

            val alreadyWatched = episodeNumbers.all { epNum ->
                currentWatched.contains("S${seasonNumber}E${epNum}")
            }

            if (alreadyWatched) {
                episodeNumbers.forEach { epNum ->
                    val key = "S${seasonNumber}E${epNum}"
                    currentWatched.remove(key)
                    tvShowProgressDao.deleteEpisode(tvShow.id, seasonNumber, epNum)
                }
                val lastWatched = tvShowProgressDao.getLastWatchedAt(tvShow.id)
                tvShowDao.updateLastWatchedAt(tvShow.id, lastWatched)
            } else {
                val items = episodeNumbers.filter { epNum ->
                    !currentWatched.contains("S${seasonNumber}E${epNum}")
                }.map { epNum ->
                    currentWatched.add("S${seasonNumber}E${epNum}")
                    TvShowProgressEntity(
                        tvShowId = tvShow.id,
                        season = seasonNumber,
                        episode = epNum
                    )
                }
                if (items.isNotEmpty()) {
                    tvShowProgressDao.insertAll(items)
                }
                tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
                val lastEp = episodeNumbers.maxOrNull() ?: 0
                if (lastEp > 0) checkAndMarkFinale(seasonNumber, lastEp)
            }

            _uiState.value = _uiState.value.copy(watchedEpisodes = currentWatched, cascadeProposal = null)
        }
    }
}
