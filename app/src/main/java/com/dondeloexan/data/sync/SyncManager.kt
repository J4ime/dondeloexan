package com.dondeloexan.data.sync

import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.remote.api.SupabaseSyncApi
import com.dondeloexan.util.AppLogger
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SyncSummary(
    val movies: Int,
    val tvShows: Int,
    val tvShowProgress: Int,
    val searchHistory: Int,
    val userPlatforms: Int,
    val blacklist: Int,
    val criticReviews: Int,
    val faMovieData: Int
) {
    val total: Int
        get() = movies + tvShows + tvShowProgress + searchHistory +
            userPlatforms + blacklist + criticReviews + faMovieData
}

@kotlinx.serialization.Serializable
private data class TvShowIdRow(
    val id: String,
    @SerialName("content_id") val contentId: String? = null
)

class SyncManager(
    private val syncApi: SupabaseSyncApi,
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
     * Sincronización por reemplazo completo (snapshot): se borran todas las
     * filas del usuario en cada tabla (RLS lo permite) y se re-sube el estado
     * local. La BD autogenera el id UUID de cada fila (DEFAULT gen_random_uuid()),
     * así que la app no envía id alguno: el borrado previo evita duplicados.
     */
    suspend fun syncAll(session: SessionState): SyncSummary {
        val userId = session.userId

        deleteUserTables(userId, session)

        val movies = movieDao.getAll()
        if (movies.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(MovieSyncDto.serializer()),
                movies.map { it.toSyncDto(userId) }
            )
            upload("movies", movies.size, payload, session)
        }

        val tvShows = tvShowDao.getAll()
        // Mapa localShowId -> contentId para resolver el uuid de cada serie.
        val showContentIdByLocalId = tvShows.associate { it.id to it.contentId }
        val showUuidByContentId = mutableMapOf<String, String>()
        if (tvShows.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(TvShowSyncDto.serializer()),
                tvShows.map { it.toSyncDto(userId) }
            )
            val body = upload("tv_shows", tvShows.size, payload, session, returnRepresentation = true)
            parseShowIds(body).forEach { row ->
                row.contentId?.let { showUuidByContentId[it] = row.id }
            }
        }

        val progress = tvShowProgressDao.getAll()
        val progressDtos = mutableListOf<TvShowProgressSyncDto>()
        if (progress.isNotEmpty()) {
            var skipped = 0
            progress.forEach { entry ->
                val contentId = showContentIdByLocalId[entry.tvShowId]
                val remoteShowId = contentId?.let { showUuidByContentId[it] }
                if (remoteShowId != null) {
                    progressDtos += entry.toSyncDto(userId, remoteShowId)
                } else {
                    skipped++
                    AppLogger.w(
                        "Sync",
                        "temporada $entry.season capítulo $entry.episode omitido: no se encontró su serie en tv_shows (content_id=$contentId)"
                    )
                }
            }
            if (progressDtos.isNotEmpty()) {
                val payload = syncJson.encodeToString(
                    ListSerializer(TvShowProgressSyncDto.serializer()),
                    progressDtos
                )
                upload("tv_show_progress", progressDtos.size, payload, session)
            }
            if (skipped > 0) {
                AppLogger.w("Sync", "temporadas omitidas por serie desconocida: $skipped")
            }
        }

        val history = searchHistoryDao.getRecent(Int.MAX_VALUE)
        if (history.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(SearchHistorySyncDto.serializer()),
                history.map { it.toSyncDto(userId) }
            )
            upload("search_history", history.size, payload, session)
        }

        val platforms = userPlatformDao.getAll()
        if (platforms.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(UserPlatformSyncDto.serializer()),
                platforms.map { it.toSyncDto(userId) }
            )
            upload("user_platforms", platforms.size, payload, session)
        }

        val blacklist = blacklistDao.getAll()
        if (blacklist.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(BlacklistSyncDto.serializer()),
                blacklist.map { it.toSyncDto(userId) }
            )
            upload("blacklist", blacklist.size, payload, session)
        }

        val criticReviews = criticReviewDao.getAll()
        if (criticReviews.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(CriticReviewSyncDto.serializer()),
                criticReviews.map { it.toSyncDto(userId) }
            )
            upload("critic_reviews", criticReviews.size, payload, session)
        }

        val faMovieData = faMovieDataDao.getAll()
        if (faMovieData.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(FaMovieDataSyncDto.serializer()),
                faMovieData.map { it.toSyncDto(userId) }
            )
            upload("fa_movie_data", faMovieData.size, payload, session)
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

    private suspend fun deleteUserTables(userId: String, session: SessionState) {
        val tables = listOf(
            "tv_show_progress",
            "tv_shows",
            "movies",
            "search_history",
            "user_platforms",
            "blacklist",
            "critic_reviews",
            "fa_movie_data"
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
        session: SessionState,
        returnRepresentation: Boolean = false
    ): String = try {
        AppLogger.i("Sync", "Subiendo $table ($rows filas)")
        syncApi.insertAll(table, payload, session, returnRepresentation)
    } catch (e: Exception) {
        AppLogger.e("Sync", "insert $table falló ($rows filas): payload=${payload.take(2000)}", e)
        throw e
    }

    private fun parseShowIds(body: String): List<TvShowIdRow> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) {
            json.decodeFromString<List<TvShowIdRow>>(trimmed)
        } else {
            listOf(json.decodeFromString<TvShowIdRow>(trimmed))
        }
    }
}