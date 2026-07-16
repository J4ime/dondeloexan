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

    private fun deriveTotalEpisodes(storedTotal: Int?): Int? {
        if (storedTotal != null && storedTotal > 0) return storedTotal
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
                totalEpisodes = deriveTotalEpisodes(show.totalEpisodes)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun SeriesWithProgress.hasFutureSeasons(): Boolean {
        return when (show.seriesStatus) {
            "Ended", "Canceled" -> false
            null -> show.inProduction != false
            else -> true
        }
    }

    private fun SeriesWithProgress.isCaughtUp(): Boolean {
        val aired = show.releasedEpisodes ?: show.totalEpisodes ?: return false
        return aired > 0 && watchedCount >= aired
    }

    private fun SeriesWithProgress.isFinished(): Boolean {
        if (show.finishedAt != null) return true
        if (show.status == WatchStatus.YA_VISTA && !hasFutureSeasons()) return true
        if (!isCaughtUp()) return false
        return !hasFutureSeasons()
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
        list.filter { s ->
            s.show.liked && s.isCaughtUp() && s.hasFutureSeasons() && !s.isFinished()
        }.sortedWith(compareBy(nullsLast<String>()) { it.show.nextEpisodeAirDate })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSeriesData()
    }

    private fun refreshSeriesData() {
        viewModelScope.launch {
            try {
                val liked = tvShowDao.getAll().filter { it.liked }
                for (show in liked) {
                    val tmdbId = show.tmdbId ?: continue
                    try {
                        val tvDetail = tmdbApi.getTvDetailLight(tmdbId)
                        val existing = tvShowDao.getByContentId(show.contentId ?: continue) ?: continue

                        val lastEp = tvDetail.lastEpisodeToAir
                        val seasons = tvDetail.seasons
                        val releasedEpisodes = if (lastEp != null && seasons != null) {
                            seasons.filter { it.seasonNumber > 0 }
                                .sumOf { season ->
                                    when {
                                        season.seasonNumber < lastEp.seasonNumber -> season.episodeCount
                                        season.seasonNumber == lastEp.seasonNumber -> lastEp.episodeNumber
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
                val tmdbId = show.tmdbId
                if (tmdbId == null) return@launch
                try {
                    val detail = tmdbApi.getTvDetailLight(tmdbId)
                    val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                    if (detail.numberOfEpisodes != null && detail.numberOfEpisodes > 0) {
                        tvShowDao.update(show.copy(totalEpisodes = detail.numberOfEpisodes))
                    }
                    val progressToInsert = mutableListOf<TvShowProgressEntity>()
                    val today = LocalDate.now()
                    for (season in seasons) {
                        try {
                            val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                            for (ep in seasonDetail.episodes) {
                                val isAired = ep.airDate == null ||
                                        try { !LocalDate.parse(ep.airDate).isAfter(today) } catch (_: Exception) { true }
                                if (isAired) {
                                    progressToInsert.add(
                                        TvShowProgressEntity(
                                            tvShowId = show.id,
                                            season = season.seasonNumber,
                                            episode = ep.episodeNumber
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("SeriesVM", "season ${season.seasonNumber} for show ${show.id}", e)
                            for (epNum in 1..season.episodeCount) {
                                progressToInsert.add(
                                    TvShowProgressEntity(
                                        tvShowId = show.id,
                                        season = season.seasonNumber,
                                        episode = epNum
                                    )
                                )
                            }
                        }
                    }
                    tvShowProgressDao.insertAll(progressToInsert)
                    tvShowDao.update(show.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                    feedbackManager.emit("Serie marcada como vista")
                } catch (e: Exception) {
                    AppLogger.e("SeriesVM", "mark watched error for show ${show.id}", e)
                }
            }
        }
    }
}
