package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.SeriesItem
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.domain.repository.SeriesRepository
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.BatchCancelledException
import com.dondeloexan.util.RefreshCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SeriesRepositoryImpl(
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao,
    private val tmdbApi: TmdbApi,
    private val refreshCoordinator: RefreshCoordinator,
    private val discoverRepository: DiscoverRepository
) : SeriesRepository {

    override val all: Flow<List<SeriesItem>> = combine(
        tvShowDao.getAllFlow(),
        tvShowProgressDao.getWatchedCounts(),
        tvShowProgressDao.getLastWatchedAtByShow()
    ) { series, counts, lastWatched ->
        val countMap = counts.associate { it.tvShowId to it.count }
        val lastWatchedMap = lastWatched.associate { it.tvShowId to it.lastWatchedAt }
        series.map { show ->
            val watchedCount = countMap[show.id] ?: 0
            show.toItem(
                watchedCount = watchedCount,
                lastWatchedAt = lastWatchedMap[show.id]
            )
        }
    }

    override suspend fun delete(id: Long) {
        tvShowDao.getById(id)?.let { tvShowDao.delete(it) }
    }

    override suspend fun toggleWatched(id: Long): Boolean {
        val show = tvShowDao.getById(id) ?: return false
        val wasWatched = show.status == WatchStatus.YA_VISTA
        return if (wasWatched) {
            tvShowProgressDao.deleteByTvShowId(show.id)
            tvShowDao.update(show.copy(status = WatchStatus.POR_VER, lastWatchedAt = null))
            false
        } else {
            val progressToInsert = collectAiredEpisodes(show)
            if (progressToInsert.isNotEmpty()) {
                tvShowProgressDao.insertAll(progressToInsert)
            }
            tvShowDao.update(show.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
            true
        }
    }

    override suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            refreshCoordinator.resetBatch()
            val now = System.currentTimeMillis()
            val cutoff = now - 86_400_000L
            val missingData = tvShowDao.getAll().filter {
                it.finishedAt == null && it.releasedEpisodes == null
            }
            val stale = tvShowDao.getAll().filter {
                it.finishedAt == null &&
                    it.lastRefreshedAt != null &&
                    it.lastRefreshedAt < cutoff
            }
            val toRefresh = (missingData + stale).distinctBy { it.id }

            coroutineScope {
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
                                    AppLogger.e("SeriesRepo", "getTvWatchProviders falló", e)
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
                            AppLogger.w("SeriesRepo", "Batch cancelled after 3 timeouts")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.e("SeriesRepo", "Refresh error -> tv/$tmdbId", e)
                        }
                    }
                }.forEach { it.await() }
            }

            try {
                discoverRepository.reconcileAllLibrarySeries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SeriesRepo", "reconcileAllLibrarySeries falló", e)
            }
        }
    }

    private suspend fun collectAiredEpisodes(show: TvShowEntity): List<TvShowProgressEntity> {
        val progressToInsert = mutableListOf<TvShowProgressEntity>()
        val tmdbId = show.tmdbId ?: return progressToInsert
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
                                AppLogger.w("SeriesRepo", "parse airDate falló: ${ep.airDate} (${e.message})")
                                true
                            }
                        if (isAired) {
                            progressToInsert.add(
                                TvShowProgressEntity(tvShowId = show.id, season = season.seasonNumber, episode = ep.episodeNumber)
                            )
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("SeriesRepo", "season ${season.seasonNumber} for show ${show.id}", e)
                    for (epNum in 1..season.episodeCount) {
                        progressToInsert.add(
                            TvShowProgressEntity(tvShowId = show.id, season = season.seasonNumber, episode = epNum)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("SeriesRepo", "mark watched detail error for show ${show.id}", e)
        }
        return progressToInsert
    }

    private fun TvShowEntity.toItem(watchedCount: Int, lastWatchedAt: Long?): SeriesItem = SeriesItem(
        id = id,
        contentId = contentId,
        tmdbId = tmdbId,
        title = title,
        year = year,
        posterUrl = posterUrl,
        addedAt = addedAt,
        isLiked = liked,
        isWatched = status == WatchStatus.YA_VISTA,
        finishedAt = finishedAt,
        watchedCount = watchedCount,
        totalEpisodes = if (totalEpisodes != null && totalEpisodes > 0) totalEpisodes else null,
        releasedEpisodes = releasedEpisodes,
        nextEpisodeAirDate = nextEpisodeAirDate,
        nextEpisodeNumber = nextEpisodeNumber,
        nextEpisodeSeasonNumber = nextEpisodeSeasonNumber,
        seriesStatus = seriesStatus,
        inProduction = inProduction,
        numberOfSeasons = numberOfSeasons,
        streamingPlatforms = streamingPlatforms.toStreamingPlatforms(),
        lastWatchedAt = lastWatchedAt
    )
}
