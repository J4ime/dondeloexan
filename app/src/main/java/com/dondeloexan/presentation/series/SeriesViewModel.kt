package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SeriesWithProgress(
    val show: TvShowEntity,
    val watchedCount: Int,
    val totalEpisodes: Int?
)

class SeriesViewModel(
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao,
    private val tmdbApi: TmdbApi,
    private val feedbackManager: FeedbackManager
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

    val inProgress: StateFlow<List<SeriesWithProgress>> = combine(
        tvShowDao.getInProgressFlow(),
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

    val upcomingAgenda: StateFlow<List<TvShowEntity>> = tvShowDao.getUpcomingFlow(
        today = LocalDate.now().toString()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSeriesData()
    }

    private fun refreshSeriesData() {
        viewModelScope.launch {
            while (true) {
                try {
                    val liked = tvShowDao.getAll().filter { it.liked }
                    for (show in liked) {
                        val tmdbId = show.tmdbId ?: continue
                        try {
                            val tvDetail = tmdbApi.getTvDetail(tmdbId)
                            val existing = tvShowDao.getByContentId(show.contentId ?: continue) ?: continue
                            tvShowDao.update(
                                existing.copy(
                                    totalEpisodes = tvDetail.numberOfEpisodes ?: existing.totalEpisodes,
                                    nextEpisodeAirDate = tvDetail.nextEpisodeToAir?.airDate,
                                    nextEpisodeNumber = tvDetail.nextEpisodeToAir?.episodeNumber,
                                    nextEpisodeSeasonNumber = tvDetail.nextEpisodeToAir?.seasonNumber,
                                    seriesStatus = tvDetail.status
                                )
                            )
                        } catch (e: Exception) {
                            AppLogger.e("SeriesVM", "Refresh series detail error", e)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("SeriesVM", "Refresh series data error", e)
                }
                delay(3_600_000)
            }
        }
    }

    fun toggleLike(show: TvShowEntity) {
        viewModelScope.launch {
            val newLiked = !show.liked
            tvShowDao.update(show.copy(liked = newLiked))
            feedbackManager.emit(
                if (newLiked) "Serie añadida"
                else "Serie quitada"
            )
        }
    }

    fun toggleWatched(show: TvShowEntity) {
        viewModelScope.launch {
            val wasWatched = show.status == WatchStatus.YA_VISTA
            val newStatus = if (wasWatched) WatchStatus.POR_VER else WatchStatus.YA_VISTA
            tvShowDao.update(show.copy(status = newStatus))
            feedbackManager.emit(
                if (!wasWatched) "Serie marcada como vista"
                else "Serie quitada de vistos"
            )
        }
    }
}
