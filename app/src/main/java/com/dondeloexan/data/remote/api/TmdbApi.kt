package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.TmdbCompanySearchResponse
import com.dondeloexan.data.remote.dto.TmdbCollectionDto
import com.dondeloexan.data.remote.dto.TmdbCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbExternalIdsDto
import com.dondeloexan.data.remote.dto.TmdbFindResponse
import com.dondeloexan.data.remote.dto.TmdbMovieDto
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResponse
import com.dondeloexan.data.remote.dto.TmdbPersonCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbPersonDetailDto
import com.dondeloexan.data.remote.dto.TmdbPersonExternalIdsDto
import com.dondeloexan.data.remote.dto.TmdbPersonSearchResponse
import com.dondeloexan.data.remote.dto.TmdbTrendingResponse
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.data.remote.dto.TmdbMovieReleaseDatesResponse
import com.dondeloexan.data.remote.dto.TmdbWatchProvidersResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private suspend fun <T> callWithTimeout(timeoutMs: Long, what: String, block: suspend () -> T): T {
    return try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        throw java.net.SocketTimeoutException("Timeout de ${timeoutMs}ms en $what")
    }
}

class TmdbApiException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)

class TmdbApi(private val client: HttpClient) {

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(what: String): T {
        if (!status.isSuccess()) {
            throw TmdbApiException("TMDB HTTP ${status.value} en $what", status.value)
        }
        return body()
    }

    suspend fun searchMulti(query: String, language: String = "es-ES", page: Int = 1): TmdbMultiSearchResponse {
        return callWithTimeout(10_000, "search/multi '$query'") {
            val response = client.get("search/multi") {
                parameter("query", query)
                parameter("language", language)
                parameter("page", page)
            }
            response.body()
        }
    }

    suspend fun searchMovie(query: String, language: String = "es-ES", year: Int? = null): TmdbMultiSearchResponse {
        return callWithTimeout(10_000, "search/movie '$query'") {
            val response = client.get("search/movie") {
                parameter("query", query)
                parameter("language", language)
                year?.let { parameter("year", it) }
            }
            response.body()
        }
    }

