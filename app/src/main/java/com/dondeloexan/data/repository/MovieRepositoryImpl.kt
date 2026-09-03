package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.MovieItem
import com.dondeloexan.domain.repository.MovieRepository
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.BatchCancelledException
import com.dondeloexan.util.RefreshCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MovieRepositoryImpl(
    private val movieDao: MovieDao,
    private val tmdbApi: TmdbApi,
    private val refreshCoordinator: RefreshCoordinator
) : MovieRepository {

    override val pending: Flow<List<MovieItem>> =
        movieDao.getPendingFlow().map { list -> list.map { it.toItem() } }

    override val watched: Flow<List<MovieItem>> =
        movieDao.getWatchedMoviesFlow().map { list -> list.map { it.toItem() } }

    override val favorites: Flow<List<MovieItem>> =
        movieDao.getFavoritesFlow().map { list -> list.map { it.toItem() } }

    override suspend fun delete(id: Long) {
        movieDao.getById(id)?.let { movieDao.delete(it) }
    }

    override suspend fun toggleFavorite(id: Long): Boolean {
        val movie = movieDao.getById(id) ?: return false
        val newLiked = !movie.liked
        movieDao.update(movie.copy(liked = newLiked))
        return newLiked
    }

    override suspend fun toggleWatched(id: Long): Boolean {
        val movie = movieDao.getById(id) ?: return false
        val nowWatched = movie.status != WatchStatus.YA_VISTA
        movieDao.update(
            movie.copy(
                status = if (nowWatched) WatchStatus.YA_VISTA else WatchStatus.POR_VER,
                watchedAt = if (nowWatched) System.currentTimeMillis() else null
            )
        )
        return nowWatched
    }

    override suspend fun refreshPlatforms() {
        withContext(Dispatchers.IO) {
            refreshCoordinator.resetBatch()
            val liked = movieDao.getAll().filter { it.liked }
            val now = System.currentTimeMillis()
            val cutoff = now - 86_400_000L
            val stale = liked.filter { it.lastRefreshedAt == null || it.lastRefreshedAt < cutoff }

            coroutineScope {
                stale.map { movie ->
                    async {
                        val tmdbId = movie.tmdbId ?: return@async
                        try {
                            val providers = refreshCoordinator.execute(coroutineContext, tmdbId) {
                                tmdbApi.getMovieWatchProviders(tmdbId)
                            }
                            val platforms = providers.results?.get("ES")?.toStreamingAvailability().orEmpty()
                            val platformsStr = platforms.toPlatformsString()
                            val existing = movieDao.getByTmdbId(tmdbId) ?: return@async
                            if (platformsStr != null) {
                                movieDao.update(existing.copy(
                                    streamingPlatforms = platformsStr,
                                    lastRefreshedAt = System.currentTimeMillis()
                                ))
                            }
                        } catch (e: BatchCancelledException) {
                            AppLogger.w("MovieRepo", "Batch cancelled after 3 timeouts")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.e("MovieRepo", "Refresh error -> ${movie.title}", e)
                        }
                    }
                }.forEach { it.await() }
            }
        }
    }

    private fun MovieEntity.toItem(): MovieItem = MovieItem(
        id = id,
        contentId = contentId,
        tmdbId = tmdbId,
        title = title,
        year = year,
        releaseDate = releaseDate,
        posterUrl = posterUrl,
        ratingImdb = ratingImdb,
        isLiked = liked,
        isWatched = status == WatchStatus.YA_VISTA,
        watchedAt = watchedAt,
        streamingPlatforms = streamingPlatforms.toStreamingPlatforms()
    )
}
