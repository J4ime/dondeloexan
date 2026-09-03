package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.BlacklistedEntity
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.local.entity.toPlatformsString
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.domain.repository.LibraryAction
import com.dondeloexan.domain.repository.LibraryMutationResult
import com.dondeloexan.domain.repository.LibraryRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.LocalDate

class LibraryRepositoryImpl(
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao,
    private val tvShowProgressDao: TvShowProgressDao,
    private val blacklistDao: BlacklistDao,
    private val faMovieDataDao: FaMovieDataDao,
    private val userPlatformDao: UserPlatformDao,
    private val tmdbApi: TmdbApi,
    private val discoverRepository: DiscoverRepository
) : LibraryRepository {

    override val activePlatforms: Flow<Set<String>> =
        userPlatformDao.getActiveFlow().map { it.map { p -> p.platformName }.toSet() }

    override val blacklistedIds: Flow<Set<String>> =
        blacklistDao.getAllFlow().map { list -> list.map { it.contentId }.toSet() }

    override val likedIds: Flow<Set<String>> = combine(
        movieDao.getLiked().map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getLiked().map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { m, t -> m + t }.distinctUntilChanged()

    override val watchedIds: Flow<Set<String>> = combine(
        movieDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getByStatus(WatchStatus.YA_VISTA).map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { m, t -> m + t }.distinctUntilChanged()

    override val libraryIds: Flow<Set<String>> = combine(
        movieDao.getAllFlow().map { list -> list.mapNotNull { it.contentId }.toSet() },
        tvShowDao.getAllFlow().map { list -> list.mapNotNull { it.contentId }.toSet() }
    ) { m, t -> m + t }.distinctUntilChanged()

    override suspend fun discoverExcludedIds(): Set<String> {
        val liked = buildSet {
            addAll(likedIds.first())
            movieDao.getLiked().first().forEach { m -> m.tmdbId?.let { add("tmdb-$it") } }
            tvShowDao.getLiked().first().forEach { s -> s.tmdbId?.let { add("tmdb-$it") } }
        }
        val inLibrary = buildSet {
            movieDao.getAll().forEach { m -> m.tmdbId?.let { add("tmdb-$it") } }
            tvShowDao.getAll().forEach { s -> s.tmdbId?.let { add("tmdb-$it") } }
        }
        return liked + blacklistedIds.first() + inLibrary
    }

    private data class SaveContentInfo(val contentId: String, val tmdbId: Int?, val imdbId: String?)

    private suspend fun resolveContentForSave(preview: ContentPreview): SaveContentInfo {
        if (preview.type == ContentType.MOVIE) {
            val byTmdb = preview.tmdbId?.let { movieDao.getByTmdbId(it) }
            if (byTmdb != null) return SaveContentInfo(byTmdb.contentId ?: preview.id, byTmdb.tmdbId, byTmdb.imdbId)
            val byImdb = preview.imdbId?.let { movieDao.getByImdbId(it) }
            if (byImdb != null) return SaveContentInfo(byImdb.contentId ?: preview.id, byImdb.tmdbId, byImdb.imdbId)
            if (preview.tmdbId != null || preview.source != ContentSource.IMDB) {
                return SaveContentInfo(preview.id, preview.tmdbId, preview.imdbId)
            }
            val rawImdbId = preview.id.removePrefix("imdb-")
            val resolved = discoverRepository.resolveTmdbId(rawImdbId, preview.type)
            return SaveContentInfo(preview.id, resolved, rawImdbId)
        } else {
            val byTmdb = preview.tmdbId?.let { tvShowDao.getByTmdbId(it) }
            if (byTmdb != null) return SaveContentInfo(byTmdb.contentId ?: preview.id, byTmdb.tmdbId, byTmdb.imdbId)
            val byImdb = preview.imdbId?.let { tvShowDao.getByImdbId(it) }
            if (byImdb != null) return SaveContentInfo(byImdb.contentId ?: preview.id, byImdb.tmdbId, byImdb.imdbId)
            if (preview.tmdbId != null || preview.source != ContentSource.IMDB) {
                return SaveContentInfo(preview.id, preview.tmdbId, preview.imdbId)
            }
            val rawImdbId = preview.id.removePrefix("imdb-")
            val resolved = discoverRepository.resolveTmdbId(rawImdbId, preview.type)
            return SaveContentInfo(preview.id, resolved, rawImdbId)
        }
    }

    private suspend fun fetchPlatformsIfEmpty(preview: ContentPreview): List<StreamingAvailability> {
        if (preview.streamingPlatforms.isNotEmpty()) return preview.streamingPlatforms
        val tmdbId = preview.tmdbId ?: return emptyList()
        return try {
            val providers = if (preview.type == ContentType.SERIES) {
                tmdbApi.getTvWatchProviders(tmdbId)
            } else {
                tmdbApi.getMovieWatchProviders(tmdbId)
            }
            providers.results?.get("ES")?.toStreamingAvailability().orEmpty()
        } catch (e: Exception) {
            AppLogger.e("LibraryRepo", "fetchPlatformsIfEmpty para ${preview.title}", e)
            emptyList()
        }
    }

    override suspend fun toggleFavorite(preview: ContentPreview): LibraryMutationResult {
        val info = resolveContentForSave(preview)
        val platformsStr = fetchPlatformsIfEmpty(preview).toPlatformsString()
        var toggledOn = false
        if (preview.type == ContentType.MOVIE) {
            val existing = movieDao.getByContentId(info.contentId) ?: (info.tmdbId?.let { movieDao.getByTmdbId(it) }
                ?: info.imdbId?.let { movieDao.getByImdbId(it) })
            if (existing != null) {
                toggledOn = !existing.liked
                movieDao.update(existing.copy(
                    liked = toggledOn,
                    ratingImdb = preview.ratingImdb ?: existing.ratingImdb,
                    streamingPlatforms = if (platformsStr.isNullOrEmpty() || platformsStr == "[]") existing.streamingPlatforms else platformsStr,
                    releaseDate = preview.releaseDate ?: existing.releaseDate
                ))
            } else {
                toggledOn = true
                movieDao.insert(
                    MovieEntity(
                        contentId = info.contentId,
                        tmdbId = info.tmdbId,
                        imdbId = info.imdbId,
                        title = preview.title,
                        year = preview.year,
                        releaseDate = preview.releaseDate,
                        posterUrl = preview.coverUrl,
                        ratingImdb = preview.ratingImdb,
                        streamingPlatforms = platformsStr,
                        liked = true,
                        status = WatchStatus.POR_VER
                    )
                )
            }
        } else {
            val existing = tvShowDao.getByContentId(info.contentId) ?: (info.tmdbId?.let { tvShowDao.getByTmdbId(it) }
                ?: info.imdbId?.let { tvShowDao.getByImdbId(it) })
            if (existing != null) {
                toggledOn = !existing.liked
                tvShowDao.update(existing.copy(
                    liked = toggledOn,
                    streamingPlatforms = platformsStr ?: existing.streamingPlatforms
                ))
            } else {
                toggledOn = true
                tvShowDao.insert(
                    TvShowEntity(
                        contentId = info.contentId,
                        tmdbId = info.tmdbId,
                        imdbId = info.imdbId,
                        title = preview.title,
                        year = preview.year,
                        posterUrl = preview.coverUrl,
                        totalEpisodes = preview.totalEpisodes,
                        streamingPlatforms = platformsStr,
                        liked = true
                    )
                )
                tvShowDao.getByContentId(info.contentId)?.let { enrichTvShowFromTmdb(it) }
            }
        }
        return LibraryMutationResult(LibraryAction.FAVORITE, toggledOn, preview.type == ContentType.MOVIE, preview.title)
    }

    override suspend fun toggleInLibrary(preview: ContentPreview): LibraryMutationResult {
        val info = resolveContentForSave(preview)
        val platformsStr = fetchPlatformsIfEmpty(preview).toPlatformsString()
        var toggledOn = false
        if (preview.type == ContentType.MOVIE) {
            val existing = movieDao.getByContentId(info.contentId) ?: (info.tmdbId?.let { movieDao.getByTmdbId(it) }
                ?: info.imdbId?.let { movieDao.getByImdbId(it) })
            if (existing == null) {
                toggledOn = true
                movieDao.insert(
                    MovieEntity(
                        contentId = info.contentId,
                        tmdbId = info.tmdbId,
                        imdbId = info.imdbId,
                        title = preview.title,
                        year = preview.year,
                        releaseDate = preview.releaseDate,
                        posterUrl = preview.coverUrl,
                        ratingImdb = preview.ratingImdb,
                        streamingPlatforms = platformsStr,
                        liked = false,
                        status = WatchStatus.POR_VER
                    )
                )
            }
        } else {
            val existing = tvShowDao.getByContentId(info.contentId) ?: (info.tmdbId?.let { tvShowDao.getByTmdbId(it) }
                ?: info.imdbId?.let { tvShowDao.getByImdbId(it) })
            if (existing == null) {
                toggledOn = true
                tvShowDao.insert(
                    TvShowEntity(
                        contentId = info.contentId,
                        tmdbId = info.tmdbId,
                        imdbId = info.imdbId,
                        title = preview.title,
                        year = preview.year,
                        posterUrl = preview.coverUrl,
                        ratingImdb = preview.ratingImdb,
                        streamingPlatforms = platformsStr,
                        liked = false,
                        status = WatchStatus.POR_VER
                    )
                )
            }
        }
        return LibraryMutationResult(LibraryAction.ADD, toggledOn, preview.type == ContentType.MOVIE, preview.title)
    }

    override suspend fun toggleWatched(preview: ContentPreview): LibraryMutationResult {
        val info = resolveContentForSave(preview)
        val platformsStr = fetchPlatformsIfEmpty(preview).toPlatformsString()
        var toggledOn = false
        if (preview.type == ContentType.MOVIE) {
            val existing = movieDao.getByContentId(info.contentId) ?: (info.tmdbId?.let { movieDao.getByTmdbId(it) }
                ?: info.imdbId?.let { movieDao.getByImdbId(it) })
            if (existing != null) {
                toggledOn = existing.status != WatchStatus.YA_VISTA
                val newStatus = if (toggledOn) WatchStatus.YA_VISTA else WatchStatus.POR_VER
                movieDao.update(existing.copy(status = newStatus, watchedAt = if (toggledOn) System.currentTimeMillis() else null))
            } else {
                toggledOn = true
                movieDao.insert(
                    MovieEntity(
                        contentId = info.contentId,
                        tmdbId = info.tmdbId,
                        imdbId = info.imdbId,
                        title = preview.title,
                        year = preview.year,
                        releaseDate = preview.releaseDate,
                        posterUrl = preview.coverUrl,
                        ratingImdb = preview.ratingImdb,
                        streamingPlatforms = platformsStr,
                        status = WatchStatus.YA_VISTA,
                        watchedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            val existing = tvShowDao.getByContentId(info.contentId) ?: (info.tmdbId?.let { tvShowDao.getByTmdbId(it) }
                ?: info.imdbId?.let { tvShowDao.getByImdbId(it) })
            val platformsStrFinal = platformsStr ?: existing?.streamingPlatforms
            if (existing != null) {
                toggledOn = existing.status != WatchStatus.YA_VISTA
                if (!toggledOn) {
                    tvShowProgressDao.deleteByTvShowId(existing.id)
                    tvShowDao.update(existing.copy(status = WatchStatus.POR_VER))
                } else {
                    tvShowProgressDao.deleteByTvShowId(existing.id)
                    val tmdbId = existing.tmdbId ?: info.tmdbId
                    if (tmdbId != null) {
                        try {
                            val detail = tmdbApi.getTvDetailLight(tmdbId)
                            val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                            if (detail.numberOfEpisodes != null && detail.numberOfEpisodes > 0) {
                                tvShowDao.update(existing.copy(totalEpisodes = detail.numberOfEpisodes))
                            }
                            val progressToInsert = mutableListOf<TvShowProgressEntity>()
                            val today = LocalDate.now()
                            for (season in seasons) {
                                try {
                                    val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                                    for (ep in seasonDetail.episodes) {
                                        val isAired = ep.airDate == null || try { !LocalDate.parse(ep.airDate).isAfter(today) } catch (_: Exception) { true }
                                        if (isAired) progressToInsert.add(TvShowProgressEntity(tvShowId = existing.id, season = season.seasonNumber, episode = ep.episodeNumber))
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("LibraryRepo", "season ${season.seasonNumber} para ${existing.id}", e)
                                    for (epNum in 1..season.episodeCount) progressToInsert.add(TvShowProgressEntity(tvShowId = existing.id, season = season.seasonNumber, episode = epNum))
                                }
                            }
                            tvShowProgressDao.insertAll(progressToInsert)
                            tvShowDao.update(existing.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                        } catch (e: Exception) {
                            AppLogger.e("LibraryRepo", "mark watched error para ${existing.id}", e)
                        }
                    } else {
                        val totalEp = existing.totalEpisodes ?: preview.totalEpisodes ?: 0
                        if (totalEp > 0) {
                            tvShowProgressDao.insertAll((1..totalEp).map { TvShowProgressEntity(tvShowId = existing.id, season = 1, episode = it) })
                        }
                        tvShowDao.update(existing.copy(status = WatchStatus.YA_VISTA, lastWatchedAt = System.currentTimeMillis()))
                    }
                }
            } else {
                toggledOn = true
                val newShowId = tvShowDao.insert(
                    TvShowEntity(
                        contentId = info.contentId,
                        tmdbId = info.tmdbId,
                        imdbId = info.imdbId,
                        title = preview.title,
                        year = preview.year,
                        posterUrl = preview.coverUrl,
                        totalEpisodes = preview.totalEpisodes,
                        streamingPlatforms = platformsStrFinal,
                        status = WatchStatus.YA_VISTA
                    )
                )
                val tmdbId = info.tmdbId
                if (tmdbId != null) {
                    try {
                        val detail = tmdbApi.getTvDetailLight(tmdbId)
                        val seasons = detail.seasons.orEmpty().filter { it.seasonNumber > 0 }
                        val progressToInsert = mutableListOf<TvShowProgressEntity>()
                        val today = LocalDate.now()
                        for (season in seasons) {
                            try {
                                val seasonDetail = tmdbApi.getTvSeason(tmdbId, season.seasonNumber)
                                for (ep in seasonDetail.episodes) {
                                    val isAired = ep.airDate == null || try { !LocalDate.parse(ep.airDate).isAfter(today) } catch (_: Exception) { true }
                                    if (isAired) progressToInsert.add(TvShowProgressEntity(tvShowId = newShowId, season = season.seasonNumber, episode = ep.episodeNumber))
                                }
                            } catch (e: Exception) {
                                AppLogger.e("LibraryRepo", "season ${season.seasonNumber} para $newShowId", e)
                                for (epNum in 1..season.episodeCount) progressToInsert.add(TvShowProgressEntity(tvShowId = newShowId, season = season.seasonNumber, episode = epNum))
                            }
                        }
                        tvShowProgressDao.insertAll(progressToInsert)
                        tvShowDao.updateLastWatchedAt(newShowId, System.currentTimeMillis())
                    } catch (e: Exception) {
                        AppLogger.e("LibraryRepo", "mark watched error para $newShowId", e)
                    }
                } else {
                    val totalEp = preview.totalEpisodes ?: 0
                    if (totalEp > 0) {
                        tvShowProgressDao.insertAll((1..totalEp).map { TvShowProgressEntity(tvShowId = newShowId, season = 1, episode = it) })
                        tvShowDao.updateLastWatchedAt(newShowId, System.currentTimeMillis())
                    }
                }
            }
        }
        return LibraryMutationResult(LibraryAction.WATCHED, toggledOn, preview.type == ContentType.MOVIE, preview.title)
    }

    override suspend fun toggleBlacklist(preview: ContentPreview): LibraryMutationResult {
        blacklistDao.insert(
            BlacklistedEntity(
                contentId = preview.id,
                title = preview.title,
                type = preview.type.name
            )
        )
        return LibraryMutationResult(LibraryAction.BLACKLIST, true, preview.type == ContentType.MOVIE, preview.title)
    }

    override suspend fun faReleases(contentId: String): List<PlatformReleaseDate> {
        val faData = faMovieDataDao.getByContentId(contentId) ?: return emptyList()
        if (faData.platformReleasesJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(faData.platformReleasesJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlatformReleaseDate(
                    platformName = obj.getString("platformName"),
                    dateLabel = obj.optString("dateLabel", ""),
                    releaseDate = obj.optString("releaseDate", "").ifEmpty { null }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun enrichTvShowFromTmdb(entity: TvShowEntity) {
        val tmdbId = entity.tmdbId ?: return
        try {
            val tvDetail = tmdbApi.getTvDetailLight(tmdbId)
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
            tvShowDao.update(
                entity.copy(
                    totalEpisodes = tvDetail.numberOfEpisodes ?: entity.totalEpisodes,
                    releasedEpisodes = releasedEpisodes,
                    nextEpisodeAirDate = tvDetail.nextEpisodeToAir?.airDate,
                    nextEpisodeNumber = tvDetail.nextEpisodeToAir?.episodeNumber,
                    nextEpisodeSeasonNumber = tvDetail.nextEpisodeToAir?.seasonNumber,
                    seriesStatus = tvDetail.status,
                    inProduction = tvDetail.inProduction ?: entity.inProduction,
                    numberOfSeasons = tvDetail.numberOfSeasons ?: entity.numberOfSeasons
                )
            )
        } catch (e: Exception) {
            AppLogger.e("LibraryRepo", "enrichTvShowFromTmdb error para ${entity.title}", e)
        }
    }
}
