package com.dondeloexan.domain.usecase

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.PersonInfo
import com.dondeloexan.domain.model.detail.CascadeProposal
import com.dondeloexan.domain.model.detail.Episode
import com.dondeloexan.domain.model.detail.EpisodeRef
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
        coEvery { repository.recordEpisodes(any(), any<List<EpisodeRef>>()) } returns afterTracking
        coEvery { repository.getSeriesTracking(content) } returns afterTracking

        val tracking = useCases.confirmCascade(
            content = content,
            proposal = CascadeProposal(season = 1, targetEpisode = 3, count = 2),
            seasonDetail = seasonDetail(),
            currentWatched = emptySet()
        )

        coVerify {
            repository.recordEpisodes(
                content,
                listOf(EpisodeRef(1, 1), EpisodeRef(1, 2), EpisodeRef(1, 3))
            )
        }
        assert(tracking.watchedEpisodes.contains("S1E3"))
    }

    @Test
    fun `cascade proposal counts previous seasons too`() = runTest {
        val seasons = listOf(
            Season(seasonNumber = 1, name = "T1", episodeCount = 4),
            Season(seasonNumber = 2, name = "T2", episodeCount = 2),
            Season(seasonNumber = 3, name = "T3", episodeCount = 6)
        )

        val result = useCases.toggleEpisode(
            content = content,
            selectedSeason = 3,
            episodeNumber = 5,
            currentWatched = emptySet(),
            seasonDetail = SeasonDetail(
                seasonNumber = 3,
                episodes = (1..6).map { Episode(episodeNumber = it, name = "E$it", seasonNumber = 3) }
            ),
            seasons = seasons
        )

        assert(result is EpisodeToggleResult.NeedsCascade)
        val proposal = (result as EpisodeToggleResult.NeedsCascade).proposal
        // 4 de T1 + 2 de T2 + 4 anteriores dentro de T3
        assert(proposal.count == 10)
        assert(proposal.currentSeasonCount == 4)
        assert(proposal.previousSeasonsCount == 6)
        assert(proposal.previousSeasons == listOf(1, 2))
    }

    @Test
    fun `confirmCascade marks previous seasons completely`() = runTest {
        val seasons = listOf(
            Season(seasonNumber = 1, name = "T1", episodeCount = 2),
            Season(seasonNumber = 2, name = "T2", episodeCount = 3),
            Season(seasonNumber = 3, name = "T3", episodeCount = 6)
        )
        val detail = SeasonDetail(
            seasonNumber = 3,
            episodes = listOf(
                Episode(episodeNumber = 4, name = "E4", seasonNumber = 3),
                Episode(episodeNumber = 5, name = "E5", seasonNumber = 3),
                Episode(episodeNumber = 6, name = "E6", seasonNumber = 3)
            )
        )
        coEvery { repository.recordEpisodes(any(), any<List<EpisodeRef>>()) } returns SeriesTracking(exists = true)

        useCases.confirmCascade(
            content = content,
            proposal = CascadeProposal(
                season = 3,
                targetEpisode = 5,
                count = 7,
                currentSeasonCount = 2,
                previousSeasons = listOf(1, 2),
                previousSeasonsCount = 5
            ),
            seasonDetail = detail,
            currentWatched = emptySet(),
            seasons = seasons
        )

        coVerify {
            repository.recordEpisodes(
                content,
                listOf(
                    EpisodeRef(1, 1), EpisodeRef(1, 2),
                    EpisodeRef(2, 1), EpisodeRef(2, 2), EpisodeRef(2, 3),
                    EpisodeRef(3, 4), EpisodeRef(3, 5)
                )
            )
        }
    }

    @Test
    fun `dismissCascade marks only the tapped episode`() = runTest {
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)

        useCases.dismissCascade(
            content = content,
            proposal = CascadeProposal(
                season = 3,
                targetEpisode = 5,
                count = 10,
                currentSeasonCount = 4,
                previousSeasons = listOf(1, 2),
                previousSeasonsCount = 6
            ),
            seasonDetail = seasonDetail()
        )

        coVerify { repository.recordEpisode(content, 3, 5) }
        coVerify(exactly = 0) { repository.recordEpisodes(any(), any<List<EpisodeRef>>()) }
    }

    @Test
    fun `toggleEpisode on finale delegates to recordEpisode (finish is decided by repo)`() = runTest {
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking(exists = true)

        val result = useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 3,
            currentWatched = setOf("S1E1", "S1E2"),
            seasonDetail = seasonDetail(types = mapOf(3 to "series_finale"))
        )

        assert(result is EpisodeToggleResult.Applied)
        coVerify { repository.recordEpisode(content, 1, 3) }
        // La terminación ya no se decide en el use case: la maneja el repo
        // (reconcileSeriesState) con datos de emisión reales.
        coVerify(exactly = 0) { repository.markSeriesFinished(any()) }
    }

    @Test
    fun `toggleEpisode non-final last episode does not call markSeriesFinished`() = runTest {
        coEvery { repository.recordEpisode(any(), any(), any()) } returns SeriesTracking(exists = true)
        coEvery { repository.getSeriesTracking(any()) } returns SeriesTracking(exists = true)

        val result = useCases.toggleEpisode(
            content = content,
            selectedSeason = 1,
            episodeNumber = 1,
            currentWatched = emptySet(),
            seasonDetail = seasonDetail()
        )

        assert(result is EpisodeToggleResult.Applied)
        coVerify { repository.recordEpisode(content, 1, 1) }
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

    @Test
    fun `getDirectorMovies delegates to repository when movie has a director`() = runTest {
        val movie = Content(
            id = "tmdb-7",
            source = ContentSource.TMDB,
            tmdbId = 7,
            title = "Directed",
            type = ContentType.MOVIE,
            directors = listOf(PersonInfo(name = "Jane Doe", tmdbId = 42))
        )
        val expected = listOf(
            ContentPreview(id = "tmdb-100", title = "A", source = ContentSource.TMDB, tmdbId = 100, type = ContentType.MOVIE)
        )
        coEvery { repository.getDirectorTopMovies(42, 7) } returns expected

        val result = useCases.getDirectorMovies(movie)

        coVerify { repository.getDirectorTopMovies(42, 7) }
        assert(result == expected)
    }

    @Test
    fun `getDirectorMovies returns empty when movie has no director`() = runTest {
        val movie = Content(
            id = "tmdb-8",
            source = ContentSource.TMDB,
            tmdbId = 8,
            title = "No Director",
            type = ContentType.MOVIE,
            directors = emptyList()
        )
        coEvery { repository.getDirectorTopMovies(any(), any()) } returns listOf(
            ContentPreview(id = "tmdb-100", title = "A", source = ContentSource.TMDB, tmdbId = 100, type = ContentType.MOVIE)
        )

        val result = useCases.getDirectorMovies(movie)

        assert(result.isEmpty())
        coVerify(exactly = 0) { repository.getDirectorTopMovies(any(), any()) }
    }

    @Test
    fun `getDirectorMovies returns empty for series`() = runTest {
        val series = Content(
            id = "tmdb-9",
            source = ContentSource.TMDB,
            tmdbId = 9,
            title = "Series",
            type = ContentType.SERIES,
            directors = listOf(PersonInfo(name = "Jane Doe", tmdbId = 42))
        )
        coEvery { repository.getDirectorTopMovies(any(), any()) } returns listOf(
            ContentPreview(id = "tmdb-100", title = "A", source = ContentSource.TMDB, tmdbId = 100, type = ContentType.MOVIE)
        )

        val result = useCases.getDirectorMovies(series)

        assert(result.isEmpty())
        coVerify(exactly = 0) { repository.getDirectorTopMovies(any(), any()) }
    }
}