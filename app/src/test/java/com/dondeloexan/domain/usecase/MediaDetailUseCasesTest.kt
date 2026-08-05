package com.dondeloexan.domain.usecase

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.detail.CascadeProposal
import com.dondeloexan.domain.model.detail.Episode
import com.dondeloexan.domain.model.detail.EpisodeToggleResult
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.domain.model.detail.SeasonDetail
import com.dondeloexan.domain.model.detail.SeriesTracking
import com.dondeloexan.domain.repository.DiscoverRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MediaDetailUseCasesTest {

    private val repository: DiscoverRepository = mockk(relaxed = true)
    private val useCases = MediaDetailUseCases(repository)

    private val content = Content(
        id = "tmdb-9",
        source = ContentSource.TMDB,
        tmdbId = 9,
        title = "Pilot",
        type = ContentType.SERIES,
        totalEpisodes = null
    )

    private fun seasonDetail(types: Map<Int, String?> = emptyMap()) = SeasonDetail(
        seasonNumber = 1,
        episodes = listOf(
            Episode(episodeNumber = 1, name = "E1", seasonNumber = 1, episodeType = types[1]),
            Episode(episodeNumber = 2, name = "E2", seasonNumber = 1, episodeType = types[2]),
            Episode(episodeNumber = 3, name = "E3", seasonNumber = 1, episodeType = types[3])
        )
    )

    @Test
    fun `toggleEpisode with unwatched previous episodes requests cascade`() = runTest {
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking()
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking()

        val result = useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 3,
            currentWatched = emptySet(),
            seasonDetail = seasonDetail()
        )

        assert(result is EpisodeToggleResult.NeedsCascade)
        val proposal = (result as EpisodeToggleResult.NeedsCascade).proposal
        assert(proposal.count == 2)
        assert(proposal.targetEpisode == 3)
    }

    @Test
    fun `toggleEpisode with all previous watched records directly`() = runTest {
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking(
            exists = true, watchedEpisodes = setOf("S1E1", "S1E2")
        )

        val result = useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 3,
            currentWatched = setOf("S1E1", "S1E2"),
            seasonDetail = seasonDetail()
        )

        assert(result is EpisodeToggleResult.Applied)
        coVerify { repository.recordEpisode(content, 1, 3) }
    }

    @Test
    fun `toggleEpisode removes watched episode`() = runTest {
        coEvery { repository.unrecordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking()

        val result = useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 2,
            currentWatched = setOf("S1E1", "S1E2"),
            seasonDetail = seasonDetail()
        )

        assert(result is EpisodeToggleResult.Applied)
        coVerify { repository.unrecordEpisode(content, 1, 2) }
    }

    @Test
    fun `confirmCascade marks episodes up to target`() = runTest {
        val afterTracking = SeriesTracking(
            exists = true, watchedEpisodes = setOf("S1E1", "S1E2", "S1E3")
        )
        coEvery { repository.recordEpisodes(any(), any(), any()) } returns afterTracking
        coEvery { repository.getSeriesTracking(content) } returns afterTracking

        val tracking = useCases.confirmCascade(
            content = content,
            proposal = CascadeProposal(season = 1, targetEpisode = 3, count = 2),
            seasonDetail = seasonDetail(),
            currentWatched = emptySet()
        )

        coVerify { repository.recordEpisodes(content, 1, listOf(1, 2, 3)) }
        assert(tracking.watchedEpisodes.contains("S1E3"))
    }

    @Test
    fun `marking finale episode finishes the series`() = runTest {
        coEvery { repository.getSeasons(content) } returns listOf(
            Season(seasonNumber = 1, name = "T1", episodeCount = 3)
        )
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking(exists = true)
        coEvery { repository.markSeriesFinished(content) } returns true

        useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 3,
            currentWatched = setOf("S1E1", "S1E2"),
            seasonDetail = seasonDetail(types = mapOf(3 to "series_finale"))
        )

        coVerify { repository.markSeriesFinished(content) }
    }

    @Test
    fun `non-final last episode does not finish in-production series`() = runTest {
        coEvery { repository.getSeasons(content) } returns listOf(
            Season(seasonNumber = 1, name = "T1", episodeCount = 3)
        )
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking(exists = true)

        useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 1,
            currentWatched = emptySet(),
            seasonDetail = seasonDetail()
        )

        coVerify(exactly = 0) { repository.markSeriesFinished(any()) }
    }

    @Test
    fun `toggleSeasonWatched marks entire season when not all watched`() = runTest {
        val afterTracking = SeriesTracking(
            exists = true, watchedEpisodes = setOf("S1E1", "S1E2", "S1E3")
        )
        coEvery { repository.getSeriesTracking(content) } returnsMany listOf(
            SeriesTracking(exists = true, watchedEpisodes = setOf("S1E1")),
            afterTracking
        )
        coEvery { repository.recordEpisodes(any(), any(), any()) } returns afterTracking

        val tracking = useCases.toggleSeasonWatched(content, selectedSeason = 1, seasonDetail = seasonDetail())

        coVerify { repository.recordEpisodes(content, 1, listOf(1, 2, 3)) }
        assert(tracking.watchedEpisodes.containsAll(setOf("S1E1", "S1E2", "S1E3")))
    }

    @Test
    fun `toggleSeasonWatched clears all when all watched`() = runTest {
        coEvery { repository.getSeriesTracking(content) } returns SeriesTracking(
            exists = true, watchedEpisodes = setOf("S1E1", "S1E2", "S1E3")
        )
        coEvery { repository.unrecordSeasonEpisodes(any(), any(), any()) } returns SeriesTracking()

        useCases.toggleSeasonWatched(content, selectedSeason = 1, seasonDetail = seasonDetail())

        coVerify { repository.unrecordSeasonEpisodes(content, 1, listOf(1, 2, 3)) }
    }

    @Test
    fun `loadSeriesState selects season from last watched`() = runTest {
        val seasons = listOf(
            Season(seasonNumber = 1, name = "T1", episodeCount = 3),
            Season(seasonNumber = 2, name = "T2", episodeCount = 3)
        )
        coEvery { repository.getSeriesTracking(content) } returns SeriesTracking(
            exists = true,
            watchedEpisodes = setOf("S1E1", "S1E2"),
            lastWatchedSeason = 1,
            lastWatchedEpisode = 2
        )
        coEvery { repository.getSeasons(content) } returns seasons
        coEvery { repository.getSeasonDetail(content, 1) } returns SeasonDetail(seasonNumber = 1)

        val state = useCases.loadSeriesState(content)

        assert(state.selectedSeason == 1)
        coVerify { repository.getSeasonDetail(content, 1) }
    }

    @Test
    fun `loadSeriesState falls back to first season`() = runTest {
        val seasons = listOf(
            Season(seasonNumber = 1, name = "T1", episodeCount = 3),
            Season(seasonNumber = 2, name = "T2", episodeCount = 3)
        )
        coEvery { repository.getSeriesTracking(content) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeasons(content) } returns seasons
        coEvery { repository.getSeasonDetail(content, 1) } returns SeasonDetail(seasonNumber = 1)

        val state = useCases.loadSeriesState(content)

        assert(state.selectedSeason == 1)
    }

    @Test
    fun `getCollectionMovies filters out the current movie`() = runTest {
        val movie = Content(
            id = "tmdb-5",
            source = ContentSource.TMDB,
            tmdbId = 5,
            title = "Test Movie",
            type = ContentType.MOVIE,
            collectionTmdbId = 10
        )
        val collection = listOf(
            com.dondeloexan.domain.model.ContentPreview(
                id = "tmdb-5", title = "Test Movie", source = ContentSource.TMDB, tmdbId = 5, type = ContentType.MOVIE
            ),
            com.dondeloexan.domain.model.ContentPreview(
                id = "tmdb-6", title = "Other Movie", source = ContentSource.TMDB, tmdbId = 6, type = ContentType.MOVIE
            )
        )
        coEvery { repository.getCollectionMovies(10) } returns collection

        val result = useCases.getCollectionMovies(movie)

        assert(result.size == 1)
        assert(result[0].title == "Other Movie")
    }
}