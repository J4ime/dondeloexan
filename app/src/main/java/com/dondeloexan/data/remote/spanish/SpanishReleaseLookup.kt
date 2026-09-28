package com.dondeloexan.data.remote.spanish

import com.dondeloexan.domain.model.SpanishReleaseInfo
import com.dondeloexan.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URLEncoder

/** Un resultado de búsqueda: título, url y extracto. */
data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * Busca la fecha (y la plataforma) del estreno en España de una temporada.
 *
 * Google devuelve una página JS/consent sin extractos utilizables, así que se
 * usan Bing y DuckDuckGo (HTML plano), que sí traen el "Estreno: <mes> de
 * <año>" de medios españoles (p. ej. la ficha de EL PAÍS).
 */
class SpanishReleaseLookup(private val httpClient: HttpClient) {

    suspend fun find(title: String, originalTitle: String? = null, season: Int? = null): SpanishReleaseInfo? =
        withContext(Dispatchers.IO) {
            val titles = listOfNotNull(title.takeIf { it.isNotBlank() }, originalTitle?.takeIf { it.isNotBlank() })
                .distinct()
            for (base in titles) {
                for (query in buildQueries(base, season)) {
                    val hits = trySearch(query)
                    if (hits.isEmpty()) continue
                    val parsed = SpanishReleaseSnippetParser.parse(hits, expectedSeason = season)
                    if (parsed != null) {
                        AppLogger.i(
                            "SpanishRelease",
                            "'$base' T${season ?: "?"} -> ${parsed.label()} plataforma=${parsed.platform ?: "-"} (${parsed.sourceUrl ?: "-"})"
                        )
                        return@withContext parsed
                    }
                }
            }
            AppLogger.i("SpanishRelease", "'$title' T${season ?: "?"}: sin fecha española en la búsqueda")
            null
        }

    private fun buildQueries(base: String, season: Int?): List<String> {
        val seasonPart = if (season != null) "temporada $season" else "última temporada"
        return listOf(
            "\"$base\" $seasonPart estreno España",
            "\"$base\" estreno en España Movistar+",
            "\"$base\" $seasonPart estreno España fecha"
        )
    }

    private suspend fun trySearch(query: String): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sources = listOf(
            "bing" to "https://www.bing.com/search?q=$encoded&setlang=es&cc=ES",
            "ddg" to "https://html.duckduckgo.com/html/?q=$encoded&kl=es-es"
        )
        for ((name, url) in sources) {
            val html = try {
                httpClient.get(url) {
                    header("Accept-Language", "es-ES,es;q=0.9")
                }.bodyAsText()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("SpanishRelease", "búsqueda en $name falló: ${e.message}")
                continue
            }
            val hits = try {
                SearchHtmlParser.parse(html, engine = name)
            } catch (e: Exception) {
                AppLogger.e("SpanishRelease", "parseo de $name falló", e)
                emptyList()
            }
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }
}

/** Extrae los resultados del HTML de Bing o DuckDuckGo. */
object SearchHtmlParser {

    fun parse(html: String, engine: String): List<SearchHit> {
        val doc = Jsoup.parse(html)
        return when (engine) {
            "bing" -> parseBing(doc)
            else -> parseDuckDuckGo(doc)
        }
    }

    private fun parseBing(doc: Document): List<SearchHit> =
        doc.select("li.b_algo").mapNotNull { item ->
            val link = item.selectFirst("h2 a") ?: return@mapNotNull null
            val url = link.attr("href")
            if (!url.startsWith("http")) return@mapNotNull null
            val snippet = item.selectFirst("p")?.text().orEmpty()
            SearchHit(title = link.text(), url = url, snippet = snippet)
        }

    private fun parseDuckDuckGo(doc: Document): List<SearchHit> =
        doc.select("div.result").mapNotNull { item ->
            val link = item.selectFirst("a.result__a") ?: return@mapNotNull null
            val raw = link.attr("href")
            SearchHit(
                title = link.text(),
                url = unwrapDuckDuckGoUrl(raw),
                snippet = item.selectFirst("a.result__snippet, .result__snippet")?.text().orEmpty()
            )
        }

    /** DDG envuelve los enlaces: //duckduckgo.com/l/?uddg=<url codificada>. */
    fun unwrapDuckDuckGoUrl(raw: String): String {
        val index = raw.indexOf("uddg=")
        if (index < 0) return raw
        val encoded = raw.substring(index + "uddg=".length).substringBefore('&')
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            raw
        }
    }
}
