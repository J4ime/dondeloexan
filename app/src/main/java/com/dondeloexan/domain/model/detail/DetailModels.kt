package com.dondeloexan.domain.model.detail

data class Episode(
    val episodeNumber: Int,
    val name: String,
    val overview: String? = null,
    val airDate: String? = null,
    val stillPath: String? = null,
    val voteAverage: Float? = null,
    val seasonNumber: Int,
    val episodeType: String? = null
)

data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val id: Int? = null
)

data class SeasonDetail(
    val seasonNumber: Int,
    val episodes: List<Episode> = emptyList(),
    val name: String? = null,
    val overview: String? = null,
    val airDate: String? = null
)

data class MovieWatchState(
    val isWatched: Boolean = false,
    val isFavorite: Boolean = false
)

data class SeriesTracking(
    val exists: Boolean = false,
    val watchedEpisodes: Set<String> = emptySet(),
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null,
    val finishedAt: Long? = null,
    val inProduction: Boolean? = null,
    val seriesStatus: String? = null,
    val nextEpisodeAirDate: String? = null
) {
    fun isEpisodeWatched(season: Int, episode: Int): Boolean =
        watchedEpisodes.contains("S${season}E${episode}")

    companion object {
        fun keyFor(season: Int, episode: Int): String = "S${season}E${episode}"
    }
}

data class CascadeProposal(
    val season: Int,
    val targetEpisode: Int,
    val count: Int
)

sealed class EpisodeToggleResult {
    data class NeedsCascade(val proposal: CascadeProposal) : EpisodeToggleResult()
    data class Applied(val tracking: SeriesTracking) : EpisodeToggleResult()
}
