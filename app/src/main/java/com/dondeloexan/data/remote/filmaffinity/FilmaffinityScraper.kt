package com.dondeloexan.data.remote.filmaffinity

import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.Sentiment
import com.dondeloexan.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

class FilmaffinityScraper(private val httpClient: HttpClient) {

    suspend fun searchMovieId(title: String, year: Int? = null): Int? = withContext(Dispatchers.IO) {
        try {
            val query = title
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.filmaffinity.com/es/search.php?stext=$encoded&stype=title&em=1"
            AppLogger.i("Filmaffinity", "searchMovieId: GET $url")
            val html = httpClient.get(url).bodyAsText()
            AppLogger.i("Filmaffinity", "searchMovieId: response length=${html.length}")
            val doc = Jsoup.parse(html)

            val link = doc.selectFirst("a[href*=/es/film]")
            if (link != null) {
                val href = link.attr("href")
                AppLogger.i("Filmaffinity", "searchMovieId: found a[href~=/es/film] -> $href")
                val id = FILM_ID_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                if (id != null) {
                    AppLogger.i("Filmaffinity", "searchMovieId: ✅ ID=$id from a tag")
                    return@withContext id
                }
                AppLogger.w("Filmaffinity", "searchMovieId: could not parse id from a tag '$href'")
            }

            val link2 = doc.selectFirst("link[rel=alternate][href*=/es/film]")
            if (link2 != null) {
                val href = link2.attr("href")
                AppLogger.i("Filmaffinity", "searchMovieId: found link[rel=alternate][href*=/es/film] -> $href")
                val id = FILM_ID_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                if (id != null) {
                    AppLogger.i("Filmaffinity", "searchMovieId: ✅ ID=$id from link tag")
                    return@withContext id
                }
                AppLogger.w("Filmaffinity", "searchMovieId: could not parse id from link '$href'")
            }

            val cards = doc.select(".movie-card")
            if (cards.isNotEmpty()) {
                AppLogger.i("Filmaffinity", "searchMovieId: found ${cards.size} movie cards, searching by year=$year")
                for (card in cards) {
                    val cardYear = card.selectFirst(".mc-year")?.text()?.trim()?.toIntOrNull()
                    val cardId = card.attr("data-movie-id").toIntOrNull()
                    AppLogger.i("Filmaffinity", "searchMovieId: card id=$cardId year=$cardYear")
                    if (cardId != null && (year == null || cardYear == year)) {
                        AppLogger.i("Filmaffinity", "searchMovieId: ✅ ID=$cardId from movie-card (year match=${cardYear == year})")
                        return@withContext cardId
                    }
                }
                val firstId = cards.first().attr("data-movie-id").toIntOrNull()
                if (firstId != null) {
                    AppLogger.i("Filmaffinity", "searchMovieId: ✅ ID=$firstId from first movie-card (no year match)")
                    return@withContext firstId
                }
            }

            AppLogger.w("Filmaffinity", "searchMovieId: no id found for '$query'")
            return@withContext null
        } catch (e: Exception) {
            AppLogger.e("Filmaffinity", "searchMovieId error for $title", e)
            null
        }
    }

    suspend fun getProReviews(faMovieId: Int): List<CriticReview> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.filmaffinity.com/es/pro-reviews.php?movie-id=$faMovieId"
            val html = httpClient.get(url).bodyAsText()
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.pro-rev-table tbody tr")
            if (rows.isEmpty()) return@withContext emptyList()

