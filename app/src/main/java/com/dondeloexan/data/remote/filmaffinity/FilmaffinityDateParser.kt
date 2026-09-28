package com.dondeloexan.data.remote.filmaffinity

import com.dondeloexan.domain.model.SpanishReleaseInfo
import java.time.LocalDate

/**
 * Normaliza las fechas de Filmaffinity ("6 de noviembre", "6 de noviembre de
 * 2026") a ISO. FA omite el año cuando es el de la fecha más cercana, así que
 * se deduce con la marca "(próx.)" de la plataforma:
 *   - próx.  -> la fecha está en el futuro (año actual o el siguiente)
 *   - normal -> la fecha ya pasó (año actual o el anterior)
 */
object FilmaffinityDateParser {

    private val DAY_MONTH = Regex("""(\d{1,2})\s+de\s+([a-záéíóúñ]{3,})(?:\s+de\s+(\d{4}))?""", RegexOption.IGNORE_CASE)

    fun toIsoDate(label: String, isUpcoming: Boolean, today: LocalDate = LocalDate.now()): String? {
        val match = DAY_MONTH.find(label) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = SpanishReleaseInfo.monthFromName(match.groupValues[2]) ?: return null
        if (day !in 1..31) return null

        val explicitYear = match.groupValues[3].toIntOrNull()
        val year = explicitYear ?: deduceYear(day, month, isUpcoming, today)
        return try {
            LocalDate.of(year, month, day).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun deduceYear(day: Int, month: Int, isUpcoming: Boolean, today: LocalDate): Int =
        try {
            val current = LocalDate.of(today.year, month, day)
            if (isUpcoming) {
                if (current.isBefore(today)) today.year + 1 else today.year
            } else {
                if (current.isAfter(today)) today.year - 1 else today.year
            }
        } catch (e: Exception) {
            today.year
        }
}
