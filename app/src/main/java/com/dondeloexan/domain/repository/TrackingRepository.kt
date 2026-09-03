package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.Content
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
    suspend fun setSeriesFavorite(content: Content, favorite: Boolean): Boolean
    suspend fun getSeriesTracking(content: Content): SeriesTracking

    suspend fun getSeasons(content: Content): List<Season>
    suspend fun getSeasonDetail(content: Content, seasonNumber: Int): SeasonDetail

    suspend fun recordEpisode(content: Content, season: Int, episode: Int): SeriesTracking
    suspend fun unrecordEpisode(content: Content, season: Int, episode: Int): SeriesTracking
    suspend fun recordEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking
    suspend fun unrecordSeasonEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking

    suspend fun markSeriesFinished(content: Content): Boolean
    suspend fun clearSeriesFinished(content: Content)
    suspend fun reconcileAllLibrarySeries()
}
