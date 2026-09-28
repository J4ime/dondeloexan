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
    val lastWatchedAt: Long? = null,
    /** Temporada pendiente de estreno en España (Filmaffinity o búsqueda web). */
    val pendingEsSeason: Int? = null,
    val spanishReleaseDate: String? = null
) {
    /** true si la temporada pendiente aún no se ha estrenado en España. */
    fun isPendingInSpain(): Boolean {
        pendingEsSeason ?: return false
        val iso = spanishReleaseDate ?: return false
        return try {
            java.time.LocalDate.parse(iso.substringBefore("T").substringBefore(" "))
                .isAfter(java.time.LocalDate.now())
        } catch (e: Exception) {
            false
        }
    }

    fun hasFutureSeasons(): Boolean =
        hasFutureSeasonsFor(seriesStatus, inProduction) || isPendingInSpain()

    /** Estado según la regla única de dominio (ver [seriesStateFor]). */
    fun state(): SeriesState = seriesStateFor(
        releasedEpisodes = releasedEpisodes,
        totalEpisodes = totalEpisodes,
        seriesStatus = seriesStatus,
        inProduction = inProduction,
        watchedCount = watchedCount,
        pendingFuture = isPendingInSpain()
    )

    fun isCaughtUp(): Boolean = state() != SeriesState.EN_CURSO

    fun isFinished(): Boolean = state() == SeriesState.TERMINADA
}
