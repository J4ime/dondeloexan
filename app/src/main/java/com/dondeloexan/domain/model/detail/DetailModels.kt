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
    val isFavorite: Boolean = false,
    val inLibrary: Boolean = false
)

data class SeriesTracking(
    val exists: Boolean = false,
    val isFavorite: Boolean = false,
    val watchedToDate: Boolean = false,
    val watchedEpisodes: Set<String> = emptySet(),
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null,
    val finishedAt: Long? = null,
    val inProduction: Boolean? = null,
    val seriesStatus: String? = null,
    val nextEpisodeAirDate: String? = null,
    /** Temporada pendiente de estreno en España (persistida, no depende de la red). */
    val pendingEsSeason: Int? = null,
    /** Fecha ISO del estreno en España de esa temporada. */
    val spanishReleaseDate: String? = null
) {
    /**
     * Temporada pendiente solo si su fecha española sigue en el futuro (regla
     * única): mientras tanto no cuenta como emitida.
     */
    fun pendingSpanishSeason(today: java.time.LocalDate = java.time.LocalDate.now()): Int? =
        com.dondeloexan.domain.model.SpanishReleaseInfo.pendingSpanishReleaseSeason(
            season = pendingEsSeason,
            isoDate = spanishReleaseDate,
            today = today
        )
    fun isEpisodeWatched(season: Int, episode: Int): Boolean =
        watchedEpisodes.contains("S${season}E${episode}")

    companion object {
        fun keyFor(season: Int, episode: Int): String = "S${season}E${episode}"
    }
}

/** Capítulo concreto de una temporada concreta (cascadas multi-temporada). */
data class EpisodeRef(
    val season: Int,
    val episode: Int
)

/**
 * Propuesta de "marcar capítulos anteriores". [count] es el total a marcar
 * (temporada seleccionada + temporadas anteriores).
 */
data class CascadeProposal(
    val season: Int,
    val targetEpisode: Int,
    val count: Int,
    val currentSeasonCount: Int = 0,
    val previousSeasons: List<Int> = emptyList(),
    val previousSeasonsCount: Int = 0
)

sealed class EpisodeToggleResult {
    data class NeedsCascade(val proposal: CascadeProposal) : EpisodeToggleResult()
    data class Applied(val tracking: SeriesTracking) : EpisodeToggleResult()
}
