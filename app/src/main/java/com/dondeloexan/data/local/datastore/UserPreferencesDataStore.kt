package com.dondeloexan.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val ACTIVE_PLATFORMS = stringSetPreferencesKey("active_platforms")
    }

    val activePlatforms: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_PLATFORMS] ?: emptySet()
    }

    suspend fun getActivePlatforms(): Set<String> {
        return context.dataStore.data.map { prefs ->
            prefs[ACTIVE_PLATFORMS] ?: emptySet()
        }.let { flow ->
            var result = emptySet<String>()
            flow.collect { result = it; return@collect }
            result
        }
    }

    suspend fun setActivePlatforms(platforms: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_PLATFORMS] = platforms
        }
    }

    suspend fun togglePlatform(platform: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[ACTIVE_PLATFORMS] ?: emptySet()
            prefs[ACTIVE_PLATFORMS] = if (platform in current) {
                current - platform
            } else {
                current + platform
            }
        }
    }
}
