package com.dondeloexan.data.sync

import com.dondeloexan.data.catalog.CatalogCriticReviewRow
import com.dondeloexan.data.catalog.CatalogFaRow
import com.dondeloexan.data.catalog.CatalogMovieRow
import com.dondeloexan.data.catalog.CatalogTvShowRow
import com.dondeloexan.data.local.entity.BlacklistedEntity
import com.dondeloexan.data.local.entity.CriticReviewEntity
import com.dondeloexan.data.local.entity.FaMovieDataEntity
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.SearchHistoryEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private fun Boolean.asInt(): Int = if (this) 1 else 0

// ── Tablas de usuario (la BD autogenera el id UUID; la app NO lo envía) ──────

@Serializable
data class UserMovieSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    val status: String,
    val liked: Int,
    @SerialName("watched_at") val watchedAt: Long? = null,
    @SerialName("added_at") val addedAt: Long,
    @SerialName("last_refreshed_at") val lastRefreshedAt: Long? = null,
    @SerialName("fa_id") val faId: Int? = null
)

@Serializable
data class UserTvShowSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    val status: String,
    val liked: Int,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("added_at") val addedAt: Long,
    @SerialName("next_episode_air_date") val nextEpisodeAirDate: String? = null,
    @SerialName("next_episode_number") val nextEpisodeNumber: Int? = null,
    @SerialName("next_episode_season") val nextEpisodeSeasonNumber: Int? = null,
    @SerialName("series_status") val seriesStatus: String? = null,
    @SerialName("in_production") val inProduction: Int? = null,
    @SerialName("num_seasons") val numberOfSeasons: Int? = null,
    @SerialName("last_watched_at") val lastWatchedAt: Long? = null,
    @SerialName("finished_at") val finishedAt: Long? = null,
    @SerialName("released_episodes") val releasedEpisodes: Int? = null,
    @SerialName("last_refreshed_at") val lastRefreshedAt: Long? = null,
    @SerialName("fa_id") val faId: Int? = null
)

@Serializable
data class TvShowProgressSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    val season: Int,
    val episode: Int,
    @SerialName("watched_at") val watchedAt: Long
)

@Serializable
data class SearchHistorySyncDto(
    @SerialName("user_id") val userId: String,
    val query: String,
    @SerialName("searched_at") val searchedAt: Long
)

@Serializable
data class UserPlatformSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("platform_name") val platformName: String,
    @SerialName("is_active") val isActive: Int
)

@Serializable
data class BlacklistSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    val title: String,
    val type: String,
    @SerialName("added_at") val addedAt: Long
)

// ── Mappers: entidades Room → DTOs ──────────────────────────────────────────

fun MovieEntity.toCatalogMovieRow(now: Long = System.currentTimeMillis()): CatalogMovieRow = CatalogMovieRow(
    contentId = contentId ?: "",
    title = title,
    tmdbId = tmdbId,
    imdbId = imdbId,
    year = year,
    releaseDate = releaseDate,
    coverUrl = posterUrl,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    certification = certification,
    streamingPlatforms = streamingPlatforms,
    updatedAt = lastRefreshedAt ?: now
)

fun TvShowEntity.toCatalogTvShowRow(now: Long = System.currentTimeMillis()): CatalogTvShowRow = CatalogTvShowRow(
    contentId = contentId ?: "",
    title = title,
    tmdbId = tmdbId,
    imdbId = imdbId,
    year = year,
    coverUrl = posterUrl,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    certification = certification,
    totalEpisodes = totalEpisodes,
    numSeasons = numberOfSeasons,
    releasedEpisodes = releasedEpisodes,
    inProduction = inProduction?.asInt(),
    seriesStatus = seriesStatus,
    nextEpisodeAirDate = nextEpisodeAirDate,
    nextEpisodeNumber = nextEpisodeNumber,
    nextEpisodeSeason = nextEpisodeSeasonNumber,
    updatedAt = lastRefreshedAt ?: now
)

fun CriticReviewEntity.toCatalogCriticReviewRow() = CatalogCriticReviewRow(
    contentId = contentId,
    reviewsJson = reviewsJson,
    cachedAt = cachedAt
)

fun FaMovieDataEntity.toCatalogFaRow() = CatalogFaRow(
    contentId = contentId,
    faId = faId,
    faRating = faRating,
    platformReleasesJson = platformReleasesJson,
    cachedAt = cachedAt
)

fun MovieEntity.toUserMovieSyncDto(userId: String) = UserMovieSyncDto(
    userId = userId,
    contentId = contentId ?: "",
    status = status.name,
    liked = liked.asInt(),
    watchedAt = watchedAt,
    addedAt = addedAt,
    lastRefreshedAt = lastRefreshedAt,
    faId = faId
)

fun TvShowEntity.toUserTvShowSyncDto(userId: String) = UserTvShowSyncDto(
    userId = userId,
    contentId = contentId ?: "",
    status = status.name,
    liked = liked.asInt(),
    totalEpisodes = totalEpisodes,
    addedAt = addedAt,
    nextEpisodeAirDate = nextEpisodeAirDate,
    nextEpisodeNumber = nextEpisodeNumber,
    nextEpisodeSeasonNumber = nextEpisodeSeasonNumber,
    seriesStatus = seriesStatus,
    inProduction = inProduction?.asInt(),
    numberOfSeasons = numberOfSeasons,
    lastWatchedAt = lastWatchedAt,
    finishedAt = finishedAt,
    releasedEpisodes = releasedEpisodes,
    lastRefreshedAt = lastRefreshedAt,
    faId = faId
)

fun TvShowProgressEntity.toSyncDto(userId: String, contentId: String) = TvShowProgressSyncDto(
    userId = userId,
    contentId = contentId,
    season = season,
    episode = episode,
    watchedAt = watchedAt
)

fun SearchHistoryEntity.toSyncDto(userId: String) = SearchHistorySyncDto(
    userId = userId, query = query, searchedAt = searchedAt
)

fun UserPlatformEntity.toSyncDto(userId: String) = UserPlatformSyncDto(
    userId = userId, platformName = platformName, isActive = isActive.asInt()
)

fun BlacklistedEntity.toSyncDto(userId: String) = BlacklistSyncDto(
    userId = userId, contentId = contentId, title = title, type = type, addedAt = addedAt
)