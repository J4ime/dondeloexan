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
        val result = authApi.signInWithPassword(email, password)
        val state = result.toSessionState()
            ?: throw SupabaseApiException("Credenciales inválidas")
        sessionStore.save(state)
        syncManager.syncAll(state)
    }

    override suspend fun register(email: String, password: String): Result<Boolean> = runCatching {
        val result = authApi.signUp(email, password)
        val state = result.toSessionState()
        if (state != null) {
            sessionStore.save(state)
            syncManager.syncAll(state)
        }
        state?.userId != null
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
}