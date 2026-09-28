package com.dondeloexan.domain.usecase

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.DataResult
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.detail.CascadeProposal
import com.dondeloexan.domain.model.detail.CastSocialInfo
import com.dondeloexan.domain.model.detail.EpisodeRef
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

    suspend fun getDirectorMovies(content: Content): List<ContentPreview> {
        if (content.type != ContentType.MOVIE) return emptyList()
        val directorId = content.directors.firstOrNull()?.tmdbId ?: return emptyList()
        return repository.getDirectorTopMovies(directorId, content.tmdbId)
    }

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

    suspend fun addMovieToLibrary(content: Content): MovieWatchState =
        repository.addMovieToLibrary(content)

    suspend fun addSeriesToLibrary(content: Content): Boolean =
        repository.addSeriesToLibrary(content)

    suspend fun toggleSeriesWatched(content: Content, watched: Boolean): Boolean =
        repository.setSeriesWatched(content, watched)

    suspend fun toggleSeriesFavorite(content: Content, favorite: Boolean): Boolean =
        repository.setSeriesFavorite(content, favorite)

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
        seasonDetail: SeasonDetail?,
        seasons: List<Season> = emptyList()
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
            val proposal = buildCascadeProposal(
                content = content,
                selectedSeason = selectedSeason,
                episodeNumber = episodeNumber,
                currentWatched = currentWatched,
                unwatchedInCurrentSeason = unwatchedBefore,
                seasons = seasons
            )
            if (proposal.count > 0) {
                EpisodeToggleResult.NeedsCascade(proposal)
            } else {
                val tracking = repository.recordEpisode(content, selectedSeason, episodeNumber)
                EpisodeToggleResult.Applied(tracking)
            }
        }
    }

    /**
     * La propuesta incluye, además de los capítulos anteriores de la temporada
     * seleccionada, TODOS los de las temporadas anteriores que aún no estén
     * marcados.
     */
    private suspend fun buildCascadeProposal(
        content: Content,
        selectedSeason: Int,
        episodeNumber: Int,
        currentWatched: Set<String>,
        unwatchedInCurrentSeason: List<Int>,
        seasons: List<Season>
    ): CascadeProposal {
        val previousSeasonsWithPending = mutableListOf<Int>()
        var previousPending = 0
        seasons
            .filter { it.seasonNumber in 1 until selectedSeason }
            .sortedBy { it.seasonNumber }
            .forEach { season ->
                val pending = seasonEpisodeNumbers(content, season)
                    .count { !currentWatched.contains(SeriesTracking.keyFor(season.seasonNumber, it)) }
                if (pending > 0) {
                    previousSeasonsWithPending += season.seasonNumber
                    previousPending += pending
                }
            }
        return CascadeProposal(
            season = selectedSeason,
            targetEpisode = episodeNumber,
            count = unwatchedInCurrentSeason.size + previousPending,
            currentSeasonCount = unwatchedInCurrentSeason.size,
            previousSeasons = previousSeasonsWithPending,
            previousSeasonsCount = previousPending
        )
    }

    /**
     * Números de capítulo de una temporada. Se usa `episodeCount` de TMDB (una
     * temporada anterior se considera emitida completa) y sólo se pide el
     * detalle cuando no hay dato.
     */
    private suspend fun seasonEpisodeNumbers(content: Content, season: Season): List<Int> {
        if (season.episodeCount > 0) return (1..season.episodeCount).toList()
        return repository.getSeasonDetail(content, season.seasonNumber).episodes.map { it.episodeNumber }
    }

    suspend fun confirmCascade(
        content: Content,
        proposal: CascadeProposal,
        seasonDetail: SeasonDetail?,
        currentWatched: Set<String>,
        seasons: List<Season> = emptyList()
    ): SeriesTracking {
        if (seasonDetail == null) return reloadTracking(content)

        val entries = mutableListOf<EpisodeRef>()
        val seasonsByNumber = seasons.associateBy { it.seasonNumber }
        proposal.previousSeasons.forEach { seasonNumber ->
            val season = seasonsByNumber[seasonNumber]
            val numbers = if (season != null) {
                seasonEpisodeNumbers(content, season)
            } else {
                emptyList()
            }
            numbers.forEach { entries += EpisodeRef(season = seasonNumber, episode = it) }
        }

        seasonDetail.episodes
            .map { it.episodeNumber }
            .filter { it <= proposal.targetEpisode }
            .forEach { entries += EpisodeRef(season = proposal.season, episode = it) }

        return repository.recordEpisodes(content, entries.distinct())
    }

    suspend fun dismissCascade(
        content: Content,
        proposal: CascadeProposal,
        seasonDetail: SeasonDetail?
    ): SeriesTracking {
        return repository.recordEpisode(content, proposal.season, proposal.targetEpisode)
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
            repository.recordEpisodes(content, selectedSeason, episodeNumbers)
        }
    }
}