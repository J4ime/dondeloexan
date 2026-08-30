package com.dondeloexan.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Filas del catálogo global de la nube. Estas mismas clases sirven de DTO de
 * subida (upsert merge por content_id) y de lectura (la respuesta de PostgREST
 * se decodifica directamente). Los campos tipo listado se guardan como JSON
 * (TEXT) fiel a los modelos de dominio (PersonInfo, StreamingAvailability…).
 */

@Serializable
data class CatalogMovieRow(
    @SerialName("content_id") val contentId: String,
    val title: String = "",
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val year: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("spanish_release_date") val spanishReleaseDate: String? = null,
    @SerialName("digital_release_date") val digitalReleaseDate: String? = null,
    @SerialName("tv_release_date") val tvReleaseDate: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("rating_tmdb") val ratingTmdb: Float? = null,
    @SerialName("rating_imdb") val ratingImdb: Float? = null,
    @SerialName("rating_rt") val ratingRt: Int? = null,
    @SerialName("rating_metacritic") val ratingMetacritic: Int? = null,
    @SerialName("rating_filmaffinity") val ratingFilmaffinity: Float? = null,
    val certification: String? = null,
    val synopsis: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val directors: String? = null,
    val writers: String? = null,
    @SerialName("cast_json") val castJson: String? = null,
    val music: String? = null,
    val cinematography: String? = null,
    @SerialName("production_companies") val productionCompanies: String? = null,
    val genres: String? = null,
    val countries: String? = null,
    @SerialName("streaming_platforms") val streamingPlatforms: String? = null,
    @SerialName("external_links") val externalLinks: String? = null,
    @SerialName("collection_tmdb_id") val collectionTmdbId: Int? = null,
    @SerialName("updated_at") val updatedAt: Long = 0L
)

@Serializable
data class CatalogTvShowRow(
    @SerialName("content_id") val contentId: String,
    val title: String = "",
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val year: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("spanish_release_date") val spanishReleaseDate: String? = null,
    @SerialName("digital_release_date") val digitalReleaseDate: String? = null,
    @SerialName("tv_release_date") val tvReleaseDate: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("rating_tmdb") val ratingTmdb: Float? = null,
    @SerialName("rating_imdb") val ratingImdb: Float? = null,
    @SerialName("rating_rt") val ratingRt: Int? = null,
    @SerialName("rating_metacritic") val ratingMetacritic: Int? = null,
    @SerialName("rating_filmaffinity") val ratingFilmaffinity: Float? = null,
    val certification: String? = null,
    val synopsis: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val directors: String? = null,
    val writers: String? = null,
    @SerialName("cast_json") val castJson: String? = null,
    val music: String? = null,
    val cinematography: String? = null,
    @SerialName("production_companies") val productionCompanies: String? = null,
    val genres: String? = null,
    val countries: String? = null,
    @SerialName("streaming_platforms") val streamingPlatforms: String? = null,
    @SerialName("external_links") val externalLinks: String? = null,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("num_seasons") val numSeasons: Int? = null,
    @SerialName("released_episodes") val releasedEpisodes: Int? = null,
    @SerialName("in_production") val inProduction: Int? = null,
    @SerialName("series_status") val seriesStatus: String? = null,
    @SerialName("next_episode_air_date") val nextEpisodeAirDate: String? = null,
    @SerialName("next_episode_number") val nextEpisodeNumber: Int? = null,
    @SerialName("next_episode_season") val nextEpisodeSeason: Int? = null,
    @SerialName("updated_at") val updatedAt: Long = 0L
)

@Serializable
data class CatalogSeasonRow(
    @SerialName("content_id") val contentId: String,
    @SerialName("season_number") val seasonNumber: Int,
    val name: String = "",
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("tmdb_season_id") val tmdbSeasonId: Int? = null
)

@Serializable
data class CatalogEpisodeRow(
    @SerialName("content_id") val contentId: String,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String = "",
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("episode_type") val episodeType: String? = null
)

@Serializable
data class CatalogCriticReviewRow(
    @SerialName("content_id") val contentId: String,
    @SerialName("reviews_json") val reviewsJson: String = "[]",
    @SerialName("cached_at") val cachedAt: Long = 0L
)

@Serializable
data class CatalogFaRow(
    @SerialName("content_id") val contentId: String,
    @SerialName("fa_id") val faId: Int? = null,
    @SerialName("fa_rating") val faRating: Float? = null,
    @SerialName("platform_releases_json") val platformReleasesJson: String? = null,
    @SerialName("cached_at") val cachedAt: Long = 0L
)

@Serializable
data class CatalogListRow(
    @SerialName("content_id") val contentId: String,
    @SerialName("list_type") val listType: String,
    val pos: Int,
    @SerialName("related_content_id") val relatedContentId: String,
    @SerialName("related_data") val relatedData: String? = null
)