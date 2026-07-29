package com.dondeloexan.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
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
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.PersonFlagUtil
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.dondeloexan.data.remote.dto.TmdbCompanySearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResult
import com.dondeloexan.data.remote.mapper.toContentPreview
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao,
    private val blacklistDao: BlacklistDao,
    private val tmdbApi: TmdbApi,
    private val feedbackManager: FeedbackManager,
    private val faMovieDataDao: FaMovieDataDao
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
    private var lastCompanySearchResults: List<TmdbCompanySearchResult> = emptyList()
    private var filmographyCache = listOf<ContentPreview>()
    private var filmographyPage = 0
    private var searchJob: Job? = null
    private var trendingJob: Job? = null

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    init {
        loadTrending()
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
        viewModelScope.launch {
            watchedIds.drop(1).collect { newWatched ->
                cachedResults = cachedResults.filter { it.id !in newWatched }
                if (_uiState.value is DiscoverUiState.Success) {
                    emitWithFaData(DiscoverUiState.Success(cachedResults))
                }
            }
        }
        viewModelScope.launch {
            likedIds.drop(1).collect { newLiked ->
                cachedResults = cachedResults.filter { it.id !in newLiked }
                if (_uiState.value is DiscoverUiState.Success) {
                    emitWithFaData(DiscoverUiState.Success(cachedResults))
                }
            }
        }
    }

    private val _filmographyView = MutableStateFlow<FilmographyView?>(null)
    val filmographyView: StateFlow<FilmographyView?> = _filmographyView.asStateFlow()

    fun onFilmographyBack() {
        _filmographyView.value = null
    }

    fun onSelectEntity(entity: FilmographyEntity) {
        _filmographyView.value = FilmographyView(entity = entity, isLoading = true)
        filmographyPage = 0
        viewModelScope.launch {
            val blacklisted = blacklistedIds.value
            val raw = when (entity.type) {
                EntityType.PERSON -> {
                    val rawId = entity.id.removePrefix("person-").substringBefore("-").toIntOrNull()
                    if (rawId != null) {
                        if (entity.role != null) {
                            val movieCredits = try {
                                tmdbApi.getPersonMovieCredits(rawId)
                            } catch (e: Exception) { null }
                            val tvCredits = try {
                                tmdbApi.getPersonTvCredits(rawId)
                            } catch (e: Exception) { null }
                            val movieList = if (movieCredits != null) {
                                val filtered = when (entity.role) {
                                    "Actor", "Actriz" -> movieCredits.cast.orEmpty()
                                    "Director", "Directora" -> movieCredits.crew.orEmpty().filter { it.job == "Director" }
                                    else -> movieCredits.cast.orEmpty() + movieCredits.crew.orEmpty()
                                }
                                filtered
                                    .filter { it.releaseDate != null }
                                    .distinctBy { it.id }
                                    .map { it.toContentPreview() }
                            } else emptyList()
                            val tvList = if (tvCredits != null) {
                                val filtered = when (entity.role) {
                                    "Actor", "Actriz" -> tvCredits.cast.orEmpty()
                                    "Director", "Directora" -> tvCredits.crew.orEmpty().filter { it.job == "Director" }
                                    else -> tvCredits.cast.orEmpty() + tvCredits.crew.orEmpty()
                                }
                                filtered
                                    .filter { it.firstAirDate != null }
                                    .distinctBy { it.id }
                                    .map { it.toContentPreview() }
                            } else emptyList()
                            (movieList + tvList).sortedByDescending { it.releaseDate }
                        } else {
                            val movies = discoverRepository.getPersonMovieCredits(rawId)
                            val tvs = discoverRepository.getPersonTvCredits(rawId)
                            (movies + tvs).sortedByDescending { it.releaseDate }
                        }
                    } else emptyList()
                }
                EntityType.COMPANY -> {
                    val firstId = entity.id.removePrefix("company-").toIntOrNull()
                    if (firstId != null) {
                        val idsToTry = mutableListOf(firstId)
                        idsToTry.addAll(
                            lastCompanySearchResults
                                .filter { it.name == entity.name && it.id != firstId && it.logoPath != null }
                                .map { it.id }
                        )
                        var all = emptyList<ContentPreview>()
                        for (id in idsToTry) {
                            val movies = discoverRepository.getCompanyMovies(id)
                            val tvs = discoverRepository.getCompanyTvShows(id)
                            val combined = (movies + tvs).sortedByDescending { it.releaseDate }
                            if (combined.isNotEmpty()) { all = combined; break }
                        }
                        all
                    } else emptyList()
                }
            }

            val filtered = raw.filter { it.id !in blacklisted }
            filmographyCache = discoverRepository.fetchPlatforms(filtered)
            filmographyPage = 1
            val pageSize = 10
            val shown = filmographyCache.take(pageSize)
            _filmographyView.value = FilmographyView(
                entity = entity,
                movies = shown,
                isLoading = false,
                hasMore = filmographyCache.size > pageSize,
                totalCount = filmographyCache.size
            )
        }
    }

    fun onFilmographyLoadMore() {
        val view = _filmographyView.value ?: return
        if (!view.hasMore || view.isLoading) return
        filmographyPage++
        val pageSize = 10
        val shown = filmographyCache.take(filmographyPage * pageSize)
        _filmographyView.value = view.copy(
            movies = shown,
            isLoading = false,
            hasMore = shown.size < filmographyCache.size
        )
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _filmographyView.value = null

        if (query.isBlank() || query.length < 3) {
            if (query.isBlank()) {
                searchJob?.cancel()
                loadTrending()
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(2000)
            trendingJob?.cancel()
            currentPage = 1
            hasMorePages = true
            cachedResults = emptyList()
            _uiState.value = DiscoverUiState.Loading

            val searchDeferred = async {
                try {
                    discoverRepository.fetchSearchPage(query, 1)
                        .filter { it.ratingImdb != null && it.ratingImdb >= 6.0f }
                } catch (e: Exception) {
                    hasError = true
                    emptyList()
                }
            }
            val peopleDeferred = async {
                try {
                    discoverRepository.searchPeople(query)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val companiesDeferred = async {
                try {
                    discoverRepository.searchCompanies(query)
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val page = searchDeferred.await()
            val people = peopleDeferred.await()
            val companies = companiesDeferred.await()
            lastCompanySearchResults = companies

            val suggestions = mutableListOf<FilmographyEntity>()
            people.take(5).forEach { p ->
                val roles = mutableSetOf<String>()
                val personDetail = try {
                    tmdbApi.getPersonDetail(p.id)
                } catch (e: Exception) {
                    null
                }
                val isFemale = personDetail?.gender == 1
                val creditsResponse = try {
                    tmdbApi.getPersonMovieCredits(p.id)
                } catch (e: Exception) {
                    null
                }
                if (creditsResponse != null) {
                    if (creditsResponse.cast.orEmpty().isNotEmpty()) {
                        roles.add(if (isFemale) "Actriz" else "Actor")
                    }
                    creditsResponse.crew.orEmpty().forEach { credit ->
                        if (credit.job == "Director") {
                            roles.add(if (isFemale) "Directora" else "Director")
                        }
                    }
                }
                val flag = PersonFlagUtil.countryFlag(personDetail?.placeOfBirth)
                val countryStr = if (flag.isNotEmpty()) " $flag" else ""
                val ageN = PersonFlagUtil.age(personDetail?.birthday, personDetail?.deathday)
                val ageStr = if (ageN != null) " · $ageN" else ""
                if (roles.isEmpty()) {
                    suggestions.add(
                        FilmographyEntity(
                            id = "person-${p.id}",
                            name = "$countryStr${p.name}$ageStr",
                            type = EntityType.PERSON,
                            profilePath = p.profilePath,
                            knownForDepartment = p.knownForDepartment
                        )
                    )
                } else {
                    roles.sorted().forEach { role ->
                        suggestions.add(
                            FilmographyEntity(
                                id = "person-${p.id}-${role.lowercase().replace(" ", "-")}",
                                name = "$countryStr${p.name} ($role)$ageStr",
                                type = EntityType.PERSON,
                                profilePath = p.profilePath,
                                knownForDepartment = p.knownForDepartment,
                                role = role
                            )
                        )
                    }
                }
            }
            companies.filter { it.logoPath != null }.take(3).forEach { c ->
                suggestions.add(
                    FilmographyEntity(
                        id = "company-${c.id}",
                        name = c.name,
                        type = EntityType.COMPANY,
                        profilePath = c.logoPath,
                        knownForDepartment = null
                    )
                )
            }

            cachedResults = page
            if (cachedResults.isEmpty() && suggestions.isEmpty()) {
                _uiState.value = DiscoverUiState.Empty(query)
            } else {
                emitWithFaData(DiscoverUiState.Success(cachedResults, personSuggestions = suggestions))
            }
        }
    }
    private fun removeAndEmit(contentId: String) {
        cachedResults = cachedResults.filter { it.id != contentId }
        if (_uiState.value is DiscoverUiState.Success) {
            emitWithFaData(DiscoverUiState.Success(cachedResults))
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
            providers.results?.get("ES")?.toStreamingAvailability().orEmpty()
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
                                status = if (newLiked) WatchStatus.YA_VISTA else existing.status,
                                watchedAt = if (newLiked) (existing.watchedAt ?: System.currentTimeMillis()) else existing.watchedAt,
                                ratingImdb = preview.ratingImdb ?: existing.ratingImdb,
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
                                    ratingImdb = preview.ratingImdb,
                                    streamingPlatforms = platformsStr,
                                    liked = true,
                                    status = WatchStatus.YA_VISTA,
                                    watchedAt = System.currentTimeMillis()
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
                                    liked = if (wasWatched) false else existing.liked,
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
                                    ratingImdb = preview.ratingImdb,
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
                                tvShowDao.update(existing.copy(status = WatchStatus.POR_VER, liked = false))
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
        if (isFilling) return
        isFilling = true
        _isLoadingMore.value = true
        viewModelScope.launch {
            val target = cachedResults.size + 10
            fillPagesUntil(target)
            isFilling = false
            _isLoadingMore.value = false
            if (cachedResults.isEmpty()) {
                _uiState.value = DiscoverUiState.Empty(_searchQuery.value)
            } else {
                emitWithFaData(DiscoverUiState.Success(cachedResults))
            }
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
        _filmographyView.value = null
        searchJob?.cancel()
        currentPage = 1
        hasMorePages = true
        cachedResults = emptyList()
        loadTrending()
    }

    fun onRetry() {
        val query = _searchQuery.value
        if (query.isBlank()) {
            trendingJob?.cancel()
            trendingJob = viewModelScope.launch {
                _uiState.value = DiscoverUiState.Loading
                cachedResults = emptyList()
                if (hasMorePages) currentPage++
                fillPagesUntil(10)
                if (cachedResults.isEmpty()) currentPage = 1
                when {
                    cachedResults.isNotEmpty() -> emitWithFaData(DiscoverUiState.Success(cachedResults))
                    hasError -> _uiState.value = DiscoverUiState.Error("No se pudo conectar con el servidor, prueba a deslizar para reintentar")
                    else -> _uiState.value = DiscoverUiState.Empty("")
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

            fillPagesUntil(10)

            when {
                cachedResults.isNotEmpty() -> emitWithFaData(DiscoverUiState.Success(cachedResults))
                hasError -> _uiState.value = DiscoverUiState.Error("No se pudo conectar con el servidor, prueba a deslizar para reintentar")
                else -> _uiState.value = DiscoverUiState.Empty("")
            }
        }
    }

    private suspend fun fillPagesUntil(minItems: Int) {
        var emptyPageCount = 0
        while (cachedResults.size < minItems && hasMorePages) {
            val next = if (currentPage == 1 && cachedResults.isEmpty()) 1 else currentPage + 1
            val page = fetchTrendingSinglePage(next)
            if (page.isNotEmpty()) {
                currentPage = next
                cachedResults = cachedResults + page
                emptyPageCount = 0
            } else {
                currentPage = next
                emptyPageCount++
                if (emptyPageCount >= 3) hasMorePages = false
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
    private fun faReleasesFromJson(json: String?): List<PlatformReleaseDate> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlatformReleaseDate(
                    platformName = obj.getString("platformName"),
                    dateLabel = obj.optString("dateLabel", ""),
                    releaseDate = obj.optString("releaseDate", "").ifEmpty { null }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun emitWithFaData(state: DiscoverUiState.Success) {
        _uiState.value = state
        viewModelScope.launch {
            val enriched = state.results.map { preview ->
                val faData = faMovieDataDao.getByContentId(preview.id)
                if (faData != null) {
                    val releases = faReleasesFromJson(faData.platformReleasesJson)
                    if (releases.isNotEmpty()) preview.copy(platformReleaseDates = releases) else preview
                } else preview
            }
            if (enriched.any { it.platformReleaseDates.isNotEmpty() }) {
                cachedResults = enriched
                _uiState.value = when (state) {
                    is DiscoverUiState.Success -> state.copy(results = enriched, personSuggestions = state.personSuggestions)
                    else -> state
                }
            }
        }
    }
}

sealed interface DiscoverUiState {
    data object Initial : DiscoverUiState
    data object Loading : DiscoverUiState
    data class Empty(val query: String) : DiscoverUiState
    data class Error(val message: String) : DiscoverUiState
    data class Success(
        val results: List<ContentPreview>,
        val personSuggestions: List<FilmographyEntity> = emptyList()
    ) : DiscoverUiState
}

enum class EntityType { PERSON, COMPANY }

data class FilmographyEntity(
    val id: String,
    val name: String,
    val type: EntityType,
    val profilePath: String?,
    val knownForDepartment: String?,
    val role: String? = null
)

data class FilmographyView(
    val entity: FilmographyEntity,
    val movies: List<ContentPreview>? = null,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val totalCount: Int = 0
)
