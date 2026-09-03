package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.SessionState
import com.dondeloexan.domain.model.SyncSummary
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    val session: Flow<SessionState?>

    suspend fun login(email: String, password: String): Result<SyncSummary>

    /** El login crea el usuario automáticamente si no existe (cuenta con su contraseña + sincronización). */
    suspend fun logout(): Result<Unit>

    suspend fun sync(): Result<SyncSummary>
}