package com.dondeloexan.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Regla única de "temporada pendiente de estreno en España". Es la que impide
 * que Babylon Berlin T5 (estrenada en Alemania el 10/09/2026, en España el
 * 06/11/2026) cuente como temporada emitida.
 */
class SpanishReleaseInfoTest {

    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun `la temporada sigue pendiente si la fecha espanola es futura`() {
        assertEquals(
            5,
            SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-11-06", today)
        )
    }

    @Test
    fun `deja de estar pendiente el dia del estreno`() {
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-09-28", today))
    }

    @Test
    fun `deja de estar pendiente cuando la fecha ya paso`() {
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-09-10", today))
    }

    @Test
    fun `acepta ISO con T y con espacio`() {
        assertEquals(
            5,
            SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-11-06T00:00:00.000Z", today)
        )
        assertEquals(
            5,
            SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-11-06 00:00:00", today)
        )
    }

    @Test
    fun `mes sin dia sigue contando como pendiente`() {
        // Filmaffinity a veces solo da mes y anio: se guarda el dia 1 del mes.
        assertEquals(
            5,
            SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-11-01", today)
        )
    }

    @Test
    fun `sin temporada o sin fecha no hay nada pendiente`() {
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(null, "2026-11-06", today))
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(5, null, today))
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "", today))
    }

    @Test
    fun `una fecha no parseable no rompe la app`() {
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "sin fecha", today))
        assertNull(SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "06/11/2026", today))
    }

    @Test
    fun `el mes de una temporada futura cae en el anio siguiente si ya paso`() {
        val diciembre = SpanishReleaseInfo.pendingSpanishReleaseSeason(5, "2026-12-01", today)
        assertEquals(5, diciembre)
    }
}
