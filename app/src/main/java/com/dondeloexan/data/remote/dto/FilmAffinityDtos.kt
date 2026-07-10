package com.dondeloexan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FaRapidSearchResponse(
    val search: String,
    val lang: String,
    val count: Int? = null,
    val results: List<FaRapidSearchItem> = emptyList(),
    val cached: Boolean = false,
    val warming: Boolean = false,
    @SerialName("retry_after") val retryAfter: Int? = null,
    val message: String? = null
)

@Serializable
data class FaRapidSearchItem(
    val id: Int,
    val title: String,
    val year: Int? = null,
    val rating: Float? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val type: String? = null,
    @SerialName("fa_url") val faUrl: String? = null
)

@Serializable
data class FaRapidItemDetail(
    val id: Int,
    val type: String? = null,
    val title: FaRapidTitle? = null,
    val year: Int? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val countries: List<String>? = null,
    val genres: List<String>? = null,
    val synopsis: String? = null,
    val rating: Float? = null,
    val votes: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("fa_url") val faUrl: String? = null,
    val credits: FaRapidCredits? = null,
    val streaming: List<FaRapidStreaming>? = null,
    val cached: Boolean = false,
    val lang: String? = null
)

@Serializable
data class FaRapidTitle(
    val original: String? = null,
    val local: String? = null
)

@Serializable
data class FaRapidCredits(
    val directors: List<String>? = null,
    val cast: List<FaRapidCastMember>? = null,
    val writers: List<String>? = null,
    val music: List<String>? = null,
    val photography: List<String>? = null,
    val producers: List<String>? = null,
    val other: List<String>? = null
)

@Serializable
data class FaRapidCastMember(
    val name: String? = null,
    val character: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null
)

@Serializable
data class FaRapidStreaming(
    val type: String,
    val providers: List<String> = emptyList()
)

// ── Keep old DTOs for xsga fallback ──

@Serializable
data class FaApiResponse<T>(
    val status: String,
    val statusCode: Int,
    val response: T
)

@Serializable
data class FaSimpleSearchRequest(val text: String)

@Serializable
data class FaSearchResponse(
    val total: Int,
    val results: List<FaSearchResultDto>
)

@Serializable
data class FaSearchResultDto(
    val id: Int,
    val title: String,
    val year: Int,
    val directors: List<String> = emptyList()
)

@Serializable
data class FaFilmDetailDto(
    @SerialName("filmAfinityId") val filmAfinityId: String,
    val title: String,
    val originalTitle: String,
    val year: Int,
    val duration: Int,
    val coverUrl: String? = null,
    val coverFile: String? = null,
    val rating: String? = null,
    val country: String? = null,
    val directors: List<String> = emptyList(),
    val screenplay: String? = null,
    val soundtrack: String? = null,
    val photography: String? = null,
    val cast: List<String> = emptyList(),
    val producer: String? = null,
    val genres: List<String> = emptyList(),
    val genreTopics: List<String> = emptyList(),
    val synopsis: String? = null
)
