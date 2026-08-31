package com.dondeloexan.data.remote.api

import com.dondeloexan.data.remote.dto.GitHubReleaseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException

class GitHubApi(
    private val client: HttpClient,
    private val owner: String,
    private val repo: String
) {

    suspend fun getLatestRelease(): GitHubReleaseDto {
        val response = try {
            withTimeout(10_000) {
                client.get("repos/$owner/$repo/releases/latest")
            }
        } catch (e: TimeoutCancellationException) {
            throw SocketTimeoutException("GitHub getLatestRelease excedió 10s: ${e.message}")
        }
        return response.body()
    }
}
