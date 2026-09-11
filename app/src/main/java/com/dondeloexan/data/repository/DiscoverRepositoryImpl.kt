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
import com.dondeloexan.data.local.entity.hasFutureSeasons
import com.dondeloexan.data.local.entity.isCaughtUpBy
import com.dondeloexan.data.remote.mapper.toEpisode
import com.dondeloexan.data.remote.mapper.toSeason
import com.dondeloexan.data.remote.mapper.toSeasonDetail
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.data.remote.api.WikidataRelationship
import com.dondeloexan.data.remote.dto.TmdbPersonCredit
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.data.sync.SessionStore
import com.dondeloexan.data.sync.SyncManager
import com.dondeloexan.data.sync.toCatalogTvShowRow as toEntityCatalogTvShowRow
import com.dondeloexan.data.sync.toTvShowEntity
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.CompanySearchResult
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.PersonSearchResult
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
import com.dondeloexan.domain.repository.TrackingRepository
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
    private val cloudCatalog: CloudCatalogRepository? = null,
    private val syncManager: SyncManager? = null,
    private val sessionStore: SessionStore? = null,
    private val trackingRepository: TrackingRepository
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

            val content = tv.toDomain(null, platforms, credits, externalLinks)

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
                        numberOfSeasons = tv.numberOfSeasons,
                        releasedEpisodes = existing.releasedEpisodes ?: computeReleasedEpisodes(tv)
                    ).let { content.toTvShowEntity(it) }
                )
            }

            content
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

    // Nº de recetas distintas que rotan por página en el Descubrir: populares,
    // mejor valoradas, novedades y tendencias semanales.
    private val DISCOVER_RECIPES = 4

    /**
     * Descubrir sin buscar. Cada página rota por una receta distinta (en vez de
     * mostrar siempre el mismo pool de populares):
     *   0 -> populares (popularity.desc, últimos 5 años, vote_count>=100)
     *   1 -> mejor valoradas (vote_average.desc, sin límite de fecha, vote_count>=200)
     *   2 -> novedades (release_date desc / first_air_date desc, último año)
     *   3 -> tendencias de la semana (trending/all/week)
     * El filtro por plataformas activas se aplica a TODAS las recetas (0-3): las
     * de discover (0-2) vía el parámetro watch_providers de TMDB y la de
     * tendencias (3) con un post-filtro, ya que /trending no acepta proveedor.
     */
    override suspend fun fetchTrendingPage(page: Int, filterByPlatforms: Boolean): List<ContentPreview> {
        val activePlatforms = userPlatformDao.getActiveNames().toSet()
        val now = LocalDate.now()
        val fiveYearsAgo = now.minusYears(5).toString()
        val oneYearFuture = now.plusYears(1).toString()
        val farFuture = LocalDate.parse(oneYearFuture).isAfter(now.plusMonths(3))
        val providerFilter = if (filterByPlatforms && !farFuture) {
            TmdbProviderIds.toPipeSeparated(activePlatforms)
        } else null
        val watchRegion = if (providerFilter == null) null else "ES"
        val postFilterByPlatforms = filterByPlatforms && providerFilter == null
        val preferredTypes = if (filterByPlatforms) {
            userPreferencesDataStore.preferredAvailabilityTypes.first()
        } else null

        return when ((page - 1) % DISCOVER_RECIPES) {
            0 -> buildDiscoverMix(
                page, activePlatforms, providerFilter, watchRegion, postFilterByPlatforms, preferredTypes,
                movieSortBy = "popularity.desc", tvSortBy = "popularity.desc",
                movieDateGte = fiveYearsAgo, movieDateLte = oneYearFuture,
                tvDateGte = fiveYearsAgo, tvDateLte = oneYearFuture,
                voteCountGte = 100
            )
            1 -> buildDiscoverMix(
                page, activePlatforms, providerFilter, watchRegion, postFilterByPlatforms, preferredTypes,
                movieSortBy = "vote_average.desc", tvSortBy = "vote_average.desc",
                movieDateGte = null, movieDateLte = null,
                tvDateGte = null, tvDateLte = null,
                voteCountGte = 200
            )
            2 -> buildDiscoverMix(
                page, activePlatforms, providerFilter, watchRegion, postFilterByPlatforms, preferredTypes,
                movieSortBy = "primary_release_date.desc", tvSortBy = "first_air_date.desc",
                movieDateGte = now.minusYears(1).toString(), movieDateLte = null,
                tvDateGte = now.minusYears(1).toString(), tvDateLte = null,
                voteCountGte = 100
            )
            else -> buildTrendingWeek(page, activePlatforms, preferredTypes, filterByPlatforms)
        }
    }

    private suspend fun buildDiscoverMix(
        page: Int,
        activePlatforms: Set<String>,
        providerFilter: String?,
        watchRegion: String?,
        postFilterByPlatforms: Boolean,
        preferredTypes: Set<String>?,
        movieSortBy: String,
        tvSortBy: String,
        movieDateGte: String?,
        movieDateLte: String?,
        tvDateGte: String?,
        tvDateLte: String?,
        voteCountGte: Int?
    ): List<ContentPreview> {
        val takePerType = if (postFilterByPlatforms) 30 else 20

        return coroutineScope {
            val movieDeferred = async {
                tmdbApi.discoverMovie(
                    page = page,
                    watchProviders = providerFilter,
                    watchRegion = watchRegion,
                    releaseDateGte = movieDateGte,
                    releaseDateLte = movieDateLte,
                    sortBy = movieSortBy,
                    voteCountGte = voteCountGte
                )
            }
            val tvDeferred = async {
                tmdbApi.discoverTv(
                    page = page,
                    watchProviders = providerFilter,
                    watchRegion = watchRegion,
                    firstAirDateGte = tvDateGte,
                    firstAirDateLte = tvDateLte,
                    sortBy = tvSortBy,
                    voteCountGte = voteCountGte
                )
            }

            val movieResults = movieDeferred.await()
            val tvResults = tvDeferred.await()

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

    private suspend fun buildTrendingWeek(
        page: Int,
        activePlatforms: Set<String>,
        preferredTypes: Set<String>?,
        filterByPlatforms: Boolean
    ): List<ContentPreview> {
        AppLogger.d("DiscoverRepo", "discover receta: tendencias de la semana page=$page")
        val results = tmdbApi.getTrending()
        val takePerType = 20

        val moviePreviews = attachTmbdPlatforms(
            results.results
                .filter { it.mediaType == "movie" }
                .filter { !it.adult }
                .map { it.toContentPreview() }
                .take(takePerType)
        )

        val tvPreviews = attachTmbdPlatforms(
            results.results
                .filter { it.mediaType == "tv" }
                .filter { !it.adult }
                .map { it.copy(mediaType = "tv").toContentPreview() }
                .take(takePerType)
        )

        var combined = (moviePreviews + tvPreviews).shuffled()

        if (filterByPlatforms) {
            val before = combined.size
            combined = combined.filter { preview ->
                preview.streamingPlatforms.any { platform ->
                    activePlatforms.any { active ->
                        platformMatches(platform.platformName, active)
                    } && (preferredTypes == null || preferredTypes.contains(platform.availabilityType.name))
                }
            }
            AppLogger.d("DiscoverRepo", "trending week platform filter: $before -> ${combined.size}")
        }

        return combined
    }

    override suspend fun fetchSearchPage(query: String, page: Int): List<ContentPreview> {
        val tokens = normalizedTokens(query)
        // Búsqueda localizada (es-ES). Si no devuelve nada, se reintenta SIN
        // idioma (TMDB usa su default) para ampliar coincidencias, p. ej. por
        // título original.
        val primary = searchPageOnce(query, page, "es-ES", tokens)
        val results = if (primary.isNotEmpty()) primary else searchPageOnce(query, page, null, tokens)
        return if (results.isNotEmpty()) fetchPlatforms(results) else emptyList()
    }

    private suspend fun searchPageOnce(
        query: String,
        page: Int,
        language: String?,
        tokens: List<String>
    ): List<ContentPreview> {
        val seen = mutableSetOf<String>()
        val tmdbResult = tmdbApi.searchMulti(query, language = language, page = page)
        return tmdbResult.results
            .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
            .map { it.toContentPreview() }
            .filter { isRelevant(it, tokens) }
            .filter { seen.add(it.id) }
            .take(20)
    }

    private fun normalizedTokens(query: String): List<String> {
        return normalizeForSearch(query)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
    }

    private fun isRelevant(preview: ContentPreview, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return preview.title.isNotBlank()
        val fullPhrase = normalizeForSearch(tokens.joinToString(" "))
        val candidates = listOfNotNull(preview.title, preview.originalTitle)
            .map { normalizeForSearch(it) }
            .filter { it.isNotBlank() }
        if (candidates.isEmpty()) return false
        return candidates.any { title ->
            title.contains(fullPhrase) || tokens.all { title.contains(it) }
        }
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

    override suspend fun searchPeople(query: String): List<PersonSearchResult> {
        return try {
            tmdbApi.searchPerson(query).results.map {
                PersonSearchResult(
                    id = it.id,
                    name = it.name,
                    profilePath = it.profilePath,
                    knownForDepartment = it.knownForDepartment
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoverRepo", "searchPeople error for $query", e)
            emptyList()
        }
    }

    override suspend fun searchCompanies(query: String): List<CompanySearchResult> {
        return try {
            tmdbApi.searchCompany(query).results.map {
                CompanySearchResult(
                    id = it.id,
                    name = it.name,
                    logoPath = it.logoPath
                )
            }
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
    // --- Detail & tracking (delegado a TrackingRepository) ---

    override suspend fun getMovieWatchState(content: Content): MovieWatchState =
        trackingRepository.getMovieWatchState(content)

    override suspend fun setMovieWatched(content: Content, watched: Boolean): MovieWatchState =
        trackingRepository.setMovieWatched(content, watched)

    override suspend fun setMovieFavorite(content: Content, favorite: Boolean): MovieWatchState =
        trackingRepository.setMovieFavorite(content, favorite)

    override suspend fun addMovieToLibrary(content: Content): MovieWatchState =
        trackingRepository.addMovieToLibrary(content)

    override suspend fun addSeriesToLibrary(content: Content): Boolean =
        trackingRepository.addSeriesToLibrary(content)

    override suspend fun getSeriesTracking(content: Content): SeriesTracking =
        trackingRepository.getSeriesTracking(content)

    override suspend fun setSeriesWatched(content: Content, watched: Boolean): Boolean =
        trackingRepository.setSeriesWatched(content, watched)

    override suspend fun setSeriesFavorite(content: Content, favorite: Boolean): Boolean =
        trackingRepository.setSeriesFavorite(content, favorite)

    override suspend fun getSeasons(content: Content): List<Season> =
        trackingRepository.getSeasons(content)

    override suspend fun getSeasonDetail(content: Content, seasonNumber: Int): SeasonDetail =
        trackingRepository.getSeasonDetail(content, seasonNumber)

    override suspend fun recordEpisode(content: Content, season: Int, episode: Int): SeriesTracking =
        trackingRepository.recordEpisode(content, season, episode)

    override suspend fun unrecordEpisode(content: Content, season: Int, episode: Int): SeriesTracking =
        trackingRepository.unrecordEpisode(content, season, episode)

    override suspend fun recordEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking =
        trackingRepository.recordEpisodes(content, season, episodes)

    override suspend fun unrecordSeasonEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking =
        trackingRepository.unrecordSeasonEpisodes(content, season, episodes)

    override suspend fun markSeriesFinished(content: Content): Boolean =
        trackingRepository.markSeriesFinished(content)

    override suspend fun clearSeriesFinished(content: Content) {
        trackingRepository.clearSeriesFinished(content)
    }

    override suspend fun reconcileAllLibrarySeries() {
        trackingRepository.reconcileAllLibrarySeries()
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
}
