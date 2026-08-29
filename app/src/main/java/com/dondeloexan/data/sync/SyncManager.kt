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

    suspend fun syncAll(session: SessionState): SyncSummary {
        val userId = session.userId

        val movies = movieDao.getAll()
        if (movies.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(MovieSyncDto.serializer()),
                movies.map { it.toSyncDto(userId) }
            )
            syncApi.upsert("movies", listOf("user_id", "local_id"), payload, session)
        }

        val tvShows = tvShowDao.getAll()
        val localToCloudId = mutableMapOf<Long, Long>()
        if (tvShows.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(TvShowSyncDto.serializer()),
                tvShows.map { it.toSyncDto(userId) }
            )
            val body = syncApi.upsert(
                "tv_shows", listOf("user_id", "local_id"), payload, session,
                returnRepresentation = true
            )
            parseRowIds(body).forEach { row ->
                row.localId?.let { localToCloudId[it] = row.id }
            }
        }

        val progress = tvShowProgressDao.getAll()
        if (progress.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(TvShowProgressSyncDto.serializer()),
                progress.map { it.toSyncDto(userId, localToCloudId[it.tvShowId] ?: it.tvShowId) }
            )
            syncApi.upsert("tv_show_progress", listOf("user_id", "local_id"), payload, session)
        }

        val history = searchHistoryDao.getRecent(Int.MAX_VALUE)
        if (history.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(SearchHistorySyncDto.serializer()),
                history.map { it.toSyncDto(userId) }
            )
            syncApi.upsert("search_history", listOf("user_id", "local_id"), payload, session)
        }

        val platforms = userPlatformDao.getAll()
        if (platforms.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(UserPlatformSyncDto.serializer()),
                platforms.map { it.toSyncDto(userId) }
            )
            syncApi.upsert("user_platforms", listOf("user_id", "platform_name"), payload, session)
        }

        val blacklist = blacklistDao.getAll()
        if (blacklist.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(BlacklistSyncDto.serializer()),
                blacklist.map { it.toSyncDto(userId) }
            )
            syncApi.upsert("blacklist", listOf("user_id", "content_id"), payload, session)
        }

        val criticReviews = criticReviewDao.getAll()
        if (criticReviews.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(CriticReviewSyncDto.serializer()),
                criticReviews.map { it.toSyncDto(userId) }
            )
            syncApi.upsert("critic_reviews", listOf("user_id", "content_id"), payload, session)
        }

        val faMovieData = faMovieDataDao.getAll()
        if (faMovieData.isNotEmpty()) {
            val payload = json.encodeToString(
                ListSerializer(FaMovieDataSyncDto.serializer()),
                faMovieData.map { it.toSyncDto(userId) }
            )
            syncApi.upsert("fa_movie_data", listOf("user_id", "content_id"), payload, session)
        }

        return SyncSummary(
            movies = movies.size,
            tvShows = tvShows.size,
            tvShowProgress = progress.size,
            searchHistory = history.size,
            userPlatforms = platforms.size,
            blacklist = blacklist.size,
            criticReviews = criticReviews.size,
            faMovieData = faMovieData.size
        )
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