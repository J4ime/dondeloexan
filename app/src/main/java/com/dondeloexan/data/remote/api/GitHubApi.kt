package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.GitHubReleaseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class GitHubApi(
    private val client: HttpClient,
    private val owner: String,
    private val repo: String
) {

    suspend fun getLatestRelease(): GitHubReleaseDto {
        val response = client.get("repos/$owner/$repo/releases/latest")
        return response.body()
    }
}
