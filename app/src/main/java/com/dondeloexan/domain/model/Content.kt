package com.dondeloexan.domain.model

data class ExternalLinks(
    val imdbId: String? = null,
    val wikipediaUrl: String? = null,
    val facebookId: String? = null,
    val instagramId: String? = null,
    val twitterId: String? = null,
    val youtubeId: String? = null,
    val homepage: String? = null
)

data class Content(
    val id: String,
    val source: ContentSource,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val title: String,
    val originalTitle: String? = null,
    val type: ContentType,
    val year: Int? = null,
    val releaseDate: String? = null,
    val durationMinutes: Int? = null,
    val totalEpisodes: Int? = null,
    val ratingTmdb: Float? = null,
    val ratingImdb: Float? = null,
    val ratingRt: Int? = null,
    val ratingMetacritic: Int? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val music: List<String> = emptyList(),
    val cinematography: List<String> = emptyList(),
    val productionCompanies: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val streamingPlatforms: List<StreamingAvailability> = emptyList(),
    val externalLinks: ExternalLinks? = null,
    val lastCachedAt: Long = System.currentTimeMillis()
)

data class ContentPreview(
    val id: String,
    val source: ContentSource,
    val tmdbId: Int? = null,
    val title: String,
    val type: ContentType,
    val year: Int? = null,
    val releaseDate: String? = null,
    val coverUrl: String? = null,
    val directors: List<String> = emptyList(),
    val ratingImdb: Float? = null,
    val genres: List<String> = emptyList(),
    val streamingPlatforms: List<StreamingAvailability> = emptyList(),
    val totalEpisodes: Int? = null,
    val voteCount: Int? = null,
    val isAdult: Boolean = false
)

data class StreamingAvailability(
    val platformName: String,
    val platformId: String? = null,
    val logoUrl: String? = null,
    val availabilityType: AvailabilityType,
    val webUrl: String? = null
)

enum class ContentSource { TMDB, IMDB }
enum class ContentType { MOVIE, SERIES }
enum class AvailabilityType { SUBSCRIPTION, RENT, BUY, FREE, ADS }
