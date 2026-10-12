package com.dondeloexan.domain.model

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Fecha efectiva para la Agenda: la del próximo capítulo o, si la temporada está
 * pendiente en España, la fecha española. Vacíos, datos no parseables y fechas ya
 * pasadas devuelven null para que la serie quede al final del listado.
 */
class SeriesItemUpcomingAirDateTest {

    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun `usa la fecha del proximo capitulo cuando esta en el futuro`() {
        val item = SeriesItem(id = 1, title = "Serie", nextEpisodeAirDate = "2026-11-06")
        assertEquals("2026-11-06", item.upcomingAirDate(today))
    }

    @Test
    fun `cae a la fecha espanola cuando hay temporada pendiente en Espana`() {
        val item = SeriesItem(
            id = 1, title = "Babylon Berlin",
            pendingEsSeason = 5, spanishReleaseDate = "2026-11-06"
        )
        assertEquals("2026-11-06", item.upcomingAirDate(today))
    }

    @Test
    fun `ignora la fecha espanola si la temporada ya se estreno`() {
        val item = SeriesItem(
            id = 1, title = "Babylon Berlin",
            pendingEsSeason = 5, spanishReleaseDate = "2020-11-06"
        )
        assertNull(item.upcomingAirDate(today))
    }

    @Test
    fun `fecha vacia devuelve null`() {
        val item = SeriesItem(id = 1, title = "Serie", nextEpisodeAirDate = "")
        assertNull(item.upcomingAirDate(today))
    }

    @Test
    fun `fecha no parseable devuelve null`() {
        val item = SeriesItem(id = 1, title = "Serie", nextEpisodeAirDate = "proximamente")
        assertNull(item.upcomingAirDate(today))
    }

    @Test
    fun `fecha ya pasada devuelve null`() {
        val item = SeriesItem(id = 1, title = "Serie", nextEpisodeAirDate = "2026-10-11")
        assertNull(item.upcomingAirDate(today))
    }

    @Test
    fun `sin ninguna fecha devuelve null`() {
        val item = SeriesItem(id = 1, title = "Serie")
        assertNull(item.upcomingAirDate(today))
    }
}
