package com.dondeloexan.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Servicios de sistema/preferencias de la app que hoy cruzan la frontera desde
 * presentación: lectura del último refresco de la biblioteca y comprobación de
 * conectividad con TMDB.
 */
interface AppSystemRepository {
    val lastLibraryUpdateTimestamp: Flow<Long?>
    suspend fun testTmdbConnection(): String
}
