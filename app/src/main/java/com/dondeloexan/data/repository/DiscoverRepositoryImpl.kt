package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toContentPreview
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.data.remote.mapper.toStreamingAvailability as imdbToStreaming
import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.repository.DiscoverRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DiscoverRepositoryImpl(
    private val imdbApi: BalloonerismmApi,
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao
) : DiscoverRepository {

    override suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>> = search(query, 1)

    override suspend fun search(query: String, page: Int): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)

        try {
            val imdbResult = imdbApi.searchMulti(query, page = page)
            val previews = imdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { it.toContentPreview() }
                .take(20)

            if (previews.isNotEmpty()) {
                val withPlatforms = attachImdbPlatforms(previews)
                emit(DataResult.Success(withPlatforms))
            } else {
                val tmdbResult = tmdbApi.searchMulti(query)
                val tmdbPreviews = tmdbResult.results
                    .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            }
        } catch (e: Exception) {
            try {
                val tmdbResult = tmdbApi.searchMulti(query)
                val tmdbPreviews = tmdbResult.results
                    .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            } catch (fallback: Exception) {
                emit(DataResult.Error(e))
            }
        }
    }

    override suspend fun getDetail(contentId: String, contentType: ContentType): Flow<DataResult<Content>> = flow {
        emit(DataResult.Loading)

        try {
            val content = when {
                contentId.startsWith("tmdb-") -> fetchTmdbDetail(contentId, contentType)
                else -> throw IllegalArgumentException("Unknown content ID: $contentId")
            }

            val activePlatforms = userPlatformDao.getActiveNames().toSet()
            val prioritized = prioritizePlatforms(content, activePlatforms)
            emit(DataResult.Success(prioritized))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }

    override suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>> = getTrending(1)

    override suspend fun getTrending(page: Int): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)
        try {
            val imdbResult = imdbApi.popularAll(page = page)
            val previews = imdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { it.toContentPreview() }
                .take(20)

            if (previews.isNotEmpty()) {
                val withPlatforms = attachImdbPlatforms(previews)
                emit(DataResult.Success(withPlatforms))
            } else {
                val tmdbTrending = tmdbApi.getTrending()
                val tmdbPreviews = tmdbTrending.results
                    .filter { it.mediaType in listOf("movie", "tv") }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            }
        } catch (e: Exception) {
            try {
                val tmdbTrending = tmdbApi.getTrending()
                val tmdbPreviews = tmdbTrending.results
                    .filter { it.mediaType in listOf("movie", "tv") }
                    .map { it.toContentPreview() }
                    .take(20)
                val withPlatforms = attachTmbdPlatforms(tmdbPreviews)
                emit(DataResult.Success(withPlatforms))
            } catch (fallback: Exception) {
                emit(DataResult.Error(e))
            }
        }
    }

    private suspend fun fetchTmdbDetail(
        id: String,
        contentType: ContentType = ContentType.MOVIE
    ): Content {
        val tmdbId = id.removePrefix("tmdb-").toInt()

        return if (contentType == ContentType.SERIES) {
            val tv = tmdbApi.getTvDetail(tmdbId)
            val credits = tmdbApi.getTvCredits(tmdbId)
            val providers = tmdbApi.getTvWatchProviders(tmdbId)
            val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()

            val existing = tvShowDao.getByContentId("tmdb-$tmdbId")
            if (existing != null) {
                tvShowDao.update(
                    existing.copy(
                        totalEpisodes = tv.numberOfEpisodes ?: existing.totalEpisodes,
                        nextEpisodeAirDate = tv.nextEpisodeToAir?.airDate,
                        nextEpisodeNumber = tv.nextEpisodeToAir?.episodeNumber,
                        nextEpisodeSeasonNumber = tv.nextEpisodeToAir?.seasonNumber,
                        seriesStatus = tv.status,
                        inProduction = tv.inProduction,
                        numberOfSeasons = tv.numberOfSeasons
                    )
                )
            }

            tv.toDomain(null, platforms, credits)
        } else {
            val movie = tmdbApi.getMovieDetail(tmdbId)
            val credits = tmdbApi.getMovieCredits(tmdbId)
            val providers = tmdbApi.getMovieWatchProviders(tmdbId)
            val platforms = providers.results["ES"]?.toStreamingAvailability().orEmpty()

            val omdbRatings = movie.imdbId?.let { imdbId ->
                try { omdbApi.getByImdbId(imdbId) } catch (_: Exception) { null }
            }

            val content = movie.toDomain(omdbRatings, platforms, credits)
            if (movie.imdbId != null) {
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
        }
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

    private suspend fun attachImdbPlatforms(previews: List<ContentPreview>): List<ContentPreview> {
        return coroutineScope {
            previews.map { preview ->
                async {
                    val platforms = try {
                        val imdbId = preview.id.removePrefix("imdb-")
                        val providerResponse = if (preview.type == ContentType.SERIES) {
                            imdbApi.getTvWatchProviders(imdbId)
                        } else {
                            imdbApi.getMovieWatchProviders(imdbId)
                        }
                        providerResponse.results["ES"]?.imdbToStreaming().orEmpty()
                    } catch (_: Exception) {
                        tryFetchTmbdPlatforms(preview)
                    }
                    preview.copy(streamingPlatforms = platforms)
                }
            }.map { it.await() }
        }
    }

    private suspend fun attachTmbdPlatforms(previews: List<ContentPreview>): List<ContentPreview> {
        return coroutineScope {
            previews.map { preview ->
                async {
                    val platforms = try {
                        val tmdbId = preview.tmdbId ?: return@async preview
                        val providerResponse = if (preview.type == ContentType.SERIES) {
                            tmdbApi.getTvWatchProviders(tmdbId)
                        } else {
                            tmdbApi.getMovieWatchProviders(tmdbId)
                        }
                        providerResponse.results["ES"]?.toStreamingAvailability().orEmpty()
                    } catch (_: Exception) { emptyList<StreamingAvailability>() }
                    preview.copy(streamingPlatforms = platforms)
                }
            }.map { it.await() }
        }
    }

    private suspend fun tryFetchTmbdPlatforms(preview: ContentPreview): List<StreamingAvailability> {
        return try {
            val tmdbSearch = tmdbApi.searchMulti(preview.title)
            val match = tmdbSearch.results.firstOrNull {
                it.mediaType == if (preview.type == ContentType.SERIES) "tv" else "movie"
            }
            if (match != null) {
                val providerResponse = if (preview.type == ContentType.SERIES) {
                    tmdbApi.getTvWatchProviders(match.id)
                } else {
                    tmdbApi.getMovieWatchProviders(match.id)
                }
                providerResponse.results["ES"]?.toStreamingAvailability().orEmpty()
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
