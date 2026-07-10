package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.remote.api.FilmAffinityApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DiscoverRepositoryImpl(
    private val filmAffinityApi: FilmAffinityApi,
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao
) : DiscoverRepository {

    override suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)

        try {
            val faResult = filmAffinityApi.search(query)
            val tmdbResult = tmdbApi.searchMulti(query)

            val faPreviews = faResult.results.map { it.toContentPreview() }
            val tmdbPreviews = tmdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") }
                .map { it.toContentPreview() }

            val merged = (faPreviews + tmdbPreviews)
                .distinctBy { it.id }
                .take(20)

            emit(DataResult.Success(merged))
        } catch (e: Exception) {
            try {
                val tmdbResult = tmdbApi.searchMulti(query)
                val previews = tmdbResult.results
                    .filter { it.mediaType in listOf("movie", "tv") }
                    .map { it.toContentPreview() }
                emit(DataResult.Success(previews.take(20)))
            } catch (fallback: Exception) {
                emit(DataResult.Error(fallback))
            }
        }
    }

    override suspend fun getDetail(contentId: String): Flow<DataResult<Content>> = flow {
        emit(DataResult.Loading)

        try {
            val content = when {
                contentId.startsWith("fa-") -> fetchFilmAffinityDetail(contentId)
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

    private suspend fun fetchFilmAffinityDetail(id: String): Content = coroutineScope {
        val faId = id.removePrefix("fa-")

        val faUrl = try {
            val searchResult = filmAffinityApi.search(faId)
            searchResult.results.firstOrNull()?.faUrl
        } catch (_: Exception) { null }

        val faItem = if (faUrl != null) {
            filmAffinityApi.getItemDetail(faUrl)
        } else {
            filmAffinityApi.getItemDetail(faId)
        }

        val faToTmdb = async {
            try {
                val tmdbSearch = tmdbApi.searchMulti(faItem.title?.local ?: "")
                tmdbSearch.results.firstOrNull { it.mediaType == "movie" || it.mediaType == "tv" }
            } catch (_: Exception) { null }
        }

        val tmdbMatch = faToTmdb.await()

        var platforms = emptyList<StreamingAvailability>()
        if (tmdbMatch != null) {
            try {
                val providerResponse = if (tmdbMatch.mediaType == "tv") {
                    tmdbApi.getTvWatchProviders(tmdbMatch.id)
                } else {
                    tmdbApi.getMovieWatchProviders(tmdbMatch.id)
                }
                platforms = providerResponse.results["ES"]?.toStreamingAvailability().orEmpty()
            } catch (_: Exception) { }
        }

        faItem.toDomain(platforms = platforms)
    }

    private suspend fun fetchTmdbDetail(id: String): Content {
        val tmdbId = id.removePrefix("tmdb-").toInt()

        val movieResult = try {
            val movie = tmdbApi.getMovieDetail(tmdbId)
            val credits = tmdbApi.getMovieCredits(tmdbId)
            val providers = tmdbApi.getMovieWatchProviders(tmdbId)
            val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()

            val omdbRatings = movie.imdbId?.let { imdbId ->
                try { omdbApi.getByImdbId(imdbId) } catch (_: Exception) { null }
            }

            val content = movie.toDomain(omdbRatings, platforms, credits)
            val enriched = if (movie.imdbId != null) {
                try {
                    val omdb = omdbApi.getByImdbId(movie.imdbId)
                    content.copy(
                        ratingImdb = omdb.imdbRating?.toFloatOrNull(),
                        ratingRt = omdb.ratings?.find { it.source == "Rotten Tomatoes" }
                            ?.value?.removeSuffix("%")?.toIntOrNull(),
                        ratingMetacritic = omdb.metascore?.toIntOrNull()
                    )
                } catch (_: Exception) { content }
            } else content

            enriched
        } catch (_: Exception) { null }

        if (movieResult != null) return movieResult

        val tvResult = try {
            val tv = tmdbApi.getTvDetail(tmdbId)
            val credits = tmdbApi.getTvCredits(tmdbId)
            val providers = tmdbApi.getTvWatchProviders(tmdbId)
            val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()

            tv.toDomain(null, platforms, credits)
        } catch (_: Exception) { null }

        if (tvResult != null) return tvResult

        throw IllegalArgumentException("Content not found: $id")
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
