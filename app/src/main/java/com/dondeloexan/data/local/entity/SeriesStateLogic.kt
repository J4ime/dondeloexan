package com.dondeloexan.data.local.entity

import com.dondeloexan.domain.model.SeriesState
import com.dondeloexan.domain.model.hasFutureSeasonsFor
import com.dondeloexan.domain.model.seriesStateFor

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
fun TvShowEntity.hasFutureSeasons(): Boolean = hasFutureSeasonsFor(seriesStatus, inProduction)

/** Estado de la serie según la regla única de dominio. */
fun TvShowEntity.seriesStateBy(watchedCount: Int): SeriesState = seriesStateFor(
    releasedEpisodes = releasedEpisodes,
    totalEpisodes = totalEpisodes,
    seriesStatus = seriesStatus,
    inProduction = inProduction,
    watchedCount = watchedCount
)

fun TvShowEntity.isCaughtUpBy(watchedCount: Int): Boolean =
    seriesStateBy(watchedCount) != SeriesState.EN_CURSO

fun TvShowEntity.isFinishedBy(watchedCount: Int): Boolean =
    seriesStateBy(watchedCount) == SeriesState.TERMINADA

/** Render del estado para logs/tests. */
fun TvShowEntity.stateLabel(watchedCount: Int): String = seriesStateBy(watchedCount).name