            rows.mapNotNull { row ->
                try {
                    val authorEl = row.selectFirst("td.author .author-name a")
                    val publicationEl = row.selectFirst("td.author em a, td.author strong a, td.author em, td.author strong")
                    val publicationOwnText = row.selectFirst("td.author")?.ownText()?.trim()
                    val revTextEl = row.selectFirst("td.rev-text a, td.rev-text")
                    val biasEl = row.selectFirst("td.bias i")

                    val author = authorEl?.text()?.trim() ?: return@mapNotNull null
                    val publication = publicationEl?.text()?.trim()
                        ?: publicationOwnText?.takeIf { it.isNotBlank() }
                        ?: ""
                    val text = revTextEl?.text()?.trim() ?: ""
                    val url = revTextEl?.attr("href")?.takeIf { it.startsWith("http") }
                    val sentiment = when {
                        biasEl?.hasClass("pos") == true -> Sentiment.POSITIVE
                        biasEl?.hasClass("neg") == true -> Sentiment.NEGATIVE
                        else -> Sentiment.NEUTRAL
                    }
                    val rating = parseRating(text)

                    CriticReview(
                        author = author,
                        publication = publication,
                        text = text.removeSurrounding("\""),
                        rating = rating,
                        url = url,
                        sentiment = sentiment
                    )
                } catch (e: Exception) {
                    AppLogger.w("Filmaffinity", "skip review row: ${e.message}")
                    null
                }
            }.let { reviews ->
                val fromSpanish = reviews.filter { it.publication.normalize() in SPANISH_MEDIA }
                val nonSpanish = reviews.filter { it.publication.normalize() !in SPANISH_MEDIA }
                val (priority, rest) = fromSpanish.partition { it.publication.normalize() in PRIORITY_MEDIA }
                (priority + rest).take(5) + nonSpanish.take((5 - fromSpanish.size).coerceAtLeast(0))
            }
        } catch (e: Exception) {
            AppLogger.e("Filmaffinity", "getProReviews error for $faMovieId", e)
            emptyList()
        }
    }

    data class FaPageData(
        val rating: Float?,
        val vodReleases: List<PlatformReleaseDate>
    )

    suspend fun getMoviePageData(faMovieId: Int): FaPageData = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.filmaffinity.com/es/film$faMovieId.html"
            AppLogger.i("Filmaffinity", "getMoviePageData: GET $url")
            val html = httpClient.get(url).bodyAsText()
            val doc = Jsoup.parse(html)

            val rating = doc.selectFirst("#movie-rat-avg")?.text()?.trim()
                ?.replace(",", ".")?.toFloatOrNull()
            AppLogger.i("Filmaffinity", "getMoviePageData: rating=$rating (raw=${doc.selectFirst("#movie-rat-avg")?.text()})")

            val releases = mutableListOf<PlatformReleaseDate>()

            val popover = doc.selectFirst("#movie-tabs-cats-popover-template")
            if (popover != null) {
                val items = popover.select("li a[href*=rdcat.php]")
                AppLogger.i("Filmaffinity", "getMoviePageData: found ${items.size} popover items with rdcat.php")
                for (item in items) {
                    val href = item.attr("href")
                    val dateFromHash = href.substringAfter("#", "").takeIf { it.isNotBlank() }
                    val dateLabel = item.selectFirst("strong")?.text()?.trim()
                    val fullText = item.text()?.trim()
                    AppLogger.i("Filmaffinity", "getMoviePageData:   popover item href='$href' dateFromHash='$dateFromHash' dateLabel='$dateLabel' fullText='$fullText'")
                    if (dateLabel == null || fullText == null) continue
                    val platformName = fullText.removeSuffix(dateLabel).trim()
                    if (platformName.startsWith("Cartelera", ignoreCase = true)) {
                        AppLogger.i("Filmaffinity", "getMoviePageData:     skipping cinema entry '$platformName'")
                        continue
                    }
                    if (platformName.isNotBlank()) {
                        val cleanName = platformName.removeSuffix(" (próx.)").trim()
                        releases.add(PlatformReleaseDate(
                            platformName = cleanName,
                            dateLabel = dateLabel,
                            releaseDate = dateFromHash
                        ))
                    }
                }
            } else {
                AppLogger.w("Filmaffinity", "getMoviePageData: popover #movie-tabs-cats-popover-template not found")
            }

            if (releases.isEmpty()) {
                AppLogger.w("Filmaffinity", "getMoviePageData: no VOD releases found for faId=$faMovieId")
            }

            AppLogger.i("Filmaffinity", "getMoviePageData: ${releases.size} VOD releases for faId=$faMovieId")
            FaPageData(rating = rating, vodReleases = releases)
        } catch (e: Exception) {
            AppLogger.e("Filmaffinity", "getMoviePageData error for $faMovieId", e)
            FaPageData(rating = null, vodReleases = emptyList())
        }
    }

    private fun parseRating(text: String): String? {
        val starMatch = STAR_RATING_REGEX.find(text)
        if (starMatch != null) return starMatch.value

        val numericMatch = NUMERIC_RATING_REGEX.find(text)
        if (numericMatch != null) return numericMatch.value

        return null
    }

    private fun String.normalize(): String =
        lowercase()
            .replace('\u00a0', ' ')
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
            .trim()

    companion object {
        private val FILM_ID_REGEX = Regex("/es/film(\\d+)\\.html")
        private val STAR_RATING_REGEX = Regex("[★☆½]{1,10}(\\s*\\(sobre \\d+\\))?")
        private val NUMERIC_RATING_REGEX = Regex("\\d{1,2}(\\.\\d)?\\s*\\(sobre \\d+\\)")
        private val PRIORITY_MEDIA = setOf("el pais")
        private val SPANISH_MEDIA = setOf(
            "el pais", "el mundo", "abc", "la vanguardia", "el periodico",
            "cinemania", "fotogramas", "sensacine", "el confidencial",
            "eldiario.es", "rtve", "cadena ser", "onda cero",
            "20 minutos", "la razon", "publico"
        )
    }
}
