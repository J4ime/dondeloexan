package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.hasFutureSeasons
import com.dondeloexan.data.local.entity.isCaughtUpBy
import com.dondeloexan.data.local.entity.isFinishedBy
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.BatchCancelledException
import com.dondeloexan.util.RefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SeriesWithProgress(
    val show: TvShowEntity,
    val watchedCount: Int,
    val totalEpisodes: Int?,
    val lastWatchedAt: Long? = null
)

class SeriesViewModel(
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao,
    private val tmdbApi: TmdbApi,
    private val refreshCoordinator: RefreshCoordinator,
    private val discoverRepository: DiscoverRepository,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    private fun deriveTotalEpisodes(storedTotal: Int?): Int? {
        if (storedTotal != null && storedTotal > 0) return storedTotal
        return null
    }

    val seriesWithProgress: StateFlow<List<SeriesWithProgress>> = combine(
        tvShowDao.getAllFlow(),
        tvShowProgressDao.getWatchedCounts(),
        tvShowProgressDao.getLastWatchedAtByShow()
    ) { series, counts, lastWatched ->
        val countMap = counts.associate { it.tvShowId to it.count }
        val lastWatchedMap = lastWatched.associate { it.tvShowId to it.lastWatchedAt }
        series.map { show ->
            val watchedCount = countMap[show.id] ?: 0
            SeriesWithProgress(
                show = show,
                watchedCount = watchedCount,
                totalEpisodes = deriveTotalEpisodes(show.totalEpisodes),
                lastWatchedAt = lastWatchedMap[show.id]
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun SeriesWithProgress.hasFutureSeasons(): Boolean = show.hasFutureSeasons()

    private fun SeriesWithProgress.isCaughtUp(): Boolean = show.isCaughtUpBy(watchedCount)

    private fun SeriesWithProgress.isFinished(): Boolean = show.isFinishedBy(watchedCount)

    private fun List<SeriesWithProgress>.sortedByLastWatched(): List<SeriesWithProgress> =
        sortedWith(compareByDescending<SeriesWithProgress> { it.lastWatchedAt ?: Long.MIN_VALUE }.thenBy { it.show.addedAt })

    val pending: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s -> s.watchedCount == 0 }.sortedByLastWatched()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inProgress: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s -> s.watchedCount > 0 && !s.isCaughtUp() && !s.isFinished() }.sortedByLastWatched()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finished: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s -> s.isFinished() }.sortedByLastWatched()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingAgenda: StateFlow<List<SeriesWithProgress>> = seriesWithProgress.map { list ->
        list.filter { s ->
            s.isCaughtUp() && s.hasFutureSeasons() && !s.isFinished()
        }.sortedWith(compareBy(nullsLast<String>()) { it.show.nextEpisodeAirDate })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val refreshJob = SupervisorJob()
    private val refreshScope = CoroutineScope(Dispatchers.IO + refreshJob)

    fun refreshSeriesData() {
        refreshScope.launch {
            refreshCoordinator.resetBatch()
            val now = System.currentTimeMillis()
            val cutoff = now - 86_400_000L
            // Refrescar todas las series (no solo "liked") para poblar los datos de
            // episodios. Prioridad a las que carecen de releasedEpisodes (el bug que
            // dejaba series al día/terminadas colgadas en "En curso").
            val missingData = tvShowDao.getAll().filter {
                it.finishedAt == null && it.releasedEpisodes == null
            }
            val stale = tvShowDao.getAll().filter {
                it.finishedAt == null &&
                    it.lastRefreshedAt != null &&
                    it.lastRefreshedAt < cutoff
            }
            val toRefresh = (missingData + stale)
                .distinctBy { it.id }

            toRefresh.map { show ->
                async {
                    val tmdbId = show.tmdbId ?: return@async
                    try {
                        val tvDetail = refreshCoordinator.execute(coroutineContext, tmdbId) {
                            tmdbApi.getTvDetailLight(tmdbId)
                        }
                        val existing = tvShowDao.getById(show.id) ?: return@async

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
                                val providers = refreshCoordinator.execute(coroutineContext, tmdbId) {
                                    tmdbApi.getTvWatchProviders(tmdbId)
                                }
                                providers.results?.get("ES")?.toStreamingAvailability().orEmpty().toPlatformsString()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.e("SeriesVM", "getTvWatchProviders falló", e)
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
                                streamingPlatforms = platformsStr ?: existing.streamingPlatforms,
                                lastRefreshedAt = System.currentTimeMillis()
                            )
                        )
                    } catch (e: BatchCancelledException) {
                        AppLogger.w("SeriesVM", "Batch cancelled after 3 timeouts")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("SeriesVM", "Refresh error -> tv/$tmdbId", e)
                    }
                }
            }.forEach { it.await() }

            // Reconciliar la clasificación de TODAS las series (corregir status/finishedAt
            // para recolocar las que estaban mal preexistentes y que no se tocan capítulo a capítulo).
            try {
                discoverRepository.reconcileAllLibrarySeries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SeriesVM", "reconcileAllLibrarySeries falló", e)
            }
        }
    }

    fun deleteSeries(tvShow: TvShowEntity) {
        viewModelScope.launch {
            tvShowDao.delete(tvShow)
            feedbackManager.emit("Serie eliminada")
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
                val progressToInsert = mutableListOf<TvShowProgressEntity>()
                if (tmdbId != null) {
                    try {
                        val detail = tmdbApi.getTvDetailLight(tmdbId)
                        val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                        if (detail.numberOfEpisodes != null && detail.numberOfEpisodes > 0) {
                            tvShowDao.update(show.copy(totalEpisodes = detail.numberOfEpisodes))
                        }
                        val today = LocalDate.now()
                        for (season in seasons) {
                            try {
                                val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                                for (ep in seasonDetail.episodes) {
                                    val isAired = ep.airDate == null ||
                                            try { !LocalDate.parse(ep.airDate).isAfter(today) }
                                            catch (e: Exception) {
                                                AppLogger.w("SeriesVM", "parse airDate falló: ${ep.airDate} (${e.message})")
                                                true
                                            }
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
                    } catch (e: Exception) {
                        AppLogger.e("SeriesVM", "mark watched detail error for show ${show.id}", e)
                    }
                }
                if (progressToInsert.isNotEmpty()) {
                    tvShowProgressDao.insertAll(progressToInsert)
                }
                tvShowDao.update(show.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                feedbackManager.emit("Serie marcada como vista")
            }
        }
    }
}
