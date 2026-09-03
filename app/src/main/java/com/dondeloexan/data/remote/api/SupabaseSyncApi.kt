package com.dondeloexan.data.remote.api

import com.dondeloexan.domain.model.SessionState
import com.dondeloexan.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
     * Sesión "anónima": el rol 'anon' se autentica con la anon key como Bearer.
     * Se usa para todo el catálogo global (compartido entre usuarios, nunca caduca).
     */
    fun anonymousSession(): SessionState =
        SessionState(
            accessToken = anonKey,
            refreshToken = "",
            expiresAt = Long.MAX_VALUE,
            userId = "",
            email = "anon"
        )

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
     * Borra las filas de [table] con un [contentId] concreto. Serve para la
     * sincronización incremental de una sola serie (estado + progreso).
     */
    suspend fun deleteTableRowsByContent(table: String, contentId: String, session: SessionState) {
        val response = client.delete("${url.trimEnd('/')}/rest/v1/$table?content_id=eq.$contentId") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header("Prefer", "return=minimal")
        }
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            AppLogger.e(
                "SyncApi",
                "borrar $table por content_id → HTTP ${response.status.value}: ${text.take(500)}"
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

    /**
     * Consulta [table] con una [query] raw tipo PostgREST
     * (p. ej. "content_id=eq.tmdb-123&select=*"). Devuelve el cuerpo JSON (array).
     */
    suspend fun select(table: String, query: String, session: SessionState): String {
        val response = client.get("${url.trimEnd('/')}/rest/v1/$table?$query") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header("Prefer", "return=representation")
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            AppLogger.e(
                "SyncApi",
                "select $table?$query → HTTP ${response.status.value}: ${text.take(500)}"
            )
            val message = runCatching {
                json.decodeFromString<PostgrestError>(text).message
            }.getOrNull()?.takeIf { it.isNotBlank() }
            throw SupabaseApiException(message ?: "Error al leer $table (${response.status.value})")
        }
        return text
    }

    /**
     * Invoca una función RPC de PostgREST (p. ej. "catalog_merge"), que hace un
     * upsert null-safe: las columnas null del nuevo payload conservan el valor
     * existente del catálogo (un usuario "pobre" no borra el catálogo rico).
     */
    suspend fun rpc(function: String, payload: String, session: SessionState): String {
        val response = client.post("${url.trimEnd('/')}/rest/v1/rpc/$function") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            AppLogger.e(
                "SyncApi",
                "rpc $function → HTTP ${response.status.value}: ${text.take(500)}"
            )
            val message = runCatching {
                json.decodeFromString<PostgrestError>(text).message
            }.getOrNull()?.takeIf { it.isNotBlank() }
            throw SupabaseApiException(message ?: "Error al actualizar el catálogo (${response.status.value})")
        }
        return text
    }
}

@kotlinx.serialization.Serializable
private data class PostgrestError(val message: String = "")