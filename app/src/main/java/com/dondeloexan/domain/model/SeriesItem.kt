package com.dondeloexan.domain.model

/**
 * Representación de dominio de una serie en la biblioteca, con su progreso de
 * visionado y la lógica de clasificación de estado ("en curso", "al día",
 * "terminada") que antes vivía en la capa de persistencia.
 */
data class SeriesItem(
    val id: Long,
    val contentId: String? = null,
    val tmdbId: Int? = null,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val addedAt: Long = 0L,
    val isLiked: Boolean = false,
    val isWatched: Boolean = false,
    val finishedAt: Long? = null,
    val watchedCount: Int = 0,
    val totalEpisodes: Int? = null,
    val releasedEpisodes: Int? = null,
    val nextEpisodeAirDate: String? = null,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeSeasonNumber: Int? = null,
    val seriesStatus: String? = null,
    val inProduction: Boolean? = null,
    val numberOfSeasons: Int? = null,
    val streamingPlatforms: List<StreamingAvailability> = emptyList(),
    val lastWatchedAt: Long? = null
) {
    fun hasFutureSeasons(): Boolean = when (seriesStatus) {
        "Ended", "Canceled" -> false
        null -> inProduction != false
        else -> true
    }

    fun isCaughtUp(): Boolean {
        val aired = releasedEpisodes
        if (aired != null) return aired > 0 && watchedCount >= aired
        val total = totalEpisodes ?: return false
        if (!hasFutureSeasons()) return total > 0 && watchedCount >= total
        return false
    }

    fun isFinished(): Boolean {
        if (!isCaughtUp()) return false
        return !hasFutureSeasons()
    }
}
