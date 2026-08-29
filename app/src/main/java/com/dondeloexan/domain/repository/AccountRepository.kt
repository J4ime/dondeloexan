package com.dondeloexan.domain.repository

import com.dondeloexan.data.sync.SessionState
import com.dondeloexan.data.sync.SyncSummary
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    val session: Flow<SessionState?>

    suspend fun login(email: String, password: String): Result<SyncSummary>

    /** Devuelve null si la cuenta requiere confirmación de email (sin sesión todavía). */
    suspend fun register(email: String, password: String): Result<Boolean>

    suspend fun logout(): Result<Unit>

    suspend fun sync(): Result<SyncSummary>
}