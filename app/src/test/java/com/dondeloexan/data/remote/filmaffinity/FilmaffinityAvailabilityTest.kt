package com.dondeloexan.data.remote.filmaffinity

import io.ktor.client.HttpClient
import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * El bloque en línea `div.movie-tabs-cats` es el que trae la fecha española en
 * las fichas de serie (el popover `#movie-tabs-cats-popover-template` solo
 * aparece en películas), así que se prueba con el HTML real de
 * filmaffinity.com/es/film493346.html (Babylon Berlin).
 */
class FilmaffinityAvailabilityTest {

    private val scraper = FilmaffinityScraper(mockk<HttpClient>())

    private val babylonBerlinBlock = """
        <div class="movie-tabs-cats text-nowrap text-end">
            <a title="Movistar Plus+ (próx.)" class="first-category" href="https://www.filmaffinity.com/es/rdcat.php?id=upc_movistar_f">Movistar Plus+ (próx.)</a>

                <a href="https://www.filmaffinity.com/es/rdcat.php?id=upc_movistar_f"><strong>6 de noviembre</strong></a>


        </div><!-- movie-categories -->
    """.trimIndent()

    @Test
    fun `lee plataforma y fecha del bloque real de Babylon Berlin`() {
        val releases = scraper.parseInlineMovieTabsCats(Jsoup.parse(babylonBerlinBlock))

        assertEquals(1, releases.size)
        assertEquals("Movistar Plus+", releases[0].platformName)
        assertEquals("6 de noviembre", releases[0].dateLabel)
        // Hoy es 28/09/2026 y la plataforma está marcada "(próx.)" -> 6/11/2026
        assertEquals(LocalDate.of(2026, 11, 6).toString(), releases[0].releaseDate)
    }

    @Test
    fun `deduce el ano pasado cuando la fecha ya paso`() {
        val html = """
            <div class="movie-tabs-cats">
                <a title="Filmin" class="first-category" href="https://www.filmaffinity.com/es/rdcat.php?id=filmin">Filmin</a>
                <a href="https://www.filmaffinity.com/es/rdcat.php?id=filmin"><strong>12 de mayo</strong></a>
            </div>
        """.trimIndent()

        val iso = scraper.parseInlineMovieTabsCats(Jsoup.parse(html)).first().releaseDate
        assertEquals("2026-05-12", iso)
    }

    @Test
    fun `respeta el ano explicito`() {
        val html = """
            <div class="movie-tabs-cats">
                <a title="SkyShowtime (próx.)" class="first-category" href="https://www.filmaffinity.com/es/rdcat.php?id=sky">SkyShowtime (próx.)</a>
                <a href="https://www.filmaffinity.com/es/rdcat.php?id=sky"><strong>6 de noviembre de 2027</strong></a>
            </div>
        """.trimIndent()

        assertEquals("2027-11-06", scraper.parseInlineMovieTabsCats(Jsoup.parse(html)).first().releaseDate)
    }

    @Test
    fun `ignora entradas no espanolas y sin fecha`() {
        val html = """
            <div class="movie-tabs-cats">
                <a title="Prime Video (USA)" class="first-category" href="https://www.filmaffinity.com/es/cat_new_th_us">Prime Video (USA)</a>
                <a href="https://www.filmaffinity.com/es/cat_new_th_us"><strong>6 de noviembre</strong></a>
            </div>
            <div class="movie-tabs-cats">
                <a title="Netflix" class="first-category" href="https://www.filmaffinity.com/es/rdcat.php?id=netflix">Netflix</a>
            </div>
        """.trimIndent()

        assertTrue(scraper.parseInlineMovieTabsCats(Jsoup.parse(html)).isEmpty())
    }

    @Test
    fun `la proxima fecha futura es la que decide el estreno pendiente`() {
        val releases = scraper.parseInlineMovieTabsCats(Jsoup.parse(babylonBerlinBlock))
        val today = LocalDate.of(2026, 9, 28)
        val upcoming = releases.firstOrNull {
            val iso = it.releaseDate ?: return@firstOrNull false
            LocalDate.parse(iso).isAfter(today)
        }

        assertEquals("Movistar Plus+", upcoming?.platformName)
    }

    @Test
    fun `una etiqueta sin mes reconocible no produce fecha`() {
        assertNull(FilmaffinityDateParser.toIsoDate("próximamente", isUpcoming = true))
    }
}
