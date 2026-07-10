package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.OmdbDetailResponse
import com.dondeloexan.data.remote.dto.OmdbSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class OmdbApi(
    private val client: HttpClient,
    private val apiKey: String
) {

    suspend fun getByImdbId(imdbId: String, plot: String = "full"): OmdbDetailResponse {
        val response = client.get("") {
            parameter("i", imdbId)
            parameter("apikey", apiKey)
            parameter("plot", plot)
        }
        return response.body()
    }

    suspend fun getByTitle(title: String, type: String? = null, year: Int? = null, plot: String = "short"): OmdbDetailResponse {
        val response = client.get("") {
            parameter("t", title)
            parameter("apikey", apiKey)
            parameter("plot", plot)
            type?.let { parameter("type", it) }
            year?.let { parameter("y", it) }
        }
        return response.body()
    }

    suspend fun search(query: String, type: String? = null, page: Int = 1): OmdbSearchResponse {
        val response = client.get("") {
            parameter("s", query)
            parameter("apikey", apiKey)
            parameter("page", page)
            type?.let { parameter("type", it) }
        }
        return response.body()
    }
}
