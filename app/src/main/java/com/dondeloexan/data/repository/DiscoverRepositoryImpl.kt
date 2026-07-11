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
import com.dondeloexan.domain.model.ContentSource
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
            val (tmdbResult, faResult) = coroutineScope {
                val tmdbDeferred = async { tmdbApi.searchMulti(query) }
                val faDeferred = async {
                    try { filmAffinityApi.search(query) } catch (_: Exception) { null }
                }
                Pair(tmdbDeferred.await(), faDeferred.await())
            }

            val faMap = faResult?.results?.mapNotNull { item: com.dondeloexan.data.remote.dto.FaRapidSearchItem ->
                val key = buildString {
                    append(item.title.trim().lowercase())
                    append('|')
                    item.year?.let { append(it) }
                }
                key to Pair(item.rating, item.id)
            }?.toMap() ?: emptyMap()

            val previews = tmdbResult.results
                .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                .map { tmdb: com.dondeloexan.data.remote.dto.TmdbMultiSearchResult ->
                    val tmdbTitle = (tmdb.title ?: tmdb.name).orEmpty().trim().lowercase()
                    val tmdbYear = tmdb.releaseDate?.substringBefore("-")?.toIntOrNull()
                        ?: tmdb.firstAirDate?.substringBefore("-")?.toIntOrNull()
                    val key = "$tmdbTitle|$tmdbYear"
                    val faMatch = faMap[key]
                    tmdb.toContentPreview(faRating = faMatch?.first, faId = faMatch?.second)
                }
                .sortedByDescending {
                    val ratingScore = (it.ratingFa?.toDouble() ?: 0.0) * 10.0
                    val popularityScore = log10((it.voteCount ?: 0).toDouble() + 1.0) * WEIGHT_POPULARITY
                    ratingScore + popularityScore
                }
                .take(20)

            val withPlatforms = attachPlatformsToPreviews(previews)
            emit(DataResult.Success(withPlatforms))
        } catch (e: Exception) {
            try {
                val tmdbResult = tmdbApi.searchMulti(query)
                val previews = tmdbResult.results
                    .filter { it.mediaType in listOf("movie", "tv") && !it.adult }
                    .map { it.toContentPreview() }
                    .sortedByDescending {
                        val ratingScore = (it.ratingFa?.toDouble() ?: 0.0) * 10.0
                        val popularityScore = log10((it.voteCount ?: 0).toDouble() + 1.0) * WEIGHT_POPULARITY
                        ratingScore + popularityScore
                    }
                emit(DataResult.Success(previews.take(20)))
            } catch (fallback: Exception) {
                emit(DataResult.Error(fallback))
            }
        }
    }

    companion object {
        private const val WEIGHT_POPULARITY = 5.0
    }

    override suspend fun getDetail(contentId: String, contentType: ContentType): Flow<DataResult<Content>> = flow {
        emit(DataResult.Loading)

        try {
            val cachedTitle = when (contentType) {
                ContentType.MOVIE -> movieDao.getByContentId(contentId)?.title
                ContentType.SERIES -> tvShowDao.getByContentId(contentId)?.title
            }

            val content = when {
                contentId.startsWith("fa-") -> fetchFilmAffinityDetail(contentId)
                contentId.startsWith("tmdb-") -> fetchTmdbDetail(contentId, contentType, cachedTitle)
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
            val withPlatforms = attachPlatformsToPreviews(previews)
            emit(DataResult.Success(withPlatforms))
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

    private suspend fun fetchTmdbDetail(
        id: String,
        contentType: ContentType = ContentType.MOVIE,
        cachedTitle: String? = null
    ): Content {
        val tmdbId = id.removePrefix("tmdb-").toInt()

        return try {
            if (contentType == ContentType.SERIES) {
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
        } catch (e: Exception) {
            fallbackTmdbDetail(id, contentType, cachedTitle) ?: throw e
        }
    }

    private suspend fun fallbackTmdbDetail(
        id: String,
        contentType: ContentType,
        title: String?
    ): Content? {
        if (title == null) return null

        return try {
            val searchResult = filmAffinityApi.search(title)
            val faItem = searchResult.results.firstOrNull { item ->
                val itemType = when (item.type?.lowercase()) {
                    "series" -> ContentType.SERIES
                    "movie" -> ContentType.MOVIE
                    else -> contentType
                }
                itemType == contentType
            } ?: searchResult.results.firstOrNull() ?: return null

            val detail = filmAffinityApi.getItemDetail(faItem.faUrl ?: faItem.id.toString())

            var platforms = emptyList<StreamingAvailability>()
            try {
                val tmdbSearch = tmdbApi.searchMulti(title)
                val tmdbMatch = tmdbSearch.results.firstOrNull {
                    it.mediaType == if (contentType == ContentType.SERIES) "tv" else "movie"
                }
                if (tmdbMatch != null) {
                    val providerResponse = if (contentType == ContentType.SERIES) {
                        tmdbApi.getTvWatchProviders(tmdbMatch.id)
                    } else {
                        tmdbApi.getMovieWatchProviders(tmdbMatch.id)
                    }
                    platforms = providerResponse.results["ES"]?.toStreamingAvailability().orEmpty()
                }
            } catch (_: Exception) { }

            detail.toDomain(platforms = platforms).copy(
                id = id,
                source = ContentSource.HYBRID,
                tmdbId = id.removePrefix("tmdb-").toIntOrNull()
            )
        } catch (_: Exception) { null }
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
}
