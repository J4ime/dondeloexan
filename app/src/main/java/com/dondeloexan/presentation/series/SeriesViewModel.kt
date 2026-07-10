package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeriesWithProgress(
    val show: TvShowEntity,
    val watchedCount: Int,
    val totalEpisodes: Int?
)

class SeriesViewModel(
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao
) : ViewModel() {

    val seriesWithProgress: StateFlow<List<SeriesWithProgress>> = combine(
        tvShowDao.getAllFlow(),
        tvShowProgressDao.getWatchedCounts()
    ) { series, counts ->
        val countMap = counts.associate { it.tvShowId to it.count }
        series.map { show ->
            SeriesWithProgress(
                show = show,
                watchedCount = countMap[show.id] ?: 0,
                totalEpisodes = show.totalEpisodes
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
