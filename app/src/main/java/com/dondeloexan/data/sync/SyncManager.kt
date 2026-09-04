package com.dondeloexan.data.sync

import com.dondeloexan.data.catalog.CloudCatalogRepository
import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.remote.api.SupabaseSyncApi
import com.dondeloexan.domain.model.SessionState
import com.dondeloexan.domain.model.SyncSummary
import com.dondeloexan.util.AppLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncManager(
    private val syncApi: SupabaseSyncApi,
    private val cloudCatalog: CloudCatalogRepository,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val userPlatformDao: UserPlatformDao,
    private val blacklistDao: BlacklistDao,
    private val criticReviewDao: CriticReviewDao,
    private val faMovieDataDao: FaMovieDataDao,
    private val json: Json
) {

    /**
     * Codifica siempre todas las claves (incluso null) para que los arrays de
     * objetos tengan claves uniformes: PostgREST rechaza arrays heterogéneos
     * con PGRST100 "all object keys must match".
     */
    private val syncJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Sincronización en dos planos:
     *   1) CATÁLOGO GLOBAL: upsert (merge por content_id), NUNCA se borra.
     *      La biblioteca local alimenta movies/tv_shows y el cache local de
     *      críticas/FA alimenta critic_reviews/fa_movie_data.
     *   2) USUARIOS: snapshot por usuario (se borran user_movies, user_tv_shows,
     *      tv_show_progress, search_history, user_platforms y blacklist y se
     *      re-suben desde el estado local). La BD autogenera el id UUID
     *      (DEFAULT gen_random_uuid()); la app no envía ids.
     */
    suspend fun syncAll(session: SessionState): SyncSummary {
        val userId = session.userId

        val movies = movieDao.getAll().filter { !it.contentId.isNullOrBlank() }
        val tvShows = tvShowDao.getAll().filter { !it.contentId.isNullOrBlank() }

        // ── Catálogo global (merge, nunca borrar) ──
        if (movies.isNotEmpty()) {
            cloudCatalog.saveMovies(movies.map { it.toCatalogMovieRow() }, session)
        }

        // Mapa localShowId -> contentId para resolver el progreso por content_id.
        val showContentIdByLocalId = tvShows.associate { it.id to it.contentId.orEmpty() }
        if (tvShows.isNotEmpty()) {
            cloudCatalog.saveTvShows(tvShows.map { it.toCatalogTvShowRow() }, session)
        }

        val criticReviews = criticReviewDao.getAll()
        if (criticReviews.isNotEmpty()) {
            cloudCatalog.saveCriticReviews(
                criticReviews.map { it.toCatalogCriticReviewRow() }, session
            )
        }

        val faMovieData = faMovieDataDao.getAll()
        if (faMovieData.isNotEmpty()) {
            cloudCatalog.saveFaMovieData(faMovieData.map { it.toCatalogFaRow() }, session)
        }

        // ── Borrar tablas por usuario (esnapshot) ──
        deleteUserTables(userId, session)

        // ── Relaciones de usuario ──
        if (movies.isNotEmpty()) {
            upload(
                "user_movies", movies.size,
                syncJson.encodeToString(
                    ListSerializer(UserMovieSyncDto.serializer()),
                    movies.map { it.toUserMovieSyncDto(userId) }
                ),
                session
            )
        }

        if (tvShows.isNotEmpty()) {
            upload(
                "user_tv_shows", tvShows.size,
                syncJson.encodeToString(
                    ListSerializer(UserTvShowSyncDto.serializer()),
                    tvShows.map { it.toUserTvShowSyncDto(userId) }
                ),
                session
            )
        }

        val progress = tvShowProgressDao.getAll()
        var progressDtos = mutableListOf<TvShowProgressSyncDto>()
        if (progress.isNotEmpty()) {
            var skipped = 0
            progress.forEach { entry ->
                val contentId = showContentIdByLocalId[entry.tvShowId]
                if (contentId.isNullOrBlank()) {
                    skipped++
                    AppLogger.w(
                        "Sync",
                        "temporada ${entry.season} capítulo ${entry.episode} omitido: su serie no tiene content_id"
                    )
                } else {
                    progressDtos += entry.toSyncDto(userId, contentId)
                }
            }
            if (progressDtos.isNotEmpty()) {
                upload(
                    "tv_show_progress", progressDtos.size,
                    syncJson.encodeToString(
                        ListSerializer(TvShowProgressSyncDto.serializer()),
                        progressDtos
                    ),
                    session
                )
            }
            if (skipped > 0) {
                AppLogger.w("Sync", "temporadas omitidas por serie desconocida: $skipped")
            }
        }

        val history = searchHistoryDao.getRecent(Int.MAX_VALUE)
        if (history.isNotEmpty()) {
            upload(
                "search_history", history.size,
                syncJson.encodeToString(
                    ListSerializer(SearchHistorySyncDto.serializer()),
                    history.map { it.toSyncDto(userId) }
                ),
                session
            )
        }

        val platforms = userPlatformDao.getAll()
        if (platforms.isNotEmpty()) {
            upload(
                "user_platforms", platforms.size,
                syncJson.encodeToString(
                    ListSerializer(UserPlatformSyncDto.serializer()),
                    platforms.map { it.toSyncDto(userId) }
                ),
                session
            )
        }

        val blacklist = blacklistDao.getAll()
        if (blacklist.isNotEmpty()) {
            upload(
                "blacklist", blacklist.size,
                syncJson.encodeToString(
                    ListSerializer(BlacklistSyncDto.serializer()),
                    blacklist.map { it.toSyncDto(userId) }
                ),
                session
            )
        }

        val summary = SyncSummary(
            movies = movies.size,
            tvShows = tvShows.size,
            tvShowProgress = progressDtos.size,
            searchHistory = history.size,
            userPlatforms = platforms.size,
            blacklist = blacklist.size,
            criticReviews = criticReviews.size,
            faMovieData = faMovieData.size
        )
        AppLogger.i(
            "Sync",
            "Sync completado para ${session.email}: ${summary.total} datos (movies=${summary.movies}, tvShows=${summary.tvShows}, progress=${summary.tvShowProgress}, history=${summary.searchHistory}, platforms=${summary.userPlatforms}, blacklist=${summary.blacklist}, criticReviews=${summary.criticReviews}, faMovieData=${summary.faMovieData})"
        )
        return summary
    }

    /**
     * Sube SOLO el catálogo global (movies, tv_shows, critic_reviews,
     * fa_movie_data) a la nube con la sesión anónima. No toca tablas de usuario
     * ni requiere login, por lo que vale para actualizar el catálogo tras un
     * refresco de biblioteca programado. Best-effort: los fallos se registran.
     */
    suspend fun syncCatalog() {
        val anon = syncApi.anonymousSession()
        val movies = movieDao.getAll().filter { !it.contentId.isNullOrBlank() }
        val tvShows = tvShowDao.getAll().filter { !it.contentId.isNullOrBlank() }
        val criticReviews = criticReviewDao.getAll()
        val faMovieData = faMovieDataDao.getAll()

        if (movies.isNotEmpty()) {
            cloudCatalog.saveMovies(movies.map { it.toCatalogMovieRow() }, anon)
        }
        if (tvShows.isNotEmpty()) {
            cloudCatalog.saveTvShows(tvShows.map { it.toCatalogTvShowRow() }, anon)
        }
        if (criticReviews.isNotEmpty()) {
            cloudCatalog.saveCriticReviews(criticReviews.map { it.toCatalogCriticReviewRow() }, anon)
        }
        if (faMovieData.isNotEmpty()) {
            cloudCatalog.saveFaMovieData(faMovieData.map { it.toCatalogFaRow() }, anon)
        }
        AppLogger.i("Sync", "syncCatalog completado: movies=${movies.size}, tvShows=${tvShows.size}, criticReviews=${criticReviews.size}, faMovieData=${faMovieData.size}")
    }

    suspend fun syncSingleTvShow(session: SessionState, tvShow: TvShowEntity) {
        val contentId = tvShow.contentId
        if (contentId.isNullOrBlank()) {
            AppLogger.w("Sync", "syncSingleTvShow omitido: serie '${tvShow.title}' sin content_id")
            return
        }
        val userId = session.userId

        deleteTableRowsByContent("user_tv_shows", contentId, session)
        deleteTableRowsByContent("tv_show_progress", contentId, session)

        syncApi.insertAll(
            "user_tv_shows",
            syncJson.encodeToString(
                ListSerializer(UserTvShowSyncDto.serializer()),
                listOf(tvShow.toUserTvShowSyncDto(userId))
            ),
            session
        )

        val progress = tvShowProgressDao.getByTvShowId(tvShow.id)
        if (progress.isNotEmpty()) {
            val dtos = progress.map { it.toSyncDto(userId, contentId) }
            syncApi.insertAll(
                "tv_show_progress",
                syncJson.encodeToString(
                    ListSerializer(TvShowProgressSyncDto.serializer()),
                    dtos
                ),
                session
            )
        }
    }

    private suspend fun deleteTableRowsByContent(table: String, contentId: String, session: SessionState) {
        try {
            syncApi.deleteTableRowsByContent(table, contentId, session)
        } catch (e: Exception) {
            AppLogger.e("Sync", "borrar $table ($contentId) falló", e)
            throw e
        }
    }

    private suspend fun deleteUserTables(userId: String, session: SessionState) {
        val tables = listOf(
            "user_movies",
            "user_tv_shows",
            "tv_show_progress",
            "search_history",
            "user_platforms",
            "blacklist"
        )
        tables.forEach { table ->
            try {
                syncApi.deleteTableRows(table, userId, session)
            } catch (e: Exception) {
                AppLogger.e("Sync", "borrar $table del usuario $userId falló", e)
                throw e
            }
        }
    }

    private suspend fun upload(
        table: String,
        rows: Int,
        payload: String,
        session: SessionState
    ): String = try {
        AppLogger.i("Sync", "Subiendo $table ($rows filas)")
        syncApi.insertAll(table, payload, session)
    } catch (e: Exception) {
        AppLogger.e("Sync", "insert $table falló ($rows filas): payload=${payload.take(2000)}", e)
        throw e
    }
}