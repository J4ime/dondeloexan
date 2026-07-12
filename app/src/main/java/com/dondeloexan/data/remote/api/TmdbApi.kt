package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.TmdbCreditsResponse
import com.dondeloexan.data.remote.dto.TmdbExternalIdsDto
import com.dondeloexan.data.remote.dto.TmdbMovieDto
import com.dondeloexan.data.remote.dto.TmdbMultiSearchResponse
import com.dondeloexan.data.remote.dto.TmdbTrendingResponse
import com.dondeloexan.data.remote.dto.TmdbTvDetailDto
import com.dondeloexan.data.remote.dto.TmdbTvSeasonDetailDto
import com.dondeloexan.data.remote.dto.TmdbWatchProvidersResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class TmdbApi(private val client: HttpClient) {

    suspend fun searchMulti(query: String, language: String = "es-ES", page: Int = 1): TmdbMultiSearchResponse {
        val response = client.get("search/multi") {
            parameter("query", query)
            parameter("language", language)
            parameter("page", page)
        }
        return response.body()
    }

    suspend fun searchMovie(query: String, language: String = "es-ES", year: Int? = null): TmdbMultiSearchResponse {
        val response = client.get("search/movie") {
            parameter("query", query)
            parameter("language", language)
            year?.let { parameter("year", it) }
        }
        return response.body()
    }

    suspend fun searchTv(query: String, language: String = "es-ES"): TmdbMultiSearchResponse {
        val response = client.get("search/tv") {
            parameter("query", query)
            parameter("language", language)
        }
        return response.body()
    }

    suspend fun getMovieDetail(movieId: Int, language: String = "es-ES"): TmdbMovieDto {
        val response = client.get("movie/$movieId") {
            parameter("language", language)
            parameter("append_to_response", "credits")
        }
        return response.body()
    }

    suspend fun getTvDetail(tvId: Int, language: String = "es-ES"): TmdbTvDetailDto {
        val response = client.get("tv/$tvId") {
            parameter("language", language)
            parameter("append_to_response", "credits")
        }
        return response.body()
    }

    suspend fun getMovieWatchProviders(movieId: Int): TmdbWatchProvidersResponse {
        val response = client.get("movie/$movieId/watch/providers")
        return response.body()
    }

    suspend fun getTvWatchProviders(tvId: Int): TmdbWatchProvidersResponse {
        val response = client.get("tv/$tvId/watch/providers")
        return response.body()
    }

    suspend fun discoverMovie(
        language: String = "es-ES",
        page: Int = 1,
        watchProviders: String? = null,
        watchRegion: String? = "ES",
        releaseDateGte: String? = "2024-01-01"
    ): TmdbTrendingResponse {
        val response = client.get("discover/movie") {
            parameter("language", language)
            parameter("page", page)
            parameter("sort_by", "popularity.desc")
            if (!watchProviders.isNullOrBlank()) parameter("with_watch_providers", watchProviders)
            if (!watchRegion.isNullOrBlank()) parameter("watch_region", watchRegion)
            if (!releaseDateGte.isNullOrBlank()) parameter("primary_release_date.gte", releaseDateGte)
        }
        return response.body()
    }

    suspend fun discoverTv(
        language: String = "es-ES",
        page: Int = 1,
        watchProviders: String? = null,
        watchRegion: String? = "ES",
        firstAirDateGte: String? = "2024-01-01"
    ): TmdbTrendingResponse {
        val response = client.get("discover/tv") {
            parameter("language", language)
            parameter("page", page)
            parameter("sort_by", "popularity.desc")
            if (!watchProviders.isNullOrBlank()) parameter("with_watch_providers", watchProviders)
            if (!watchRegion.isNullOrBlank()) parameter("watch_region", watchRegion)
            if (!firstAirDateGte.isNullOrBlank()) parameter("first_air_date.gte", firstAirDateGte)
        }
        return response.body()
    }

    suspend fun getTrending(language: String = "es-ES"): TmdbTrendingResponse {
        val response = client.get("trending/all/week") {
            parameter("language", language)
        }
        return response.body()
    }

    suspend fun getMovieCredits(movieId: Int): TmdbCreditsResponse {
        val response = client.get("movie/$movieId/credits")
        return response.body()
    }

    suspend fun getMovieExternalIds(movieId: Int): TmdbExternalIdsDto {
        val response = client.get("movie/$movieId/external_ids")
        return response.body()
    }

    suspend fun getTvExternalIds(tvId: Int): TmdbExternalIdsDto {
        val response = client.get("tv/$tvId/external_ids")
        return response.body()
    }

    suspend fun getTvCredits(tvId: Int): TmdbCreditsResponse {
        val response = client.get("tv/$tvId/credits")
        return response.body()
    }

    suspend fun getTvSeason(tvId: Int, seasonNumber: Int, language: String = "es-ES"): TmdbTvSeasonDetailDto {
        val response = client.get("tv/$tvId/season/$seasonNumber") {
            parameter("language", language)
        }
        return response.body()
    }
}
