package com.dondeloexan.data.remote.spanish

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpanishReleaseSnippetParserTest {

    private fun hit(title: String, url: String, snippet: String) = SearchHit(title, url, snippet)

    /** Extracto real de la ficha de EL PAÍS (Bing, 28/09/2026). */
    private val elPaisHit = hit(
        title = "Babylon Berlin | Series de Televisión | EL PAÍS",
        url = "https://elpais.com/television/series/babylon-berlin/",
        snippet = "Temporada 5 y última. Estreno: Noviembre de 2026. Nacionalidad: Alemania. " +
            "Sitúa la acción en febrero de 1933."
    )

    /** Trampa: web que NO habla de España y anuncia otra fecha. */
    private val trackerHit = hit(
        title = "¿Cuándo se estrena Temporada 5 de Babylon Berlin?",
        url = "https://www.tvshowtracker.eu/es/show/babylon-berlin-66980/next-season",
        snippet = "Temporada 5 de Babylon Berlin está prevista para el 19 de septiembre de 2026."
    )

    /** Extracto real sobre plataformas en España (sin fecha). */
    private val platformHit = hit(
        title = "Babylon Berlin estrena su quinta y última temporada",
        url = "https://www.altafidelidad.org/babylon-berlin-estrena-su-quinta-y-ultima-temporada/",
        snippet = "En España, las temporadas previas se han emitido a través de Movistar+, aunque " +
            "todavía no hay confirmación oficial sobre la plataforma que distribuirá esta última tanda."
    )

    @Test
    fun `extrae mes y año del estreno en España`() {
        val info = SpanishReleaseSnippetParser.parse(listOf(elPaisHit), expectedSeason = 5)!!

        assertEquals(5, info.season)
        assertEquals(2026, info.year)
        assertEquals(11, info.month)
        assertNull(info.day)
        assertTrue(info.monthPrecision)
        assertEquals("noviembre de 2026", info.label())
        assertEquals("2026-11-01", info.isoDate())
    }

    @Test
    fun `una web que solo dice prevista para una fecha no cuenta como estreno`() {
        assertNull(SpanishReleaseSnippetParser.parse(listOf(trackerHit), expectedSeason = 5))
    }

    @Test
    fun `un anuncio sin fecha no produce resultado`() {
        assertNull(SpanishReleaseSnippetParser.parse(listOf(platformHit), expectedSeason = 5))
    }

    @Test
    fun `prefiere la fecha española frente a una extranjera`() {
        val german = hit(
            title = "Babylon Berlin: la quinta temporada se estrena",
            url = "https://example.de/babylon-berlin-5",
            snippet = "La quinta temporada se estrena el 10 de septiembre de 2026 en Alemania."
        )
        val info = SpanishReleaseSnippetParser.parse(listOf(german, elPaisHit), expectedSeason = 5)!!

        assertEquals(11, info.month)
        assertEquals(2026, info.year)
    }

    @Test
    fun `detecta dia concreto y plataforma`() {
        val info = SpanishReleaseSnippetParser.parse(
            listOf(
                hit(
                    title = "Babylon Berlin T5 llega a Movistar Plus+",
                    url = "https://www.mundoplus.tv/series/babylon-berlin-t5/",
                    snippet = "La temporada 5 se estrena el 20 de noviembre de 2026 en España, en Movistar Plus+."
                )
            ),
            expectedSeason = 5
        )!!

        assertEquals(20, info.day)
        assertEquals(11, info.month)
        assertEquals("20 de noviembre de 2026", info.label())
        assertEquals("Movistar Plus+", info.platform)
    }

    @Test
    fun `acepta fecha numerica`() {
        val info = SpanishReleaseSnippetParser.parse(
            listOf(
                hit(
                    title = "Estreno de Babylon Berlin temporada 5",
                    url = "https://www.sensacine.com/series/serie-19804/temporada-52097/",
                    snippet = "Estreno en España: 20/11/2026."
                )
            ),
            expectedSeason = 5
        )!!

        assertEquals(20, info.day)
        assertEquals(11, info.month)
        assertEquals(2026, info.year)
    }

    @Test
    fun `nombres de mes con abreviaturas y acentos`() {
        assertEquals(11, com.dondeloexan.domain.model.SpanishReleaseInfo.monthFromName("nov"))
        assertEquals(11, com.dondeloexan.domain.model.SpanishReleaseInfo.monthFromName("Nov."))
        assertEquals(9, com.dondeloexan.domain.model.SpanishReleaseInfo.monthFromName("septiembre"))
        assertEquals(12, com.dondeloexan.domain.model.SpanishReleaseInfo.monthFromName("diciembre"))
        assertEquals(2, com.dondeloexan.domain.model.SpanishReleaseInfo.monthFromName("febrero"))
        assertNull(com.dondeloexan.domain.model.SpanishReleaseInfo.monthFromName("xyz"))
    }

    @Test
    fun `el parser de HTML saca los resultados de DuckDuckGo y desenvuelve la url`() {
        val html = """
            <html><body>
              <div class="result">
                <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Felpais.com%2Ftelevision%2Fseries%2Fbabylon-berlin%2F&amp;rut=abc">Babylon Berlin | EL PAÍS</a>
                <a class="result__snippet">Temporada 5 y última. Estreno: Noviembre de 2026.</a>
              </div>
            </body></html>
        """.trimIndent()

        val hits = SearchHtmlParser.parse(html, engine = "ddg")

        assertEquals(1, hits.size)
        assertEquals("https://elpais.com/television/series/babylon-berlin/", hits[0].url)
        assertEquals("Temporada 5 y última. Estreno: Noviembre de 2026.", hits[0].snippet)

        val info = SpanishReleaseSnippetParser.parse(hits, expectedSeason = 5)!!
        assertEquals(11, info.month)
        assertEquals(2026, info.year)
    }

    @Test
    fun `el parser de HTML saca los resultados de Bing`() {
        val html = """
            <html><body>
              <li class="b_algo">
                <h2><a href="https://elpais.com/television/series/babylon-berlin/">Babylon Berlin | EL PAÍS</a></h2>
                <div class="b_caption"><p>Temporada 5 y última. Estreno: Noviembre de 2026.</p></div>
              </li>
            </body></html>
        """.trimIndent()

        val hits = SearchHtmlParser.parse(html, engine = "bing")

        assertEquals(1, hits.size)
        assertEquals("https://elpais.com/television/series/babylon-berlin/", hits[0].url)

        val info = SpanishReleaseSnippetParser.parse(hits, expectedSeason = 5)!!
        assertEquals(11, info.month)
    }
}
