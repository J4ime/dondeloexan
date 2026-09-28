package com.dondeloexan.data.remote.spanish

import com.dondeloexan.domain.model.SpanishReleaseInfo

/**
 * Extrae "Estreno en España: <mes/fecha>" de los extractos de búsqueda.
 *
 * Es una función pura y sin red a propósito: así se puede probar con extractos
 * reales (EL PAÍS, SensaCine, etc.) sin depender de que el buscador responda.
 */
object SpanishReleaseSnippetParser {

    private val SEASON_REGEX = Regex("""(?i)temporada\s+(\d+)""")
    private val DAY_MONTH_YEAR_REGEX = Regex(
        """(?i)estren\w*[^.\n;]{0,60}?(\d{1,2})\s+de\s+([a-záéíóúñ]{3,})\s+(?:de\s+)?(\d{4})"""
    )
    private val NUMERIC_DATE_REGEX = Regex("""(?i)estren\w*[^.\n;]{0,40}?(\d{1,2})/(\d{1,2})/(\d{4})""")
    private val MONTH_YEAR_REGEX = Regex(
        """(?i)estren\w*\s*(?:en\s+espa[ñn]a\s*)?[:\-–]\s*([a-záéíóúñ]{3,})\s+de\s+(\d{4})"""
    )

    /** Plataformas que operan en España, de más larga a más corta. */
    private val PLATFORMS = listOf(
        "Movistar Plus+ Ficción Total", "Movistar Plus+", "Movistar+", "Movistar",
        "SkyShowtime", "Prime Video", "Amazon Prime Video", "Disney+", "Disney Plus",
        "HBO Max", "Max", "Netflix", "Apple TV+", "Apple TV", "Paramount+",
        "Filmin", "Atresplayer", "RTVE Play", "Mitele", "FlixOlé", "Movistar Estrenos"
    )

    private val SPAIN_SIGNAL = listOf("españa", "espana", "movistar", "en españa")
    private val FOREIGN_COUNTRIES = listOf(
        "alemania", "deutschland", "estados unidos", "ee. uu.", "ee uu", "reino unido",
        "francia", "italia", "méxico", "mexico", "argentina", "usa"
    )

    fun parse(hits: List<SearchHit>, expectedSeason: Int? = null): SpanishReleaseInfo? {
        val candidates = hits.mapIndexedNotNull { index, hit -> toCandidate(hit, index, expectedSeason) }
        if (candidates.isEmpty()) return null
        return candidates.maxWithOrNull(
            compareBy<Candidate> { it.score }
                .thenBy { if (it.info.day != null) 1 else 0 }
                .thenByDescending { it.order }
        )?.info
    }

    private data class Candidate(val info: SpanishReleaseInfo, val score: Int, val order: Int)

    private fun toCandidate(hit: SearchHit, order: Int, expectedSeason: Int?): Candidate? {
        val rawText = "${hit.title} ${hit.snippet}".replace(Regex("\\s+"), " ")
        val answer = parseDate(rawText) ?: return null
        // "Nacionalidad: Alemania" habla del país de producción, no de dónde se
        // estrena: se elimina antes de puntuar para no penalizar a EL PAÍS.
        val text = rawText
            .replace(Regex("(?i)(nacionalidad|pa[íi]s)\\s*:\\s*[\\p{L}]+"), " ")
            .replace(Regex("\\s+"), " ")
        val normalized = text.lowercase()

        val season = SEASON_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: expectedSeason

        // El contexto se calcula sobre el texto YA saneado: así un
        // "Nacionalidad: Alemania" no cuenta como estreno en el extranjero.
        val afterDate = dateContext(text, answer)

        var score = 0
        if (SPAIN_SIGNAL.any { normalized.contains(it) }) score += 2
        if (FOREIGN_COUNTRIES.any { afterDate.contains(it) }) score -= 3
        if (expectedSeason != null && season == expectedSeason) score += 1
        if (hit.url.contains(".es/") || hit.url.endsWith(".es")) score += 1

        return Candidate(
            info = SpanishReleaseInfo(
                season = season,
                day = answer.day,
                month = answer.month,
                year = answer.year,
                platform = detectPlatform(text),
                sourceUrl = hit.url,
                snippet = text.take(300)
            ),
            score = score,
            order = order
        )
    }

    /** Trozo de texto inmediatamente posterior a la fecha ("… 2026 en Alemania"). */
    private fun dateContext(text: String, answer: DateAnswer): String {
        val marker = if (answer.day != null) {
            "${answer.day} de ${SpanishReleaseInfo.monthName(answer.month)}"
        } else {
            SpanishReleaseInfo.monthName(answer.month)
        }
        val index = text.lowercase().indexOf(marker)
        val start = if (index >= 0) index + marker.length else 0
        return text.substring(start, (start + 60).coerceAtMost(text.length)).lowercase()
    }

    private data class DateAnswer(val day: Int?, val month: Int, val year: Int)

    private fun parseDate(text: String): DateAnswer? {
        DAY_MONTH_YEAR_REGEX.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = SpanishReleaseInfo.monthFromName(match.groupValues[2])
            val year = match.groupValues[3].toIntOrNull()
            if (day != null && month != null && valid(year, month, day)) {
                return DateAnswer(day, month, year!!)
            }
        }
        NUMERIC_DATE_REGEX.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3].toIntOrNull()
            if (day != null && month != null && valid(year, month, day)) {
                return DateAnswer(day, month, year!!)
            }
        }
        MONTH_YEAR_REGEX.find(text)?.let { match ->
            val month = SpanishReleaseInfo.monthFromName(match.groupValues[1])
            val year = match.groupValues[2].toIntOrNull()
            if (month != null && valid(year, month, 1)) {
                return DateAnswer(null, month, year!!)
            }
        }
        return null
    }

    private fun valid(year: Int?, month: Int, day: Int): Boolean {
        if (year == null || year !in 1900..2100) return false
        if (month !in 1..12) return false
        return day in 1..31
    }

    private fun detectPlatform(text: String): String? {
        val normalized = text.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
        return PLATFORMS.firstOrNull { platform ->
            val p = platform.lowercase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u")
            normalized.contains(p)
        }
    }
}
