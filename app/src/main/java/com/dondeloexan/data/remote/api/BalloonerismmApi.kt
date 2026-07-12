package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.ImdbExternalIds
import com.dondeloexan.data.remote.dto.ImdbMovieDetailDto
import com.dondeloexan.data.remote.dto.ImdbSearchResponse
import com.dondeloexan.data.remote.dto.ImdbSeasonDetailDto
import com.dondeloexan.data.remote.dto.ImdbTvDetailDto
import com.dondeloexan.data.remote.dto.ImdbWatchProvidersResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class BalloonerismmApi(private val client: HttpClient) {

    suspend fun popularAll(language: String = "es-ES", page: Int = 1): ImdbSearchResponse {
        val response = client.get("popular/all") {
            parameter("language", language)
            parameter("page", page)
        }
        return response.body()
    }

    suspend fun popularMovies(language: String = "es-ES", page: Int = 1): ImdbSearchResponse {
        val response = client.get("popular/movie") {
            parameter("language", language)
            parameter("page", page)
        }
        return response.body()
    }

    suspend fun popularTv(language: String = "es-ES", page: Int = 1): ImdbSearchResponse {
        val response = client.get("popular/tv") {
            parameter("language", language)
            parameter("page", page)
        }
        return response.body()
    }

    suspend fun searchMulti(query: String, language: String = "es-ES", page: Int = 1): ImdbSearchResponse {
        val response = client.get("search/multi") {
            parameter("query", query)
            parameter("language", language)
            parameter("page", page)
        }
        return response.body()
    }

    suspend fun getMovieExternalIds(movieId: String): ImdbExternalIds {
        val response = client.get("movie/$movieId/external_ids")
        return response.body()
    }

    suspend fun getTvExternalIds(tvId: String): ImdbExternalIds {
        val response = client.get("tv/$tvId/external_ids")
        return response.body()
    }

    suspend fun getMovieDetail(movieId: String): ImdbMovieDetailDto {
        val response = client.get("movie/$movieId")
        return response.body()
    }

    suspend fun getTvDetail(tvId: String): ImdbTvDetailDto {
        val response = client.get("tv/$tvId")
        return response.body()
    }

    suspend fun getTvSeason(tvId: String, seasonNumber: Int): ImdbSeasonDetailDto {
        val response = client.get("tv/$tvId/season/$seasonNumber")
        return response.body()
    }

    suspend fun getMovieWatchProviders(movieId: String): ImdbWatchProvidersResponse {
        val response = client.get("movie/$movieId/watch/providers") {
            parameter("region", "ES")
        }
        return response.body()
    }

    suspend fun getTvWatchProviders(tvId: String): ImdbWatchProvidersResponse {
        val response = client.get("tv/$tvId/watch/providers") {
            parameter("region", "ES")
        }
        return response.body()
    }
}
