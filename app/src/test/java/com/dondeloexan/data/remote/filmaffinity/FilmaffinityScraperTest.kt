package com.dondeloexan.data.remote.filmaffinity

import com.dondeloexan.domain.model.Sentiment
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.net.URLEncoder

class FilmaffinityScraperTest {

    @Test
    fun `url encoding encodes spaces as plus`() {
        val query = "The Matrix 1999"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assert(encoded == "The+Matrix+1999")
    }

    @Test
    fun `url encoding encodes special characters`() {
        val query = "Los Señores del Acero"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assert(encoded == "Los+Se%C3%B1ores+del+Acero")
    }

    @Test
    fun `url encoding encodes ampersand`() {
        val query = "A & B 2020"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assert(encoded == "A+%26+B+2020")
    }

    @Test
    fun `url encoding handles numeric characters`() {
        val query = "2001: A Space Odyssey"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assert(encoded == "2001%3A+A+Space+Odyssey")
    }

    @Test
    fun `url encoding combines title and year`() {
        val query = "The Godfather 1972"
        val encoded = URLEncoder.encode(query, "UTF-8")
        assert(encoded == "The+Godfather+1972")
    }

    @Test
    fun `Jsoup parses film id from search results`() {
        val html = """
            <html><body>
                <div class="searched">
                    <a href="/es/film12345.html">Movie Title</a>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val link = doc.selectFirst("a[href^=/es/film]")
        val href = link?.attr("href")
        val id = Regex("/es/film(\\d+)\\.html").find(href ?: "")?.groupValues?.get(1)?.toIntOrNull()

        assert(link != null)
        assert(id == 12345)
    }

    @Test
    fun `Jsoup returns null when no film link found`() {
        val html = """
            <html><body>
                No film links here
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val link = doc.selectFirst("a[href^=/es/film]")

        assert(link == null)
    }

    @Test
    fun `Jsoup parses pro reviews table`() {
        val html = """
            <html><body>
                <table class="pro-rev-table"><tbody>
                    <tr>
                        <td class="author"><span class="author-name"><a>Critic One</a></span><em><a>Pub A</a></em></td>
                        <td class="rev-text"><a>Great film!</a></td>
                        <td class="bias"><i class="pos"></i></td>
                    </tr>
                    <tr>
                        <td class="author"><span class="author-name"><a>Critic Two</a></span><em><a>Pub B</a></em></td>
                        <td class="rev-text"><a href="http://example.com">Not bad</a></td>
                        <td class="bias"><i class="neg"></i></td>
                    </tr>
                </tbody></table>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val rows = doc.select("table.pro-rev-table tbody tr")

        assert(rows.size == 2)

        val row1 = rows[0]
        assert(row1.selectFirst("td.author .author-name a")?.text()?.trim() == "Critic One")
        assert(row1.selectFirst("td.author em a")?.text()?.trim() == "Pub A")
        assert(row1.selectFirst("td.rev-text a")?.text()?.trim() == "Great film!")
        assert(row1.selectFirst("td.bias i")?.hasClass("pos") == true)

        val row2 = rows[1]
        assert(row2.selectFirst("td.author .author-name a")?.text()?.trim() == "Critic Two")
        assert(row2.selectFirst("td.bias i")?.hasClass("neg") == true)
        assert(row2.selectFirst("td.rev-text a")?.attr("href") == "http://example.com")
    }

    @Test
    fun `Jsoup returns empty rows for empty table`() {
        val html = """
            <html><body>
                <table class="pro-rev-table"><tbody>
                </tbody></table>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val rows = doc.select("table.pro-rev-table tbody tr")

        assert(rows.isEmpty())
    }
}
