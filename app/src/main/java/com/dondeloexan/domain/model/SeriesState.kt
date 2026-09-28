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
    watchedCount: Int
): SeriesState {
    val hasFuture = hasFutureSeasonsFor(seriesStatus, inProduction)
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
