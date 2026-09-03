package com.dondeloexan.data.sync

import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.dto.TmdbCastMemberDto
import com.dondeloexan.data.remote.dto.TmdbCountryProviders
import com.dondeloexan.data.remote.dto.TmdbCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbCrewMemberDto
import com.dondeloexan.data.remote.dto.TmdbExternalIdsDto
import com.dondeloexan.data.remote.dto.TmdbGenreDto
import com.dondeloexan.data.remote.dto.TmdbProvider
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.dto.TmdbWatchProvidersResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesMetadataEnricherTest {

    private val tmdbApi: TmdbApi = mockk(relaxed = true)
    private val tvShowDao: TvShowDao = mockk(relaxed = true)

    private fun enricher() = SeriesMetadataEnricher(tmdbApi, tvShowDao)

    private fun sparse(tmdbId: Int?) = TvShowEntity(
        id = (tmdbId ?: 1).toLong(), title = "S", status = WatchStatus.POR_VER, tmdbId = tmdbId
    )

    @Test
    fun `isSparse true solo si hay tmdb y ficha minima vacia`() {
        assertTrue(enricher().isSparse(sparse(1396)))
        assertFalse(enricher().isSparse(sparse(1396).copy(genres = "[Drama]")))
        assertFalse(enricher().isSparse(sparse(1396).copy(castJson = "[{\"name\":\"X\"}]")))
        assertFalse(enricher().isSparse(sparse(null)))
    }

    @Test
    fun `enrichAll enriquece solo las series sin ficha y las actualiza`() = runTest {
        coEvery { tvShowDao.getAll() } returns listOf(
            sparse(1396).copy(id = 1, title = "Pobre"),
            sparse(1396).copy(id = 2, title = "Con genero", genres = "[Drama]"),
            sparse(null).copy(id = 3, title = "Sin tmdb")
        )
        // Solo la serie 1 reúne isSparse y debe llamar a la API.
        val captured = slot<TvShowEntity>()
        coEvery { tvShowDao.getById(1) } returns sparse(1396).copy(id = 1)
        coEvery { tvShowDao.update(capture(captured)) } returns Unit
        stubTmdb(1396)

        val total = enricher().enrichAll()

        assertEquals(1, total)
        coVerify(exactly = 1) { tvShowDao.update(any()) }
        coVerify(exactly = 0) { tmdbApi.getTvDetail(2) }
        coVerify(exactly = 0) { tmdbApi.getTvDetail(3) }
    }

    @Test
    fun `enrich rellena la ficha tecnica del Content en la entidad local`() = runTest {
        coEvery { tvShowDao.getById(7) } returns sparse(1396).copy(id = 7, contentId = "s1")
        stubTmdb(1396)
        val captured = slot<TvShowEntity>()
        coEvery { tvShowDao.update(capture(captured)) } returns Unit

        val ok = enricher().enrich(sparse(1396).copy(id = 7))

        assertTrue(ok)
        val updated = captured.captured
        assertEquals("Breaking Bad", updated.originalTitle)
        othersOneOf(updated.genres, "Drama")
        othersOneOf(updated.countries, "Estados Unidos")
        assertTrue(updated.castJson?.contains("Bryan Cranston") == true)
        assertFalse(updated.directors.isNullOrBlank())
        assertFalse(updated.productionCompanies.isNullOrBlank())
    }

    private fun stubTmdb(tmdbId: Int) {
        coEvery { tmdbApi.getTvDetail(tmdbId, any()) } returns TvShowEntityTvDetail()
        coEvery { tmdbApi.getTvCredits(tmdbId) } returns TmdbCreditsResponse(
            id = tmdbId,
            cast = listOf(TmdbCastMemberDto(id = 1, name = "Bryan Cranston", character = "Walter White", profilePath = "/p")),
            crew = listOf(TmdbCrewMemberDto(id = 2, name = "Vince Gilligan", job = "Director", department = "Directing"))
        )
        coEvery { tmdbApi.getTvWatchProviders(tmdbId) } returns TmdbWatchProvidersResponse(
            id = tmdbId,
            results = mapOf("ES" to TmdbCountryProviders(flatrate = listOf(TmdbProvider(1, "Netflix", "/n"))))
        )
        coEvery { tmdbApi.getTvExternalIds(tmdbId) } returns TmdbExternalIdsDto(
            id = tmdbId, imdbId = "tt0903747", facebookId = "fb"
        )
    }

    private fun TvShowEntityTvDetail() = TmdbTvDetailDto(
        id = 1396, name = "Breaking Bad", originalName = "Breaking Bad",
        overview = "Un profesor de química...", backdropPath = "/b",
        firstAirDate = "2008-01-20", voteAverage = 9.5f,
        numberOfSeasons = 5, numberOfEpisodes = 62, episodeRunTime = listOf(47),
        genres = listOf(TmdbGenreDto(18, "Drama")),
        productionCountries = listOf(com.dondeloexan.data.remote.dto.TmdbCountryDto("US", "Estados Unidos")),
        productionCompanies = listOf(com.dondeloexan.data.remote.dto.TmdbProductionCompanyDto(1, "AMC"))
    )

    private fun othersOneOf(value: String?, vararg expected: String) {
        assertTrue(value != null && expected.any { value.contains(it) })
    }
}
