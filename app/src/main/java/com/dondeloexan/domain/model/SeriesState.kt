package com.dondeloexan.domain.model

/**
 * Regla ÚNICA de clasificación de una serie en "En curso", "Al día (agenda)" o
 * "Terminada". Antes existían tres copias divergentes (la entidad Room, el
 * [SeriesItem] de dominio y la tarjeta de la biblioteca); todas delegan aquí.
 *
 * El "al día" se computa siempre contra los capítulos EMITIDOS
 * (`releasedEpisodes`) y no contra el total de toda la serie (que incluye
 * temporadas futuras). Cuando no hay dato fiable de emitidos:
 *   - si la serie no tiene futuro (terminada/cancelada) se usa el total (todo lo
 *     emitido = todo), por ejemplo miniseries de un tirón;
 *   - si tiene futuro, se considera "no al día" para que no se cuele en
 *     Agenda/Terminada con datos incompletos: el refresco aportará los emitidos
 *     y la recolocará.
 */
enum class SeriesState {
    EN_CURSO,
    AL_DIA,
    TERMINADA
}

fun hasFutureSeasonsFor(seriesStatus: String?, inProduction: Boolean?): Boolean = when (seriesStatus) {
    "Ended", "Canceled" -> false
    null -> inProduction != false
    else -> true
}

fun seriesStateFor(
    releasedEpisodes: Int?,
    totalEpisodes: Int?,
    seriesStatus: String?,
    inProduction: Boolean?,
    watchedCount: Int,
    /** true si hay una temporada pendiente de estreno en España. */
    pendingFuture: Boolean = false
): SeriesState {
    val hasFuture = hasFutureSeasonsFor(seriesStatus, inProduction) || pendingFuture
    val caughtUp = if (releasedEpisodes != null) {
        releasedEpisodes > 0 && watchedCount >= releasedEpisodes
    } else {
        val total = totalEpisodes
        total != null && !hasFuture && total > 0 && watchedCount >= total
    }
    return when {
        !caughtUp -> SeriesState.EN_CURSO
        hasFuture -> SeriesState.AL_DIA
        else -> SeriesState.TERMINADA
    }
}

/**
 * Regla ÚNICA del badge "Capítulo final": solo aparece cuando la serie **ha
 * terminado** y el usuario **ha visto todos sus capítulos**.
 *
 * No basta con que quede un próximo capítulo en TMDB ni con que la serie esté
 * "al día": mientras quede algún capítulo sin estrenar ([releasedEpisodes] por
 * debajo del total) o sin ver, el badge no se pinta. Así una serie con una
 * temporada pendiente de estreno en España (Babylon Berlin T5) no lo muestra.
 */
fun isFinalEpisodeWatched(
    releasedEpisodes: Int?,
    totalEpisodes: Int?,
    watchedCount: Int,
    /** Hay temporadas futuras, incluida una pendiente de estreno en España. */
    hasFuture: Boolean
): Boolean {
    if (hasFuture) return false
    val released = releasedEpisodes ?: totalEpisodes ?: return false
    val total = totalEpisodes ?: released
    if (total <= 0) return false
    return released >= total && watchedCount >= released
}
