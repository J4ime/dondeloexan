package com.dondeloexan.presentation.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.BatchCancelledException
import com.dondeloexan.util.RefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val movieDao: MovieDao,
    private val tmdbApi: TmdbApi,
    private val refreshCoordinator: RefreshCoordinator,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    val pendingMovies: StateFlow<List<MovieEntity>> = movieDao.getPendingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedMovies: StateFlow<List<MovieEntity>> = movieDao.getWatchedMoviesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val refreshJob = SupervisorJob()
    private val refreshScope = CoroutineScope(Dispatchers.IO + refreshJob)

    fun refreshMoviePlatforms() {
        refreshScope.launch {
            refreshCoordinator.resetBatch()
            val liked = movieDao.getAll().filter { it.liked }
            val now = System.currentTimeMillis()
            val cutoff = now - 86_400_000L
            val stale = liked.filter { it.lastRefreshedAt == null || it.lastRefreshedAt < cutoff }

            stale.map { movie ->
                async {
                    val tmdbId = movie.tmdbId ?: return@async
                    try {
                        val providers = refreshCoordinator.execute(coroutineContext, tmdbId) {
                            tmdbApi.getMovieWatchProviders(tmdbId)
                        }
                        val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()
                        val platformsStr = platforms.toPlatformsString()
                        val existing = movieDao.getByTmdbId(tmdbId) ?: return@async
                        if (platformsStr != null) {
                            movieDao.update(existing.copy(
                                streamingPlatforms = platformsStr,
                                lastRefreshedAt = System.currentTimeMillis()
                            ))
                        }
                    } catch (e: BatchCancelledException) {
                        AppLogger.w("MoviesVM", "Batch cancelled after 3 timeouts")
                    } catch (e: Exception) {
                        AppLogger.e("MoviesVM", "Refresh error -> ${movie.title}", e)
                    }
                }
            }.forEach { it.await() }
        }
    }

    fun deleteMovie(movie: MovieEntity) {
        viewModelScope.launch {
            movieDao.delete(movie)
            feedbackManager.emit("Película eliminada")
        }
    }

    fun toggleWatched(movie: MovieEntity) {
        viewModelScope.launch {
            val wasWatched = movie.status == WatchStatus.YA_VISTA
            val newStatus = if (wasWatched) WatchStatus.POR_VER else WatchStatus.YA_VISTA
            movieDao.update(
                movie.copy(
                    status = newStatus,
                    watchedAt = if (wasWatched) null else System.currentTimeMillis()
                )
            )
            feedbackManager.emit(
                if (!wasWatched) "Película marcada como vista"
                else "Película quitada de vistos"
            )
        }
    }
}
