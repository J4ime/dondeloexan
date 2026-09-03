package com.dondeloexan.data.sync

import com.dondeloexan.data.remote.api.SupabaseApiException
import com.dondeloexan.data.remote.api.SupabaseAuthApi
import com.dondeloexan.data.remote.api.toResult
import com.dondeloexan.data.remote.api.toSessionState
import com.dondeloexan.domain.model.SessionState
import com.dondeloexan.domain.model.SyncSummary
import com.dondeloexan.domain.repository.AccountRepository
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.flow.Flow

class AccountRepositoryImpl(
    private val authApi: SupabaseAuthApi,
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore,
    private val seriesMetadataEnricher: SeriesMetadataEnricher
) : AccountRepository {

    override val session: Flow<SessionState?> = sessionStore.session

    private suspend fun requireSession(): SessionState {
        val current = sessionStore.current() ?: throw SupabaseApiException("No hay sesión iniciada")
        if (current.isExpired && current.refreshToken.isNotEmpty()) {
            val refreshed = authApi.refresh(current.refreshToken)
            val state = refreshed.toSessionState() ?: throw SupabaseApiException("La sesión ha caducado")
            sessionStore.save(state)
            return state
        }
        return current
    }

    override suspend fun login(email: String, password: String): Result<SyncSummary> = runCatching {
        val session = try {
            authApi.signInWithPassword(email, password).toSessionState()
        } catch (e: SupabaseApiException) {
            if (!e.isInvalidCredentials()) throw e
            signUpAndAuthenticate(email, password)
        } ?: throw SupabaseApiException(ERR_INVALID_CREDENTIALS)
        sessionStore.save(session)
        syncManager.syncAll(session)
    }

    private suspend fun signUpAndAuthenticate(email: String, password: String): SessionState? {
        val signUpState = try {
            authApi.signUp(email, password).toSessionState()
        } catch (e: SupabaseApiException) {
            if (e.isUserAlreadyExists()) throw SupabaseApiException(ERR_INVALID_CREDENTIALS)
            throw e
        }
        if (signUpState != null) return signUpState
        return try {
            authApi.signInWithPassword(email, password).toSessionState()
        } catch (e: SupabaseApiException) {
            throw when {
                e.isEmailNotConfirmed() -> SupabaseApiException(ERR_EMAIL_NOT_CONFIRMED)
                e.isInvalidCredentials() -> SupabaseApiException(ERR_INVALID_CREDENTIALS)
                else -> e
            }
        }
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        val session = sessionStore.current()
        if (session != null) {
            runCatching { authApi.signOut(session.accessToken) }.onFailure {
                AppLogger.e("AccountRepo", "signOut falló", it)
            }
        }
        sessionStore.clear()
    }

    override suspend fun sync(): Result<SyncSummary> = runCatching {
        val session = requireSession()
        // Antes de subir, completamos la ficha técnica de las series que aún
        // la tienen incompleta (batch desde Ajustes → Sincronizar nube), para
        // que la nube no reciba campos null.
        runCatching { seriesMetadataEnricher.enrichAll() }.onFailure { e ->
            AppLogger.e("AccountRepo", "enrichAll falló (se continúa con el sync)", e)
        }
        syncManager.syncAll(session)
    }

    companion object {
        private const val ERR_INVALID_CREDENTIALS = "Email o contraseña incorrectos"
        private const val ERR_EMAIL_NOT_CONFIRMED =
            "La confirmación de email está activada en Supabase: desactívala en Authentication → Sign In / Providers → Email (Confirm email) para entrar sin confirmar."
    }
}

private val INVALID_CREDENTIALS_CODES = setOf("invalid_credentials", "invalid_grant")
private val USER_ALREADY_EXISTS_CODES = setOf("user_already_exists", "email_exists")

private fun SupabaseApiException.isInvalidCredentials(): Boolean =
    errorCode in INVALID_CREDENTIALS_CODES ||
        message.orEmpty().contains("invalid login credentials", ignoreCase = true)

private fun SupabaseApiException.isUserAlreadyExists(): Boolean =
    errorCode in USER_ALREADY_EXISTS_CODES ||
        message.orEmpty().contains("already registered", ignoreCase = true)

private fun SupabaseApiException.isEmailNotConfirmed(): Boolean =
    errorCode == "email_not_confirmed" ||
        message.orEmpty().contains("email not confirmed", ignoreCase = true)