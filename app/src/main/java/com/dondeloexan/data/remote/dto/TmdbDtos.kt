package com.dondeloexan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbWatchProvidersResponse(
    val id: Int,
    val results: Map<String, TmdbCountryProviders> = emptyMap()
)

@Serializable
data class TmdbCountryProviders(
    val link: String? = null,
    val flatrate: List<TmdbProvider>? = null,
    val rent: List<TmdbProvider>? = null,
    val buy: List<TmdbProvider>? = null,
    val ads: List<TmdbProvider>? = null,
    val free: List<TmdbProvider>? = null
)

@Serializable
data class TmdbProvider(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = 0
)

@Serializable
data class TmdbMovieDto(
    val id: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val runtime: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val genres: List<TmdbGenreDto>? = null,
    @SerialName("production_countries") val productionCountries: List<TmdbCountryDto>? = null
)

@Serializable
data class TmdbGenreDto(val id: Int, val name: String)

@Serializable
data class TmdbCountryDto(
    @SerialName("iso_3166_1") val isoCode: String,
    val name: String
)

@Serializable
data class TmdbMultiSearchResponse(
    val page: Int,
    @SerialName("total_results") val totalResults: Int,
    @SerialName("total_pages") val totalPages: Int,
    val results: List<TmdbMultiSearchResult>
)

@Serializable
data class TmdbMultiSearchResult(
    val id: Int,
    @SerialName("media_type") val mediaType: String,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    val overview: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null
)

@Serializable
data class TmdbTrendingResponse(
    val page: Int,
    @SerialName("total_results") val totalResults: Int,
    val results: List<TmdbMultiSearchResult>
)
