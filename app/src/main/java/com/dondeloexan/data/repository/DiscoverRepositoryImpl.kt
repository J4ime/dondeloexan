package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.remote.TmdbProviderIds
import com.dondeloexan.data.remote.filmaffinity.FilmaffinityScraper
import com.dondeloexan.data.local.entity.CriticReviewEntity
import com.dondeloexan.data.local.entity.FaMovieDataEntity
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.data.remote.api.WikidataRelationship
import com.dondeloexan.data.remote.dto.TmdbCompanySearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResult
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.data.remote.mapper.toStreamingAvailability as imdbToStreaming
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.Sentiment
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.retryWithBackoff
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class DiscoverRepositoryImpl(
    private val imdbApi: BalloonerismmApi,
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val wikidataApi: WikidataApi,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao? = null,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val filmaffinityScraper: FilmaffinityScraper,
    private val criticReviewDao: CriticReviewDao,
    private val faMovieDataDao: FaMovieDataDao
) : DiscoverRepository {

    private data class CachedPlatforms(
        val platforms: List<StreamingAvailability>,
        val timestamp: Long
    )

    private val platformsCache = ConcurrentHashMap<String, CachedPlatforms>()
    private val CACHE_TTL_MS = 4 * 60 * 60 * 1000L

    private data class CachedRelationships(
        val previews: List<ContentPreview>,
        val excludeIds: Set<String>,
        val timestamp: Long
    )
    private val relationshipsCache = ConcurrentHashMap<String, CachedRelationships>()
    private val RELATIONSHIPS_CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    override suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>> = search(query, 1)

    override suspend fun search(query: String, page: Int): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)

        try {
            val imdbResult = imdbApi.searchMulti(query, page = page)
            val previews = imdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { it.toContentPreview() }
                .take(20)

            if (previews.isNotEmpty()) {
                val withPlatforms = attachImdbPlatforms(previews)
                emit(DataResult.Success(withPlatforms))
            } else {
                val tmdbResult = tmdbApi.searchMulti(query)
                val tmdbPreviews = tmdbResult.results
                    .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            }
        } catch (e: Exception) {
            try {
                val tmdbResult = tmdbApi.searchMulti(query)
                val tmdbPreviews = tmdbResult.results
                    .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            } catch (fallback: Exception) {
                emit(DataResult.Error(e))
            }
        }
    }

    override suspend fun resolveTmdbId(imdbId: String, type: ContentType): Int? {
        return try {
            val externalIds = when (type) {
                ContentType.MOVIE -> imdbApi.getMovieExternalIds(imdbId)
                ContentType.SERIES -> imdbApi.getTvExternalIds(imdbId)
            }
            externalIds.tmdbId
        } catch (_: Exception) {
            AppLogger.e("DiscoverRepo", "resolveTmdbId failed for $imdbId")
            null
        }
    }

    override suspend fun getDetail(contentId: String, contentType: ContentType): Flow<DataResult<Content>> = flow {
        emit(DataResult.Loading)

        try {
            val content = when {
                contentId.startsWith("tmdb-") -> fetchTmdbDetail(contentId, contentType)
                contentId.startsWith("imdb-") -> fetchImdbDetail(contentId, contentType)
                else -> fetchLocalDetail(contentId, contentType)
            }

            val activePlatforms = userPlatformDao.getActiveNames().toSet()
            val prioritized = prioritizePlatforms(content, activePlatforms)
            emit(DataResult.Success(prioritized))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }

    private suspend fun fetchLocalDetail(localContentId: String, contentType: ContentType): Content {
        return when (contentType) {
            ContentType.MOVIE -> {
                val movie = movieDao.getByContentId(localContentId)
                    ?: throw IllegalArgumentException("Content not found locally: $localContentId")
                val tmdbId = movie.tmdbId
                if (tmdbId != null) {
                    fetchTmdbDetail("tmdb-$tmdbId", contentType)
                } else {
                    val imdbId = movie.imdbId
                    if (imdbId != null) {
                        fetchImdbDetail("imdb-$imdbId", contentType)
                    } else {
                        throw IllegalArgumentException("No API ID for content: $localContentId")
                    }
                }
            }
            ContentType.SERIES -> {
                val series = tvShowDao.getByContentId(localContentId)
                    ?: throw IllegalArgumentException("Content not found locally: $localContentId")
                val tmdbId = series.tmdbId
                if (tmdbId != null) {
                    fetchTmdbDetail("tmdb-$tmdbId", contentType)
                } else {
                    val imdbId = series.imdbId
                    if (imdbId != null) {
                        fetchImdbDetail("imdb-$imdbId", contentType)
                    } else {
                        throw IllegalArgumentException("No API ID for content: $localContentId")
                    }
                }
            }
        }
    }

    private suspend fun fetchImdbDetail(id: String, contentType: ContentType): Content {
        val imdbId = id.removePrefix("imdb-")
        val tmdbId = try {
            val externalIds = when (contentType) {
                ContentType.MOVIE -> imdbApi.getMovieExternalIds(imdbId)
                ContentType.SERIES -> imdbApi.getTvExternalIds(imdbId)
            }
            externalIds.tmdbId
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "fetchImdbDetail tmdbId for $id", e)
            null
        }

        if (tmdbId != null) {
            return fetchTmdbDetail("tmdb-$tmdbId", contentType)
        }

        return fetchImdbDirectDetail(imdbId, contentType)
    }

    private suspend fun fetchImdbDirectDetail(imdbId: String, contentType: ContentType): Content {
        val providers = when (contentType) {
            ContentType.MOVIE -> imdbApi.getMovieWatchProviders(imdbId)
            ContentType.SERIES -> imdbApi.getTvWatchProviders(imdbId)
        }
        val platforms = providers.results?.get("ES")?.imdbToStreaming().orEmpty()

        val omdbRating = try { omdbApi.getByImdbId(imdbId) } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "OMDB rating for $imdbId", e)
            null
        }

        val externalLinks = try {
            val social = when (contentType) {
                ContentType.MOVIE -> imdbApi.getMovieExternalIds(imdbId)
                ContentType.SERIES -> imdbApi.getTvExternalIds(imdbId)
            }
            ExternalLinks(
                imdbId = social.imdbId,
                wikipediaUrl = social.wikipediaUrl,
                facebookId = social.facebookId,
                instagramId = social.instagramId,
                twitterId = social.twitterId,
                youtubeId = social.youtubeId,
                homepage = social.homepage,
                wikidataId = social.wikidataId
            )
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "externalLinks for imdb $imdbId", e)
            null
        }

        return when (contentType) {
            ContentType.MOVIE -> imdbApi.getMovieDetail(imdbId).toDomain(omdbRating, platforms, externalLinks)
            ContentType.SERIES -> imdbApi.getTvDetail(imdbId).toDomain(omdbRating, platforms, externalLinks)
        }
    }

    override suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>> = getTrending(1)

    override suspend fun getTrending(page: Int): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)
        try {
            val imdbResult = imdbApi.popularAll(page = page)
            val previews = imdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { it.toContentPreview() }
                .take(20)

            if (previews.isNotEmpty()) {
                val withPlatforms = attachImdbPlatforms(previews)
                emit(DataResult.Success(withPlatforms))
            } else {
                val tmdbTrending = tmdbApi.getTrending()
                val tmdbPreviews = tmdbTrending.results
                    .filter { it.mediaType in listOf("movie", "tv") }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            }
        } catch (e: Exception) {
            try {
                val tmdbTrending = tmdbApi.getTrending()
                val tmdbPreviews = tmdbTrending.results
                    .filter { it.mediaType in listOf("movie", "tv") }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            } catch (fallback: Exception) {
                emit(DataResult.Error(e))
            }
        }
    }

    private suspend fun fetchTmdbDetail(
        id: String,
        contentType: ContentType = ContentType.MOVIE
    ): Content {
        val tmdbId = id.removePrefix("tmdb-").toInt()

        return if (contentType == ContentType.SERIES) {
            val tv = tmdbApi.getTvDetail(tmdbId)
            val credits = tmdbApi.getTvCredits(tmdbId)
            val providers = tmdbApi.getTvWatchProviders(tmdbId)
            val platforms = providers.results?.get("ES")?.toStreamingAvailability().orEmpty()

            val existing = tvShowDao.getByContentId("tmdb-$tmdbId")
            if (existing != null) {
                tvShowDao.update(
                    existing.copy(
                        totalEpisodes = tv.numberOfEpisodes ?: existing.totalEpisodes,
                        nextEpisodeAirDate = tv.nextEpisodeToAir?.airDate,
                        nextEpisodeNumber = tv.nextEpisodeToAir?.episodeNumber,
                        nextEpisodeSeasonNumber = tv.nextEpisodeToAir?.seasonNumber,
                        seriesStatus = tv.status,
                        inProduction = tv.inProduction,
                        numberOfSeasons = tv.numberOfSeasons
                    )
                )
            }

            val tvImdbId = try {
                tmdbApi.getTvExternalIds(tmdbId).imdbId
            } catch (e: Exception) {
                AppLogger.e("DiscoverRepo", "TMDB externalIds failed for tmdb=$tmdbId", e)
                null
            }

            val externalLinks = if (tvImdbId != null) {
                val social = try {
                    imdbApi.getTvExternalIds(tvImdbId)
                } catch (e: Exception) {
                    AppLogger.e("DiscoverRepo", "Balloonerismm externalIds failed for imdb=$tvImdbId", e)
                    null
                }
                ExternalLinks(
                    imdbId = social?.imdbId ?: tvImdbId,
                    wikipediaUrl = social?.wikipediaUrl,
                    facebookId = social?.facebookId,
                    instagramId = social?.instagramId,
                    twitterId = social?.twitterId,
                    youtubeId = social?.youtubeId,
                    homepage = social?.homepage,
                    wikidataId = social?.wikidataId
                )
            } else {
                AppLogger.w("DiscoverRepo", "No IMDb ID from TMDB for tmdb=$tmdbId")
                null
            }

            tv.toDomain(null, platforms, credits, externalLinks)
        } else {
            val movie = tmdbApi.getMovieDetail(tmdbId)
            val credits = tmdbApi.getMovieCredits(tmdbId)
            val providers = tmdbApi.getMovieWatchProviders(tmdbId)
            val platforms = providers.results?.get("ES")?.toStreamingAvailability().orEmpty()

            val omdbRatings = movie.imdbId?.let { imdbId ->
                try { omdbApi.getByImdbId(imdbId) } catch (e: Exception) {
                    AppLogger.e("DiscoverRepo", "OMDB ratings for movie $imdbId", e)
                    null
                }
            }

            val externalLinks = try {
                movie.imdbId?.let { imdb ->
                    val social = imdbApi.getMovieExternalIds(imdb)
                    ExternalLinks(
                        imdbId = social.imdbId,
                        wikipediaUrl = social.wikipediaUrl,
                        facebookId = social.facebookId,
                        instagramId = social.instagramId,
                        twitterId = social.twitterId,
                        youtubeId = social.youtubeId,
                        homepage = social.homepage
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("DiscoverRepo", "externalLinks for movie ${movie.imdbId}", e)
                null
            }

            val releaseDatesEs = try {
                val rd = tmdbApi.getMovieReleaseDates(tmdbId)
                val esRels = rd.results.firstOrNull { c -> c.isoCode == "ES" }
                if (esRels != null) {
                    fun pick(type: Int): String? =
                        esRels.releaseDates.firstOrNull { r -> r.type == type }?.releaseDate
                            ?.substringBefore("T")?.substringBefore(" ")
                    Triple(pick(3), pick(4), pick(6))
                } else Triple(null, null, null)
            } catch (e: Exception) {
                AppLogger.e("DiscoverRepo", "releaseDates for tmdb=$tmdbId", e)
                Triple(null, null, null)
            }

            var content = movie.toDomain(omdbRatings, platforms, credits, externalLinks)
            content = content.copy(
                spanishReleaseDate = releaseDatesEs.first,
                digitalReleaseDate = releaseDatesEs.second,
                tvReleaseDate = releaseDatesEs.third
            )

            if (movie.imdbId != null) {
                try {
                    val omdb = omdbApi.getByImdbId(movie.imdbId)
                    content = content.copy(
                        ratingImdb = omdb.imdbRating?.toFloatOrNull(),
                        ratingRt = omdb.ratings?.find { it.source == "Rotten Tomatoes" }
                            ?.value?.removeSuffix("%")?.toIntOrNull(),
                        ratingMetacritic = omdb.metascore?.toIntOrNull()
                    )
                } catch (e: Exception) {
                    AppLogger.e("DiscoverRepo", "OMDB override for ${movie.imdbId}", e)
                }
            }
            content
        }
    }

    private fun platformMatches(platformName: String, userPlatform: String): Boolean {
        if (userPlatform == "Cines" && platformName == "Cine") return true
        return platformName.contains(userPlatform, ignoreCase = true) ||
                userPlatform.contains(platformName, ignoreCase = true)
    }

    private fun prioritizePlatforms(content: Content, userPlatforms: Set<String>): Content {
        if (userPlatforms.isEmpty()) return content
        val (active, others) = content.streamingPlatforms.partition { platform ->
            userPlatforms.any { userP ->
                platformMatches(platform.platformName, userP)
            }
        }
        return content.copy(streamingPlatforms = active + others)
    }

    private suspend fun attachImdbPlatforms(previews: List<ContentPreview>): List<ContentPreview> {
        return coroutineScope {
            previews.map { preview ->
                async {
                    val platforms = try {
                        val imdbId = preview.id.removePrefix("imdb-")
                        val providerResponse = if (preview.type == ContentType.SERIES) {
                            imdbApi.getTvWatchProviders(imdbId)
                        } else {
                            imdbApi.getMovieWatchProviders(imdbId)
                        }
                        providerResponse.results?.get("ES")?.imdbToStreaming().orEmpty()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        AppLogger.w("DiscoverRepo", "IMDB platforms for ${preview.id} (timeout): ${e.message}")
                        emptyList()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("DiscoverRepo", "IMDB platforms for ${preview.id}, fallback to TMDB", e)
                        tryFetchTmbdPlatforms(preview)
                    }
                    preview.copy(streamingPlatforms = platforms)
                }
            }.map { it.await() }
        }
    }

    override suspend fun fetchPlatforms(previews: List<ContentPreview>): List<ContentPreview> {
        return coroutineScope {
            previews.map { preview ->
                async {
                    val platforms = try {
                        val tmdbId = preview.tmdbId ?: return@async preview
                        val cacheKey = "tmdb-$tmdbId-${preview.type}"
                        val cached = platformsCache[cacheKey]
                        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
                            cached.platforms
                        } else {
                            val providerResponse = retryWithBackoff {
                                if (preview.type == ContentType.SERIES) {
                                    tmdbApi.getTvWatchProviders(tmdbId)
                                } else {
                                    tmdbApi.getMovieWatchProviders(tmdbId)
                                }
                            }
                            val platforms = providerResponse.results?.get("ES")?.toStreamingAvailability().orEmpty()
                            platformsCache[cacheKey] = CachedPlatforms(platforms, System.currentTimeMillis())
                            platforms
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        AppLogger.w("DiscoverRepo", "TMDB platforms for ${preview.id} (timeout): ${e.message}")
                        emptyList<StreamingAvailability>()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("DiscoverRepo", "TMDB platforms for ${preview.id}", e)
                        emptyList<StreamingAvailability>()
                    }
                    preview.copy(streamingPlatforms = platforms)
                }
            }.map { it.await() }
        }
    }

    private suspend fun attachTmbdPlatforms(previews: List<ContentPreview>): List<ContentPreview> {
        return coroutineScope {
            previews.map { preview ->
                async {
                    val platforms = try {
                        val tmdbId = preview.tmdbId ?: return@async preview
                        val cacheKey = "tmdb-$tmdbId-${preview.type}"
                        val cached = platformsCache[cacheKey]
                        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
                            cached.platforms
                        } else {
                            val providerResponse = retryWithBackoff {
                                if (preview.type == ContentType.SERIES) {
                                    tmdbApi.getTvWatchProviders(tmdbId)
                                } else {
                                    tmdbApi.getMovieWatchProviders(tmdbId)
                                }
                            }
                            val platforms = providerResponse.results?.get("ES")?.toStreamingAvailability().orEmpty()
                            platformsCache[cacheKey] = CachedPlatforms(platforms, System.currentTimeMillis())
                            platforms
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        AppLogger.w("DiscoverRepo", "TMDB platforms for ${preview.id} (timeout): ${e.message}")
                        emptyList<StreamingAvailability>()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("DiscoverRepo", "TMDB platforms for ${preview.id}", e)
                        emptyList<StreamingAvailability>()
                    }
                    preview.copy(streamingPlatforms = platforms)
                }
            }.map { it.await() }
        }
    }

    private suspend fun tryFetchTmbdPlatforms(preview: ContentPreview): List<StreamingAvailability> {
        return try {
            val tmdbSearch = tmdbApi.searchMulti(preview.title)
            val match = tmdbSearch.results.firstOrNull {
                it.mediaType == if (preview.type == ContentType.SERIES) "tv" else "movie"
            }
            if (match != null) {
                val cacheKey = "tmdb-${match.id}-${preview.type}"
                val cached = platformsCache[cacheKey]
                if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
                    cached.platforms
                } else {
                    val providerResponse = if (preview.type == ContentType.SERIES) {
                        tmdbApi.getTvWatchProviders(match.id)
                    } else {
                        tmdbApi.getMovieWatchProviders(match.id)
                    }
                    val platforms = providerResponse.results?.get("ES")?.toStreamingAvailability().orEmpty()
                    platformsCache[cacheKey] = CachedPlatforms(platforms, System.currentTimeMillis())
                    platforms
                }
            } else emptyList()
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "TMDB fallback platforms for ${preview.title}", e)
            emptyList()
        }
    }

    override suspend fun fetchTrendingPage(page: Int, filterByPlatforms: Boolean): List<ContentPreview> {
        val activePlatforms = userPlatformDao.getActiveNames().toSet()
        val now = LocalDate.now()
        val fiveYearsAgo = now.minusYears(5).toString()
        val oneYearFuture = now.plusYears(1).toString()
        val farFuture = LocalDate.parse(oneYearFuture).isAfter(now.plusMonths(3))
        val providerFilter = if (filterByPlatforms && !farFuture) {
            TmdbProviderIds.toPipeSeparated(activePlatforms)
        } else null

        val postFilterByPlatforms = filterByPlatforms && providerFilter == null
        val watchRegion = if (providerFilter == null) null else "ES"

        return coroutineScope {
            val movieDeferred = async {
                tmdbApi.discoverMovie(page = page, watchProviders = providerFilter, watchRegion = watchRegion, releaseDateGte = fiveYearsAgo, releaseDateLte = oneYearFuture, sortBy = null, voteCountGte = 100)
            }
            val tvDeferred = async {
                tmdbApi.discoverTv(page = page, watchProviders = providerFilter, watchRegion = watchRegion, firstAirDateGte = fiveYearsAgo, firstAirDateLte = oneYearFuture, sortBy = null, voteCountGte = 100)
            }

            val movieResults = movieDeferred.await()
            val tvResults = tvDeferred.await()

            val takePerType = if (postFilterByPlatforms) 30 else 20

            val moviePreviews = attachTmbdPlatforms(
                movieResults.results
                    .filter { !it.adult }
                    .map { it.toContentPreview() }
                    .take(takePerType)
            )

            val tvPreviews = attachTmbdPlatforms(
                tvResults.results
                    .filter { !it.adult }
                    .map { it.copy(mediaType = "tv").toContentPreview() }
                    .take(takePerType)
            )

            var combined = (moviePreviews + tvPreviews).shuffled()

            val preferredTypes = if (filterByPlatforms) {
                userPreferencesDataStore.preferredAvailabilityTypes.first()
            } else null

            if (postFilterByPlatforms) {
                val before = combined.size
                combined = combined.filter { preview ->
                    preview.streamingPlatforms.any { platform ->
                        activePlatforms.any { active ->
                            platformMatches(platform.platformName, active)
                        } && (preferredTypes == null || preferredTypes.contains(platform.availabilityType.name))
                    }
                }
                AppLogger.d("DiscoverRepo", "platform filter: $before -> ${combined.size}")
            }

            combined
        }
    }

    override suspend fun fetchSearchPage(query: String, page: Int): List<ContentPreview> {
        val tmdbResult = tmdbApi.searchMulti(query, page = page)
        val tmdbPreviews = tmdbResult.results
            .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
            .map { it.toContentPreview() }
            .take(20)
        return if (tmdbPreviews.isNotEmpty()) {
            fetchPlatforms(tmdbPreviews)
        } else {
            emptyList()
        }
    }

    override suspend fun searchPeople(query: String): List<TmdbPersonSearchResult> {
        return try {
            tmdbApi.searchPerson(query).results
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "searchPeople error for $query", e)
            emptyList()
        }
    }

    override suspend fun searchCompanies(query: String): List<TmdbCompanySearchResult> {
        return try {
            tmdbApi.searchCompany(query).results
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "searchCompanies error for $query", e)
            emptyList()
        }
    }

    override suspend fun getPersonTvCredits(personId: Int): List<ContentPreview> {
        return try {
            val credits = tmdbApi.getPersonTvCredits(personId)
            (credits.cast.orEmpty() + credits.crew.orEmpty())
                .filter { it.firstAirDate != null }
                .distinctBy { it.id }
                .sortedByDescending { it.firstAirDate }
                .map { it.toContentPreview() }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getPersonTvCredits error for $personId", e)
            emptyList()
        }
    }

    override suspend fun getPersonMovieCredits(personId: Int): List<ContentPreview> {
        return try {
            val credits = tmdbApi.getPersonMovieCredits(personId)
            (credits.cast.orEmpty() + credits.crew.orEmpty())
                .filter { it.releaseDate != null }
                .distinctBy { it.id }
                .sortedByDescending { it.releaseDate }
                .map { it.toContentPreview() }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getPersonMovieCredits error for $personId", e)
            emptyList()
        }
    }

    override suspend fun getCompanyMovies(companyId: Int): List<ContentPreview> {
        return try {
            val response = tmdbApi.discoverMovie(
                withCompanies = companyId.toString(),
                sortBy = "primary_release_date.desc",
                releaseDateGte = null,
                voteCountGte = null
            )
            response.results
                .filter { !it.adult && it.releaseDate != null }
                .map { it.toContentPreview() }
                .sortedByDescending { it.releaseDate }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getCompanyMovies error for $companyId", e)
            emptyList()
        }
    }

    override suspend fun getCompanyTvShows(companyId: Int): List<ContentPreview> {
        return try {
            val response = tmdbApi.discoverTv(
                withCompanies = companyId.toString(),
                sortBy = "first_air_date.desc",
                firstAirDateGte = null,
                voteCountGte = null
            )
            response.results
                .filter { !it.adult && it.firstAirDate != null }
                .map { it.toContentPreview() }
                .sortedByDescending { it.releaseDate }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getCompanyTvShows error for $companyId", e)
            emptyList()
        }
    }

    override suspend fun getCriticReviews(contentId: String, title: String, year: Int?): List<CriticReview> {
        criticReviewDao.deleteAll()
        val cacheTtlMs = 24 * 60 * 60 * 1000L
        val cached = criticReviewDao.getByContentId(contentId)
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.cachedAt
            val cachedReviews = reviewsFromJson(cached.reviewsJson)
            AppLogger.i("DiscoverRepo", "getCriticReviews: cache hit for $title, age=${age}ms / ttl=${cacheTtlMs}ms, expired=${age >= cacheTtlMs}, cachedReviews=${cachedReviews.size}")
            if (age < cacheTtlMs && cachedReviews.isNotEmpty()) {
                if (cachedReviews.size <= 5) return cachedReviews
                criticReviewDao.deleteByContentId(contentId)
            }
        }

        val faId = filmaffinityScraper.searchMovieId(title, year)
        if (faId == null) {
            AppLogger.w("DiscoverRepo", "getCriticReviews: no FA id for $title")
            return emptyList()
        }
        AppLogger.i("DiscoverRepo", "getCriticReviews: FA id=$faId, calling getProReviews")
        val reviews = filmaffinityScraper.getProReviews(faId)
        movieDao.updateFaId(contentId, faId)
        tvShowDao.updateFaId(contentId, faId)
        criticReviewDao.upsert(
            CriticReviewEntity(
                contentId = contentId,
                reviewsJson = reviewsToJson(reviews)
            )
        )
        return reviews
    }

    override suspend fun getFaMovieData(contentId: String, title: String, year: Int?): Pair<Float?, List<PlatformReleaseDate>> {
        val cacheTtlMs = 24 * 60 * 60 * 1000L
        val cached = faMovieDataDao.getByContentId(contentId)
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.cachedAt
            AppLogger.i("DiscoverRepo", "getFaMovieData: cache hit for $title, age=${age}ms")
            if (age < cacheTtlMs) {
                val releases = platformReleasesFromJson(cached.platformReleasesJson)
                return Pair(cached.faRating, releases)
            }
        }

        val faId = cached?.faId ?: filmaffinityScraper.searchMovieId(title, year)
        if (faId == null) {
            AppLogger.w("DiscoverRepo", "getFaMovieData: no FA id for $title")
            return Pair(null, emptyList())
        }

        val pageData = filmaffinityScraper.getMoviePageData(faId)
        faMovieDataDao.upsert(
            FaMovieDataEntity(
                contentId = contentId,
                faId = faId,
                faRating = pageData.rating,
                platformReleasesJson = platformReleasesToJson(pageData.vodReleases)
            )
        )
        return Pair(pageData.rating, pageData.vodReleases)
    }

    private fun platformReleasesToJson(releases: List<PlatformReleaseDate>): String {
        val arr = JSONArray()
        for (r in releases) {
            val obj = JSONObject()
            obj.put("platformName", r.platformName)
            obj.put("dateLabel", r.dateLabel)
            r.releaseDate?.let { obj.put("releaseDate", it) }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun platformReleasesFromJson(json: String?): List<PlatformReleaseDate> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlatformReleaseDate(
                    platformName = obj.getString("platformName"),
                    dateLabel = obj.optString("dateLabel", ""),
                    releaseDate = obj.optString("releaseDate", null)
                )
            }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "platformReleasesFromJson error", e)
            emptyList()
        }
    }

    private fun reviewsToJson(reviews: List<CriticReview>): String {
        val arr = JSONArray()
        for (r in reviews) {
            val obj = JSONObject()
            obj.put("author", r.author)
            obj.put("publication", r.publication)
            obj.put("text", r.text)
            r.rating?.let { obj.put("rating", it) }
            r.url?.let { obj.put("url", it) }
            obj.put("sentiment", r.sentiment.name)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun reviewsFromJson(json: String): List<CriticReview> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            CriticReview(
                author = obj.getString("author"),
                publication = obj.optString("publication", ""),
                text = obj.optString("text", ""),
                rating = obj.optString("rating", null),
                url = obj.optString("url", null),
                sentiment = Sentiment.valueOf(obj.optString("sentiment", "NEUTRAL"))
            )
        }
    }

    override suspend fun getCollectionMovies(collectionId: Int): List<ContentPreview> {
        return try {
            val collection = tmdbApi.getCollection(collectionId)
            collection.parts
                .filter { it.posterPath != null }
                .map { it.toContentPreview() }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getCollectionMovies error for $collectionId", e)
            emptyList()
        }
    }

    override suspend fun getRecommendations(contentId: String, contentType: ContentType): List<ContentPreview> {
        return try {
            val prefix = contentId.substringBefore("-")
            val rawId = contentId.removePrefix("$prefix-")
            val tmdbId = when (prefix) {
                "tmdb" -> rawId.toIntOrNull()
                "imdb" -> resolveTmdbId(rawId, contentType)
                else -> null
            } ?: return emptyList()
            val response = when (contentType) {
                ContentType.MOVIE -> tmdbApi.getMovieRecommendations(tmdbId)
                ContentType.SERIES -> tmdbApi.getTvRecommendations(tmdbId)
            }
            response.results
                .filter { it.posterPath != null }
                .take(5)
                .map { it.toContentPreview() }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getRecommendations error for $contentId", e)
            emptyList()
        }
    }

    override suspend fun getSeriesRelationships(wikidataId: String?, imdbId: String?): Pair<List<ContentPreview>, Set<String>> {
        AppLogger.d("DiscoverRepo", "getSeriesRelationships called — wikidataId=$wikidataId, imdbId=$imdbId")
        val cacheKey = "${wikidataId.orEmpty()}|${imdbId.orEmpty()}"
        val cached = relationshipsCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < RELATIONSHIPS_CACHE_TTL_MS) {
            AppLogger.d("DiscoverRepo", "getSeriesRelationships cache hit for $cacheKey, returning ${cached.previews.size} previews")
            return cached.previews to cached.excludeIds
        }
        return try {
            val relationships = wikidataApi.getRelationships(wikidataId, imdbId)
            val results = mutableListOf<ContentPreview>()
            val excludeIds = mutableSetOf<String>()

            for (rel in relationships) {
                val tmdbId = rel.tmdbTvId ?: rel.tmdbMovieId ?: continue
                val isTv = rel.tmdbTvId != null
                val type = if (isTv) ContentType.SERIES else ContentType.MOVIE
                val contentId = "tmdb-$tmdbId"
                excludeIds.add(contentId)
                try {
                    val detail = if (isTv) tmdbApi.getTvDetailLight(tmdbId) else tmdbApi.getMovieDetail(tmdbId)
                    val posterPath = (detail as? com.dondeloexan.data.remote.dto.TmdbTvDetailDto)?.posterPath
                        ?: (detail as? com.dondeloexan.data.remote.dto.TmdbMovieDto)?.posterPath
                    results.add(
                        ContentPreview(
                            id = contentId,
                            source = ContentSource.TMDB,
                            tmdbId = tmdbId,
                            title = rel.targetLabel,
                            type = type,
                            coverUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                        )
                    )
                } catch (e: Exception) {
                    results.add(
                        ContentPreview(
                            id = contentId,
                            source = ContentSource.TMDB,
                            tmdbId = tmdbId,
                            title = rel.targetLabel,
                            type = type
                        )
                    )
                }
            }
            AppLogger.d("DiscoverRepo", "getSeriesRelationships returning ${results.size} previews, ${excludeIds.size} excludeIds")
            val pair = results to excludeIds
            relationshipsCache[cacheKey] = CachedRelationships(results, excludeIds, System.currentTimeMillis())
            pair
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getSeriesRelationships error", e)
            emptyList<ContentPreview>() to emptySet()
        }
    }
}
