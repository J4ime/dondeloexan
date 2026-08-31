package com.dondeloexan.data.repository

import com.dondeloexan.data.catalog.CatalogCriticReviewRow
import com.dondeloexan.data.catalog.CatalogFaRow
import com.dondeloexan.data.catalog.CatalogListRow
import com.dondeloexan.data.catalog.CatalogSeasonRow
import com.dondeloexan.data.catalog.CloudCatalogRepository
import com.dondeloexan.data.catalog.toCatalogEpisodeRow
import com.dondeloexan.data.catalog.toCatalogListRow
import com.dondeloexan.data.catalog.toCatalogMovieRow
import com.dondeloexan.data.catalog.toCatalogSeasonRow
import com.dondeloexan.data.catalog.toCatalogTvShowRow
import com.dondeloexan.data.catalog.toContent
import com.dondeloexan.data.catalog.toContentPreview
import com.dondeloexan.data.catalog.toEpisode
import com.dondeloexan.data.catalog.toSeason
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
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.mapper.toEpisode
import com.dondeloexan.data.remote.mapper.toSeason
import com.dondeloexan.data.remote.mapper.toSeasonDetail
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.data.remote.api.WikidataRelationship
import com.dondeloexan.data.remote.dto.TmdbCompanySearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResult
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.data.sync.toCatalogTvShowRow as toEntityCatalogTvShowRow
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
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
import com.dondeloexan.domain.model.detail.CastSocialInfo
import com.dondeloexan.domain.model.detail.MovieWatchState
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import com.dondeloexan.domain.model.detail.SocialLinkType
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class DiscoverRepositoryImpl(
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
    private val faMovieDataDao: FaMovieDataDao,
    private val cloudCatalog: CloudCatalogRepository? = null
) : DiscoverRepository {

    private data class CachedPlatforms(
        val platforms: List<StreamingAvailability>,
        val timestamp: Long
    )

    private val platformsCache = ConcurrentHashMap<String, CachedPlatforms>()
    private val CACHE_TTL_MS = 4 * 60 * 60 * 1000L

    private val providerSemaphore = Semaphore(6)

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
            val tmdbResult = tmdbApi.searchMulti(query, page = page)
            val previews = tmdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { it.toContentPreview() }
                .take(20)
            val withPlatforms = attachTmbdPlatforms(previews)
            emit(DataResult.Success(withPlatforms))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "search error for $query", e)
            emit(DataResult.Error(e))
        }
    }

    override suspend fun resolveTmdbId(imdbId: String, type: ContentType): Int? {
        return try {
            when (type) {
                ContentType.MOVIE -> tmdbApi.findMovieByImdbId(imdbId).movieResults.firstOrNull()?.id
                ContentType.SERIES -> tmdbApi.findTvByImdbId(imdbId).tvResults.firstOrNull()?.id
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "resolveTmdbId failed for $imdbId", e)
            null
        }
    }

    override suspend fun getDetail(contentId: String, contentType: ContentType): Flow<DataResult<Content>> = flow {
        emit(DataResult.Loading)

        try {
            val content = cloudFirstDetail(contentId, contentType)

            val activePlatforms = userPlatformDao.getActiveNames().toSet()
            val prioritized = prioritizePlatforms(content, activePlatforms)
            emit(DataResult.Success(prioritized))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "getDetail error for $contentId", e)
            emit(DataResult.Error(e))
        }
    }

    /**
     * Lectura nube con límite de tiempo y registro de fallos: devuelve null si
     * no existe o si la nube no responde rápido (para que el flujo caiga a las
     * APIs), NUNCA silenciosa y NUNCA bloqueando más de 4s.
     */
    private suspend inline fun <T> cloudRead(
        context: String,
        crossinline block: suspend () -> T?
    ): T? = try {
        withTimeout(4_000) { block() }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        AppLogger.w("DiscoverRepo", "lectura nube $context agotó 4s; se usa API/cache")
        null
    } catch (e: Exception) {
        AppLogger.w("DiscoverRepo", "lectura nube $context falló; se usa API/cache (${e.message})")
        null
    }

    /**
     * Fila "pobre" = variante de biblioteca sincronizada desde Room
     * (MovieEntity/TvShowEntity), que no guarda dirección/reparto/géneros…
     * En ese caso el detalle carece de ficha técnica y hay que refrescar desde
     * las APIs y re-escribir la nube con los datos ricos.
     */
    private fun Content.isCatalogSparse(): Boolean =
        directors.isEmpty() && cast.isEmpty() && genres.isEmpty()

    /**
     * Nube primero: si el contenido está en el catálogo global se sirve desde
     * ahí (compartido por todos los usuarios). Si no, se trae de las APIs
     * como hasta ahora y se guarda en la nube (write-through, best-effort).
     */
    private suspend fun cloudFirstDetail(contentId: String, contentType: ContentType): Content {
        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }

        if (session != null) {
            val cached = cloudRead("getDetail($contentId)") {
                when (contentType) {
                    ContentType.MOVIE -> cloudCatalog?.getMovie(contentId, session)?.toContent()
                    ContentType.SERIES -> cloudCatalog?.getTvShow(contentId, session)?.toContent()
                }
            }
            if (cached != null && !cached.isCatalogSparse()) {
                AppLogger.i("DiscoverRepo", "getDetail: catálogo nube hit rico para $contentId")
                return cached
            }
            if (cached != null) {
                AppLogger.i(
                    "DiscoverRepo",
                    "getDetail: hit nube pobre para $contentId (biblioteca); refrescando desde APIs y re-escribiendo nube"
                )
            } else {
                AppLogger.d("DiscoverRepo", "getDetail: catálogo nube miss para $contentId, usando APIs")
            }
        }

        val content = when {
            contentId.startsWith("tmdb-") -> fetchTmdbDetail(contentId, contentType)
            contentId.startsWith("imdb-") -> fetchImdbDetail(contentId, contentType)
            else -> fetchLocalDetail(contentId, contentType)
        }

        if (session != null) {
            runCatching {
                when (contentType) {
                    ContentType.MOVIE ->
                        cloudCatalog?.saveMovies(listOf(content.toCatalogMovieRow()), session)
                    ContentType.SERIES ->
                        cloudCatalog?.saveTvShows(listOf(content.toCatalogTvShowRow()), session)
                }
            }.onFailure {
                AppLogger.e("DiscoverRepo", "write-through catálogo falló para $contentId", it)
            }
        }
        return content
    }

    /**
     * Nube primero para listas derivadas (content_lists, genéricas por
     * content_id + list_type). En la nube se guardan las listas COMPLETAS (sin
     * exclusiones dependientes de contexto) y la exclusión se aplica aquí al
     * devolver. Write-through en miss, best-effort.
     */
    private suspend fun cloudFirstList(
        contentId: String,
        listType: String,
        fetch: suspend () -> List<ContentPreview>
    ): List<ContentPreview> {
        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            val cached = cloudRead("getList($contentId/$listType)") {
                cloudCatalog?.getList(contentId, listType, session)
            }
            if (!cached.isNullOrEmpty()) {
                AppLogger.d("DiscoverRepo", "cloudFirstList nube hit para $contentId/$listType (${cached.size})")
                return cached.map { it.toContentPreview() }
            }
        }
        val previews = try {
            fetch()
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "cloudFirstList fetch $contentId/$listType falló", e)
            emptyList()
        }
        if (previews.isNotEmpty() && session != null) {
            runCatching {
                cloudCatalog?.saveLists(
                    previews.mapIndexed { index, p -> p.toCatalogListRow(contentId, listType, index) },
                    session
                )
            }.onFailure {
                AppLogger.e("DiscoverRepo", "write-through content_lists $contentId/$listType falló", it)
            }
        }
        return previews
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
            val find = when (contentType) {
                ContentType.MOVIE -> tmdbApi.findMovieByImdbId(imdbId)
                ContentType.SERIES -> tmdbApi.findTvByImdbId(imdbId)
            }
            when (contentType) {
                ContentType.MOVIE -> find.movieResults.firstOrNull()?.id
                ContentType.SERIES -> find.tvResults.firstOrNull()?.id
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "resolve TMDB id for $imdbId", e)
            null
        }

        if (tmdbId != null) {
            return fetchTmdbDetail("tmdb-$tmdbId", contentType)
        }

        val omdb = try {
            omdbApi.getByImdbId(imdbId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "OMDB fallback for $imdbId", e)
            null
        }
        if (omdb == null || omdb.title.isNullOrBlank() || omdb.response == "False") {
            throw IllegalArgumentException("Content not found for IMDb id: $imdbId")
        }
        return Content(
            id = "imdb-$imdbId",
            source = ContentSource.IMDB,
            imdbId = imdbId,
            title = omdb.title,
            type = contentType,
            year = omdb.year?.toIntOrNull(),
            releaseDate = omdb.released?.takeIf { it != "N/A" },
            ratingImdb = omdb.imdbRating?.toFloatOrNull(),
            ratingRt = omdb.ratings?.find { it.source == "Rotten Tomatoes" }
                ?.value?.removeSuffix("%")?.toIntOrNull(),
            ratingMetacritic = omdb.metascore?.toIntOrNull(),
            synopsis = omdb.plot?.takeIf { it.isNotBlank() && it != "N/A" },
            genres = omdb.genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
            countries = omdb.country?.split(", ")?.filter { it.isNotBlank() && it != "N/A" } ?: emptyList(),
            coverUrl = omdb.poster?.takeIf { it.isNotBlank() && it != "N/A" && !it.startsWith("data:") },
            externalLinks = ExternalLinks(imdbId = imdbId),
            lastCachedAt = System.currentTimeMillis()
        )
    }

    override suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>> = getTrending(1)

    override suspend fun getTrending(page: Int): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)
        try {
            val tmdbTrending = tmdbApi.getTrending()
            val tmdbPreviews = tmdbTrending.results
                .filter { it.mediaType in listOf("movie", "tv") }
                .map { it.toContentPreview() }
                .take(20)
            val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
            emit(DataResult.Success(withPlatforms))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "getTrending error for page $page", e)
            emit(DataResult.Error(e))
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
                val social = tmdbApi.getTvExternalIds(tmdbId)
                ExternalLinks(
                    imdbId = social.imdbId,
                    facebookId = social.facebookId,
                    instagramId = social.instagramId,
                    twitterId = social.twitterId,
                    youtubeId = social.youtubeId,
                    wikidataId = social.wikidataId
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DiscoverRepo", "TMDB externalIds failed for tv tmdb=$tmdbId", e)
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
                val social = tmdbApi.getMovieExternalIds(tmdbId)
                ExternalLinks(
                    imdbId = social.imdbId,
                    facebookId = social.facebookId,
                    instagramId = social.instagramId,
                    twitterId = social.twitterId,
                    youtubeId = social.youtubeId,
                    wikidataId = social.wikidataId
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e("DiscoverRepo", "TMDB externalIds failed for movie tmdb=$tmdbId", e)
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
                            val providerResponse = providerSemaphore.withPermit {
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
                            val providerResponse = providerSemaphore.withPermit {
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
        val tokens = normalizedTokens(query)
        val seen = mutableSetOf<String>()
        val tmdbResult = tmdbApi.searchMulti(query, page = page)
        val tmdbPreviews = tmdbResult.results
            .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
            .map { it.toContentPreview() }
            .filter { isRelevant(it, tokens) }
            .filter { seen.add(it.id) }
            .take(20)
        return if (tmdbPreviews.isNotEmpty()) {
            fetchPlatforms(tmdbPreviews)
        } else {
            emptyList()
        }
    }

    private fun normalizedTokens(query: String): List<String> {
        return normalizeForSearch(query)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
    }

    private fun isRelevant(preview: ContentPreview, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return preview.title.isNotBlank()
        val title = normalizeForSearch(preview.title)
        if (title.isBlank()) return false
        val fullPhrase = normalizeForSearch(tokens.joinToString(" "))
        if (title.contains(fullPhrase)) return true
        return tokens.all { title.contains(it) }
    }

    private fun normalizeForSearch(value: String): String {
        val lower = value.lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            sb.append(
                when (ch) {
                    'á', 'à', 'ä', 'â', 'ã', 'å' -> 'a'
                    'é', 'è', 'ë', 'ê' -> 'e'
                    'í', 'ì', 'ï', 'î' -> 'i'
                    'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
                    'ú', 'ù', 'ü', 'û' -> 'u'
                    'ñ' -> 'n'
                    'ç' -> 'c'
                    else -> ch
                }
            )
        }
        return sb.toString()
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
                .map { it.toContentPreview(forceType = ContentType.SERIES) }
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
                .map { it.toContentPreview(forceType = ContentType.MOVIE) }
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getPersonMovieCredits error for $personId", e)
            emptyList()
        }
    }

    override suspend fun getDirectorTopMovies(directorId: Int, excludeTmdbId: Int?): List<ContentPreview> =
    cloudFirstList("director-$directorId", "director_movies") {
        tmdbApi.getPersonMovieCredits(directorId)
            .crew.orEmpty()
            .filter { it.job.equals("Director", ignoreCase = true) }
            .filter { it.releaseDate != null && (it.voteAverage ?: 0f) > 0f }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TmdbPersonCredit> { it.voteAverage ?: 0f }
                .thenByDescending { it.voteCount ?: 0 })
            .take(5)
            .map { it.toContentPreview(forceType = ContentType.MOVIE) }
    }.filter { excludeTmdbId == null || it.tmdbId != excludeTmdbId }

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

        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            val cloudCached = cloudRead("getCriticReviews($contentId)") {
                cloudCatalog?.getCriticReviews(contentId, session)
            }
            if (cloudCached != null) {
                val age = System.currentTimeMillis() - cloudCached.cachedAt
                val cloudReviews = reviewsFromJson(cloudCached.reviewsJson)
                AppLogger.i("DiscoverRepo", "getCriticReviews: catálogo nube hit para $title, age=${age}ms / ttl=${cacheTtlMs}ms, expired=${age >= cacheTtlMs}, cachedReviews=${cloudReviews.size}")
                if (age < cacheTtlMs && cloudReviews.isNotEmpty()) {
                    return cloudReviews
                }
            }
        }

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
        if (session != null) {
            runCatching {
                cloudCatalog?.saveCriticReviews(
                    listOf(
                        CatalogCriticReviewRow(
                            contentId = contentId,
                            reviewsJson = reviewsToJson(reviews),
                            cachedAt = System.currentTimeMillis()
                        )
                    ),
                    session
                )
            }.onFailure {
                AppLogger.e("DiscoverRepo", "write-through critic_reviews falló para $contentId", it)
            }
        }
        return reviews
    }

    override suspend fun getFaMovieData(contentId: String, title: String, year: Int?): Pair<Float?, List<PlatformReleaseDate>> {
        AppLogger.i("DiscoverRepo", "getFaMovieData: title='$title' contentId='$contentId' year=$year")
        faMovieDataDao.deleteAll()
        val cacheTtlMs = 24 * 60 * 60 * 1000L

        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            val cloudCached = cloudRead("getFaMovieData($contentId)") {
                cloudCatalog?.getFaMovieData(contentId, session)
            }
            if (cloudCached != null) {
                val age = System.currentTimeMillis() - cloudCached.cachedAt
                val cloudReleases = platformReleasesFromJson(cloudCached.platformReleasesJson)
                AppLogger.i("DiscoverRepo", "getFaMovieData: catálogo nube hit para $title, age=${age}ms, releases.size=${cloudReleases.size}")
                if (age < cacheTtlMs && cloudReleases.isNotEmpty()) {
                    return Pair(cloudCached.faRating, cloudReleases)
                }
            }
        }

        val cached = faMovieDataDao.getByContentId(contentId)
        if (cached != null) {
            val age = System.currentTimeMillis() - cached.cachedAt
            val releases = platformReleasesFromJson(cached.platformReleasesJson)
            AppLogger.i("DiscoverRepo", "getFaMovieData: cache hit for $title, age=${age}ms, releases.size=${releases.size}")
            for ((i, r) in releases.withIndex()) {
                AppLogger.i("DiscoverRepo", "getFaMovieData:   release[$i] platform='${r.platformName}' dateLabel='${r.dateLabel}' releaseDate='${r.releaseDate}'")
            }
            if (age < cacheTtlMs && releases.isNotEmpty()) {
                return Pair(cached.faRating, releases)
            }
        }

        val faId = cached?.faId
            ?: movieDao.getByContentId(contentId)?.faId?.also { AppLogger.i("DiscoverRepo", "getFaMovieData: faId=$it from movieDao") }
            ?: tvShowDao.getByContentId(contentId)?.faId?.also { AppLogger.i("DiscoverRepo", "getFaMovieData: faId=$it from tvShowDao") }
            ?: filmaffinityScraper.searchMovieId(title, year)?.also { AppLogger.i("DiscoverRepo", "getFaMovieData: faId=$it from searchMovieId") }
        if (faId == null) {
            AppLogger.w("DiscoverRepo", "getFaMovieData: no FA id for $title")
            return Pair(null, emptyList())
        }

        val pageData = filmaffinityScraper.getMoviePageData(faId)
        AppLogger.i("DiscoverRepo", "getFaMovieData: pageData rating=${pageData.rating} releases=${pageData.vodReleases.size}")
        for ((i, r) in pageData.vodReleases.withIndex()) {
            AppLogger.i("DiscoverRepo", "getFaMovieData:   release[$i] platform='${r.platformName}' dateLabel='${r.dateLabel}' releaseDate='${r.releaseDate}'")
        }
        faMovieDataDao.upsert(
            FaMovieDataEntity(
                contentId = contentId,
                faId = faId,
                faRating = pageData.rating,
                platformReleasesJson = platformReleasesToJson(pageData.vodReleases)
            )
        )
        if (session != null) {
            runCatching {
                cloudCatalog?.saveFaMovieData(
                    listOf(
                        CatalogFaRow(
                            contentId = contentId,
                            faId = faId,
                            faRating = pageData.rating,
                            platformReleasesJson = platformReleasesToJson(pageData.vodReleases),
                            cachedAt = System.currentTimeMillis()
                        )
                    ),
                    session
                )
            }.onFailure {
                AppLogger.e("DiscoverRepo", "write-through fa_movie_data falló para $contentId", it)
            }
        }
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
                    releaseDate = obj.optString("releaseDate", "")
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
                rating = obj.optString("rating", ""),
                url = obj.optString("url", ""),
                sentiment = Sentiment.valueOf(obj.optString("sentiment", "NEUTRAL"))
            )
        }
    }

    override suspend fun getCollectionMovies(collectionId: Int): List<ContentPreview> =
    cloudFirstList("collection-$collectionId", "collection") {
        val collection = tmdbApi.getCollection(collectionId)
        collection.parts
            .filter { it.posterPath != null }
            .map { it.toContentPreview() }
    }

    override suspend fun getRecommendations(contentId: String, contentType: ContentType): List<ContentPreview> =
        cloudFirstList(contentId, "similar") {
            val prefix = contentId.substringBefore("-")
            val rawId = contentId.removePrefix("$prefix-")
            val tmdbId = when (prefix) {
                "tmdb" -> rawId.toIntOrNull()
                "imdb" -> resolveTmdbId(rawId, contentType)
                else -> null
            } ?: return@cloudFirstList emptyList()
            val response = when (contentType) {
                ContentType.MOVIE -> tmdbApi.getMovieRecommendations(tmdbId)
                ContentType.SERIES -> tmdbApi.getTvRecommendations(tmdbId)
            }
            response.results
                .filter { it.posterPath != null }
                .take(5)
                .map { it.toContentPreview() }
        }

    override suspend fun getSeriesRelationships(wikidataId: String?, imdbId: String?): Pair<List<ContentPreview>, Set<String>> {
        AppLogger.d("DiscoverRepo", "getSeriesRelationships called — wikidataId=$wikidataId, imdbId=$imdbId")
        val cacheKey = "${wikidataId.orEmpty()}|${imdbId.orEmpty()}"
        val listKey = "series-$cacheKey"
        val cached = relationshipsCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < RELATIONSHIPS_CACHE_TTL_MS) {
            AppLogger.d("DiscoverRepo", "getSeriesRelationships cache hit for $cacheKey, returning ${cached.previews.size} previews")
            return cached.previews to cached.excludeIds
        }

        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            val rows = cloudRead("getList($listKey/relationships)") {
                cloudCatalog?.getList(listKey, "relationships", session)
            }
            if (!rows.isNullOrEmpty()) {
                AppLogger.d("DiscoverRepo", "getSeriesRelationships nube hit para $cacheKey, ${rows.size} previews")
                val previews = rows.map { it.toContentPreview() }
                val excludeIds = rows.map { it.relatedContentId }.toSet()
                relationshipsCache[cacheKey] = CachedRelationships(previews, excludeIds, System.currentTimeMillis())
                return previews to excludeIds
            }
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
                    val detail: Any = if (isTv) tmdbApi.getTvDetailLight(tmdbId) else tmdbApi.getMovieDetail(tmdbId)
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
            if (results.isNotEmpty() && session != null) {
                runCatching {
                    cloudCatalog?.saveLists(
                        results.mapIndexed { index, p -> p.toCatalogListRow(listKey, "relationships", index) },
                        session
                    )
                }.onFailure {
                    AppLogger.e("DiscoverRepo", "write-through content_lists $listKey falló", it)
                }
            }
            pair
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getSeriesRelationships error", e)
            emptyList<ContentPreview>() to emptySet()
        }
    }

    // --- Detail & tracking ---

    override suspend fun getMovieWatchState(content: Content): MovieWatchState {
        val existing = findMovie(content)
        return MovieWatchState(
            isWatched = existing?.status == WatchStatus.YA_VISTA,
            isFavorite = existing?.liked == true,
            inLibrary = existing != null
        )
    }

    override suspend fun setMovieWatched(content: Content, watched: Boolean): MovieWatchState {
        val existing = findMovie(content)
        val status = if (watched) WatchStatus.YA_VISTA else WatchStatus.POR_VER
        if (existing != null) {
            movieDao.update(existing.copy(status = status, watchedAt = if (watched) System.currentTimeMillis() else null))
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
                    status = status,
                    watchedAt = if (watched) System.currentTimeMillis() else null
                )
            )
        }
        return getMovieWatchState(content)
    }

    override suspend fun setMovieFavorite(content: Content, favorite: Boolean): MovieWatchState {
        val existing = findMovie(content)
        if (existing != null) {
            movieDao.update(existing.copy(liked = favorite))
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
                    liked = favorite,
                    status = WatchStatus.POR_VER
                )
            )
        }
        return getMovieWatchState(content)
    }

    override suspend fun addMovieToLibrary(content: Content): MovieWatchState {
        if (findMovie(content) == null) {
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
                    status = WatchStatus.POR_VER,
                    liked = false
                )
            )
        }
        return getMovieWatchState(content)
    }

    override suspend fun addSeriesToLibrary(content: Content): Boolean {
        if (findTvShow(content) != null) return false
        tvShowDao.insert(
            TvShowEntity(
                contentId = content.id,
                tmdbId = content.tmdbId,
                imdbId = content.imdbId,
                title = content.title,
                year = content.year,
                posterUrl = content.coverUrl,
                ratingImdb = content.ratingImdb,
                totalEpisodes = content.totalEpisodes,
                status = WatchStatus.POR_VER,
                liked = false
            )
        )
        return true
    }

    override suspend fun getSeriesTracking(content: Content): SeriesTracking {
        val tvShow = findTvShow(content) ?: return SeriesTracking()
        val progress = tvShowProgressDao?.getByTvShowId(tvShow.id) ?: emptyList()
        val watchedSet = progress.map { SeriesTracking.keyFor(it.season, it.episode) }.toSet()
        val lastWatched = progress.maxByOrNull { it.watchedAt }
        return SeriesTracking(
            exists = true,
            isFavorite = tvShow.liked,
            watchedToDate = tvShow.status == WatchStatus.YA_VISTA,
            watchedEpisodes = watchedSet,
            lastWatchedSeason = lastWatched?.season,
            lastWatchedEpisode = lastWatched?.episode,
            finishedAt = tvShow.finishedAt,
            inProduction = tvShow.inProduction,
            seriesStatus = tvShow.seriesStatus,
            nextEpisodeAirDate = tvShow.nextEpisodeAirDate
        )
    }

    override suspend fun setSeriesWatched(content: Content, watched: Boolean): Boolean {
        val tvShow = findTvShow(content) ?: return false
        val today = LocalDate.now()
        if (watched) {
            val progressToInsert = mutableListOf<TvShowProgressEntity>()
            val tmdbId = tvShow.tmdbId
            if (tmdbId != null) {
                try {
                    val detail = tmdbApi.getTvDetailLight(tmdbId)
                    for (season in detail.seasons.orEmpty().filter { it.seasonNumber > 0 }) {
                        try {
                            val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                            for (ep in seasonDetail.episodes) {
                                val isAired = ep.airDate == null ||
                                        try {
                                            !LocalDate.parse(ep.airDate).isAfter(today)
                                        } catch (_: Exception) {
                                            true
                                        }
                                if (isAired) {
                                    progressToInsert.add(
                                        TvShowProgressEntity(
                                            tvShowId = tvShow.id,
                                            season = season.seasonNumber,
                                            episode = ep.episodeNumber
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("DiscoverRepo", "setSeriesWatched season ${season.seasonNumber} for ${tvShow.id}", e)
                            for (epNum in 1..season.episodeCount) {
                                progressToInsert.add(
                                    TvShowProgressEntity(
                                        tvShowId = tvShow.id,
                                        season = season.seasonNumber,
                                        episode = epNum
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("DiscoverRepo", "setSeriesWatched detail error for ${tvShow.title}", e)
                }
            }
            if (progressToInsert.isNotEmpty()) {
                tvShowProgressDao?.insertAll(progressToInsert)
            }
            tvShowDao.update(tvShow.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
        } else {
            tvShowProgressDao?.deleteByTvShowId(tvShow.id)
            tvShowDao.update(tvShow.copy(status = WatchStatus.POR_VER, lastWatchedAt = null))
        }
        return true
    }

    override suspend fun setSeriesFavorite(content: Content, favorite: Boolean): Boolean {
        val tvShow = findTvShow(content) ?: return addSeriesToLibrary(content)
        tvShowDao.update(tvShow.copy(liked = favorite))
        return true
    }

    override suspend fun getSeasons(content: Content): List<Season> {
        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            val rows = cloudRead("getSeasons(${content.id})") {
                cloudCatalog?.getSeasons(content.id, session)
            }
            if (!rows.isNullOrEmpty()) {
                AppLogger.d("DiscoverRepo", "getSeasons nube hit para ${content.id} (${rows.size})")
                return rows.sortedBy { it.seasonNumber }.map { it.toSeason() }
            }
        }
        return try {
            val seasons = when (content.source) {
                ContentSource.TMDB -> {
                    val tmdbId = content.tmdbId ?: return emptyList()
                    tmdbApi.getTvDetail(tmdbId).seasons
                        ?.filter { it.seasonNumber > 0 }
                        ?.map { it.toSeason() }
                        ?: emptyList()
                }
                ContentSource.IMDB -> {
                    val imdbId = content.imdbId ?: return emptyList()
                    val tmdbId = resolveTmdbId(imdbId, ContentType.SERIES) ?: return emptyList()
                    tmdbApi.getTvDetail(tmdbId).seasons
                        ?.filter { it.seasonNumber > 0 }
                        ?.map { it.toSeason() }
                        ?: emptyList()
                }
            }
            if (seasons.isNotEmpty() && session != null) {
                runCatching {
                    cloudCatalog?.saveSeasons(
                        seasons.map { it.toCatalogSeasonRow(content.id) },
                        session
                    )
                }.onFailure {
                    AppLogger.e("DiscoverRepo", "write-through tv_seasons falló para ${content.id}", it)
                }
            }
            seasons
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getSeasons error for ${content.id}", e)
            emptyList()
        }
    }

    override suspend fun getSeasonDetail(content: Content, seasonNumber: Int): SeasonDetail {
        val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            val seasonRow = cloudRead("getSeason(${content.id}/S$seasonNumber)") {
                cloudCatalog?.getSeason(content.id, seasonNumber, session)
            }
            val episodes = cloudRead("getSeasonEpisodes(${content.id}/S$seasonNumber)") {
                cloudCatalog?.getSeasonEpisodes(content.id, seasonNumber, session)
            }
            if (!episodes.isNullOrEmpty()) {
                AppLogger.d("DiscoverRepo", "getSeasonDetail nube hit para ${content.id} S$seasonNumber (${episodes.size} eps)")
                return SeasonDetail(
                    seasonNumber = seasonNumber,
                    episodes = episodes.sortedBy { it.episodeNumber }.map { it.toEpisode() },
                    name = seasonRow?.name,
                    overview = seasonRow?.overview,
                    airDate = seasonRow?.airDate
                )
            }
        }
        return try {
            val detail = when (content.source) {
                ContentSource.TMDB -> {
                    val tmdbId = content.tmdbId ?: return SeasonDetail(seasonNumber)
                    tmdbApi.getTvSeason(tmdbId, seasonNumber).toSeasonDetail()
                }
                ContentSource.IMDB -> {
                    val imdbId = content.imdbId ?: return SeasonDetail(seasonNumber)
                    val tmdbId = resolveTmdbId(imdbId, ContentType.SERIES) ?: return SeasonDetail(seasonNumber)
                    tmdbApi.getTvSeason(tmdbId, seasonNumber).toSeasonDetail()
                }
            }
            if (detail.episodes.isNotEmpty() && session != null) {
                runCatching {
                    cloudCatalog?.saveSeasons(
                        listOf(
                            Season(
                                seasonNumber = seasonNumber,
                                name = detail.name ?: "",
                                episodeCount = detail.episodes.size,
                                airDate = detail.airDate,
                                overview = detail.overview
                            ).toCatalogSeasonRow(content.id)
                        ),
                        session
                    )
                    cloudCatalog?.saveEpisodes(
                        detail.episodes.map { it.toCatalogEpisodeRow(content.id) },
                        session
                    )
                }.onFailure {
                    AppLogger.e("DiscoverRepo", "write-through tv_seasons/tv_episodes falló para ${content.id} S$seasonNumber", it)
                }
            }
            detail
        } catch (e: Exception) {
            AppLogger.e("DiscoverRepo", "getSeasonDetail error for ${content.id} S$seasonNumber", e)
            SeasonDetail(seasonNumber)
        }
    }

    override suspend fun recordEpisode(content: Content, season: Int, episode: Int): SeriesTracking {
        val tvShow = findTvShow(content) ?: return getSeriesTracking(content)
        tvShowProgressDao?.insert(
            TvShowProgressEntity(tvShowId = tvShow.id, season = season, episode = episode)
        )
        tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
        reconcileSeriesState(tvShow)
        return getSeriesTracking(content)
    }

    override suspend fun unrecordEpisode(content: Content, season: Int, episode: Int): SeriesTracking {
        val tvShow = findTvShow(content) ?: return getSeriesTracking(content)
        tvShowProgressDao?.deleteEpisode(tvShow.id, season, episode)
        val lastWatched = tvShowProgressDao?.getLastWatchedAt(tvShow.id)
        tvShowDao.updateLastWatchedAt(tvShow.id, lastWatched)
        reconcileSeriesState(tvShow)
        return getSeriesTracking(content)
    }

    override suspend fun recordEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking {
        val tvShow = findTvShow(content) ?: return getSeriesTracking(content)
        val tracking = getSeriesTracking(content)
        val items = episodes
            .filter { !tracking.isEpisodeWatched(season, it) }
            .map {
                TvShowProgressEntity(tvShowId = tvShow.id, season = season, episode = it)
            }
        if (items.isNotEmpty()) {
            tvShowProgressDao?.insertAll(items)
        }
        tvShowDao.updateLastWatchedAt(tvShow.id, System.currentTimeMillis())
        reconcileSeriesState(tvShow)
        return getSeriesTracking(content)
    }

    override suspend fun unrecordSeasonEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking {
        val tvShow = findTvShow(content) ?: return getSeriesTracking(content)
        episodes.forEach { epNum ->
            tvShowProgressDao?.deleteEpisode(tvShow.id, season, epNum)
        }
        val lastWatched = tvShowProgressDao?.getLastWatchedAt(tvShow.id)
        tvShowDao.updateLastWatchedAt(tvShow.id, lastWatched)
        reconcileSeriesState(tvShow)
        return getSeriesTracking(content)
    }

    /**
     * Recalcula el estado de la serie respecto al usuario tras marcar/desmarcar
     * capítulos y lo persiste en DB local (y en la nube si había que ir a las
     * APIs por falta de datos de emisión), para que la serie se mueva entre los
     * listados de "Mis series" (En curso / Al día-próximos / Terminadas).
     */
    private suspend fun reconcileSeriesState(tvShow: TvShowEntity) {
        var current = tvShow
        var watchedCount = tvShowProgressDao?.getEpisodeCount(tvShow.id) ?: 0
        var aired = current.releasedEpisodes ?: current.totalEpisodes

        // Datos de emisión insuficientes -> obtenerlos de las APIs y persistirlos.
        if (aired == null || aired == 0) {
            val tmdbId = current.tmdbId
            if (tmdbId != null) {
                try {
                    val tv = tmdbApi.getTvDetailLight(tmdbId)
                    val released = computeReleasedEpisodes(tv)
                    current = current.copy(
                        totalEpisodes = tv.numberOfEpisodes ?: current.totalEpisodes,
                        releasedEpisodes = released,
                        nextEpisodeAirDate = tv.nextEpisodeToAir?.airDate,
                        nextEpisodeNumber = tv.nextEpisodeToAir?.episodeNumber,
                        nextEpisodeSeasonNumber = tv.nextEpisodeToAir?.seasonNumber,
                        seriesStatus = tv.status,
                        inProduction = tv.inProduction ?: current.inProduction,
                        numberOfSeasons = tv.numberOfSeasons ?: current.numberOfSeasons
                    )
                    tvShowDao.update(current)
                    aired = released ?: current.totalEpisodes
                    val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
                    if (session != null && current.contentId != null) {
                        cloudRead("reconcile-save-${current.contentId}") {
                            cloudCatalog?.saveTvShows(listOf(current.toEntityCatalogTvShowRow()), session)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("DiscoverRepo", "reconcile fetch detalle falló para ${current.title}", e)
                }
            }
        }

        val hasFuture = when (current.seriesStatus) {
            "Ended", "Canceled" -> false
            null -> current.inProduction != false
            else -> true
        }
        val caughtUp = aired != null && aired > 0 && watchedCount >= aired
        val now = System.currentTimeMillis()
        val reconciled = when {
            !caughtUp -> current.copy(status = WatchStatus.POR_VER, finishedAt = null, lastWatchedAt = now)
            hasFuture -> current.copy(status = WatchStatus.YA_VISTA, finishedAt = null, lastWatchedAt = now)
            else -> current.copy(status = WatchStatus.YA_VISTA, finishedAt = now, lastWatchedAt = now)
        }
        tvShowDao.update(reconciled)
        val state = when {
            !caughtUp -> "EN_CURSO"
            hasFuture -> "AL_DIA"
            else -> "TERMINADA"
        }
        AppLogger.i(
            "DiscoverRepo",
            "reconcile ${reconciled.title}: vistos=$watchedCount aired=$aired hasFuture=$hasFuture -> $state"
        )
    }

    private fun computeReleasedEpisodes(tv: TmdbTvDetailDto): Int? =
        if (tv.lastEpisodeToAir != null && tv.seasons != null) {
            tv.seasons.filter { it.seasonNumber > 0 }
                .sumOf { season ->
                    when {
                        season.seasonNumber < tv.lastEpisodeToAir.seasonNumber -> season.episodeCount
                        season.seasonNumber == tv.lastEpisodeToAir.seasonNumber -> tv.lastEpisodeToAir.episodeNumber
                        else -> 0
                    }
                }
        } else tv.numberOfEpisodes

    override suspend fun markSeriesFinished(content: Content): Boolean {
        val tvShow = findTvShow(content) ?: return false
        if (tvShow.inProduction == true
            || tvShow.seriesStatus in listOf("Returning Series", "In Production")
            || tvShow.nextEpisodeAirDate != null
        ) {
            return false
        }
        tvShowDao.update(tvShow.copy(status = WatchStatus.YA_VISTA, finishedAt = System.currentTimeMillis()))
        return true
    }

    override suspend fun clearSeriesFinished(content: Content) {
        val tvShow = findTvShow(content) ?: return
        if (tvShow.finishedAt != null) {
            tvShowDao.update(tvShow.copy(finishedAt = null, status = WatchStatus.POR_VER))
        }
    }

    override suspend fun reconcileAllLibrarySeries() {
        val shows = tvShowDao.getAll()
        for (show in shows) {
            try {
                reconcileSeriesState(show)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("DiscoverRepo", "reconcileAllLibrarySeries skip ${show.title}: ${e.message}")
            }
        }
    }

    override suspend fun getPersonSocialInfo(personId: Int): CastSocialInfo? {
        return try {
            val social = tmdbApi.getPersonExternalIds(personId)
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
            if (url != null && type != null) CastSocialInfo(url, type) else null
        } catch (e: Exception) {
            AppLogger.w("DiscoverRepo", "getPersonSocialInfo failed for person=$personId: ${e.message}")
            null
        }
    }

override suspend fun getFaId(content: Content): Int? {
    movieDao.getByContentId(content.id)?.faId
        ?.let { return it }
    tvShowDao.getByContentId(content.id)?.faId
        ?.let { return it }
val session = cloudRead("currentSession") { cloudCatalog?.currentSession() }
        if (session != null) {
            cloudRead("getFaMovieData(${content.id})") {
                cloudCatalog?.getFaMovieData(content.id, session)
            }?.faId
                ?.let { return it }
        }
    return null
}

    private suspend fun findMovie(content: Content): MovieEntity? {
        return movieDao.getByContentId(content.id)
            ?: content.tmdbId?.let { movieDao.getByTmdbId(it) }
            ?: content.imdbId?.let { movieDao.getByImdbId(it) }
    }

    private suspend fun findTvShow(content: Content): com.dondeloexan.data.local.entity.TvShowEntity? {
        return tvShowDao.getByContentId(content.id)
            ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            ?: content.imdbId?.let { tvShowDao.getByImdbId(it) }
    }
}
