package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.dto.TmdbTvDetailDto

/**
 * Cálculo ÚNICO de capítulos emitidos a partir del detalle de TMDB.
 *
 * [excludeSeason] permite no contar la temporada cuyo estreno en España todavía
 * no se ha producido (TMDB solo publica la fecha original de emisión).
 */
fun TmdbTvDetailDto.calculateReleasedEpisodes(excludeSeason: Int? = null): Int? {
    val last = lastEpisodeToAir
    val seasonList = seasons
    if (last != null && seasonList != null) {
        return seasonList
            .filter { it.seasonNumber > 0 && it.seasonNumber != excludeSeason }
            .sumOf { season ->
                when {
                    season.seasonNumber < last.seasonNumber -> season.episodeCount
                    season.seasonNumber == last.seasonNumber -> last.episodeNumber
                    else -> 0
                }
            }
    }
    return numberOfEpisodes
}
