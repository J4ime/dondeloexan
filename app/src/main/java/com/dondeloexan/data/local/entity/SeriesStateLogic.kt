package com.dondeloexan.data.local.entity

/**
 * Criterio único para clasificar una serie en "En curso", "Al día (agenda)" o
 * "Terminada", compartido entre el ViewModel de listados y la reconciliación
 * del repositorio (para que ambos decidan lo mismo).
 *
 * El "al día" se computa siempre contra los capítulos EMITIDOS
 * ([TvShowEntity.releasedEpisodes]) y no contra el total de toda la serie
 * (que incluye temporadas futuras). Cuando no hay dato fiable de emitidos:
 *   - si la serie no tiene futuro (terminada/cancelada) se usa el total (todo lo
 *     emitido = todo), por ejemplo miniseries de un tirón;
 *   - si tiene futuro, se considera "no al día" para que no se cuele en
 *     Agenda/Terminada con datos incompletos: el refresco aportará los emitidos
 *     y la recolocará.
 */
fun TvShowEntity.hasFutureSeasons(): Boolean = when (seriesStatus) {
    "Ended", "Canceled" -> false
    null -> inProduction != false
    else -> true
}

private fun TvShowEntity.alDayBy(watchedCount: Int, aired: Int): Boolean =
    aired > 0 && watchedCount >= aired

fun TvShowEntity.isCaughtUpBy(watchedCount: Int): Boolean {
    val released = releasedEpisodes
    if (released != null) return alDayBy(watchedCount, released)
    val total = totalEpisodes ?: return false
    if (!hasFutureSeasons()) return alDayBy(watchedCount, total)
    return false
}

fun TvShowEntity.isFinishedBy(watchedCount: Int): Boolean {
    if (!isCaughtUpBy(watchedCount)) return false
    return !hasFutureSeasons()
}

/** Render del estado para logs/tests. */
fun TvShowEntity.stateLabel(watchedCount: Int): String = when {
    !isCaughtUpBy(watchedCount) -> "EN_CURSO"
    hasFutureSeasons() -> "AL_DIA"
    else -> "TERMINADA"
}
