package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
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

    private fun deriveTotalEpisodes(tvShowId: Long, storedTotal: Int?, watchedCount: Int): Int? {
        if (storedTotal != null && storedTotal > 0) return storedTotal
        if (watchedCount > 0) return watchedCount
        return null
    }

    val seriesWithProgress: StateFlow<List<SeriesWithProgress>> = combine(
        tvShowDao.getAllFlow(),
        tvShowProgressDao.getWatchedCounts()
    ) { series, counts ->
        val countMap = counts.associate { it.tvShowId to it.count }
        series.map { show ->
            val watchedCount = countMap[show.id] ?: 0
            SeriesWithProgress(
                show = show,
                watchedCount = watchedCount,
                totalEpisodes = deriveTotalEpisodes(show.id, show.totalEpisodes, watchedCount)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inProgress: StateFlow<List<SeriesWithProgress>> = combine(
        tvShowDao.getInProgressFlow(),
        tvShowProgressDao.getWatchedCounts()
    ) { series, counts ->
        val countMap = counts.associate { it.tvShowId to it.count }
        series.map { show ->
            val watchedCount = countMap[show.id] ?: 0
            SeriesWithProgress(
                show = show,
                watchedCount = watchedCount,
                totalEpisodes = deriveTotalEpisodes(show.id, show.totalEpisodes, watchedCount)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finished: StateFlow<List<SeriesWithProgress>> = combine(
        tvShowDao.getFinishedFlow(),
        tvShowProgressDao.getWatchedCounts()
    ) { series, counts ->
        val countMap = counts.associate { it.tvShowId to it.count }
        series.map { show ->
            val watchedCount = countMap[show.id] ?: 0
            SeriesWithProgress(
                show = show,
                watchedCount = watchedCount,
                totalEpisodes = deriveTotalEpisodes(show.id, show.totalEpisodes, watchedCount)
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
            if (wasWatched) {
                tvShowProgressDao.deleteByTvShowId(show.id)
                tvShowDao.update(show.copy(status = WatchStatus.POR_VER, lastWatchedAt = null))
                feedbackManager.emit("Serie quitada de vistos")
            } else {
                val totalEp = show.totalEpisodes
                if (totalEp != null && totalEp > 0) {
                    val allEpisodes = (1..totalEp).map { epNum ->
                        TvShowProgressEntity(
                            tvShowId = show.id,
                            season = 1,
                            episode = epNum
                        )
                    }
                    tvShowProgressDao.insertAll(allEpisodes)
                    tvShowDao.update(show.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                } else {
                    tvShowDao.update(show.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                }
                feedbackManager.emit("Serie marcada como vista")
            }
        }
    }
}
