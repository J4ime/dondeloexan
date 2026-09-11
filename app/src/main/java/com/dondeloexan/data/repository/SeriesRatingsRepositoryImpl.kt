package com.dondeloexan.data.repository

import com.dondeloexan.data.remote.api.SeriesGraphApi
import com.dondeloexan.domain.model.EpisodeRating
import com.dondeloexan.domain.repository.SeriesRatingsRepository
import com.dondeloexan.util.AppLogger
import java.util.concurrent.ConcurrentHashMap

class SeriesRatingsRepositoryImpl(
    private val api: SeriesGraphApi
) : SeriesRatingsRepository {

    private val cache = ConcurrentHashMap<Int, List<EpisodeRating>>()

    override suspend fun getEpisodeRatings(tmdbId: Int): List<EpisodeRating> {
        cache[tmdbId]?.let { return it }
        return try {
            val seasons = api.getSeasonRatings(tmdbId)
            val ratings = seasons.flatMap { season ->
                season.episodes.map { ep ->
                    EpisodeRating(
                        seasonNumber = if (ep.seasonNumber > 0) ep.seasonNumber else season.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        name = ep.name,
                        airDate = ep.airDate,
                        imdbRating = ep.imdbRating,
                        imdbVotes = ep.imdbVotes
                    )
                }
            }
            if (ratings.isNotEmpty()) cache[tmdbId] = ratings
            ratings
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("SeriesRatings", "getEpisodeRatings error para show/$tmdbId", e)
            emptyList()
        }
    }
}
