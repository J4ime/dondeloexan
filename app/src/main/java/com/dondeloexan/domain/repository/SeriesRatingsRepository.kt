package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.EpisodeRating

interface SeriesRatingsRepository {
    suspend fun getEpisodeRatings(tmdbId: Int): List<EpisodeRating>
}
