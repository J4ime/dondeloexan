package com.dondeloexan.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_store")

data class SessionState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt - 60_000
}

class SessionStore(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT = stringPreferencesKey("expires_at")
        private val USER_ID = stringPreferencesKey("user_id")
        private val EMAIL = stringPreferencesKey("email")
    }

    val session: Flow<SessionState?> = context.sessionDataStore.data.map { prefs ->
        val access = prefs[ACCESS_TOKEN] ?: return@map null
        SessionState(
            accessToken = access,
            refreshToken = prefs[REFRESH_TOKEN] ?: "",
            expiresAt = prefs[EXPIRES_AT]?.toLongOrNull() ?: 0L,
            userId = prefs[USER_ID] ?: "",
            email = prefs[EMAIL] ?: ""
        )
    }

    suspend fun current(): SessionState? = session.first()

    suspend fun save(state: SessionState) {
        context.sessionDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = state.accessToken
            prefs[REFRESH_TOKEN] = state.refreshToken
            prefs[EXPIRES_AT] = state.expiresAt.toString()
            prefs[USER_ID] = state.userId
            prefs[EMAIL] = state.email
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}