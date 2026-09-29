package com.dondeloexan.data.repository

import com.dondeloexan.data.catalog.CloudCatalogRepository
import com.dondeloexan.data.catalog.toCatalogEpisodeRow
import com.dondeloexan.data.catalog.toCatalogSeasonRow
import com.dondeloexan.data.catalog.toEpisode
import com.dondeloexan.data.catalog.toSeason
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.pendingSpanishSeason
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.mapper.calculateReleasedEpisodes
import com.dondeloexan.data.remote.mapper.toEpisode
import com.dondeloexan.data.remote.mapper.toSeason
import com.dondeloexan.data.remote.mapper.toSeasonDetail
import com.dondeloexan.data.sync.SessionRefresher
import com.dondeloexan.data.sync.SyncManager
import com.dondeloexan.data.sync.toCatalogTvShowRow as toEntityCatalogTvShowRow
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.SeriesItem
import com.dondeloexan.domain.model.SeriesState
import com.dondeloexan.domain.model.detail.EpisodeRef
import com.dondeloexan.domain.model.detail.MovieWatchState
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import com.dondeloexan.domain.model.seriesStateFor
import com.dondeloexan.domain.repository.TrackingRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

class TrackingRepositoryImpl(
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao? = null,
    private val tmdbApi: TmdbApi,
    private val cloudCatalog: CloudCatalogRepository? = null,
    private val syncManager: SyncManager? = null,
    private val sessionRefresher: SessionRefresher? = null
) : TrackingRepository {

    private suspend inline fun <T> cloudRead(
        context: String,
        crossinline block: suspend () -> T?
    ): T? = try {
        withTimeout(4_000) { block() }
    } catch (e: TimeoutCancellationException) {
        AppLogger.w("DiscoveryTracking", "lectura nube $context agotó 4s; se usa API/cache")
        null
    } catch (e: Exception) {
        AppLogger.w("DiscoveryTracking", "lectura nube $context falló; se usa API/cache (${e.message})")
        null
    }

    private suspend fun resolveTmdbId(imdbId: String, type: ContentType): Int? {
        return try {
            when (type) {
                ContentType.MOVIE -> tmdbApi.findMovieByImdbId(imdbId).movieResults.firstOrNull()?.id
                ContentType.SERIES -> tmdbApi.findTvByImdbId(imdbId).tvResults.firstOrNull()?.id
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("DiscoveryTracking", "resolveTmdbId failed for $imdbId", e)
            null
        }
    }

    private suspend fun findMovie(content: Content): MovieEntity? {
        return movieDao.getByContentId(content.id)
            ?: content.tmdbId?.let { movieDao.getByTmdbId(it) }
            ?: content.imdbId?.let { movieDao.getByImdbId(it) }
    }

    private suspend fun findTvShow(content: Content): TvShowEntity? {
        return tvShowDao.getByContentId(content.id)
            ?: content.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            ?: content.imdbId?.let { tvShowDao.getByImdbId(it) }
    }

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
            nextEpisodeAirDate = tvShow.nextEpisodeAirDate,
            pendingEsSeason = tvShow.pendingEsSeason,
            spanishReleaseDate = tvShow.spanishReleaseDate
        )
    }

    override suspend fun setSeriesWatched(content: Content, watched: Boolean): Boolean {
        val tvShow = findTvShow(content) ?: return false
        return setSeriesWatchedById(tvShow.id, watched)
    }

    /**
     * Ruta ÚNICA de "marcar serie vista": marca todos los capítulos emitidos de
     * todas las temporadas (la 0/especiales se excluye, como en el resto de la
     * app) y recalcula el estado desde el último capítulo marcado.
     */
    override suspend fun setSeriesWatchedById(tvShowId: Long, watched: Boolean): Boolean {
        val tvShow = tvShowDao.getById(tvShowId) ?: return false
        val dao = tvShowProgressDao ?: return false
        val now = System.currentTimeMillis()

        if (!watched) {
            dao.deleteByTvShowId(tvShow.id)
            val cleared = tvShow.copy(
                status = WatchStatus.POR_VER,
                lastWatchedAt = null,
                finishedAt = null
            )
            tvShowDao.update(cleared)
            if (tvShow.status != cleared.status || tvShow.finishedAt != null) {
                pushTvShowStateToCloud(cleared)
            }
            return true
        }

        val aired = collectAiredEpisodes(tvShow)
        // Reemplazo completo: el progreso queda EXACTAMENTE igual al conjunto de
        // capítulos emitidos, así que repetir la acción no duplica filas.
        dao.deleteByTvShowId(tvShow.id)
        if (aired.refs.isNotEmpty()) {
            dao.insertAll(
                aired.refs.map {
                    TvShowProgressEntity(tvShowId = tvShow.id, season = it.season, episode = it.episode)
                }
            )
        }

        val watchedCount = aired.refs.size
        val released = if (aired.refs.isNotEmpty()) aired.refs.size else tvShow.releasedEpisodes
        val state = seriesStateFor(
            releasedEpisodes = released,
            totalEpisodes = aired.totalEpisodes,
            seriesStatus = aired.seriesStatus,
            inProduction = aired.inProduction,
            watchedCount = watchedCount
        )
        val reconciled = tvShow.copy(
            totalEpisodes = aired.totalEpisodes,
            releasedEpisodes = released,
            numberOfSeasons = aired.numberOfSeasons,
            seriesStatus = aired.seriesStatus,
            inProduction = aired.inProduction,
            nextEpisodeAirDate = aired.nextEpisodeAirDate,
            nextEpisodeNumber = aired.nextEpisodeNumber,
            nextEpisodeSeasonNumber = aired.nextEpisodeSeasonNumber,
            lastWatchedAt = now,
            status = if (state == SeriesState.EN_CURSO) WatchStatus.POR_VER else WatchStatus.YA_VISTA,
            finishedAt = if (state == SeriesState.TERMINADA) now else null
        )
        tvShowDao.update(reconciled)
        if (reconciled.status != tvShow.status || reconciled.finishedAt != tvShow.finishedAt) {
            pushTvShowStateToCloud(reconciled)
        }
        AppLogger.i(
            "DiscoveryTracking",
            "marcar ${reconciled.title}: vistos=$watchedCount emitidos=$released -> $state"
        )
        return true
    }

    private data class AiredEpisodes(
        val refs: List<EpisodeRef>,
        val totalEpisodes: Int?,
        val numberOfSeasons: Int?,
        val seriesStatus: String?,
        val inProduction: Boolean?,
        val nextEpisodeAirDate: String?,
        val nextEpisodeNumber: Int?,
        val nextEpisodeSeasonNumber: Int?
    )

    private suspend fun collectAiredEpisodes(tvShow: TvShowEntity): AiredEpisodes {
        val detail = tvShow.tmdbId?.let { tmdbId ->
            try {
                tmdbApi.getTvDetailLight(tmdbId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DiscoveryTracking", "marcar ${tvShow.title}: detalle TMDB falló", e)
                null
            }
        }
        val refs = mutableListOf<EpisodeRef>()
        if (detail != null) {
            val today = LocalDate.now()
            for (season in detail.seasons.orEmpty().filter { it.seasonNumber > 0 }) {
                try {
                    val seasonDetail = tmdbApi.getTvSeason(detail.id, season.seasonNumber)
                    for (ep in seasonDetail.episodes) {
                        if (isAired(ep.airDate, today)) {
                            refs += EpisodeRef(season = season.seasonNumber, episode = ep.episodeNumber)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(
                        "DiscoveryTracking",
                        "temporada ${season.seasonNumber} de ${tvShow.title}: uso episodeCount",
                        e
                    )
                    for (epNum in 1..season.episodeCount) {
                        refs += EpisodeRef(season = season.seasonNumber, episode = epNum)
                    }
                }
            }
        }
        return AiredEpisodes(
            refs = refs.filter { it.season > 0 && it.episode > 0 }.distinct(),
            totalEpisodes = detail?.numberOfEpisodes?.takeIf { it > 0 } ?: tvShow.totalEpisodes,
            numberOfSeasons = detail?.numberOfSeasons?.takeIf { it > 0 } ?: tvShow.numberOfSeasons,
            seriesStatus = detail?.status ?: tvShow.seriesStatus,
            inProduction = detail?.inProduction ?: tvShow.inProduction,
            nextEpisodeAirDate = detail?.nextEpisodeToAir?.airDate ?: tvShow.nextEpisodeAirDate,
            nextEpisodeNumber = detail?.nextEpisodeToAir?.episodeNumber ?: tvShow.nextEpisodeNumber,
            nextEpisodeSeasonNumber = detail?.nextEpisodeToAir?.seasonNumber ?: tvShow.nextEpisodeSeasonNumber
        )
    }

    private fun isAired(airDate: String?, today: LocalDate): Boolean {
        if (airDate == null) return true
        return try {
            !LocalDate.parse(airDate).isAfter(today)
        } catch (e: Exception) {
            true
        }
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
                AppLogger.d("DiscoveryTracking", "getSeasons nube hit para ${content.id} (${rows.size})")
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
                    AppLogger.e("DiscoveryTracking", "write-through tv_seasons falló para ${content.id}", it)
                }
            }
            seasons
        } catch (e: Exception) {
            AppLogger.e("DiscoveryTracking", "getSeasons error para ${content.id}", e)
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
                AppLogger.d("DiscoveryTracking", "getSeasonDetail nube hit para ${content.id} S$seasonNumber (${episodes.size} eps)")
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
                    AppLogger.e("DiscoveryTracking", "write-through tv_seasons/tv_episodes falló para ${content.id} S$seasonNumber", it)
                }
            }
            detail
        } catch (e: Exception) {
            AppLogger.e("DiscoveryTracking", "getSeasonDetail error para ${content.id} S$seasonNumber", e)
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

    override suspend fun recordEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking =
        recordEpisodes(content, episodes.map { EpisodeRef(season = season, episode = it) })

    /**
     * Cascada que puede abarcar varias temporadas (temporadas anteriores
     * completas + la actual hasta el capítulo elegido) en una sola escritura y
     * una única reconciliación de estado.
     */
    override suspend fun recordEpisodes(content: Content, entries: List<EpisodeRef>): SeriesTracking {
        val tvShow = findTvShow(content) ?: return getSeriesTracking(content)
        val tracking = getSeriesTracking(content)
        val items = entries
            .asSequence()
            .filter { it.season > 0 && it.episode > 0 }
            .distinct()
            .filter { !tracking.isEpisodeWatched(it.season, it.episode) }
            .map { TvShowProgressEntity(tvShowId = tvShow.id, season = it.season, episode = it.episode) }
            .toList()
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

    private suspend fun reconcileSeriesState(tvShow: TvShowEntity) {
        var current = tvShow
        var watchedCount = tvShowProgressDao?.getEpisodeCount(tvShow.id) ?: 0
        var aired = current.releasedEpisodes ?: current.totalEpisodes

        if (aired == null || aired == 0) {
            val tmdbId = current.tmdbId
            if (tmdbId != null) {
                try {
                    val tv = tmdbApi.getTvDetailLight(tmdbId)
                    val released = tv.calculateReleasedEpisodes(excludeSeason = current.pendingSpanishSeason())
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
                    AppLogger.e("DiscoveryTracking", "reconcile fetch detalle falló para ${current.title}", e)
                }
            }
        }

        val item = current.toSeriesItem(watchedCount)
        val hasFuture = item.hasFutureSeasons()
        val caughtUp = item.isCaughtUp()
        val now = System.currentTimeMillis()
        val knownLastWatched = tvShowProgressDao?.getLastWatchedAt(tvShow.id) ?: tvShow.lastWatchedAt
        val base = current.copy(lastWatchedAt = knownLastWatched)
        val reconciled = when {
            !caughtUp -> base.copy(status = WatchStatus.POR_VER, finishedAt = null)
            hasFuture -> base.copy(status = WatchStatus.YA_VISTA, finishedAt = null)
            else -> base.copy(status = WatchStatus.YA_VISTA, finishedAt = now)
        }
        val state = if (!caughtUp) "EN_CURSO" else if (hasFuture) "AL_DIA" else "TERMINADA"

        val stateChanged = reconciled.status != tvShow.status || reconciled.finishedAt != tvShow.finishedAt

        tvShowDao.update(reconciled)
        if (stateChanged) {
            pushTvShowStateToCloud(reconciled)
            AppLogger.i(
                "DiscoveryTracking",
                "reconcile ${reconciled.title}: vistos=$watchedCount aired=$aired hasFuture=$hasFuture -> $state (cambio de estado)"
            )
        } else {
            AppLogger.i(
                "DiscoveryTracking",
                "reconcile ${reconciled.title}: vistos=$watchedCount aired=$aired hasFuture=$hasFuture -> $state (sin cambios)"
            )
        }
    }

    private suspend fun pushTvShowStateToCloud(reconciled: TvShowEntity) {
        val session = sessionRefresher?.freshOrNull() ?: return
        val sync = syncManager ?: return
        try {
            cloudRead("syncSingle-${reconciled.contentId}") {
                sync.syncSingleTvShow(session, reconciled)
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("DiscoveryTracking", "push estado a nube falló para ${reconciled.title}", e)
        }
    }

    override suspend fun reconcileSeries(content: Content) {
        val tvShow = findTvShow(content) ?: return
        reconcileSeriesState(tvShow)
    }

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
                AppLogger.w("DiscoveryTracking", "reconcileAllLibrarySeries skip ${show.title}: ${e.message}")
            }
        }
    }

    private fun TvShowEntity.toSeriesItem(watchedCount: Int): SeriesItem = SeriesItem(
        id = id,
        contentId = contentId,
        tmdbId = tmdbId,
        title = title,
        year = year,
        posterUrl = posterUrl,
        addedAt = addedAt,
        isLiked = liked,
        isWatched = status == WatchStatus.YA_VISTA,
        finishedAt = finishedAt,
        watchedCount = watchedCount,
        totalEpisodes = if (totalEpisodes != null && totalEpisodes > 0) totalEpisodes else null,
        releasedEpisodes = releasedEpisodes,
        nextEpisodeAirDate = nextEpisodeAirDate,
        nextEpisodeNumber = nextEpisodeNumber,
        nextEpisodeSeasonNumber = nextEpisodeSeasonNumber,
        seriesStatus = seriesStatus,
        inProduction = inProduction,
        numberOfSeasons = numberOfSeasons,
        lastWatchedAt = lastWatchedAt,
        pendingEsSeason = pendingEsSeason,
        spanishReleaseDate = spanishReleaseDate
    )
}
