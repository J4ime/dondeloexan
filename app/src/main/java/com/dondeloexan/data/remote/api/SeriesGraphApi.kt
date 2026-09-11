package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.SeriesGraphSeasonRatingsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/**
 * Cliente de la API pública (no documentada) de SeriesGraph para obtener las
 * notas IMDb por episodio de una serie a partir de su id de TMDB.
 */
class SeriesGraphApi(private val client: HttpClient) {

    suspend fun getSeasonRatings(tmdbId: Int): List<SeriesGraphSeasonRatingsDto> {
        val response: HttpResponse = client.get("api/shows/$tmdbId/season-ratings")
        if (!response.status.isSuccess()) {
            throw IllegalStateException("SeriesGraph HTTP ${response.status.value} para show/$tmdbId")
        }
        return response.body()
    }
}
