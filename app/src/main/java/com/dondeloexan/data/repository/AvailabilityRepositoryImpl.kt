package com.dondeloexan.data.repository

import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.domain.repository.AvailabilityRepository
import kotlinx.coroutines.flow.Flow

class AvailabilityRepositoryImpl(
    private val dataStore: UserPreferencesDataStore
) : AvailabilityRepository {

    override val selectedTypes: Flow<Set<String>> = dataStore.preferredAvailabilityTypes

    override suspend fun toggle(type: String) {
        dataStore.toggleAvailabilityType(type)
    }
}
