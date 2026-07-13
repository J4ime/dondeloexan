package com.dondeloexan.di

import com.dondeloexan.BuildConfig
import com.dondeloexan.data.remote.api.BalloonerismmApi
import com.dondeloexan.data.remote.api.GitHubApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.TmdbApi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }

    // ── Balloonerismm (IMDb) ──
    single {
        val client = HttpClient {
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 60_000
            }
            install(Logging) { level = LogLevel.HEADERS }
            defaultRequest {
                url("https://api.balloonerismm.workers.dev/")
                contentType(ContentType.Application.Json)
            }
        }
        BalloonerismmApi(client)
    }

    // ── TMDB ──
    single {
        val client = HttpClient {
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 60_000
            }
            install(Logging) { level = LogLevel.HEADERS }
            defaultRequest {
                url("https://api.themoviedb.org/3/")
                header("Authorization", "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
                contentType(ContentType.Application.Json)
            }
        }
        TmdbApi(client)
    }

    // ── OMDb ──
    single {
        val client = HttpClient {
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 60_000
            }
            defaultRequest {
                url("https://www.omdbapi.com/")
                contentType(ContentType.Application.Json)
            }
        }
        OmdbApi(client, BuildConfig.OMDB_API_KEY)
    }

    // ── GitHub ──
    single {
        val client = HttpClient {
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
            }
            defaultRequest {
                url("https://api.github.com/")
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "DondeLoExan/${BuildConfig.VERSION_NAME}")
                contentType(ContentType.Application.Json)
            }
        }
        GitHubApi(client, BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)
    }
}
