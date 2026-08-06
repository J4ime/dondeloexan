package com.dondeloexan.domain.repository

import com.dondeloexan.data.remote.dto.TmdbCompanySearchResult
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResult
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.detail.CastSocialInfo
import com.dondeloexan.domain.model.detail.MovieWatchState
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import kotlinx.coroutines.flow.Flow

interface DiscoverRepository {
    suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>>
    suspend fun search(query: String, page: Int): Flow<DataResult<List<ContentPreview>>>
    suspend fun getDetail(contentId: String, contentType: ContentType = ContentType.MOVIE): Flow<DataResult<Content>>
    suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>>
    suspend fun getTrending(page: Int): Flow<DataResult<List<ContentPreview>>>
    suspend fun fetchTrendingPage(page: Int, filterByPlatforms: Boolean = true): List<ContentPreview>
    suspend fun fetchSearchPage(query: String, page: Int): List<ContentPreview>
    suspend fun resolveTmdbId(imdbId: String, type: ContentType): Int?
    suspend fun searchPeople(query: String): List<TmdbPersonSearchResult>
    suspend fun searchCompanies(query: String): List<TmdbCompanySearchResult>
    suspend fun getPersonMovieCredits(personId: Int): List<ContentPreview>
    suspend fun getPersonTvCredits(personId: Int): List<ContentPreview>
    suspend fun getDirectorTopMovies(directorId: Int, excludeTmdbId: Int? = null): List<ContentPreview>
    suspend fun getCompanyMovies(companyId: Int): List<ContentPreview>
    suspend fun getCompanyTvShows(companyId: Int): List<ContentPreview>
    suspend fun fetchPlatforms(previews: List<ContentPreview>): List<ContentPreview>
    suspend fun getCriticReviews(contentId: String, title: String, year: Int? = null): List<CriticReview>
    suspend fun getCollectionMovies(collectionId: Int): List<ContentPreview>
    suspend fun getRecommendations(contentId: String, contentType: ContentType = ContentType.MOVIE): List<ContentPreview>
    suspend fun getSeriesRelationships(wikidataId: String? = null, imdbId: String? = null): Pair<List<ContentPreview>, Set<String>>
    suspend fun getFaMovieData(contentId: String, title: String, year: Int? = null): Pair<Float?, List<PlatformReleaseDate>>

    // --- Detail & tracking (migrado desde MediaDetailViewModel) ---
    suspend fun getMovieWatchState(content: Content): MovieWatchState
    suspend fun setMovieWatched(content: Content, watched: Boolean): MovieWatchState
    suspend fun setMovieFavorite(content: Content, favorite: Boolean): MovieWatchState

    suspend fun getSeriesTracking(content: Content): SeriesTracking
    suspend fun getSeasons(content: Content): List<Season>
    suspend fun getSeasonDetail(content: Content, seasonNumber: Int): SeasonDetail

    suspend fun recordEpisode(content: Content, season: Int, episode: Int): SeriesTracking
    suspend fun unrecordEpisode(content: Content, season: Int, episode: Int): SeriesTracking
    suspend fun recordEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking
    suspend fun unrecordSeasonEpisodes(content: Content, season: Int, episodes: List<Int>): SeriesTracking

    suspend fun markSeriesFinished(content: Content): Boolean
    suspend fun clearSeriesFinished(content: Content)

    suspend fun getPersonSocialInfo(personId: Int): CastSocialInfo?
    suspend fun getFaId(content: Content): Int?
}
