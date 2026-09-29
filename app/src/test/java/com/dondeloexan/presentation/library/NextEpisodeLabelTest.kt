package com.dondeloexan.presentation.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Texto del próximo capítulo de la ficha. Con la temporada pendiente de estreno
 * en España y TMDB sin `next_episode_to_air`, la fecha que se le pasa es la
 * española (p. ej. Babylon Berlin T5 → 06/11/2026), de modo que bajo el % de
 * episodios debe verse la fecha del estreno y no un hueco.
 */
class NextEpisodeLabelTest {

    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun `estreno lejano de una temporada nueva`() {
        assertEquals(
            "Próxima temporada: 06/11/2026",
            nextEpisodeLabel("2026-11-06", episode = 1, isCaughtUp = true, today = today)
        )
    }

    @Test
    fun `estreno de una temporada nueva a menos de una semana`() {
        assertEquals(
            "Próxima temporada en 4 días",
            nextEpisodeLabel("2026-10-02", episode = 1, isCaughtUp = true, today = today)
        )
    }

    @Test
    fun `manana y hoy`() {
        assertEquals(
            "Mañana nueva temporada",
            nextEpisodeLabel("2026-09-29", episode = 1, isCaughtUp = true, today = today)
        )
        assertEquals(
            "¡Hoy nueva temporada!",
            nextEpisodeLabel("2026-09-28", episode = 1, isCaughtUp = true, today = today)
        )
    }

    @Test
    fun `un capitulo suelto no habla de temporada`() {
        assertEquals(
            "Próximo: 06/11/2026",
            nextEpisodeLabel("2026-11-06", episode = 3, isCaughtUp = false, today = today)
        )
        assertEquals(
            "Próximo en 4 días",
            nextEpisodeLabel("2026-10-02", episode = 3, isCaughtUp = false, today = today)
        )
    }

    @Test
    fun `sin numero de capitulo se deduce temporada si esta al dia`() {
        assertEquals(
            "Próxima temporada: 06/11/2026",
            nextEpisodeLabel("2026-11-06", episode = null, isCaughtUp = true, today = today)
        )
        assertEquals(
            "Próximo: 06/11/2026",
            nextEpisodeLabel("2026-11-06", episode = null, isCaughtUp = false, today = today)
        )
    }

    @Test
    fun `una fecha ya pasada no pinta nada`() {
        assertNull(nextEpisodeLabel("2026-09-10", episode = 1, isCaughtUp = true, today = today))
    }

    @Test
    fun `sin fecha o con fecha invalida no rompe nada`() {
        assertNull(nextEpisodeLabel(null, episode = 1, isCaughtUp = false, today = today))
        assertNull(nextEpisodeLabel("sin fecha", episode = 1, isCaughtUp = false, today = today))
    }
}
