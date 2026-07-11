package com.dondeloexan.presentation.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.presentation.feedback.FeedbackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val movieDao: MovieDao,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    val pendingMovies: StateFlow<List<MovieEntity>> = movieDao.getPendingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedMovies: StateFlow<List<MovieEntity>> = movieDao.getWatchedMoviesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleLike(movie: MovieEntity) {
        viewModelScope.launch {
            val newLiked = !movie.liked
            movieDao.update(movie.copy(liked = newLiked))
            feedbackManager.emit(
                if (newLiked) "Película añadida a favoritos"
                else "Película quitada de favoritos"
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
