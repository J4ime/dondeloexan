package com.dondeloexan.data.sync

import com.dondeloexan.data.remote.api.SupabaseApiException
import com.dondeloexan.data.remote.api.SupabaseAuthApi
import com.dondeloexan.data.remote.api.toResult
import com.dondeloexan.data.remote.api.toSessionState
import com.dondeloexan.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

class AccountRepositoryImpl(
    private val authApi: SupabaseAuthApi,
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore
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
            if (e.errorCode != "invalid_grant") throw e
            signUpAndAuthenticate(email, password)
        } ?: throw SupabaseApiException(ERR_INVALID_CREDENTIALS)
        sessionStore.save(session)
        syncManager.syncAll(session)
    }

    private suspend fun signUpAndAuthenticate(email: String, password: String): SessionState? {
        val signUpState = try {
            authApi.signUp(email, password).toSessionState()
        } catch (e: SupabaseApiException) {
            throw if (e.errorCode == "user_already_exists") {
                SupabaseApiException(ERR_INVALID_CREDENTIALS)
            } else {
                e
            }
        }
        if (signUpState != null) return signUpState
        return try {
            authApi.signInWithPassword(email, password).toSessionState()
        } catch (e: SupabaseApiException) {
            throw if (e.errorCode == "email_not_confirmed") {
                SupabaseApiException(ERR_EMAIL_NOT_CONFIRMED)
            } else {
                e
            }
        }
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        sessionStore.current()?.let {
            runCatching { authApi.signOut(it.accessToken) }
        }
        sessionStore.clear()
    }

    override suspend fun sync(): Result<SyncSummary> = runCatching {
        syncManager.syncAll(requireSession())
    }

    companion object {
        private const val ERR_INVALID_CREDENTIALS = "Email o contraseña incorrectos"
        private const val ERR_EMAIL_NOT_CONFIRMED =
            "La confirmación de email está activada en Supabase: desactívala en Authentication → Sign In / Providers → Email (Confirm email) para entrar sin confirmar."
    }
}