package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.UserPlatformDao
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
import kotlin.math.log10

class DiscoverRepositoryImpl(
    private val tmdbApi: TmdbApi,
    private val omdbApi: OmdbApi,
    private val userPlatformDao: UserPlatformDao,
    private val movieDao: MovieDao,
    private val tvShowDao: TvShowDao
) : DiscoverRepository {

    override suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)

        try {
            val tmdbResult = tmdbApi.searchMulti(query)

            val previews = tmdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { it.toContentPreview() }
                .sortedByDescending {
                    log10((it.voteCount ?: 0).toDouble() + 1.0) * WEIGHT_POPULARITY
                }
                .take(20)

            val withImdbRatings = enrichWithImdbRatings(previews)
            val withPlatforms = attachPlatformsToPreviews(withImdbRatings)
            emit(DataResult.Success(withPlatforms))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
        }
    }

    companion object {
        private const val WEIGHT_POPULARITY = 5.0
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

    override suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>> = flow {
        emit(DataResult.Loading)
        try {
            val trending = tmdbApi.getTrending()
            val previews = trending.results
                .filter { it.mediaType in listOf("movie", "tv") }
                .map { it.toContentPreview() }
            val withImdbRatings = enrichWithImdbRatings(previews)
            val withPlatforms = attachPlatformsToPreviews(withImdbRatings)
            emit(DataResult.Success(withPlatforms))
        } catch (e: Exception) {
            emit(DataResult.Error(e))
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

    private suspend fun attachPlatformsToPreviews(previews: List<ContentPreview>): List<ContentPreview> {
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

    private suspend fun enrichWithImdbRatings(previews: List<ContentPreview>): List<ContentPreview> {
        return coroutineScope {
            previews.map { preview ->
                async {
                    try {
                        val omdb = omdbApi.getByTitle(preview.title, year = preview.year)
                        val rating = omdb.imdbRating?.toFloatOrNull()
                        if (rating != null) preview.copy(ratingImdb = rating) else preview
                    } catch (_: Exception) { preview }
                }
            }.map { it.await() }
        }
    }
}
