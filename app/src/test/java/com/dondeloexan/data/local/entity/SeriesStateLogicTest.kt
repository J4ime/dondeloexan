package com.dondeloexan.data.local.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesStateLogicTest {

    private fun show(
        released: Int? = 10,
        total: Int? = 10,
        status: String? = "Returning Series",
        inProduction: Boolean? = true
    ) = TvShowEntity(
        id = 1,
        title = "Serie",
        status = WatchStatus.POR_VER,
        releasedEpisodes = released,
        totalEpisodes = total,
        seriesStatus = status,
        inProduction = inProduction
    )

    @Test
    fun `al dia se decide por emitidos no por total de temporadas futuras`() {
        val serie = show(released = 10, total = 26, status = "Returning Series", inProduction = true)
        assertTrue(serie.isCaughtUpBy(10))
        assertFalse(serie.isFinishedBy(10))
        assertEquals("AL_DIA", serie.stateLabel(10))
    }

    @Test
    fun `sin emitidos y sin futuro usa el total (serie terminada de un tiron)`() {
        val serie = show(released = null, total = 8, status = "Ended", inProduction = false)
        assertTrue(serie.isCaughtUpBy(8))
        assertTrue(serie.isFinishedBy(8))
        assertEquals("TERMINADA", serie.stateLabel(8))
    }

    @Test
    fun `sin emitidos y con futuro no se considera al dia`() {
        val serie = show(released = null, total = 26, status = "Returning Series", inProduction = true)
        assertFalse(serie.isCaughtUpBy(26))
        assertFalse(serie.isFinishedBy(26))
        assertEquals("EN_CURSO", serie.stateLabel(26))
    }

    @Test
    fun `sin emitidos ni total y con futuro no se considera al dia`() {
        val serie = show(released = null, total = null, status = "Returning Series", inProduction = true)
        assertFalse(serie.isCaughtUpBy(99))
    }

    @Test
    fun `serie terminada al dia es terminada`() {
        val serie = show(released = 8, total = 8, status = "Ended", inProduction = false)
        assertTrue(serie.isFinishedBy(8))
        assertEquals("TERMINADA", serie.stateLabel(8))
    }

    @Test
    fun `sin progreso no se considera al dia`() {
        val serie = show(released = 10, total = 26, status = "Returning Series", inProduction = true)
        assertFalse(serie.isCaughtUpBy(0))
        assertFalse(serie.isFinishedBy(0))
        assertEquals("EN_CURSO", serie.stateLabel(0))
    }
}
