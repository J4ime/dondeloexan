package com.dondeloexan.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regla del badge "Capítulo final": solo si la serie ha terminado y el usuario
 * la ha visto entera. Con una temporada pendiente de estreno en España
 * (Babylon Berlin T5) no debe aparecer aunque el usuario la haya visto toda.
 */
class IsFinalEpisodeWatchedTest {

    @Test
    fun `serie terminada y vista entera si lo muestra`() {
        assertTrue(
            isFinalEpisodeWatched(
                releasedEpisodes = 32,
                totalEpisodes = 32,
                watchedCount = 32,
                hasFuture = false
            )
        )
    }

    @Test
    fun `queda un capitulo sin ver no se muestra`() {
        assertFalse(
            isFinalEpisodeWatched(
                releasedEpisodes = 32,
                totalEpisodes = 32,
                watchedCount = 31,
                hasFuture = false
            )
        )
    }

    @Test
    fun `queda un capitulo sin estrenar no se muestra`() {
        // Babylon Berlin: 24 emitidos de 32 (la T5 va por detrás).
        assertFalse(
            isFinalEpisodeWatched(
                releasedEpisodes = 24,
                totalEpisodes = 32,
                watchedCount = 24,
                hasFuture = false
            )
        )
    }

    @Test
    fun `una temporada pendiente de estreno en Espana lo descarta`() {
        // Todo emitido y visto, pero la T5 sigue pendiente en España.
        assertFalse(
            isFinalEpisodeWatched(
                releasedEpisodes = 32,
                totalEpisodes = 32,
                watchedCount = 32,
                hasFuture = true
            )
        )
    }

    @Test
    fun `sin total conocido se apoya en los emitidos`() {
        assertTrue(
            isFinalEpisodeWatched(
                releasedEpisodes = 8,
                totalEpisodes = null,
                watchedCount = 8,
                hasFuture = false
            )
        )
        assertFalse(
            isFinalEpisodeWatched(
                releasedEpisodes = 8,
                totalEpisodes = null,
                watchedCount = 7,
                hasFuture = false
            )
        )
    }

    @Test
    fun `sin datos de capitulos no se muestra`() {
        assertFalse(
            isFinalEpisodeWatched(
                releasedEpisodes = null,
                totalEpisodes = null,
                watchedCount = 10,
                hasFuture = false
            )
        )
        assertFalse(
            isFinalEpisodeWatched(
                releasedEpisodes = 0,
                totalEpisodes = 0,
                watchedCount = 0,
                hasFuture = false
            )
        )
    }
}
