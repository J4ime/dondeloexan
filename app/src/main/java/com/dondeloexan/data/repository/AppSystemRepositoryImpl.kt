package com.dondeloexan.data.repository

import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.domain.repository.AppSystemRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException

class AppSystemRepositoryImpl(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val tmdbApi: TmdbApi
) : AppSystemRepository {

    override val lastLibraryUpdateTimestamp: Flow<Long?> =
        userPreferencesDataStore.lastLibraryUpdateTimestamp

    override suspend fun testTmdbConnection(): String {
        try {
            val trending = withContext(Dispatchers.IO) {
                try {
                    withTimeout(12_000) { tmdbApi.getTrending() }
                } catch (e: TimeoutCancellationException) {
                    throw SocketTimeoutException("TMDB API no respondió en 12s")
                }
            }
            val sample = trending.results
                .filter { it.mediaType in listOf("movie", "tv") }
                .take(3)
                .joinToString(", ") { it.title ?: it.name ?: "?" }
            val total = trending.results.count { it.mediaType in listOf("movie", "tv") }
            AppLogger.i("SettingsVM", "Test conexión: paso 1/2 OK ($total resultados; muestra: $sample)")

            val bytes = withContext(Dispatchers.IO) { tmdbApi.pingImage() }
            AppLogger.i("SettingsVM", "Test conexión: paso 2/2 OK ($bytes bytes)")

            return if (bytes > 0) {
                "TMDB OK · $total resultados (${sample}) · CDN $bytes bytes"
            } else {
                "TMDB OK · $total resultados (${sample}) · CDN sin tamaño declarado"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("SettingsVM", "Test conexión TMDB falló", e)
            return "Error TMDB: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
