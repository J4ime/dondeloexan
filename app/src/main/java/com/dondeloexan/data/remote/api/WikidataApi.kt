package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.WikidataSparqlResponse
import com.dondeloexan.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

data class WikidataRelationship(
    val relationType: String,
    val targetLabel: String,
    val tmdbTvId: Int?,
    val tmdbMovieId: Int?,
    val imdbId: String?
)

class WikidataApi(
    private val client: HttpClient,
    private val json: Json
) {
    suspend fun getRelationships(wikidataId: String): List<WikidataRelationship> {
        return try {
            val cleanId = wikidataId.removePrefix("http://www.wikidata.org/entity/")
                .removePrefix("https://www.wikidata.org/wiki/")
                .trim()
            val query = buildQuery(cleanId)
            val response = client.get("sparql?format=json&query=${encodeQuery(query)}")
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<WikidataSparqlResponse>(body)
            parsed.results.bindings.mapNotNull { binding ->
                try {
                    val relationType = binding["relationType"]?.value ?: return@mapNotNull null
                    val targetLabel = binding["targetLabel"]?.value ?: return@mapNotNull null
                    val tmdbTvId = binding["tmdbTvId"]?.value?.toIntOrNull()
                    val tmdbMovieId = binding["tmdbMovieId"]?.value?.toIntOrNull()
                    val imdbId = binding["imdbId"]?.value?.takeIf { it.startsWith("tt") }
                    WikidataRelationship(
                        relationType = relationType,
                        targetLabel = targetLabel,
                        tmdbTvId = tmdbTvId,
                        tmdbMovieId = tmdbMovieId,
                        imdbId = imdbId
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e("WikidataApi", "getRelationships error for $wikidataId", e)
            emptyList()
        }
    }

    private fun buildQuery(id: String): String = """
        SELECT ?relationType ?target ?targetLabel ?tmdbTvId ?tmdbMovieId ?imdbId WHERE {
          {
            wd:$id wdt:P144 ?target .    BIND("basado_en" AS ?relationType)
            ?target wdt:P31 wd:Q5398426 .
          } UNION {
            wd:$id wdt:P1235 ?target .   BIND("tiene_spin_off" AS ?relationType)
          } UNION {
            ?target wdt:P1235 wd:$id .   BIND("spin_off_de" AS ?relationType)
            ?target wdt:P31 wd:Q5398426 .
          } UNION {
            wd:$id wdt:P155 ?target .    BIND("sigue_a" AS ?relationType)
            ?target wdt:P31 wd:Q5398426 .
          } UNION {
            wd:$id wdt:P156 ?target .    BIND("seguido_por" AS ?relationType)
            ?target wdt:P31 wd:Q5398426 .
          }
          OPTIONAL { ?target wdt:P4983 ?tmdbTvId . }
          OPTIONAL { ?target wdt:P4947 ?tmdbMovieId . }
          OPTIONAL { ?target wdt:P345 ?imdbId . }
          SERVICE wikibase:label { bd:serviceParam wikibase:language "es,en". }
        }
    """.trimIndent()

    private fun encodeQuery(query: String): String =
        java.net.URLEncoder.encode(query, "UTF-8")
}
