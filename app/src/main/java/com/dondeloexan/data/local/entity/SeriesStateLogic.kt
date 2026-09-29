package com.dondeloexan.data.local.entity

import com.dondeloexan.domain.model.SeriesState
import com.dondeloexan.domain.model.hasFutureSeasonsFor
import com.dondeloexan.domain.model.seriesStateFor
import java.time.LocalDate

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
fun TvShowEntity.hasFutureSeasons(): Boolean =
    hasFutureSeasonsFor(seriesStatus, inProduction) || pendingSpanishSeason() != null

/**
 * Temporada que NO debe contar como emitida porque su estreno en España sigue
 * en el futuro ([spanishReleaseDate], obtenida de Filmaffinity o de la búsqueda
 * web). Devuelve null cuando ya se estrenó (o cuando no hay dato).
 */
fun TvShowEntity.pendingSpanishSeason(today: LocalDate = LocalDate.now()): Int? =
    com.dondeloexan.domain.model.SpanishReleaseInfo.pendingSpanishReleaseSeason(
        season = pendingEsSeason,
        isoDate = spanishReleaseDate,
        today = today
    )

/** Estado de la serie según la regla única de dominio. */
fun TvShowEntity.seriesStateBy(watchedCount: Int): SeriesState = seriesStateFor(
    releasedEpisodes = releasedEpisodes,
    totalEpisodes = totalEpisodes,
    seriesStatus = seriesStatus,
    inProduction = inProduction,
    watchedCount = watchedCount,
    pendingFuture = pendingSpanishSeason() != null
)

fun TvShowEntity.isCaughtUpBy(watchedCount: Int): Boolean =
    seriesStateBy(watchedCount) != SeriesState.EN_CURSO

fun TvShowEntity.isFinishedBy(watchedCount: Int): Boolean =
    seriesStateBy(watchedCount) == SeriesState.TERMINADA

/** Render del estado para logs/tests. */
fun TvShowEntity.stateLabel(watchedCount: Int): String = seriesStateBy(watchedCount).name
