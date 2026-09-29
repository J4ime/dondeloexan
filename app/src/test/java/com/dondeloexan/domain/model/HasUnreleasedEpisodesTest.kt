package com.dondeloexan.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * "Terminada" SOLO cuando el usuario ha visto el capítulo final: la serie no
 * tiene nada por delante. Estos casos fijan la regla que lo hace posible, porque
 * TMDB marca "Ended" series que aún tienen capítulos por estrenar en España
 * (Babylon Berlin: 24 emitidos de 32).
 */
class HasUnreleasedEpisodesTest {

    @Test
    fun `24 de 32 emitidos quedan capitulos por estrenar`() {
        assertTrue(hasUnreleasedEpisodes(24, 32))
    }

    @Test
    fun `32 de 32 emitidos no queda nada por estrenar`() {
        assertFalse(hasUnreleasedEpisodes(32, 32))
    }

    @Test
    fun `sin total o sin emitidos no se inventa`() {
        assertFalse(hasUnreleasedEpisodes(24, null))
        assertFalse(hasUnreleasedEpisodes(null, 32))
        assertFalse(hasUnreleasedEpisodes(null, null))
    }

    @Test
    fun `vista entera pero con capitulos por estrenar queda en agenda`() {
        assertEquals(
            SeriesState.AL_DIA,
            seriesStateFor(
                releasedEpisodes = 24,
                totalEpisodes = 32,
                seriesStatus = "Ended",
                inProduction = false,
                watchedCount = 24
            )
        )
    }

    @Test
    fun `vista entera y sin nada mas por delante esta terminada`() {
        assertEquals(
            SeriesState.TERMINADA,
            seriesStateFor(
                releasedEpisodes = 32,
                totalEpisodes = 32,
                seriesStatus = "Ended",
                inProduction = false,
                watchedCount = 32
            )
        )
    }

    @Test
    fun `le falta un capitulo por ver no esta terminada`() {
        assertEquals(
            SeriesState.EN_CURSO,
            seriesStateFor(
                releasedEpisodes = 32,
                totalEpisodes = 32,
                seriesStatus = "Ended",
                inProduction = false,
                watchedCount = 31
            )
        )
    }

    @Test
    fun `temporada pendiente de estreno en Espana no esta terminada`() {
        assertEquals(
            SeriesState.AL_DIA,
            seriesStateFor(
                releasedEpisodes = 24,
                totalEpisodes = 32,
                seriesStatus = "Ended",
                inProduction = false,
                watchedCount = 24,
                pendingFuture = true
            )
        )
    }

    @Test
    fun `con temporadas futuras no esta terminada aunque la haya visto entera`() {
        assertEquals(
            SeriesState.AL_DIA,
            seriesStateFor(
                releasedEpisodes = 32,
                totalEpisodes = 64,
                seriesStatus = "Returning Series",
                inProduction = true,
                watchedCount = 32
            )
        )
    }

    @Test
    fun `serie en curso y a cero capitulos no esta terminada`() {
        assertEquals(
            SeriesState.EN_CURSO,
            seriesStateFor(
                releasedEpisodes = 24,
                totalEpisodes = 32,
                seriesStatus = "Returning Series",
                inProduction = true,
                watchedCount = 0
            )
        )
    }

    @Test
    fun `sin dato de emitidos una serie sin futuro y vista entera esta terminada`() {
        assertEquals(
            SeriesState.TERMINADA,
            seriesStateFor(
                releasedEpisodes = null,
                totalEpisodes = 8,
                seriesStatus = "Ended",
                inProduction = false,
                watchedCount = 8
            )
        )
    }
}
