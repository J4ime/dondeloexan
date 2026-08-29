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

    /** Codifica siempre todas las claves (incluso null) para que los arrays de
     *  objetos tengan claves uniformes: PostgREST rechaza arrays heterogéneos
     *  con PGRST100 "all object keys must match". */
    private val syncJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun syncAll(session: SessionState): SyncSummary {
        val userId = session.userId

        val movies = movieDao.getAll()
        if (movies.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(MovieSyncDto.serializer()),
                movies.map { it.toSyncDto(userId) }
            )
            upsert("movies", listOf("user_id", "local_id"), movies.size, payload, session)
        }

        val tvShows = tvShowDao.getAll()
        val localToCloudId = mutableMapOf<Long, Long>()
        if (tvShows.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(TvShowSyncDto.serializer()),
                tvShows.map { it.toSyncDto(userId) }
            )
            val body = upsert(
                "tv_shows", listOf("user_id", "local_id"), tvShows.size, payload, session,
                returnRepresentation = true
            )
            parseRowIds(body).forEach { row ->
                row.localId?.let { localToCloudId[it] = row.id }
            }
        }

        val progress = tvShowProgressDao.getAll()
        if (progress.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(TvShowProgressSyncDto.serializer()),
                progress.map { it.toSyncDto(userId, localToCloudId[it.tvShowId] ?: it.tvShowId) }
            )
            upsert("tv_show_progress", listOf("user_id", "local_id"), progress.size, payload, session)
        }

        val history = searchHistoryDao.getRecent(Int.MAX_VALUE)
        if (history.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(SearchHistorySyncDto.serializer()),
                history.map { it.toSyncDto(userId) }
            )
            upsert("search_history", listOf("user_id", "local_id"), history.size, payload, session)
        }

        val platforms = userPlatformDao.getAll()
        if (platforms.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(UserPlatformSyncDto.serializer()),
                platforms.map { it.toSyncDto(userId) }
            )
            upsert("user_platforms", listOf("user_id", "platform_name"), platforms.size, payload, session)
        }

        val blacklist = blacklistDao.getAll()
        if (blacklist.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(BlacklistSyncDto.serializer()),
                blacklist.map { it.toSyncDto(userId) }
            )
            upsert("blacklist", listOf("user_id", "content_id"), blacklist.size, payload, session)
        }

        val criticReviews = criticReviewDao.getAll()
        if (criticReviews.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(CriticReviewSyncDto.serializer()),
                criticReviews.map { it.toSyncDto(userId) }
            )
            upsert("critic_reviews", listOf("user_id", "content_id"), criticReviews.size, payload, session)
        }

        val faMovieData = faMovieDataDao.getAll()
        if (faMovieData.isNotEmpty()) {
            val payload = syncJson.encodeToString(
                ListSerializer(FaMovieDataSyncDto.serializer()),
                faMovieData.map { it.toSyncDto(userId) }
            )
            upsert("fa_movie_data", listOf("user_id", "content_id"), faMovieData.size, payload, session)
        }

        val summary = SyncSummary(
            movies = movies.size,
            tvShows = tvShows.size,
            tvShowProgress = progress.size,
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

    private suspend fun upsert(
        table: String,
        onConflict: List<String>,
        rows: Int,
        payload: String,
        session: SessionState,
        returnRepresentation: Boolean = false
    ): String = try {
        AppLogger.i("Sync", "Subiendo $table ($rows filas)")
        syncApi.upsert(table, onConflict, payload, session, returnRepresentation)
    } catch (e: Exception) {
        AppLogger.e("Sync", "upsert $table falló ($rows filas): payload=${payload.take(2000)}", e)
        throw e
    }

    private fun parseRowIds(body: String): List<DbRowId> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) {
            json.decodeFromString<List<DbRowId>>(trimmed)
        } else {
            listOf(json.decodeFromString<DbRowId>(trimmed))
        }
    }
}