package com.dondeloexan.data.remote.api

import com.dondeloexan.data.sync.SessionState
import com.dondeloexan.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
     * Borra todas las filas de [table] del usuario [userId] (RLS lo permite).
     * La sincronización es por reemplazo completo: se borra y se re-sube.
     */
    suspend fun deleteTableRows(table: String, userId: String, session: SessionState) {
        val response = client.delete("${url.trimEnd('/')}/rest/v1/$table?user_id=eq.$userId") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header("Prefer", "return=minimal")
        }
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            AppLogger.e(
                "SyncApi",
                "borrar $table del usuario → HTTP ${response.status.value}: ${text.take(500)}"
            )
            val message = runCatching {
                json.decodeFromString<PostgrestError>(text).message
            }.getOrNull()?.takeIf { it.isNotBlank() }
            throw SupabaseApiException(message ?: "Error al borrar $table (${response.status.value})")
        }
    }

    /**
     * Inserta [payload] (array JSON) en [table] sin conflictos (las filas del
     * usuario ya fueron borradas antes). Con [returnRepresentation] devuelve las
     * filas insertadas (incluido el id UUID autogenerado por la BD).
     */
    suspend fun insertAll(
        table: String,
        payload: String,
        session: SessionState,
        returnRepresentation: Boolean = false
    ): String {
        val response = client.post("${url.trimEnd('/')}/rest/v1/$table") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(
                "Prefer",
                if (returnRepresentation) "return=representation" else "return=minimal"
            )
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            AppLogger.e(
                "SyncApi",
                "insert $table → HTTP ${response.status.value}: ${text.take(500)}"
            )
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