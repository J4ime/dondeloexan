package com.dondeloexan.presentation.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.WatchStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val movieDao: MovieDao
) : ViewModel() {

    val movies: StateFlow<List<MovieEntity>> = movieDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleLike(movie: MovieEntity) {
        viewModelScope.launch {
            movieDao.update(movie.copy(liked = !movie.liked))
        }
    }

    fun toggleWatched(movie: MovieEntity) {
        viewModelScope.launch {
            val newStatus = if (movie.status == WatchStatus.YA_VISTA) WatchStatus.POR_VER else WatchStatus.YA_VISTA
            movieDao.update(movie.copy(status = newStatus))
        }
    }

    fun delete(movie: MovieEntity) {
        viewModelScope.launch {
            movieDao.delete(movie)
        }
    }
}
