package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    private fun deriveTotalEpisodes(storedTotal: Int?, watchedCount: Int): Int? {
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
                totalEpisodes = deriveTotalEpisodes(show.totalEpisodes, watchedCount)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun SeriesWithProgress.hasFutureEpisodes(): Boolean {
        return show.nextEpisodeAirDate != null
                && show.seriesStatus !in listOf("Ended", "Canceled")
    }

    private fun SeriesWithProgress.isCaughtUp(): Boolean {
        val aired = show.releasedEpisodes ?: totalEpisodes ?: return false
        return aired > 0 && watchedCount >= aired && hasFutureEpisodes()
    }

    private fun SeriesWithProgress.isFinished(): Boolean {
        if (show.finishedAt != null && !hasFutureEpisodes()) return true
        if (show.status == com.dondeloexan.data.local.entity.WatchStatus.YA_VISTA && !hasFutureEpisodes()) return true
        val total = totalEpisodes ?: return false
        if (total <= 0) return false
        val target = show.releasedEpisodes ?: total
        if (watchedCount < target) return false
        if (show.inProduction == true) return false
        if (show.seriesStatus in listOf("Returning Series", "In Production")) return false
        return true
    }

    val pending: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s -> s.show.liked && s.watchedCount == 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inProgress: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s -> s.show.liked && s.watchedCount > 0 && !s.isCaughtUp() && !s.isFinished() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finished: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s -> s.show.liked && s.isFinished() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingAgenda: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        val today = LocalDate.now().toString()
        list.filter { s ->
            s.show.liked && s.isCaughtUp() && s.show.nextEpisodeAirDate != null && s.show.nextEpisodeAirDate >= today
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                            val tvDetail = tmdbApi.getTvDetailLight(tmdbId)
                            val existing = tvShowDao.getByContentId(show.contentId ?: continue) ?: continue

                            val releasedEpisodes = if (tvDetail.lastEpisodeToAir != null && tvDetail.seasons != null) {
                                val last = tvDetail.lastEpisodeToAir!!
                                tvDetail.seasons!!
                                    .filter { it.seasonNumber > 0 }
                                    .sumOf { season ->
                                        when {
                                            season.seasonNumber < last.seasonNumber -> season.episodeCount
                                            season.seasonNumber == last.seasonNumber -> last.episodeNumber
                                            else -> 0
                                        }
                                    }
                            } else tvDetail.numberOfEpisodes

                            val platformsStr = if (existing.streamingPlatforms.isNullOrEmpty()) {
                                try {
                                    val providers = tmdbApi.getTvWatchProviders(tmdbId)
                                    val platforms = providers.results.get("ES")?.toStreamingAvailability().orEmpty()
                                    platforms.toPlatformsString()
                                } catch (e: Exception) {
                                    AppLogger.e("SeriesVM", "platforms for show ${show.id}", e)
                                    null
                                }
                            } else existing.streamingPlatforms

                            tvShowDao.update(
                                existing.copy(
                                    totalEpisodes = tvDetail.numberOfEpisodes ?: existing.totalEpisodes,
                                    releasedEpisodes = releasedEpisodes,
                                    nextEpisodeAirDate = tvDetail.nextEpisodeToAir?.airDate,
                                    nextEpisodeNumber = tvDetail.nextEpisodeToAir?.episodeNumber,
                                    nextEpisodeSeasonNumber = tvDetail.nextEpisodeToAir?.seasonNumber,
                                    seriesStatus = tvDetail.status,
                                    inProduction = tvDetail.inProduction ?: existing.inProduction,
                                    numberOfSeasons = tvDetail.numberOfSeasons ?: existing.numberOfSeasons,
                                    streamingPlatforms = platformsStr
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
                var totalEp = show.totalEpisodes
                var seasons = emptyList<TmdbSeasonDto>()
                if (totalEp == null || totalEp <= 0) {
                    val tmdbId = show.tmdbId
                    if (tmdbId != null) {
                        try {
                            val detail = tmdbApi.getTvDetailLight(tmdbId)
                            totalEp = detail.numberOfEpisodes
                            seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                            if (totalEp != null && totalEp > 0) {
                                tvShowDao.update(show.copy(totalEpisodes = totalEp))
                            }
                        } catch (e: Exception) {
                            AppLogger.e("SeriesVM", "totalEpisodes for show ${show.id}", e)
                        }
                    }
                }
                if (totalEp != null && totalEp > 0) {
                    if (seasons.isEmpty() && show.tmdbId != null) {
                        try {
                            val detail = tmdbApi.getTvDetailLight(show.tmdbId)
                            seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                        } catch (e: Exception) {
                            AppLogger.e("SeriesVM", "seasons detail for show ${show.id}", e)
                        }
                    }
                    val allProgress = if (seasons.isNotEmpty()) {
                        seasons.flatMap { s ->
                            (1..s.episodeCount).map { ep ->
                                TvShowProgressEntity(
                                    tvShowId = show.id,
                                    season = s.seasonNumber,
                                    episode = ep
                                )
                            }
                        }
                    } else {
                        (1..totalEp).map { ep ->
                            TvShowProgressEntity(
                                tvShowId = show.id,
                                season = 1,
                                episode = ep
                            )
                        }
                    }
                    tvShowProgressDao.insertAll(allProgress)
                }
                tvShowDao.update(show.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                feedbackManager.emit("Serie marcada como vista")
            }
        }
    }
}
