package com.dondeloexan.data.sync

import com.dondeloexan.data.catalog.CatalogCriticReviewRow
import com.dondeloexan.data.catalog.CatalogFaRow
import com.dondeloexan.data.catalog.CatalogMovieRow
import com.dondeloexan.data.catalog.CatalogTvShowRow
import com.dondeloexan.data.catalog.personInfoToJson
import com.dondeloexan.data.catalog.stringListToJson
import com.dondeloexan.data.catalog.streamingToJson
import com.dondeloexan.data.catalog.toJson
import com.dondeloexan.data.local.entity.BlacklistedEntity
import com.dondeloexan.data.local.entity.CriticReviewEntity
import com.dondeloexan.data.local.entity.FaMovieDataEntity
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.SearchHistoryEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.domain.model.Content
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
    originalTitle = originalTitle,
    year = year,
    releaseDate = releaseDate,
    spanishReleaseDate = spanishReleaseDate,
    digitalReleaseDate = digitalReleaseDate,
    tvReleaseDate = tvReleaseDate,
    durationMinutes = durationMinutes,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    ratingRt = ratingRt,
    ratingMetacritic = ratingMetacritic,
    ratingFilmaffinity = ratingFilmaffinity,
    certification = certification,
    synopsis = synopsis,
    coverUrl = posterUrl,
    backdropUrl = backdropUrl,
    directors = directors,
    writers = writers,
    castJson = castJson,
    music = music,
    cinematography = cinematography,
    productionCompanies = productionCompanies,
    genres = genres,
    countries = countries,
    streamingPlatforms = streamingPlatforms,
    externalLinks = externalLinks,
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

/**
 * Vuelca la ficha técnica rica de un [Content] (detalle TMDB) en una entidad
 * local existente, SOLO cuando hay información (no machaca campos válidos con
 * null/empty). Conserva el estado de usuario (status/finished/lastWatched) que
 * no forma parte del detalle. Los listados se serializan como JSON idéntico al
 * de [TvShowEntity.toCatalogTvShowRow] para que la nube viaje completa.
 */
fun Content.toTvShowEntity(existing: TvShowEntity): TvShowEntity {
    fun <T> pick(current: T?, from: T?): T? = if (from != null) from else current
    fun pickList(current: String?, fromJson: String?): String? = if (!fromJson.isNullOrBlank()) fromJson else current
    return existing.copy(
        originalTitle = pick(existing.originalTitle, originalTitle),
        releaseDate = pick(existing.releaseDate, releaseDate),
        spanishReleaseDate = pick(existing.spanishReleaseDate, spanishReleaseDate),
        digitalReleaseDate = pick(existing.digitalReleaseDate, digitalReleaseDate),
        tvReleaseDate = pick(existing.tvReleaseDate, tvReleaseDate),
        durationMinutes = pick(existing.durationMinutes, durationMinutes),
        ratingTmdb = pick(existing.ratingTmdb, ratingTmdb),
        ratingImdb = pick(existing.ratingImdb, ratingImdb),
        ratingRt = pick(existing.ratingRt, ratingRt),
        ratingMetacritic = pick(existing.ratingMetacritic, ratingMetacritic),
        ratingFilmaffinity = pick(existing.ratingFilmaffinity, ratingFilmaffinity),
        certification = pick(existing.certification, certification),
        synopsis = pick(existing.synopsis, synopsis),
        backdropUrl = pick(existing.backdropUrl, backdropUrl),
        directors = pickList(existing.directors, directors.personInfoToJson()),
        writers = pickList(existing.writers, writers.stringListToJson()),
        castJson = pickList(existing.castJson, cast.personInfoToJson()),
        music = pickList(existing.music, music.stringListToJson()),
        cinematography = pickList(existing.cinematography, cinematography.stringListToJson()),
        productionCompanies = pickList(existing.productionCompanies, productionCompanies.stringListToJson()),
        genres = pickList(existing.genres, genres.stringListToJson()),
        countries = pickList(existing.countries, countries.stringListToJson()),
        streamingPlatforms = pickList(existing.streamingPlatforms, streamingPlatforms.streamingToJson()),
        externalLinks = pickList(existing.externalLinks, externalLinks?.toJson()),
        totalEpisodes = pick(existing.totalEpisodes, totalEpisodes),
        lastRefreshedAt = System.currentTimeMillis()
    )
}