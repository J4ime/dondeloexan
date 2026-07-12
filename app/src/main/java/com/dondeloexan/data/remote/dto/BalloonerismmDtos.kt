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

@Serializable
data class ImdbMovieDetailDto(
    val id: String,
    @SerialName("imdb_url") val imdbUrl: String? = null,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Long? = null,
    val genres: List<ImdbDetailGenreDto>? = null,
    @SerialName("spoken_languages") val spokenLanguages: List<ImdbDetailLanguageDto>? = null,
    @SerialName("production_countries") val productionCountries: List<ImdbDetailCountryDto>? = null,
    @SerialName("production_companies") val productionCompanies: List<ImdbDetailCompanyDto>? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val certificate: ImdbDetailCertificateDto? = null,
    val adult: Boolean? = null,
    val video: Boolean? = null,
    val budget: Long? = null,
    @SerialName("domestic_gross") val domesticGross: Long? = null,
    @SerialName("worldwide_gross") val worldwideGross: Long? = null,
    val metascore: Int? = null
)

@Serializable
data class ImdbTvDetailDto(
    val id: String,
    @SerialName("imdb_url") val imdbUrl: String? = null,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("in_production") val inProduction: Boolean? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    val seasons: List<ImdbDetailSeasonDto>? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    val type: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Long? = null,
    val genres: List<ImdbDetailGenreDto>? = null,
    @SerialName("spoken_languages") val spokenLanguages: List<ImdbDetailLanguageDto>? = null,
    @SerialName("production_countries") val productionCountries: List<ImdbDetailCountryDto>? = null,
    @SerialName("production_companies") val productionCompanies: List<ImdbDetailCompanyDto>? = null,
    @SerialName("origin_country") val originCountry: List<String>? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val certificate: ImdbDetailCertificateDto? = null,
    val adult: Boolean? = null
)

@Serializable
data class ImdbDetailGenreDto(val id: String, val name: String)

@Serializable
data class ImdbDetailLanguageDto(
    @SerialName("iso_639_1") val isoCode: String? = null,
    val name: String? = null
)

@Serializable
data class ImdbDetailCountryDto(
    @SerialName("iso_3166_1") val isoCode: String? = null,
    val name: String? = null
)

@Serializable
data class ImdbDetailCompanyDto(
    val id: String? = null,
    val name: String? = null,
    val category: String? = null,
    @SerialName("origin_country") val originCountry: String? = null,
    @SerialName("logo_path") val logoPath: String? = null
)

@Serializable
data class ImdbDetailSeasonDto(
    @SerialName("season_number") val seasonNumber: Int? = null,
    val label: String? = null
)

@Serializable
data class ImdbDetailCertificateDto(
    val rating: String? = null,
    val body: String? = null
)

@Serializable
data class ImdbExternalIds(
    val id: String,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("wikidata_id") val wikidataId: String? = null,
    @SerialName("wikipedia_url") val wikipediaUrl: String? = null,
    @SerialName("facebook_id") val facebookId: String? = null,
    @SerialName("instagram_id") val instagramId: String? = null,
    @SerialName("twitter_id") val twitterId: String? = null,
    @SerialName("youtube_id") val youtubeId: String? = null,
    val homepage: String? = null
)
