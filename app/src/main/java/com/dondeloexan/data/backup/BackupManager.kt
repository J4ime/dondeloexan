package com.dondeloexan.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.dondeloexan.data.local.AppDatabase
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.SearchHistoryEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.data.local.entity.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

class BackupManager(
    private val appDatabase: AppDatabase,
    private val contentResolver: ContentResolver
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val movies = appDatabase.movieDao().getAll()
        val tvShows = appDatabase.tvShowDao().getAll()
        val progress = appDatabase.tvShowProgressDao().getAll()
        val history = appDatabase.searchHistoryDao().getRecent(Int.MAX_VALUE)
        val platforms = appDatabase.userPlatformDao().getAll()

        val data = BackupData(
            movies = movies.map { it.toBackup() },
            tvShows = tvShows.map { it.toBackup() },
            tvShowProgress = progress.map { BackupProgress(it.tvShowId, it.season, it.episode, it.watchedAt) },
            searchHistory = history.map { BackupSearchEntry(it.query, it.searchedAt) },
            userPlatforms = platforms.map { BackupPlatform(it.platformName, it.isActive) }
        )

        contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
        } ?: throw IOException("No se pudo abrir el archivo para escritura")
    }

    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val jsonString = contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: throw IOException("No se pudo abrir el archivo para lectura")

        val data = json.decodeFromString(BackupData.serializer(), jsonString)

        if (data.version !in 1..1) {
            throw IllegalArgumentException("Version de backup no compatible: ${data.version}")
        }

        var count = 0

        appDatabase.movieDao().insertAll(data.movies.map { it.toEntity() })
        count += data.movies.size

        appDatabase.tvShowDao().insertAll(data.tvShows.map { it.toEntity() })
        count += data.tvShows.size

        appDatabase.tvShowProgressDao().insertAll(
            data.tvShowProgress.map { TvShowProgressEntity(tvShowId = it.tvShowId, season = it.season, episode = it.episode, watchedAt = it.watchedAt) }
        )
        count += data.tvShowProgress.size

        appDatabase.searchHistoryDao().insertAll(
            data.searchHistory.map { SearchHistoryEntity(query = it.query, searchedAt = it.searchedAt) }
        )
        count += data.searchHistory.size

        appDatabase.userPlatformDao().upsertAll(
            data.userPlatforms.map { UserPlatformEntity(platformName = it.platformName, isActive = it.isActive) }
        )
        count += data.userPlatforms.size

        count
    }

    private     fun MovieEntity.toBackup() = BackupMovie(
        tmdbId = tmdbId, title = title,
        posterUrl = posterUrl, ratingTmdb = ratingTmdb,
        ratingImdb = ratingImdb, status = status.name, liked = liked, addedAt = addedAt
    )

    private     fun TvShowEntity.toBackup() = BackupTvShow(
        tmdbId = tmdbId, title = title,
        posterUrl = posterUrl, ratingTmdb = ratingTmdb,
        ratingImdb = ratingImdb, status = status.name,
        totalEpisodes = totalEpisodes, addedAt = addedAt
    )

    private     fun BackupMovie.toEntity() = MovieEntity(
        tmdbId = tmdbId, title = title,
        posterUrl = posterUrl, ratingTmdb = ratingTmdb,
        ratingImdb = ratingImdb,
        status = try { WatchStatus.valueOf(status) } catch (_: Exception) { WatchStatus.POR_VER },
        liked = liked, addedAt = addedAt
    )

    private     fun BackupTvShow.toEntity() = TvShowEntity(
        tmdbId = tmdbId, title = title,
        posterUrl = posterUrl, ratingTmdb = ratingTmdb,
        ratingImdb = ratingImdb,
        status = try { WatchStatus.valueOf(status) } catch (_: Exception) { WatchStatus.POR_VER },
        totalEpisodes = totalEpisodes, addedAt = addedAt
    )
}
