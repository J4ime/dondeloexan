package com.dondeloexan.data.sync

import com.dondeloexan.data.remote.api.SupabaseAuthApi
import com.dondeloexan.data.remote.api.toSessionState
import com.dondeloexan.domain.model.SessionState
import com.dondeloexan.util.AppLogger

/**
 * Único responsable de devolver una sesión con el token de acceso FRESCO antes
 * de cualquier escritura de datos de usuario en la nube. Si el token ha
 * caducado, lo refresca con el refresh_token, guarda la nueva sesión y la
 * devuelve. Devuelve null cuando no hay sesión o no se pudo refrescar.
 */
class SessionRefresher(
    private val sessionStore: SessionStore,
    private val authApi: SupabaseAuthApi
) {

    suspend fun freshOrNull(): SessionState? {
        val current = sessionStore.current() ?: return null
        if (!current.isExpired || current.refreshToken.isEmpty()) return current
        val refreshed = runCatching { authApi.refresh(current.refreshToken).toSessionState() }.getOrNull()
        if (refreshed != null) {
            sessionStore.save(refreshed)
        } else {
            AppLogger.w("SessionRefresher", "No se pudo refrescar el token de sesión")
        }
        return refreshed
    }
}
