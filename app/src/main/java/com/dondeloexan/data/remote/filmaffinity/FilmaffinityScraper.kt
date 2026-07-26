package com.dondeloexan.data.remote.filmaffinity

import com.dondeloexan.domain.model.CriticReview
import com.dondeloexan.domain.model.Sentiment
import com.dondeloexan.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class FilmaffinityScraper(private val httpClient: HttpClient) {

    suspend fun searchMovieId(title: String, year: Int? = null): Int? = withContext(Dispatchers.IO) {
        try {
            val query = if (year != null) "$title $year" else title
            val html = httpClient.get("https://www.filmaffinity.com/es/search.php?stext=$query").bodyAsText()
            val doc = Jsoup.parse(html)
            val link = doc.selectFirst("a[href^=/es/film]") ?: return@withContext null
            val href = link.attr("href")
            val id = FILM_ID_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
            id
        } catch (e: Exception) {
            AppLogger.e("Filmaffinity", "searchMovieId error for $title", e)
            null
        }
    }

    suspend fun getProReviews(faMovieId: Int): List<CriticReview> = withContext(Dispatchers.IO) {
        try {
            val html = httpClient.get("https://www.filmaffinity.com/es/pro-reviews.php?movie-id=$faMovieId").bodyAsText()
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.pro-rev-table tbody tr")
            if (rows.isEmpty()) return@withContext emptyList()

            rows.mapNotNull { row ->
                try {
                    val authorEl = row.selectFirst("td.author .author-name a")
                    val publicationEl = row.selectFirst("td.author em a, td.author strong a")
                    val revTextEl = row.selectFirst("td.rev-text a, td.rev-text")
                    val biasEl = row.selectFirst("td.bias i")

                    val author = authorEl?.text()?.trim() ?: return@mapNotNull null
                    val publication = publicationEl?.text()?.trim() ?: ""
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
            }
        } catch (e: Exception) {
            AppLogger.e("Filmaffinity", "getProReviews error for $faMovieId", e)
            emptyList()
        }
    }

    private fun parseRating(text: String): String? {
        val starMatch = STAR_RATING_REGEX.find(text)
        if (starMatch != null) return starMatch.value

        val numericMatch = NUMERIC_RATING_REGEX.find(text)
        if (numericMatch != null) return numericMatch.value

        return null
    }

    companion object {
        private val FILM_ID_REGEX = Regex("/es/film(\\d+)\\.html")
        private val STAR_RATING_REGEX = Regex("[★☆½]{1,10}(\\s*\\(sobre \\d+\\))?")
        private val NUMERIC_RATING_REGEX = Regex("\\d{1,2}(\\.\\d)?\\s*\\(sobre \\d+\\)")
    }
}