    suspend fun searchTv(query: String, language: String = "es-ES"): TmdbMultiSearchResponse {
        return callWithTimeout(10_000, "search/tv '$query'") {
            val response = client.get("search/tv") {
                parameter("query", query)
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getMovieDetail(movieId: Int, language: String = "es-ES"): TmdbMovieDto {
        return callWithTimeout(12_000, "movie/$movieId") {
            val response = client.get("movie/$movieId") {
                parameter("language", language)
                parameter("append_to_response", "credits")
            }
            response.body()
        }
    }

    suspend fun getTvDetail(tvId: Int, language: String = "es-ES"): TmdbTvDetailDto {
        return callWithTimeout(12_000, "tv/$tvId") {
            val response = client.get("tv/$tvId") {
                parameter("language", language)
                parameter("append_to_response", "credits")
            }
            response.bodyOrThrow<TmdbTvDetailDto>("tv/$tvId")
        }
    }

    suspend fun getTvDetailLight(tvId: Int, language: String = "es-ES"): TmdbTvDetailDto {
        return callWithTimeout(12_000, "tv/$tvId (light)") {
            val response = client.get("tv/$tvId") {
                parameter("language", language)
            }
            response.bodyOrThrow<TmdbTvDetailDto>("tv/$tvId")
        }
    }

    suspend fun getMovieWatchProviders(movieId: Int): TmdbWatchProvidersResponse {
        return callWithTimeout(8_000, "movie/$movieId/watch/providers") {
            val response = client.get("movie/$movieId/watch/providers")
            response.body()
        }
    }

    suspend fun getTvWatchProviders(tvId: Int): TmdbWatchProvidersResponse {
        return callWithTimeout(8_000, "tv/$tvId/watch/providers") {
            val response = client.get("tv/$tvId/watch/providers")
            response.body()
        }
    }

    suspend fun findMovieByImdbId(imdbId: String, language: String = "es-ES"): TmdbFindResponse {
        return callWithTimeout(10_000, "find/$imdbId") {
            val response = client.get("find/$imdbId") {
                parameter("external_source", "imdb_id")
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun findTvByImdbId(imdbId: String, language: String = "es-ES"): TmdbFindResponse {
        return callWithTimeout(10_000, "find/$imdbId") {
            val response = client.get("find/$imdbId") {
                parameter("external_source", "imdb_id")
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getMovieReleaseDates(movieId: Int): TmdbMovieReleaseDatesResponse {
        return callWithTimeout(8_000, "movie/$movieId/release_dates") {
            val response = client.get("movie/$movieId/release_dates")
            response.body()
        }
    }

    suspend fun discoverMovie(
        language: String = "es-ES",
        page: Int = 1,
        watchProviders: String? = null,
        watchRegion: String? = "ES",
        releaseDateGte: String? = "2024-01-01",
        releaseDateLte: String? = null,
        sortBy: String? = "popularity.desc",
        voteCountGte: Int? = null,
        withPeople: String? = null,
        withCompanies: String? = null
    ): TmdbTrendingResponse {
        val response = client.get("discover/movie") {
            timeout {
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 15_000
            }
            parameter("language", language)
            parameter("page", page)
            parameter("sort_by", sortBy ?: "popularity.desc")
            if (!watchProviders.isNullOrBlank()) parameter("with_watch_providers", watchProviders)
            if (!watchRegion.isNullOrBlank()) parameter("watch_region", watchRegion)
            if (!releaseDateGte.isNullOrBlank()) parameter("primary_release_date.gte", releaseDateGte)
            if (!releaseDateLte.isNullOrBlank()) parameter("primary_release_date.lte", releaseDateLte)
            if (voteCountGte != null) parameter("vote_count.gte", voteCountGte)
            if (!withPeople.isNullOrBlank()) parameter("with_people", withPeople)
            if (!withCompanies.isNullOrBlank()) parameter("with_companies", withCompanies)
        }
        return response.body()
    }

    suspend fun discoverTv(
        language: String = "es-ES",
        page: Int = 1,
        watchProviders: String? = null,
        watchRegion: String? = "ES",
        firstAirDateGte: String? = null,
        firstAirDateLte: String? = null,
        sortBy: String? = "popularity.desc",
        voteCountGte: Int? = null,
        withCompanies: String? = null
    ): TmdbTrendingResponse {
        val response = client.get("discover/tv") {
            timeout {
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 15_000
            }
            parameter("language", language)
            parameter("page", page)
            parameter("sort_by", sortBy ?: "popularity.desc")
            if (!watchProviders.isNullOrBlank()) parameter("with_watch_providers", watchProviders)
            if (!watchRegion.isNullOrBlank()) parameter("watch_region", watchRegion)
            if (!firstAirDateGte.isNullOrBlank()) parameter("first_air_date.gte", firstAirDateGte)
            if (!firstAirDateLte.isNullOrBlank()) parameter("first_air_date.lte", firstAirDateLte)
            if (voteCountGte != null) parameter("vote_count.gte", voteCountGte)
            if (!withCompanies.isNullOrBlank()) parameter("with_companies", withCompanies)
        }
        return response.body()
    }

    suspend fun getTrending(language: String = "es-ES"): TmdbTrendingResponse {
        return callWithTimeout(10_000, "trending/all/week") {
            val response = client.get("trending/all/week") {
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getMovieCredits(movieId: Int): TmdbCreditsResponse {
        return callWithTimeout(12_000, "movie/$movieId/credits") {
            val response = client.get("movie/$movieId/credits")
            response.body()
        }
    }

    suspend fun getMovieExternalIds(movieId: Int): TmdbExternalIdsDto {
        return callWithTimeout(8_000, "movie/$movieId/external_ids") {
            val response = client.get("movie/$movieId/external_ids")
            response.body()
        }
    }

    suspend fun getTvExternalIds(tvId: Int): TmdbExternalIdsDto {
        return callWithTimeout(8_000, "tv/$tvId/external_ids") {
            val response = client.get("tv/$tvId/external_ids")
            response.body()
        }
    }

    suspend fun getTvCredits(tvId: Int): TmdbCreditsResponse {
        return callWithTimeout(12_000, "tv/$tvId/credits") {
            val response = client.get("tv/$tvId/credits")
            response.body()
        }
    }

    suspend fun getTvSeason(tvId: Int, seasonNumber: Int, language: String = "es-ES"): TmdbTvSeasonDetailDto {
        return callWithTimeout(12_000, "tv/$tvId/season/$seasonNumber") {
            val response = client.get("tv/$tvId/season/$seasonNumber") {
                parameter("language", language)
            }
            response.bodyOrThrow<TmdbTvSeasonDetailDto>("tv/$tvId/season/$seasonNumber")
        }
    }

    suspend fun searchPerson(query: String, page: Int = 1, language: String = "es-ES"): TmdbPersonSearchResponse {
        return callWithTimeout(10_000, "search/person '$query'") {
            val response = client.get("search/person") {
                parameter("query", query)
                parameter("page", page)
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun searchCompany(query: String, page: Int = 1, language: String = "es-ES"): TmdbCompanySearchResponse {
        return callWithTimeout(10_000, "search/company '$query'") {
            val response = client.get("search/company") {
                parameter("query", query)
                parameter("page", page)
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getPersonDetail(personId: Int): TmdbPersonDetailDto {
        return callWithTimeout(10_000, "person/$personId") {
            val response = client.get("person/$personId")
            response.body()
        }
    }

    suspend fun getPersonExternalIds(personId: Int): TmdbPersonExternalIdsDto {
        return callWithTimeout(8_000, "person/$personId/external_ids") {
            val response = client.get("person/$personId/external_ids")
            response.body()
        }
    }

    suspend fun getPersonTvCredits(personId: Int, language: String = "es-ES"): TmdbPersonCreditsResponse {
        return callWithTimeout(12_000, "person/$personId/tv_credits") {
            val response = client.get("person/$personId/tv_credits") {
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getPersonMovieCredits(personId: Int, language: String = "es-ES"): TmdbPersonCreditsResponse {
        return callWithTimeout(12_000, "person/$personId/movie_credits") {
            val response = client.get("person/$personId/movie_credits") {
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getCollection(collectionId: Int, language: String = "es-ES"): TmdbCollectionDto {
        return callWithTimeout(12_000, "collection/$collectionId") {
            val response = client.get("collection/$collectionId") {
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getMovieRecommendations(movieId: Int, language: String = "es-ES"): TmdbTrendingResponse {
        return callWithTimeout(10_000, "movie/$movieId/recommendations") {
            val response = client.get("movie/$movieId/recommendations") {
                parameter("language", language)
            }
            response.body()
        }
    }

    suspend fun getTvRecommendations(tvId: Int, language: String = "es-ES"): TmdbTrendingResponse {
        return callWithTimeout(10_000, "tv/$tvId/recommendations") {
            val response = client.get("tv/$tvId/recommendations") {
                parameter("language", language)
            }
            response.body()
        }
    }

    /**
     * Descarga un póster real (Coil) de la CDN para verificar de punta a punta
     * que la misma imagen que carga la app es accesible. Devuelve bytes leídos
     * o -1 si el body no declara tamaño.
     */
    suspend fun pingImage(): Int = callWithTimeout(8_000, "CDN imagen") {
        val response = client.get("https://image.tmdb.org/t/p/w500/tpW2X2DvxtTHJ61iJ7zNYYrJihs.jpg")
        val channel = response.bodyAsChannel()
        val buf = ByteArray(1024)
        var total = 0
        while (true) {
            val n = channel.readAvailable(buf, 0, buf.size)
            if (n <= 0) break
            total += n
        }
        total
    }
}
