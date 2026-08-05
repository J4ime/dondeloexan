package com.dondeloexan.domain.usecase

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.detail.CascadeProposal
import com.dondeloexan.domain.model.detail.CastSocialInfo
import com.dondeloexan.domain.model.detail.EpisodeToggleResult
import com.dondeloexan.domain.model.detail.MovieWatchState
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import com.dondeloexan.domain.repository.DiscoverRepository
import kotlinx.coroutines.flow.Flow

data class SeriesState(
    val seasons: List<Season>,
    val tracking: SeriesTracking,
    val selectedSeason: Int,
    val seasonDetail: SeasonDetail?
)

class MediaDetailUseCases(
    private val repository: DiscoverRepository
) {

    suspend fun getDetail(contentId: String, contentType: ContentType = ContentType.MOVIE): Flow<DataResult<Content>> =
        repository.getDetail(contentId, contentType)

    suspend fun getCriticReviews(content: Content): List<CriticReview> =
        repository.getCriticReviews(content.id, content.originalTitle ?: content.title, content.year)

    suspend fun getFaMovieData(content: Content): Pair<Float?, List<PlatformReleaseDate>> =
        repository.getFaMovieData(content.id, content.originalTitle ?: content.title, content.year)

    suspend fun getFaId(content: Content): Int? = repository.getFaId(content)

    suspend fun getCollectionMovies(content: Content): List<ContentPreview> {
        val collectionId = content.collectionTmdbId ?: return emptyList()
        return repository.getCollectionMovies(collectionId).filter { it.tmdbId != content.tmdbId }
    }

    suspend fun getSimilar(content: Content): List<ContentPreview> =
        repository.getRecommendations(content.id, content.type)

    suspend fun getSeriesRelationships(content: Content): Pair<List<ContentPreview>, Set<String>> {
        val wikidataId = content.externalLinks?.wikidataId
        val imdbId = content.externalLinks?.imdbId ?: content.imdbId
        if (wikidataId == null && imdbId == null) return emptyList<ContentPreview>() to emptySet()
        return repository.getSeriesRelationships(wikidataId, imdbId)
    }

    suspend fun getPersonSocialInfo(personId: Int): CastSocialInfo? =
        repository.getPersonSocialInfo(personId)

    // --- Movie state ---

    suspend fun loadMovieState(content: Content): MovieWatchState =
        repository.getMovieWatchState(content)

    suspend fun toggleMovieWatched(content: Content): MovieWatchState =
        repository.setMovieWatched(content, !repository.getMovieWatchState(content).isWatched)

    suspend fun toggleMovieFavorite(content: Content): MovieWatchState =
        repository.setMovieFavorite(content, !repository.getMovieWatchState(content).isFavorite)

    // --- Series: seasons & tracking ---

    suspend fun loadSeriesState(content: Content): SeriesState {
        val tracking = repository.getSeriesTracking(content)
        val seasons = repository.getSeasons(content)
        if (seasons.isEmpty()) {
            return SeriesState(seasons, tracking, selectedSeason = 0, seasonDetail = null)
        }
        val targetSeason = tracking.lastWatchedSeason
            ?.let { s -> seasons.find { it.seasonNumber == s } }
            ?: seasons.first()
        val seasonDetail = repository.getSeasonDetail(content, targetSeason.seasonNumber)
        return SeriesState(
            seasons = seasons,
            tracking = tracking,
            selectedSeason = targetSeason.seasonNumber,
            seasonDetail = seasonDetail
        )
    }

    suspend fun loadSeasonDetail(content: Content, seasonNumber: Int): SeasonDetail =
        repository.getSeasonDetail(content, seasonNumber)

    suspend fun reloadTracking(content: Content): SeriesTracking =
        repository.getSeriesTracking(content)

    // --- Episode toggling (cascada + final) ---

    suspend fun toggleEpisode(
        content: Content,
        selectedSeason: Int,
        episodeNumber: Int,
        currentWatched: Set<String>,
        seasonDetail: SeasonDetail?
    ): EpisodeToggleResult {
        val key = SeriesTracking.keyFor(selectedSeason, episodeNumber)
        return if (currentWatched.contains(key)) {
            repository.unrecordEpisode(content, selectedSeason, episodeNumber)
            val tracking = repository.getSeriesTracking(content)
            if (tracking.finishedAt != null && tracking.watchedEpisodes.isEmpty()) {
                repository.clearSeriesFinished(content)
            }
            EpisodeToggleResult.Applied(repository.getSeriesTracking(content))
        } else {
            val unwatchedBefore = seasonDetail?.episodes
                ?.map { it.episodeNumber }
                ?.filter { it < episodeNumber && !currentWatched.contains(SeriesTracking.keyFor(selectedSeason, it)) }
                ?: emptyList()
            if (unwatchedBefore.isNotEmpty()) {
                EpisodeToggleResult.NeedsCascade(
                    CascadeProposal(season = selectedSeason, targetEpisode = episodeNumber, count = unwatchedBefore.size)
                )
            } else {
                val tracking = repository.recordEpisode(content, selectedSeason, episodeNumber)
                EpisodeToggleResult.Applied(maybeMarkFinale(content, selectedSeason, episodeNumber, seasonDetail, tracking))
            }
        }
    }

    suspend fun confirmCascade(
        content: Content,
        proposal: CascadeProposal,
        seasonDetail: SeasonDetail?,
        currentWatched: Set<String>
    ): SeriesTracking {
        if (seasonDetail == null) return reloadTracking(content)
        val episodesToMark = seasonDetail.episodes
            .map { it.episodeNumber }
            .filter { it <= proposal.targetEpisode && !currentWatched.contains(SeriesTracking.keyFor(proposal.season, it)) }
        val tracking = repository.recordEpisodes(content, proposal.season, episodesToMark)
        return maybeMarkFinale(content, proposal.season, proposal.targetEpisode, seasonDetail, tracking)
    }

    suspend fun dismissCascade(
        content: Content,
        proposal: CascadeProposal,
        seasonDetail: SeasonDetail?
    ): SeriesTracking {
        val tracking = repository.recordEpisode(content, proposal.season, proposal.targetEpisode)
        return maybeMarkFinale(content, proposal.season, proposal.targetEpisode, seasonDetail, tracking)
    }

    suspend fun toggleSeasonWatched(
        content: Content,
        selectedSeason: Int,
        seasonDetail: SeasonDetail?
    ): SeriesTracking {
        if (seasonDetail == null || seasonDetail.episodes.isEmpty()) return repository.getSeriesTracking(content)
        val episodeNumbers = seasonDetail.episodes.map { it.episodeNumber }
        val tracking = repository.getSeriesTracking(content)
        val alreadyWatched = episodeNumbers.all { tracking.isEpisodeWatched(selectedSeason, it) }
        return if (alreadyWatched) {
            repository.unrecordSeasonEpisodes(content, selectedSeason, episodeNumbers)
        } else {
            val newTracking = repository.recordEpisodes(content, selectedSeason, episodeNumbers)
            val lastEp = episodeNumbers.maxOrNull() ?: 0
            if (lastEp > 0) maybeMarkFinale(content, selectedSeason, lastEp, seasonDetail, newTracking)
            else newTracking
        }
    }

    private suspend fun maybeMarkFinale(
        content: Content,
        seasonNumber: Int,
        episodeNumber: Int,
        seasonDetail: SeasonDetail?,
        tracking: SeriesTracking
    ): SeriesTracking {
        val stateSeasons = repository.getSeasons(content)
        val isFinaleType = seasonDetail?.episodes?.any {
            it.episodeNumber == episodeNumber &&
                (it.episodeType == "finale" || it.episodeType == "series_finale")
        } == true

        val isLastOfLastSeason = if (!isFinaleType) {
            val lastSeason = stateSeasons.maxOfOrNull { it.seasonNumber }
            val lastEpCount = seasonDetail?.episodes?.size
            seasonNumber == lastSeason && episodeNumber == lastEpCount &&
                (content.totalEpisodes == null || seasonNumber == lastSeason)
        } else true

        if (isFinaleType || isLastOfLastSeason) {
            repository.markSeriesFinished(content)
        }
        return repository.getSeriesTracking(content)
    }
}