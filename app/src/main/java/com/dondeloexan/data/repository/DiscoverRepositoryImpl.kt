package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.repository.DiscoverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DiscoverRepositoryImpl(
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val userPlatformDao: UserPlatformDao
) : DiscoverRepository {

    override suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)

        try {
            val tmdbResult = tmdbApi.searchMulti(query)
            val previews = tmdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") }
                .map { it.toContentPreview() }
            emit(DataResult.Success(previews.take(20)))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }

    override suspend fun getDetail(contentId: String): Flow<DataResult<Content>> = flow {
        emit(DataResult.Loading)

        try {
            val content = when {
                contentId.startsWith("tmdb-") -> fetchTmdbDetail(contentId)
                else -> throw IllegalArgumentException("Unknown content ID: $contentId")
            }

            val activePlatforms = userPlatformDao.getActiveNames().toSet()
            val prioritized = prioritizePlatforms(content, activePlatforms)

            emit(DataResult.Success(prioritized))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }

    override suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)
        try {
            val trending = tmdbApi.getTrending()
            val previews = trending.results
                .filter { it.mediaType in listOf("movie", "tv") }
                .map { it.toContentPreview() }
            emit(DataResult.Success(previews))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }

    private suspend fun fetchTmdbDetail(id: String): Content {
        val tmdbId = id.removePrefix("tmdb-").toInt()
        val movie = tmdbApi.getMovieDetail(tmdbId)
        val providers = tmdbApi.getMovieWatchProviders(tmdbId)
        val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()

        val omdbRatings = movie.imdbId?.let { imdbId ->
            try { omdbApi.getByImdbId(imdbId) } catch (_: Exception) { null }
        }

        return movie.toDomain(omdbRatings, platforms)
    }

    private fun prioritizePlatforms(content: Content, userPlatforms: Set<String>): Content {
        if (userPlatforms.isEmpty()) return content
        val (active, others) = content.streamingPlatforms.partition { platform ->
            userPlatforms.any { userP ->
                platform.platformName.contains(userP, ignoreCase = true)
            }
        }
        return content.copy(streamingPlatforms = active + others)
    }
}
