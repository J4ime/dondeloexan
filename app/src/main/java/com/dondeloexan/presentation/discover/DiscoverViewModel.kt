package com.dondeloexan.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.domain.repository.LibraryAction
import com.dondeloexan.domain.repository.LibraryMutationResult
import com.dondeloexan.domain.repository.LibraryRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.PersonFlagUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.domain.model.CompanySearchResult
import com.dondeloexan.domain.model.PersonSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiscoverViewModel(
    private val discoverRepository: DiscoverRepository,
    private val libraryRepository: LibraryRepository,
    private val tmdbApi: TmdbApi,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Initial)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _filterByPlatforms = MutableStateFlow(true)
    val filterByPlatforms: StateFlow<Boolean> = _filterByPlatforms.asStateFlow()

    val activePlatforms: StateFlow<Set<String>> = libraryRepository.activePlatforms
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val blacklistedIds: StateFlow<Set<String>> = libraryRepository.blacklistedIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val likedIds: StateFlow<Set<String>> = libraryRepository.likedIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val watchedIds: StateFlow<Set<String>> = libraryRepository.watchedIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val allIds: StateFlow<Set<String>> = libraryRepository.libraryIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var currentPage = 1
    private var hasMorePages = true
    private var isFilling = false
    private var hasError = false
    private var isSearching = false
    private var cachedResults = listOf<ContentPreview>()
    private var lastCompanySearchResults: List<CompanySearchResult> = emptyList()
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
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.e("DiscoverVM", "filmography movie credits for $rawId", e)
                                null
                            }
                            val tvCredits = try {
                                tmdbApi.getPersonTvCredits(rawId)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.e("DiscoverVM", "filmography tv credits for $rawId", e)
                                null
                            }
                            val movieList = if (movieCredits != null) {
                                val filtered = when (entity.role) {
                                    "Actor", "Actriz" -> movieCredits.cast.orEmpty()
                                    "Director", "Directora" -> movieCredits.crew.orEmpty().filter { it.job == "Director" }
                                    else -> movieCredits.cast.orEmpty() + movieCredits.crew.orEmpty()
                                }
                                filtered
                                    .filter { it.releaseDate != null }
                                    .distinctBy { it.id }
                                    .map { it.toContentPreview(forceType = com.dondeloexan.domain.model.ContentType.MOVIE) }
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
                                    .map { it.toContentPreview(forceType = com.dondeloexan.domain.model.ContentType.SERIES) }
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
            isSearching = true
            currentPage = 1
            hasMorePages = true
            cachedResults = emptyList()
            _uiState.value = DiscoverUiState.Loading

            try {
                withTimeout(15_000) {
                    performSearch(query)
                }
            } catch (e: TimeoutCancellationException) {
                AppLogger.e("DiscoverVM", "search '$query' excedió 15s; se muestra error", e)
                hasError = true
                _uiState.value = DiscoverUiState.Error("La búsqueda tardó demasiado; inténtalo de nuevo")
            }
        }
    }

    private suspend fun CoroutineScope.performSearch(query: String) {
        try {
            trendingJob?.cancel()

            val searchDeferred = async {
                try {
                    discoverRepository.fetchSearchPage(query, 1)
                        .filter { it.id !in excludedIds() }
                        .filter { it.ratingImdb != null && it.ratingImdb >= 6.0f }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "search error for $query", e)
                    hasError = true
                    emptyList()
                }
            }
            val peopleDeferred = async {
                try {
                    discoverRepository.searchPeople(query)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "searchPeople error for $query", e)
                    emptyList()
                }
            }
            val companiesDeferred = async {
                try {
                    discoverRepository.searchCompanies(query)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "searchCompanies error for $query", e)
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "personDetail for ${p.id}", e)
                    null
                }
                val isFemale = personDetail?.gender == 1
                val creditsResponse = try {
                    tmdbApi.getPersonMovieCredits(p.id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DiscoverVM", "personMovieCredits for ${p.id}", e)
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("DiscoverVM", "performSearch error for $query", e)
            hasError = true
            _uiState.value = DiscoverUiState.Error("Error al buscar: ${e.message ?: "desconocido"}")
        }
    }
    private fun removeAndEmit(contentId: String) {
        cachedResults = cachedResults.filter { it.id != contentId }
        if (_uiState.value is DiscoverUiState.Success) {
            emitWithFaData(DiscoverUiState.Success(cachedResults))
        }
    }

    fun onToggleFavorite(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                val result = libraryRepository.toggleFavorite(preview)
                applyMutation(result, preview)
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Toggle favorite error", e)
            }
        }
    }

    fun onToggleAdd(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                val result = libraryRepository.toggleInLibrary(preview)
                applyMutation(result, preview)
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Toggle add error", e)
            }
        }
    }

    fun onToggleWatched(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                val result = libraryRepository.toggleWatched(preview)
                applyMutation(result, preview)
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Toggle watched error", e)
            }
        }
    }

    fun onToggleBlacklist(preview: ContentPreview) {
        viewModelScope.launch {
            try {
                val result = libraryRepository.toggleBlacklist(preview)
                applyMutation(result, preview)
            } catch (e: Exception) {
                AppLogger.e("DiscoverVM", "Blacklist error", e)
            }
        }
    }

    private suspend fun applyMutation(result: LibraryMutationResult, preview: ContentPreview) {
        val message = when (result.action) {
            LibraryAction.FAVORITE ->
                if (result.toggledOn) {
                    if (result.isMovie) "Película marcada como favorita" else "Serie añadida"
                } else {
                    if (result.isMovie) "Película quitada de favoritas" else "Serie quitada"
                }
            LibraryAction.ADD ->
                if (result.isMovie) "Película añadida a pendientes" else "Serie añadida a pendientes"
            LibraryAction.WATCHED ->
                if (result.toggledOn) {
                    if (result.isMovie) "Película marcada como vista" else "Serie marcada como vista"
                } else {
                    if (result.isMovie) "Película quitada de vistos" else "Serie quitada de vistos"
                }
            LibraryAction.BLACKLIST -> "${result.title} ocultado"
        }
        feedbackManager.emit(message)
        val shouldRemove = result.action == LibraryAction.BLACKLIST ||
            (result.action != LibraryAction.ADD && result.toggledOn)
        if (shouldRemove) removeAndEmit(preview.id)
    }

    fun loadNextPage() {
        if (isFilling) return
        isFilling = true
        _isLoadingMore.value = true
        viewModelScope.launch {
            val query = _searchQuery.value
            val target = cachedResults.size + 10
            if (isSearching && query.length >= 3) {
                fillSearchPagesUntil(target)
            } else {
                fillPagesUntil(target)
            }
            isFilling = false
            _isLoadingMore.value = false
            if (cachedResults.isEmpty()) {
                _uiState.value = DiscoverUiState.Empty(query)
            } else {
                emitWithFaData(DiscoverUiState.Success(cachedResults))
            }
        }
    }

    fun onClearSearch() {
        _searchQuery.value = ""
        _filmographyView.value = null
        searchJob?.cancel()
        isSearching = false
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
        isSearching = false
        trendingJob = viewModelScope.launch {
            currentPage = 1
            hasMorePages = true
            cachedResults = emptyList()
            _uiState.value = DiscoverUiState.Loading

            try {
                withTimeout(25_000) { fillPagesUntil(10) }
            } catch (e: TimeoutCancellationException) {
                AppLogger.e("DiscoverVM", "trending excedió 25s; se marca error", e)
                hasError = true
            }

            when {
                cachedResults.isNotEmpty() -> emitWithFaData(DiscoverUiState.Success(cachedResults))
                hasError -> _uiState.value = DiscoverUiState.Error("No se pudo conectar con el servidor, prueba a deslizar para reintentar")
                else -> _uiState.value = DiscoverUiState.Empty("")
            }
        }
    }

    private suspend fun fillSearchPagesUntil(minItems: Int) {
        val query = _searchQuery.value
        var emptyPageCount = 0
        while (cachedResults.size < minItems && hasMorePages) {
            val next = currentPage + 1
            val page = fetchSearchPageFiltered(query, next)
            if (page.isNotEmpty()) {
                currentPage = next
                val before = cachedResults.size
                cachedResults = (cachedResults + page).distinctBy { it.id }
                if (cachedResults.size > before) emptyPageCount = 0 else emptyPageCount++
                if (emptyPageCount >= 3) hasMorePages = false
            } else {
                currentPage = next
                emptyPageCount++
                if (emptyPageCount >= 3) hasMorePages = false
            }
        }
    }

    private suspend fun fetchSearchPageFiltered(query: String, page: Int): List<ContentPreview> {
        return try {
            discoverRepository.fetchSearchPage(query, page)
                .filter { it.id !in excludedIds() }
                .filter { it.ratingImdb != null && it.ratingImdb >= 6.0f }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("DiscoverVM", "fetchSearchPage error page=$page", e)
            hasError = true
            emptyList()
        }
    }

    private suspend fun excludedIds(): Set<String> =
        libraryRepository.discoverExcludedIds()

    private suspend fun fillPagesUntil(minItems: Int) {
        var emptyPageCount = 0
        while (cachedResults.size < minItems && hasMorePages) {
            val next = if (currentPage == 1 && cachedResults.isEmpty()) 1 else currentPage + 1
            val page = fetchTrendingSinglePage(next)
            if (page.isNotEmpty()) {
                currentPage = next
                val before = cachedResults.size
                cachedResults = (cachedResults + page).distinctBy { it.id }
                if (cachedResults.size > before) emptyPageCount = 0 else emptyPageCount++
                if (emptyPageCount >= 3) hasMorePages = false
            } else {
                currentPage = next
                emptyPageCount++
                if (emptyPageCount >= 3) hasMorePages = false
            }
        }
    }

    private suspend fun fetchTrendingSinglePage(page: Int): List<ContentPreview> {
        val filterByPlatforms = _filterByPlatforms.value
        val excluded = excludedIds()
        return try {
            val results = discoverRepository.fetchTrendingPage(page, filterByPlatforms)
            results.filter { it.id !in excluded }
                .filter { it.ratingImdb != null && it.ratingImdb >= 6.0f }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("DiscoverVM", "fetchTrendingPage error page=$page", e)
            hasError = true
            emptyList()
        }
    }
    private fun emitWithFaData(state: DiscoverUiState.Success) {
        _uiState.value = state
        viewModelScope.launch {
            val enriched = state.results.map { preview ->
                val releases = libraryRepository.faReleases(preview.id)
                if (releases.isNotEmpty()) preview.copy(platformReleaseDates = releases) else preview
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
