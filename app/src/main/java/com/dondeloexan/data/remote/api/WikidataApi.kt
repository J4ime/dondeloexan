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
    suspend fun getRelationships(wikidataId: String? = null, imdbId: String? = null): List<WikidataRelationship> {
        AppLogger.d("WikidataApi", "getRelationships called — wikidataId=$wikidataId, imdbId=$imdbId")
        val resolvedId = if (!wikidataId.isNullOrBlank()) {
            wikidataId
        } else if (!imdbId.isNullOrBlank()) {
            resolveWikidataId(imdbId)
        } else {
            AppLogger.d("WikidataApi", "No wikidataId nor imdbId provided")
            return emptyList()
        }
        if (resolvedId == null) {
            AppLogger.d("WikidataApi", "Could not resolve a Wikidata ID")
            return emptyList()
        }
        return try {
            val cleanId = resolvedId.removePrefix("http://www.wikidata.org/entity/")
                .removePrefix("https://www.wikidata.org/wiki/")
                .trim()
            val query = buildQuery(cleanId)
            val response = client.get("sparql?format=json&query=${encodeQuery(query)}")
            val body = response.bodyAsText()
            AppLogger.d("WikidataApi", "SPARQL response length=${body.length}")
            val parsed = json.decodeFromString<WikidataSparqlResponse>(body)
            val results = parsed.results.bindings.mapNotNull { binding ->
                try {
                    val relationType = binding["relationType"]?.value ?: return@mapNotNull null
                    val targetLabel = binding["targetLabel"]?.value ?: return@mapNotNull null
                    val tmdbTvId = binding["tmdbTvId"]?.value?.toIntOrNull()
                    val tmdbMovieId = binding["tmdbMovieId"]?.value?.toIntOrNull()
                    val foundImdbId = binding["imdbId"]?.value?.takeIf { it.startsWith("tt") }
                    WikidataRelationship(
                        relationType = relationType,
                        targetLabel = targetLabel,
                        tmdbTvId = tmdbTvId,
                        tmdbMovieId = tmdbMovieId,
                        imdbId = foundImdbId
                    )
                } catch (e: Exception) {
                    null
                }
            }
            AppLogger.d("WikidataApi", "Found ${results.size} relationships")
            results
        } catch (e: Exception) {
            AppLogger.e("WikidataApi", "getRelationships error for $resolvedId", e)
            emptyList()
        }
    }

    private suspend fun resolveWikidataId(imdbId: String): String? {
        return try {
            val query = "SELECT ?entity WHERE { ?entity wdt:P345 \"$imdbId\" }"
            val response = client.get("sparql?format=json&query=${encodeQuery(query)}")
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<WikidataSparqlResponse>(body)
            val bindings = parsed.results.bindings
            if (bindings.isEmpty()) {
                AppLogger.d("WikidataApi", "No Wikidata entity found for imdbId=$imdbId")
                null
            } else {
                val entityUri = bindings[0]["entity"]?.value ?: return null
                entityUri.substringAfterLast("/")
                    .takeIf { it.startsWith("Q") }
                    .also { AppLogger.d("WikidataApi", "Resolved imdbId=$imdbId -> $it") }
            }
        } catch (e: Exception) {
            AppLogger.e("WikidataApi", "resolveWikidataId error for $imdbId", e)
            null
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
