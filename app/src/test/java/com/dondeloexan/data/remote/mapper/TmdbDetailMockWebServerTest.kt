package com.dondeloexan.data.remote.mapper

import com.dondeloexan.data.remote.api.TmdbApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TmdbDetailMockWebServerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: TmdbApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest {
                url(server.url("/").toString())
                contentType(ContentType.Application.Json)
            }
        }
        api = TmdbApi(client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getTvSeason maps real HTTP JSON response to domain SeasonDetail`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "_id": "5b47c1d60e0a9f2e0f000001",
                      "air_date": "2013-04-01",
                      "name": "Temporada 1",
                      "overview": "Primera temporada",
                      "id": 3627,
                      "season_number": 1,
                      "episodes": [
                        {
                          "air_date": "2013-04-01",
                          "episode_number": 1,
                          "id": 147181,
                          "name": "Pilot",
                          "overview": "La historia comienza.",
                          "season_number": 1,
                          "vote_average": 8.1,
                          "episode_type": "series_premiere"
                        },
                        {
                          "air_date": "2013-05-20",
                          "episode_number": 10,
                          "id": 147182,
                          "name": "Finale",
                          "overview": "Conclusión.",
                          "season_number": 1,
                          "vote_average": 9.3,
                          "episode_type": "season_finale"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val detail = api.getTvSeason(tvId = 1399, seasonNumber = 1).toSeasonDetail()

        assert(detail.seasonNumber == 1)
        assert(detail.episodes.size == 2)
        val first = detail.episodes[0]
        assert(first.episodeNumber == 1)
        assert(first.name == "Pilot")
        assert(first.voteAverage == 8.1f)
        assert(first.episodeType == "series_premiere")
        assert(detail.episodes[1].name == "Finale")
    }

    @Test
    fun `getTvSeason handles unknown fields and nulls gracefully`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "season_number": 2,
                      "episodes": [
                        { "episode_number": 1, "id": 5, "name": "X", "season_number": 2, "unknown_field": "ignored" }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val detail = api.getTvSeason(tvId = 1, seasonNumber = 2).toSeasonDetail()

        assert(detail.seasonNumber == 2)
        assert(detail.episodes.size == 1)
        assert(detail.episodes[0].airDate == null)
    }

    @Test
    fun `getTvSeason returns domain via mapper for season with many episodes`() = runTest {
        val episodesJson = (1..20).joinToString(",") {
            """{"air_date":"2013-01-01","episode_number":$it,"id":$it,"name":"Ep $it","season_number":1}"""
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"season_number":1,"episodes":[$episodesJson]}""")
        )

        val detail = api.getTvSeason(tvId = 1, seasonNumber = 1).toSeasonDetail()

        assert(detail.episodes.size == 20)
        assert(detail.episodes[19].name == "Ep 20")
        assert(detail.episodes[0].episodeType == null)
    }
}