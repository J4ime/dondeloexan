package com.dondeloexan.data.sync

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

@Serializable
data class DbRowId(
    val id: Long,
    @SerialName("local_id") val localId: Long? = null
)

@Serializable
data class MovieSyncDto(
    @SerialName("local_id") val localId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val title: String,
    val year: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("rating_tmdb") val ratingTmdb: Float? = null,
    @SerialName("rating_imdb") val ratingImdb: Float? = null,
    val certification: String? = null,
    val status: String,
    val liked: Int,
    @SerialName("streaming_platforms") val streamingPlatforms: String? = null,
    @SerialName("watched_at") val watchedAt: Long? = null,
    @SerialName("added_at") val addedAt: Long,
    @SerialName("last_refreshed_at") val lastRefreshedAt: Long? = null,
    @SerialName("fa_id") val faId: Int? = null
)

@Serializable
data class TvShowSyncDto(
    @SerialName("local_id") val localId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val title: String,
    val year: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("rating_tmdb") val ratingTmdb: Float? = null,
    @SerialName("rating_imdb") val ratingImdb: Float? = null,
    val certification: String? = null,
    val status: String,
    val liked: Int,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("streaming_platforms") val streamingPlatforms: String? = null,
    @SerialName("added_at") val addedAt: Long,
    @SerialName("next_episode_air_date") val nextEpisodeAirDate: String? = null,
    @SerialName("next_episode_number") val nextEpisodeNumber: Int? = null,
    @SerialName("next_episode_season") val nextEpisodeSeasonNumber: Int? = null,
    @SerialName("series_status") val seriesStatus: String? = null,
    @SerialName("in_production") val inProduction: Int? = null,
    @SerialName("num_seasons") val numberOfSeasons: Int? = null,
    @SerialName("released_episodes") val releasedEpisodes: Int? = null,
    @SerialName("last_watched_at") val lastWatchedAt: Long? = null,
    @SerialName("finished_at") val finishedAt: Long? = null,
    @SerialName("last_refreshed_at") val lastRefreshedAt: Long? = null,
    @SerialName("fa_id") val faId: Int? = null
)

@Serializable
data class TvShowProgressSyncDto(
    @SerialName("local_id") val localId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("tv_show_id") val tvShowId: Long,
    val season: Int,
    val episode: Int,
    @SerialName("watched_at") val watchedAt: Long
)

@Serializable
data class SearchHistorySyncDto(
    @SerialName("local_id") val localId: Long,
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

@Serializable
data class CriticReviewSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("reviews_json") val reviewsJson: String,
    @SerialName("cached_at") val cachedAt: Long
)

@Serializable
data class FaMovieDataSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("fa_id") val faId: Int? = null,
    @SerialName("fa_rating") val faRating: Float? = null,
    @SerialName("platform_releases_json") val platformReleasesJson: String? = null,
    @SerialName("cached_at") val cachedAt: Long
)

fun MovieEntity.toSyncDto(userId: String) = MovieSyncDto(
    localId = id, userId = userId,
    contentId = contentId, tmdbId = tmdbId, imdbId = imdbId,
    title = title, year = year, releaseDate = releaseDate,
    posterUrl = posterUrl, ratingTmdb = ratingTmdb, ratingImdb = ratingImdb,
    certification = certification, status = status.name,
    liked = liked.asInt(), streamingPlatforms = streamingPlatforms,
    watchedAt = watchedAt, addedAt = addedAt,
    lastRefreshedAt = lastRefreshedAt, faId = faId
)

fun TvShowEntity.toSyncDto(userId: String) = TvShowSyncDto(
    localId = id, userId = userId,
    contentId = contentId, tmdbId = tmdbId, imdbId = imdbId,
    title = title, year = year, posterUrl = posterUrl,
    ratingTmdb = ratingTmdb, ratingImdb = ratingImdb,
    certification = certification, status = status.name,
    liked = liked.asInt(), totalEpisodes = totalEpisodes,
    streamingPlatforms = streamingPlatforms, addedAt = addedAt,
    nextEpisodeAirDate = nextEpisodeAirDate,
    nextEpisodeNumber = nextEpisodeNumber,
    nextEpisodeSeasonNumber = nextEpisodeSeasonNumber,
    seriesStatus = seriesStatus,
    inProduction = inProduction?.asInt(),
    numberOfSeasons = numberOfSeasons, releasedEpisodes = releasedEpisodes,
    lastWatchedAt = lastWatchedAt, finishedAt = finishedAt,
    lastRefreshedAt = lastRefreshedAt, faId = faId
)

fun TvShowProgressEntity.toSyncDto(userId: String, cloudShowId: Long) = TvShowProgressSyncDto(
    localId = id, userId = userId, tvShowId = cloudShowId,
    season = season, episode = episode, watchedAt = watchedAt
)

fun SearchHistoryEntity.toSyncDto(userId: String) = SearchHistorySyncDto(
    localId = id, userId = userId, query = query, searchedAt = searchedAt
)

fun UserPlatformEntity.toSyncDto(userId: String) = UserPlatformSyncDto(
    userId = userId, platformName = platformName, isActive = isActive.asInt()
)

fun BlacklistedEntity.toSyncDto(userId: String) = BlacklistSyncDto(
    userId = userId, contentId = contentId, title = title, type = type, addedAt = addedAt
)

fun CriticReviewEntity.toSyncDto(userId: String) = CriticReviewSyncDto(
    userId = userId, contentId = contentId,
    reviewsJson = reviewsJson, cachedAt = cachedAt
)

fun FaMovieDataEntity.toSyncDto(userId: String) = FaMovieDataSyncDto(
    userId = userId, contentId = contentId,
    faId = faId, faRating = faRating,
    platformReleasesJson = platformReleasesJson, cachedAt = cachedAt
)