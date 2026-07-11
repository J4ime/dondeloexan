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
    @SerialName("production_countries") val productionCountries: List<TmdbCountryDto>? = null,
    @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompanyDto>? = null
)

@Serializable
data class TmdbGenreDto(val id: Int, val name: String)

@Serializable
data class TmdbCountryDto(
    @SerialName("iso_3166_1") val isoCode: String,
    val name: String
)

@Serializable
data class TmdbProductionCompanyDto(
    val id: Int,
    val name: String,
    @SerialName("logo_path") val logoPath: String? = null
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
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Float? = null,
    val adult: Boolean = false,
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

@Serializable
data class TmdbTvDetailDto(
    val id: Int,
    val name: String,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    val genres: List<TmdbGenreDto>? = null,
    val seasons: List<TmdbSeasonDto>? = null,
    @SerialName("production_countries") val productionCountries: List<TmdbCountryDto>? = null,
    @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompanyDto>? = null,
    @SerialName("in_production") val inProduction: Boolean? = null,
    val status: String? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeDto? = null,
    @SerialName("last_episode_to_air") val lastEpisodeToAir: TmdbEpisodeDto? = null
)

@Serializable
data class TmdbSeasonDto(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("season_number") val seasonNumber: Int
)

@Serializable
data class TmdbTvSeasonDetailDto(
    @SerialName("_id") val internalId: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
    val name: String? = null,
    val overview: String? = null,
    val id: Int? = null,
    @SerialName("season_number") val seasonNumber: Int = 0
)

@Serializable
data class TmdbEpisodeDto(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_number") val episodeNumber: Int,
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("season_number") val seasonNumber: Int
)

@Serializable
data class TmdbCreditsResponse(
    val id: Int,
    val cast: List<TmdbCastMemberDto> = emptyList(),
    val crew: List<TmdbCrewMemberDto> = emptyList()
)

@Serializable
data class TmdbCastMemberDto(
    val id: Int,
    val name: String,
    val character: String? = null,
    val order: Int? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null
)

@Serializable
data class TmdbCrewMemberDto(
    val id: Int,
    val name: String,
    val job: String? = null,
    val department: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null
)
