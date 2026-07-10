package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.FaRapidItemDetail
import com.dondeloexan.data.remote.dto.FaRapidSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class FilmAffinityApi(private val client: HttpClient) {

    suspend fun search(query: String, lang: String = "es"): FaRapidSearchResponse {
        val response = client.get("v1/search") {
            parameter("search", query)
            parameter("lang", lang)
            parameter("cache_bd", true)
        }
        return response.body()
    }

    suspend fun getItemDetail(faUrl: String, lang: String = "es"): FaRapidItemDetail {
        val response = client.get("v1/item") {
            parameter("url", faUrl)
            parameter("lang", lang)
            parameter("cache_bd", true)
        }
        return response.body()
    }
}
