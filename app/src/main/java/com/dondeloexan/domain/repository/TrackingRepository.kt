package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.detail.EpisodeRef
import com.dondeloexan.domain.model.detail.MovieWatchState
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking

/**
 * Repositorio del seguimiento (tracking) de la biblioteca del usuario:
 * estado de películas, progreso de series capítulo a capítulo y la
 * reconciliación de la clasificación ("en curso" / "al día" / "terminada").
 */
interface TrackingRepository {
    suspend fun getMovieWatchState(content: Content): MovieWatchState
    suspend fun setMovieWatched(content: Content, watched: Boolean): MovieWatchState
    suspend fun setMovieFavorite(content: Content, favorite: Boolean): MovieWatchState
    suspend fun addMovieToLibrary(content: Content): MovieWatchState

    suspend fun addSeriesToLibrary(content: Content): Boolean
    suspend fun setSeriesWatched(content: Content, watched: Boolean): Boolean

    /**
     * Marca/desmarca una serie como vista partiendo de su id local (lo usa el
     * listado, que no tiene un [Content] a mano). Es la MISMA ruta que
     * [setSeriesWatched]: marca todos los capítulos emitidos de todas las
     * temporadas y recalcula el estado desde el último capítulo marcado.
     */
    suspend fun setSeriesWatchedById(tvShowId: Long, watched: Boolean): Boolean

    suspend fun setSeriesFavorite(content: Content, favorite: Boolean): Boolean
    suspend fun getSeriesTracking(content: Content): SeriesTracking

    suspend fun getSeasons(content: Content): List<Season>
    suspend fun getSeasonDetail(content: Content, seasonNumber: Int): SeasonDetail

    suspend fun recordEpisode(content: Content, season: Int, episode: Int): SeriesTracking
    suspend fun unrecordEpisode(content: Content, season: Int, episode: Int): SeriesTracking
    suspend fun recordEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking

    /**
     * Registra de una sola vez capítulos de varias temporadas (cascada que
     * abarca también las temporadas anteriores a la seleccionada) y recalcula
     * el estado una única vez.
     */
    suspend fun recordEpisodes(content: Content, entries: List<EpisodeRef>): SeriesTracking

    suspend fun unrecordSeasonEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking

    suspend fun markSeriesFinished(content: Content): Boolean
    suspend fun clearSeriesFinished(content: Content)
    suspend fun reconcileAllLibrarySeries()
}
