package com.dondeloexan.presentation.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.domain.model.MovieItem
import com.dondeloexan.domain.repository.MovieRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val repository: MovieRepository,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    val pendingMovies: StateFlow<List<MovieItem>> = repository.pending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedMovies: StateFlow<List<MovieItem>> = repository.watched
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteMovies: StateFlow<List<MovieItem>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshMoviePlatforms() {
        viewModelScope.launch { repository.refreshPlatforms() }
    }

    fun deleteMovie(movie: MovieItem) {
        viewModelScope.launch {
            repository.delete(movie.id)
            feedbackManager.emit("Película eliminada")
        }
    }

    fun toggleLike(movie: MovieItem) {
        viewModelScope.launch {
            val newLiked = repository.toggleFavorite(movie.id)
            feedbackManager.emit(
                if (newLiked) "Película marcada como favorita"
                else "Película quitada de favoritas"
            )
        }
    }

    fun toggleWatched(movie: MovieItem) {
        viewModelScope.launch {
            val nowWatched = repository.toggleWatched(movie.id)
            feedbackManager.emit(
                if (nowWatched) "Película marcada como vista"
                else "Película quitada de vistos"
            )
        }
    }
}
