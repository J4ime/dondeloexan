package com.dondeloexan.di

import com.dondeloexan.BuildConfig
import com.dondeloexan.data.remote.api.GitHubApi
import com.dondeloexan.data.remote.api.OmdbApi
import com.dondeloexan.data.remote.api.SeriesGraphApi
import com.dondeloexan.data.remote.api.SupabaseAuthApi
import com.dondeloexan.data.remote.api.SupabaseSyncApi
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.api.WikidataApi
import com.dondeloexan.util.HttpTracingInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val networkModule = module {

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }

    // ── TMDB ──
    single {
        val client = HttpClient(OkHttp) {
            engine {
                config {
                    dispatcher(Dispatcher().apply {
                        maxRequestsPerHost = 15
                        maxRequests = 30
                    })
                    connectionPool(ConnectionPool(10, 30, TimeUnit.SECONDS))
                    retryOnConnectionFailure(true)
                    connectTimeout(5, TimeUnit.SECONDS)
                    readTimeout(8, TimeUnit.SECONDS)
                    writeTimeout(8, TimeUnit.SECONDS)
                    callTimeout(20, TimeUnit.SECONDS)
                    addInterceptor(HttpTracingInterceptor("TMDB-HTTP"))
                }
            }
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
            }
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
        val client = HttpClient(OkHttp) {
            engine {
                config {
                    connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
                    connectTimeout(5, TimeUnit.SECONDS)
                    readTimeout(8, TimeUnit.SECONDS)
                    callTimeout(12, TimeUnit.SECONDS)
                    addInterceptor(HttpTracingInterceptor("OMDB-HTTP"))
                }
            }
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
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
        val client = HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(8, TimeUnit.SECONDS)
                    callTimeout(15, TimeUnit.SECONDS)
                    addInterceptor(HttpTracingInterceptor("GH-HTTP"))
                }
            }
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

    // ── Wikidata ──
    single {
        val client = HttpClient(OkHttp) {
            engine {
                config {
                    retryOnConnectionFailure(true)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    callTimeout(30, TimeUnit.SECONDS)
                    addInterceptor(HttpTracingInterceptor("WIKIDATA-HTTP"))
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            defaultRequest {
                url("https://query.wikidata.org/")
                header("User-Agent", "DondeLoExan/${BuildConfig.VERSION_NAME}")
                header("Accept", "application/sparql-results+json")
            }
        }
        WikidataApi(client, get())
    }

    // ── Supabase (auth + datos) ──
    single(named("supabase")) {
        HttpClient(OkHttp) {
            engine {
                config {
                    retryOnConnectionFailure(true)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    callTimeout(30, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }

    single {
        SupabaseAuthApi(
            client = get(named("supabase")),
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
            json = get()
        )
    }

    single {
        SupabaseSyncApi(
            client = get(named("supabase")),
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
            json = get()
        )
    }

    // ── SeriesGraph (ratings IMDb por episodio) ──
    single {
        val client = HttpClient(OkHttp) {
            engine {
                config {
                    retryOnConnectionFailure(true)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    callTimeout(30, TimeUnit.SECONDS)
                    addInterceptor(HttpTracingInterceptor("SERIESGRAPH-HTTP"))
                }
            }
            install(ContentNegotiation) { json(get()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                url("https://seriesgraph.com/")
                header("User-Agent", "DondeLoExan/${BuildConfig.VERSION_NAME}")
                contentType(ContentType.Application.Json)
            }
        }
        SeriesGraphApi(client)
    }

    // ── Filmaffinity (plain HTML client, no JSON) ──
    single(named("filmaffinity")) {
        HttpClient(OkHttp) {
            engine {
                config {
                    retryOnConnectionFailure(true)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    callTimeout(30, TimeUnit.SECONDS)
                    addInterceptor(HttpTracingInterceptor("FA-HTTP"))
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            }
        }
    }
}
