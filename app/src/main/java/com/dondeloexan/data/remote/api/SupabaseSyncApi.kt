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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SupabaseSyncApi(
    private val client: HttpClient,
    private val url: String,
    private val anonKey: String,
    private val json: Json
) {

    /**
     * Upsert PostgREST: inserte o actualiza filas usando [onConflict] como
     * constraint de conflicto (columnas separadas por coma). Devuelve el cuerpo
     * de la respuesta (representación de las filas tocadas si solicitado).
     */
    suspend fun upsert(
        table: String,
        onConflict: List<String>,
        payload: String,
        session: SessionState,
        returnRepresentation: Boolean = false
    ): String {
        val onConflictEncoded = onConflict.joinToString("%2C")
        val response = client.post("${url.trimEnd('/')}/rest/v1/$table?on_conflict=$onConflictEncoded") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(
                "Prefer",
                "resolution=merge-duplicates" +
                    if (returnRepresentation) ",return=representation" else ",return=minimal"
            )
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching {
                json.decodeFromString<PostgrestError>(text).message
            }.getOrNull()?.takeIf { it.isNotBlank() }
            throw SupabaseApiException(message ?: "Error de sincronización (${response.status.value})")
        }
        return text
    }
}

@kotlinx.serialization.Serializable
private data class PostgrestError(val message: String = "")