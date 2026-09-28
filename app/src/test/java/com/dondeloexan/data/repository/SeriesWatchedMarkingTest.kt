package com.dondeloexan.data.repository

import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbEpisodeDto
import com.dondeloexan.data.remote.dto.TmdbSeasonDto
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Marcar una serie como vista desde el listado debe:
 *  - marcar TODOS los capítulos emitidos de TODAS las temporadas,
 *  - ser idempotente (sin duplicar filas),
 *  - calcular el estado desde el último capítulo marcado.
 */
class SeriesWatchedMarkingTest {

    private val movieDao: MovieDao = mockk(relaxed = true)
    private val tvShowDao: TvShowDao = mockk(relaxed = true)
    private val tvShowProgressDao: TvShowProgressDao = mockk(relaxed = true)
    private val tmdbApi: TmdbApi = mockk(relaxed = true)

    private val repository = TrackingRepositoryImpl(
        movieDao = movieDao,
        tvShowDao = tvShowDao,
        tvShowProgressDao = tvShowProgressDao,
        tmdbApi = tmdbApi,
        cloudCatalog = null,
        syncManager = null,
        sessionRefresher = null
    )

    private fun show(
        status: String? = "Ended",
        inProduction: Boolean? = false
    ) = TvShowEntity(
        id = 1,
        contentId = "tmdb-100",
        tmdbId = 100,
        title = "Serie",
        status = WatchStatus.POR_VER,
        seriesStatus = status,
        inProduction = inProduction
    )

    private fun episode(number: Int, airDate: String? = "2020-01-01") = TmdbEpisodeDto(
        airDate = airDate,
        episodeNumber = number,
        id = number,
        name = "E$number",
        seasonNumber = 1
    )

    private fun mockTmdb(seasonEpisodes: Map<Int, List<Int>>, status: String, inProduction: Boolean) {
        val seasons = seasonEpisodes.keys.sorted().map { number ->
            TmdbSeasonDto(
                episodeCount = seasonEpisodes.getValue(number).size,
                id = number,
                name = "T$number",
                seasonNumber = number
            )
        }
        coEvery { tmdbApi.getTvDetailLight(100) } returns TmdbTvDetailDto(
            id = 100,
            name = "Serie",
            numberOfSeasons = seasons.size,
            numberOfEpisodes = seasonEpisodes.values.sumOf { it.size },
            seasons = seasons,
            inProduction = inProduction,
            status = status
        )
        seasonEpisodes.forEach { (number, episodes) ->
            coEvery { tmdbApi.getTvSeason(100, number) } returns TmdbTvSeasonDetailDto(
                episodes = episodes.map { episode(it) },
                id = number,
                seasonNumber = number
            )
        }
    }

    @Test
    fun `marca todos los capitulos emitidos de todas las temporadas`() = runTest {
        val show = show()
        coEvery { tvShowDao.getById(1) } returns show
        mockTmdb(mapOf(1 to listOf(1, 2), 2 to listOf(1, 2, 3)), status = "Ended", inProduction = false)

        val inserted = slot<List<TvShowProgressEntity>>()
        coEvery { tvShowProgressDao.insertAll(capture(inserted)) } returns Unit

        val ok = repository.setSeriesWatchedById(1, watched = true)

        assert(ok)
        assert(inserted.captured.size == 5)
        assert(inserted.captured.filter { it.season > 0 }.size == 5)
    }

    @Test
    fun `marcar dos veces no duplica filas`() = runTest {
        val show = show()
        coEvery { tvShowDao.getById(1) } returns show
        mockTmdb(mapOf(1 to listOf(1, 2), 2 to listOf(1, 2, 3)), status = "Ended", inProduction = false)

        val inserted = mutableListOf<List<TvShowProgressEntity>>()
        coEvery { tvShowProgressDao.insertAll(capture(inserted)) } returns Unit

        repository.setSeriesWatchedById(1, watched = true)
        repository.setSeriesWatchedById(1, watched = true)

        assert(inserted.size == 2)
        assert(inserted.all { it.distinctBy { p -> p.season to p.episode }.size == it.size })
        assert(inserted[0].size == inserted[1].size)
    }

    @Test
    fun `serie terminada queda con finishedAt y estado YA_VISTA`() = runTest {
        val show = show(status = "Ended", inProduction = false)
        coEvery { tvShowDao.getById(1) } returns show
        mockTmdb(mapOf(1 to listOf(1, 2)), status = "Ended", inProduction = false)

        val updated = slot<TvShowEntity>()
        coEvery { tvShowDao.update(capture(updated)) } returns Unit

        repository.setSeriesWatchedById(1, watched = true)

        assert(updated.captured.status == WatchStatus.YA_VISTA)
        assert(updated.captured.finishedAt != null)
        assert(updated.captured.releasedEpisodes == 2)
    }

    @Test
    fun `serie en produccion queda al dia sin finishedAt`() = runTest {
        val show = show(status = "Returning Series", inProduction = true)
        coEvery { tvShowDao.getById(1) } returns show
        mockTmdb(mapOf(1 to listOf(1, 2)), status = "Returning Series", inProduction = true)

        val updated = slot<TvShowEntity>()
        coEvery { tvShowDao.update(capture(updated)) } returns Unit

        repository.setSeriesWatchedById(1, watched = true)

        assert(updated.captured.status == WatchStatus.YA_VISTA)
        assert(updated.captured.finishedAt == null)
        assert(updated.captured.seriesStatus == "Returning Series")
    }

    @Test
    fun `desmarcar limpia el progreso y el finishedAt`() = runTest {
        val show = show().copy(status = WatchStatus.YA_VISTA, finishedAt = 123L)
        coEvery { tvShowDao.getById(1) } returns show

        val updated = slot<TvShowEntity>()
        coEvery { tvShowDao.update(capture(updated)) } returns Unit

        repository.setSeriesWatchedById(1, watched = false)

        io.mockk.coVerify { tvShowProgressDao.deleteByTvShowId(1) }
        assert(updated.captured.status == WatchStatus.POR_VER)
        assert(updated.captured.finishedAt == null)
        assert(updated.captured.lastWatchedAt == null)
    }
}
