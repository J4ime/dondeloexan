package com.dondeloexan.presentation.settings

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.local.entity.toStreamingPlatforms
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.util.AppLogger
import com.dondeloexan.util.BatchCancelledException
import com.dondeloexan.util.RefreshCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class LibraryRefresher(
    private val tvShowDao: TvShowDao,
    private val movieDao: MovieDao,
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val refreshCoordinator: RefreshCoordinator,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val notificationManager: LibraryNotificationManager
) {
    data class RefreshResult(
        val seriesUpdated: Int,
        val moviesUpdated: Int
    )

    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        refreshCoordinator.resetBatch()

        val newEpisodeDates = mutableListOf<NewEpisodeDateInfo>()
        val newPlatforms = mutableListOf<NewPlatformInfo>()

        var seriesCount = 0
        var moviesCount = 0

        coroutineScope {
            val seriesJob = async { refreshSeries(newEpisodeDates) }
            val moviesJob = async { refreshMovies(newPlatforms) }
            seriesCount = seriesJob.await()
            moviesCount = moviesJob.await()
        }

        userPreferencesDataStore.setLastLibraryUpdateTimestamp(System.currentTimeMillis())

        if (newEpisodeDates.isNotEmpty() || newPlatforms.isNotEmpty()) {
            notificationManager.notifyChanges(newEpisodeDates, newPlatforms)
        }

        RefreshResult(
            seriesUpdated = seriesCount,
            moviesUpdated = moviesCount
        )
    }

    private suspend fun refreshSeries(newEpisodeDates: MutableList<NewEpisodeDateInfo>): Int {
        val liked = tvShowDao.getAllLiked().filter { it.liked && it.finishedAt == null }
        var count = 0

        coroutineScope {
            liked.map { show ->
                async {
                    val tmdbId = show.tmdbId ?: return@async
                    try {
                        val hadNoNextDate = show.nextEpisodeAirDate == null

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
                                providers.results["ES"]?.toStreamingAvailability().orEmpty().toPlatformsString()
                            } catch (e: Exception) {
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

                        if (hadNoNextDate && tvDetail.nextEpisodeToAir?.airDate != null) {
                            synchronized(newEpisodeDates) {
                                newEpisodeDates.add(
                                    NewEpisodeDateInfo(
                                        seriesTitle = show.title,
                                        season = tvDetail.nextEpisodeToAir.seasonNumber,
                                        episode = tvDetail.nextEpisodeToAir.episodeNumber,
                                        airDate = tvDetail.nextEpisodeToAir.airDate
                                    )
                                )
                            }
                        }

                        synchronized(this@LibraryRefresher) { count++ }
                    } catch (e: BatchCancelledException) {
                        AppLogger.w("LibraryRefresher", "Series batch cancelled after 3 timeouts")
                    } catch (e: Exception) {
                        AppLogger.e("LibraryRefresher", "Refresh series error -> tv/$tmdbId", e)
                    }
                }
            }.forEach { it.await() }
        }

        return count
    }

    private suspend fun refreshMovies(newPlatforms: MutableList<NewPlatformInfo>): Int {
        val pending = movieDao.getAll().filter { it.status != WatchStatus.YA_VISTA }
        var count = 0

        coroutineScope {
            pending.map { movie ->
                async {
                    val tmdbId = movie.tmdbId ?: return@async
                    try {
                        val oldPlatforms = movie.streamingPlatforms
                        val hadNoRealPlatform = oldPlatforms.isNullOrBlank() ||
                                parsePlatformNames(oldPlatforms).all { it == "Cine" }

                        val detail = refreshCoordinator.execute(coroutineContext, tmdbId) {
                            tmdbApi.getMovieDetail(tmdbId)
                        }

                        val providers = refreshCoordinator.execute(coroutineContext, tmdbId) {
                            tmdbApi.getMovieWatchProviders(tmdbId)
                        }
                        val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()
                        val platformsStr = platforms.toPlatformsString()

                        val newRatingImdb = if (movie.imdbId != null) {
                            try {
                                val omdb = omdbApi.getByImdbId(movie.imdbId)
                                omdb.imdbRating?.toFloatOrNull()
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        val existing = movieDao.getByTmdbId(tmdbId) ?: return@async
                        movieDao.update(
                            existing.copy(
                                ratingTmdb = detail.voteAverage ?: existing.ratingTmdb,
                                releaseDate = detail.releaseDate ?: existing.releaseDate,
                                posterUrl = detail.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                                        ?: existing.posterUrl,
                                imdbId = detail.imdbId ?: existing.imdbId,
                                streamingPlatforms = platformsStr ?: existing.streamingPlatforms,
                                ratingImdb = newRatingImdb ?: detail.voteAverage ?: existing.ratingImdb,
                                lastRefreshedAt = System.currentTimeMillis()
                            )
                        )

                        if (hadNoRealPlatform && platformsStr != null) {
                            val realPlatforms = platforms.filter { it.platformName != "Cine" }
                            if (realPlatforms.isNotEmpty()) {
                                synchronized(newPlatforms) {
                                    newPlatforms.add(
                                        NewPlatformInfo(
                                            movieTitle = movie.title,
                                            platformNames = realPlatforms.map { it.platformName }
                                        )
                                    )
                                }
                            }
                        }

                        synchronized(this@LibraryRefresher) { count++ }
                    } catch (e: BatchCancelledException) {
                        AppLogger.w("LibraryRefresher", "Movie batch cancelled after 3 timeouts")
                    } catch (e: Exception) {
                        AppLogger.e("LibraryRefresher", "Refresh movie error -> ${movie.title}", e)
                    }
                }
            }.forEach { it.await() }
        }

        return count
    }

    private fun parsePlatformNames(platformsStr: String): List<String> {
        return try {
            platformsStr.toStreamingPlatforms().map { it.platformName }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
