package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.remote.TmdbProviderIds
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
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
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.retryWithBackoff
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class DiscoverRepositoryImpl(
    private val imdbApi: BalloonerismmApi,
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao? = null
) : DiscoverRepository {

    private data class CachedPlatforms(
        val platforms: List<StreamingAvailability>,
        val timestamp: Long
    )

    private val platformsCache = ConcurrentHashMap<String, CachedPlatforms>()
    private val CACHE_TTL_MS = 4 * 60 * 60 * 1000L

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
                homepage = social.homepage
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

            val externalLinks = try {
                val tvImdbId = tmdbApi.getTvExternalIds(tmdbId).imdbId
                tvImdbId?.let { imdb ->
                    val social = imdbApi.getTvExternalIds(imdb)
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
                AppLogger.e("DiscoverRepo", "externalLinks for tmdb $tmdbId", e)
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

            val content = movie.toDomain(omdbRatings, platforms, credits, externalLinks)
            if (movie.imdbId != null) {
                try {
                    val omdb = omdbApi.getByImdbId(movie.imdbId)
                    content.copy(
                        ratingImdb = omdb.imdbRating?.toFloatOrNull(),
                        ratingRt = omdb.ratings?.find { it.source == "Rotten Tomatoes" }
                            ?.value?.removeSuffix("%")?.toIntOrNull(),
                        ratingMetacritic = omdb.metascore?.toIntOrNull()
                    )
                } catch (e: Exception) {
                    AppLogger.e("DiscoverRepo", "OMDB override for ${movie.imdbId}", e)
                    content
                }
            } else content
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

    private suspend fun fetchPlatforms(previews: List<ContentPreview>): List<ContentPreview> {
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

    enum class TrendingVariant { POPULAR, TOP_RATED, RECENT, RANDOM_YEAR }

    override suspend fun fetchTrendingPage(page: Int, filterByPlatforms: Boolean): List<ContentPreview> {
        val activePlatforms = userPlatformDao.getActiveNames().toSet()
        val providerFilter = if (filterByPlatforms) {
            TmdbProviderIds.toPipeSeparated(activePlatforms)
        } else null

        val postFilterByPlatforms = filterByPlatforms && providerFilter == null
        val variant = TrendingVariant.values().random()
        val futureDate = LocalDate.now().plusYears(2).toString()

        val movieSort: String?
        val tvSort: String?
        val movieYearGte: String?
        val tvYearGte: String?

        when (variant) {
            TrendingVariant.POPULAR -> {
                movieSort = "popularity.desc"
                tvSort = "popularity.desc"
                movieYearGte = "2024-01-01"
                tvYearGte = "2024-01-01"
            }
            TrendingVariant.TOP_RATED -> {
                movieSort = "vote_average.desc"
                tvSort = "vote_average.desc"
                movieYearGte = null
                tvYearGte = null
            }
            TrendingVariant.RECENT -> {
                movieSort = "primary_release_date.desc"
                tvSort = "first_air_date.desc"
                movieYearGte = "2025-01-01"
                tvYearGte = "2025-01-01"
            }
            TrendingVariant.RANDOM_YEAR -> {
                val year = (2020..2025).random()
                movieSort = "popularity.desc"
                tvSort = "popularity.desc"
                movieYearGte = "$year-01-01"
                tvYearGte = "$year-01-01"
            }
        }

        return coroutineScope {
            val movieDeferred = async {
                tmdbApi.discoverMovie(page = page, watchProviders = providerFilter, releaseDateGte = movieYearGte, releaseDateLte = futureDate, sortBy = movieSort)
            }
            val tvDeferred = async {
                tmdbApi.discoverTv(page = page, watchProviders = providerFilter, firstAirDateGte = tvYearGte, firstAirDateLte = futureDate, sortBy = tvSort)
            }

            val movieResults = movieDeferred.await()
            val tvResults = tvDeferred.await()

            val moviePreviews = attachTmbdPlatforms(
                movieResults.results
                    .filter { !it.adult }
                    .map { it.toContentPreview() }
                    .take(5)
            )

            val tvPreviews = attachTmbdPlatforms(
                tvResults.results
                    .filter { !it.adult }
                    .map { it.copy(mediaType = "tv").toContentPreview() }
                    .take(5)
            )

            var combined = (moviePreviews + tvPreviews).shuffled()

            if (postFilterByPlatforms) {
                combined = combined.filter { preview ->
                    preview.streamingPlatforms.any { platform ->
                        activePlatforms.any { active ->
                            platformMatches(platform.platformName, active)
                        }
                    }
                }
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
}
