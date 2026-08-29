package com.dondeloexan.data.remote.api

import com.dondeloexan.data.sync.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SupabaseApiException(
    message: String,
    val errorCode: String? = null,
    val statusCode: Int? = null
) : Exception(message)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String? = null,
    val user: AuthUserResponse? = null
)

@Serializable
data class AuthUserResponse(val id: String = "", val email: String? = null)

@Serializable
private data class AuthErrorMessage(
    val message: String = "",
    val msg: String = "",
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = ""
)

@Serializable
private data class AuthErrorCodeMessage(
    val code: Int = 0,
    @SerialName("error_code") val errorCode: String = "",
    val msg: String = "",
    val message: String = ""
)

data class AuthResult(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: Long,
    val userId: String?,
    val email: String?
)

fun AuthResponse.toResult(now: Long = System.currentTimeMillis()) = AuthResult(
    accessToken = accessToken.takeIf { it.isNotBlank() },
    refreshToken = refreshToken.takeIf { it.isNotBlank() },
    expiresAt = if (expiresIn > 0) now + expiresIn * 1_000 else 0L,
    userId = user?.id?.takeIf { it.isNotBlank() },
    email = user?.email
)

fun AuthResult?.toSessionState(): SessionState? {
    if (this == null || accessToken == null || refreshToken == null || userId == null) return null
    return SessionState(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        userId = userId,
        email = email ?: ""
    )
}

class SupabaseAuthApi(
    private val client: HttpClient,
    private val url: String,
    private val anonKey: String,
    private val json: Json
) {

    suspend fun signInWithPassword(email: String, password: String): AuthResult = request(
        path = "/auth/v1/token",
        query = "grant_type=password",
        body = authLoginBody(email, password)
    )

    suspend fun signUp(email: String, password: String): AuthResult = request(
        path = "/auth/v1/signup",
        query = "",
        body = authLoginBody(email, password)
    )

    suspend fun refresh(refreshToken: String): AuthResult = request(
        path = "/auth/v1/token",
        query = "grant_type=refresh_token",
        body = """{"refresh_token":"$refreshToken"}"""
    )

    suspend fun signOut(accessToken: String) {
        request(
            path = "/auth/v1/logout",
            query = "",
            body = "{}",
            bearer = accessToken
        )
    }

    private fun authLoginBody(email: String, password: String): String =
        """{"email":"$email","password":"$password"}"""

    private suspend fun request(
        path: String,
        query: String,
        body: String,
        bearer: String? = null
    ): AuthResult {
        val fullUrl = if (query.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$query"
        val response = client.post(fullUrl) {
            header("apikey", anonKey)
            if (bearer != null) header(HttpHeaders.Authorization, "Bearer $bearer")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val legacy = runCatching { json.decodeFromString<AuthErrorMessage>(text) }
                .getOrNull()
            val coded = runCatching { json.decodeFromString<AuthErrorCodeMessage>(text) }
                .getOrNull()
            val message = listOf(
                legacy?.msg, legacy?.errorDescription, legacy?.message,
                coded?.msg, coded?.message
            ).firstOrNull { !it.isNullOrBlank() }
            val errorCode = coded?.errorCode?.takeIf { it.isNotBlank() }
                ?: legacy?.error?.takeIf { it.isNotBlank() }
            throw SupabaseApiException(
                message ?: "Error de autenticación (${response.status.value})",
                errorCode = errorCode,
                statusCode = response.status.value
            )
        }
        if (text.isBlank()) return AuthResult(null, null, 0, null, null)
        return json.decodeFromString<AuthResponse>(text).toResult()
    }

    private val baseUrl: String get() = url.trimEnd('/')
}