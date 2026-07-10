package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeriesViewModel(
    private val tvShowDao: TvShowDao
) : ViewModel() {

    val series: StateFlow<List<TvShowEntity>> = tvShowDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleLike(show: TvShowEntity) {
        viewModelScope.launch {
            tvShowDao.update(show.copy(liked = !show.liked))
        }
    }

    fun toggleWatched(show: TvShowEntity) {
        viewModelScope.launch {
            val newStatus = if (show.status == WatchStatus.YA_VISTA) WatchStatus.POR_VER else WatchStatus.YA_VISTA
            tvShowDao.update(show.copy(status = newStatus))
        }
    }

    fun delete(show: TvShowEntity) {
        viewModelScope.launch {
            tvShowDao.delete(show)
        }
    }
}
