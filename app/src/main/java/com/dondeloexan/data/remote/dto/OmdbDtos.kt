package com.dondeloexan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OmdbDetailResponse(
    @SerialName("Title") val title: String? = null,
    @SerialName("Year") val year: String? = null,
    @SerialName("Rated") val rated: String? = null,
    @SerialName("Released") val released: String? = null,
    @SerialName("Runtime") val runtime: String? = null,
    @SerialName("Genre") val genre: String? = null,
    @SerialName("Director") val director: String? = null,
    @SerialName("Writer") val writer: String? = null,
    @SerialName("Actors") val actors: String? = null,
    @SerialName("Plot") val plot: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Country") val country: String? = null,
    @SerialName("Awards") val awards: String? = null,
    @SerialName("Poster") val poster: String? = null,
    @SerialName("Ratings") val ratings: List<OmdbRating>? = null,
    @SerialName("Metascore") val metascore: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    @SerialName("imdbVotes") val imdbVotes: String? = null,
    @SerialName("imdbID") val imdbID: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("totalSeasons") val totalSeasons: String? = null,
    @SerialName("Response") val response: String = "False"
)

@Serializable
data class OmdbRating(
    @SerialName("Source") val source: String,
    @SerialName("Value") val value: String
)

@Serializable
data class OmdbSearchResponse(
    @SerialName("Search") val search: List<OmdbSearchResult>? = null,
    @SerialName("totalResults") val totalResults: String? = null,
    @SerialName("Response") val response: String = "False"
)

@Serializable
data class OmdbSearchResult(
    @SerialName("Title") val title: String,
    @SerialName("Year") val year: String,
    @SerialName("imdbID") val imdbID: String,
    @SerialName("Type") val type: String,
    @SerialName("Poster") val poster: String? = null
)
