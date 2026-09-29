package com.dondeloexan.domain.model

import java.time.LocalDate

/**
 * Fecha de estreno EN ESPAÑA de una temporada, obtenida de una búsqueda web.
 *
 * TMDB solo publica la fecha original (p. ej. el estreno alemán de Babylon
 * Berlin T5 el 10/09/2026), así que para el usuario español la app se apoya en
 * esta búsqueda y NUNCA da por estrenada una temporada cuya fecha española
 * sigue en el futuro.
 */
data class SpanishReleaseInfo(
    val season: Int? = null,
    val day: Int? = null,
    val month: Int,
    val year: Int,
    val platform: String? = null,
    val sourceUrl: String? = null,
    val snippet: String? = null
) {
    /** Primer día del mes cuando la fuente solo da mes y año. */
    val date: LocalDate get() = LocalDate.of(year, month, day ?: 1)

    val monthPrecision: Boolean get() = day == null

    /** "noviembre de 2026" o "19 de septiembre de 2026". */
    fun label(): String = buildString {
        if (day != null) append("$day de ")
        append("${monthName(month)} de $year")
    }

    fun isoDate(): String = date.toString()

    companion object {

        /**
         * REGLA ÚNICA: temporada cuyo estreno en España sigue en el futuro.
         *
         * Devuelve [season] solo si la fecha española no ha llegado todavía; es
         * lo que impide contar esa temporada como emitida (TMDB solo publica la
         * fecha original, p. ej. el estreno alemán). Acepta ISO con "T" o
         * espacio y devuelve null ante cualquier dato no parseable.
         */
        fun pendingSpanishReleaseSeason(
            season: Int?,
            isoDate: String?,
            today: LocalDate = LocalDate.now()
        ): Int? {
            season ?: return null
            val parsed = isoDate ?: return null
            val date = try {
                LocalDate.parse(parsed.substringBefore("T").substringBefore(" ").trim())
            } catch (e: Exception) {
                return null
            }
            return season.takeIf { date.isAfter(today) }
        }
        val MONTHS_ES = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )

        fun monthName(month: Int): String = MONTHS_ES.getOrElse(month - 1) { "mes $month" }

        /** Acepta "noviembre", "nov", "sept." … ignorando acentos y mayúsculas. */
        fun monthFromName(name: String): Int? {
            val normalized = name.trim().lowercase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u")
                .trimEnd('.')
            if (normalized.isEmpty()) return null
            val exact = MONTHS_ES.indexOfFirst { it == normalized }
            if (exact >= 0) return exact + 1
            if (normalized.length >= 3) {
                val prefix = normalized.take(4)
                val byPrefix = MONTHS_ES.indexOfFirst { it.startsWith(prefix) || prefix.startsWith(it.take(4)) }
                if (byPrefix >= 0) return byPrefix + 1
            }
            return null
        }
    }
}
