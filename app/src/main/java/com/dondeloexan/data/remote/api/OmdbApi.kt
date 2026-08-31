package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.OmdbDetailResponse
import com.dondeloexan.data.remote.dto.OmdbSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException

class OmdbApi(
    private val client: HttpClient,
    private val apiKey: String
) {

    private suspend fun <T> callWithTimeout(timeoutMs: Long, what: String, block: suspend () -> T): T {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            throw SocketTimeoutException("OMDb $what excedió ${timeoutMs}ms: ${e.message}")
        }
    }

    suspend fun getByImdbId(imdbId: String, plot: String = "full"): OmdbDetailResponse {
        return callWithTimeout(10_000, "byImdbId '$imdbId'") {
            val response = client.get("") {
                parameter("i", imdbId)
                parameter("apikey", apiKey)
                parameter("plot", plot)
            }
            response.body()
        }
    }

    suspend fun getByTitle(title: String, type: String? = null, year: Int? = null, plot: String = "short"): OmdbDetailResponse {
        return callWithTimeout(10_000, "byTitle '$title'") {
            val response = client.get("") {
                parameter("t", title)
                parameter("apikey", apiKey)
                parameter("plot", plot)
                type?.let { parameter("type", it) }
                year?.let { parameter("y", it) }
            }
            response.body()
        }
    }

    suspend fun search(query: String, type: String? = null, page: Int = 1): OmdbSearchResponse {
        return callWithTimeout(10_000, "search '$query'") {
            val response = client.get("") {
                parameter("s", query)
                parameter("apikey", apiKey)
                parameter("page", page)
                type?.let { parameter("type", it) }
            }
            response.body()
        }
    }
}
