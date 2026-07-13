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
import kotlinx.coroutines.delay
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
        refreshMoviePlatforms()
    }

    private fun refreshMoviePlatforms() {
        viewModelScope.launch {
            while (true) {
                try {
                    val liked = movieDao.getAll().filter { it.liked }
                    for (movie in liked) {
                        val tmdbId = movie.tmdbId ?: continue
                        if (!movie.streamingPlatforms.isNullOrEmpty()) continue
                        try {
                            val providers = tmdbApi.getMovieWatchProviders(tmdbId)
                            val platforms = providers.results.get("ES")?.toStreamingAvailability().orEmpty()
                            val platformsStr = platforms.toPlatformsString()
                            val existing = if (!movie.contentId.isNullOrBlank()) {
                                movieDao.getByContentId(movie.contentId) ?: movieDao.getByTmdbId(tmdbId)
                            } else {
                                movieDao.getByTmdbId(tmdbId)
                            } ?: continue
                            movieDao.update(existing.copy(streamingPlatforms = platformsStr))
                        } catch (e: Exception) {
                            AppLogger.e("MoviesVM", "Refresh movie platforms error", e)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("MoviesVM", "Refresh movie platforms error", e)
                }
                delay(300_000)
            }
        }
    }

    fun toggleLike(movie: MovieEntity) {
        viewModelScope.launch {
            val newLiked = !movie.liked
            movieDao.update(movie.copy(liked = newLiked))
            feedbackManager.emit(
                if (newLiked) "Película añadida"
                else "Película quitada"
            )
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
