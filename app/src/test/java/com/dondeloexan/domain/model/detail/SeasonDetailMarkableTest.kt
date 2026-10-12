package com.dondeloexan.domain.model.detail

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * El botón "marcar todo" de una temporada debe togglear EXACTAMENTE los
 * capítulos marcables (emitidos y no bloqueados por estreno en España) y su
 * etiqueta debe reflejar ese mismo conjunto: si el usuario marca todo, pasa a
 * "Desmarcar todo".
 */
class SeasonDetailMarkableTest {

    private val today = LocalDate.of(2026, 10, 12)

    private fun season(vararg airDates: String?) = SeasonDetail(
        seasonNumber = 1,
        episodes = airDates.mapIndexed { index, airDate ->
            Episode(
                episodeNumber = index + 1,
                name = "E${index + 1}",
                airDate = airDate,
                seasonNumber = 1
            )
        }
    )

    @Test
    fun `los capitulos futuros no son marcables`() {
        val detail = season("2026-10-01", "2026-10-20", null)
        assertEquals(listOf(1, 3), detail.markableEpisodeNumbers(today = today))
    }

    @Test
    fun `temporada pendiente en Espana no tiene capitulos marcables`() {
        val detail = season("2026-10-01", "2026-10-02")
        assertTrue(detail.markableEpisodeNumbers(pendingInSpain = true, today = today).isEmpty())
    }

    @Test
    fun `marcar todo cambia la etiqueta aunque queden capitulos por emitir`() {
        val detail = season("2026-10-01", "2026-10-05", "2026-12-01")
        val watched = setOf("S1E1", "S1E2")
        assertTrue(detail.isFullyWatched(season = 1, watchedEpisodes = watched, today = today))
    }

    @Test
    fun `si falta por ver un capitulo emitido no esta completo`() {
        val detail = season("2026-10-01", "2026-10-05")
        assertFalse(
            detail.isFullyWatched(season = 1, watchedEpisodes = setOf("S1E1"), today = today)
        )
    }

    @Test
    fun `sin capitulos marcables nunca esta completo`() {
        val detail = season("2026-12-01")
        assertFalse(detail.isFullyWatched(season = 1, watchedEpisodes = emptySet(), today = today))
        assertFalse(
            detail.isFullyWatched(
                season = 1,
                watchedEpisodes = emptySet(),
                pendingInSpain = true,
                today = today
            )
        )
    }
}
