package com.dondeloexan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImdbSearchResponse(
    val page: Int = 1,
    val results: List<ImdbSearchResult> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0
)

@Serializable
data class ImdbSearchResult(
    val id: String,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Float? = null,
    val overview: String? = null,
    @SerialName("genre_ids") val genreIds: List<String>? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val adult: Boolean = false,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null
)

@Serializable
data class ImdbWatchProvidersResponse(
    val id: String? = null,
    val results: Map<String, ImdbCountryProviders> = emptyMap()
)

@Serializable
data class ImdbCountryProviders(
    val link: String? = null,
    val flatrate: List<ImdbProvider>? = null,
    val rent: List<ImdbProvider>? = null,
    val buy: List<ImdbProvider>? = null,
    val ads: List<ImdbProvider>? = null,
    val free: List<ImdbProvider>? = null
)

@Serializable
data class ImdbProvider(
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int? = null
)
