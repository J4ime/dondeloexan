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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val movieDao: MovieDao,
    private val tmdbApi: TmdbApi,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    val pendingMovies: StateFlow<List<MovieEntity>> = movieDao.getPendingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedMovies: StateFlow<List<MovieEntity>> = movieDao.getWatchedMoviesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            refreshMoviePlatforms()
        }
    }

    private suspend fun refreshMoviePlatforms() {
        try {
            val liked = movieDao.getAll().filter { it.liked }
            coroutineScope {
                liked.map { movie ->
                    async {
                        val tmdbId = movie.tmdbId ?: return@async
                        try {
                            val providers = tmdbApi.getMovieWatchProviders(tmdbId)
                            val platforms = providers.results.get("ES")?.toStreamingAvailability().orEmpty()
                            val platformsStr = platforms.toPlatformsString()
                            val existing = if (!movie.contentId.isNullOrBlank()) {
                                movieDao.getByContentId(movie.contentId) ?: movieDao.getByTmdbId(tmdbId)
                            } else {
                                movieDao.getByTmdbId(tmdbId)
                            } ?: return@async
                            if (platformsStr != null) {
                                movieDao.update(existing.copy(streamingPlatforms = platformsStr))
                            }
                            AppLogger.d("MoviesVM", "Refreshed platforms for ${movie.title}: ${platforms.size} platforms, saved=${platformsStr != null}, preview=${platformsStr?.take(120)}")
                        } catch (e: Exception) {
                            AppLogger.e("MoviesVM", "Refresh movie platforms error for ${movie.title}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MoviesVM", "Refresh movie platforms error", e)
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
