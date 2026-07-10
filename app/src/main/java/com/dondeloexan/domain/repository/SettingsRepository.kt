package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.GitHubRelease

interface SettingsRepository {
    suspend fun checkForUpdate(): Result<GitHubRelease?>
}
