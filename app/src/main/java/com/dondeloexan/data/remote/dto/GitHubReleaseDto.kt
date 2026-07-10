package com.dondeloexan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    val url: String,
    @SerialName("html_url") val htmlUrl: String,
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    @SerialName("target_commitish") val targetCommitish: String,
    val name: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val body: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
    @SerialName("tarball_url") val tarballUrl: String? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null,
    val author: GitHubAuthorDto? = null
)

@Serializable
data class GitHubAssetDto(
    val id: Long,
    val name: String,
    @SerialName("content_type") val contentType: String,
    val size: Long,
    @SerialName("download_count") val downloadCount: Int,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

@Serializable
data class GitHubAuthorDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String?
)
