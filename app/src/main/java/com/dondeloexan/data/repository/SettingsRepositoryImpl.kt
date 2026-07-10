package com.dondeloexan.data.repository

import com.dondeloexan.BuildConfig
import com.dondeloexan.data.remote.api.GitHubApi
import com.dondeloexan.domain.model.AppVersion
import com.dondeloexan.domain.model.GitHubRelease
import com.dondeloexan.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val gitHubApi: GitHubApi
) : SettingsRepository {

    override suspend fun checkForUpdate(): Result<GitHubRelease?> {
        return runCatching {
            val dto = gitHubApi.getLatestRelease()
            if (dto.draft) return@runCatching null

            val latestVersion = AppVersion.fromTag(dto.tagName) ?: return@runCatching null
            val currentVersion = AppVersion.fromTag(BuildConfig.VERSION_NAME)
                ?: return@runCatching null

            if (!latestVersion.isNewerThan(currentVersion)) return@runCatching null

            GitHubRelease(
                version = latestVersion,
                name = dto.name,
                publishedAt = dto.publishedAt,
                changelog = dto.body,
                htmlUrl = dto.htmlUrl,
                apkDownloadUrl = dto.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }?.browserDownloadUrl
            )
        }
    }
}
