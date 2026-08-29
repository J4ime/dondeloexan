package com.dondeloexan.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SupabaseAuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SupabaseAuthApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            defaultRequest {
                url(server.url("/").toString())
                contentType(ContentType.Application.Json)
            }
        }
        api = SupabaseAuthApi(
            client = client,
            url = server.url("/").toString(),
            anonKey = "pubkey",
            json = Json { ignoreUnknownKeys = true }
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `signInWithPassword devuelve sesion y envia apikey y body correctos`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "access_token": "atoken",
                      "refresh_token": "rtoken",
                      "expires_in": 3600,
                      "token_type": "bearer",
                      "user": { "id": "uid-1", "email": "a@b.c" }
                    }
                    """.trimIndent()
                )
        )

        val result = api.signInWithPassword("a@b.c", "secreto")

        assertNotNull(result.accessToken)
        assertEquals("atoken", result.accessToken)
        assertEquals("rtoken", result.refreshToken)
        assertEquals("uid-1", result.userId)
        assertEquals("a@b.c", result.email)
        assertTrue(result.expiresAt > System.currentTimeMillis())

        val request: RecordedRequest = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=password", request.path)
        assertEquals("pubkey", request.getHeader("apikey"))
        assertTrue(request.body.readUtf8().contains("\"email\":\"a@b.c\""))
    }

    @Test
    fun `credenciales invalidas lanzan SupabaseApiException con msg error_description y error_code`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"invalid_grant","error_description":"Invalid login credentials"}""")
        )

        val exception = assertThrows(SupabaseApiException::class.java) {
            runBlocking { api.signInWithPassword("a@b.c", "mal") }
        }
        assertEquals("Invalid login credentials", exception.message)
        assertEquals("invalid_grant", exception.errorCode)
        assertEquals(400, exception.statusCode)
    }

    @Test
    fun `errores con formato msg y error_code se parsean correctamente`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":400,"error_code":"email_not_confirmed","msg":"Email not confirmed"}""")
        )

        val exception = assertThrows(SupabaseApiException::class.java) {
            runBlocking { api.signInWithPassword("a@b.c", "secreto") }
        }
        assertEquals("Email not confirmed", exception.message)
        assertEquals("email_not_confirmed", exception.errorCode)
        assertEquals(400, exception.statusCode)
    }

    @Test
    fun `error_code invalid_credentials actual se parsea correctamente`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""")
        )

        val exception = assertThrows(SupabaseApiException::class.java) {
            runBlocking { api.signInWithPassword("a@b.c", "secreto") }
        }
        assertEquals("Invalid login credentials", exception.message)
        assertEquals("invalid_credentials", exception.errorCode)
        assertEquals(400, exception.statusCode)
    }

    @Test
    fun `signUp sin confirmacion de email no crea sesion`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "access_token": "",
                      "refresh_token": "",
                      "user": { "id": "uid-1", "email": "nuevo@test.es" }
                    }
                    """.trimIndent()
                )
        )

        val result = api.signUp("nuevo@test.es", "secreto")

        assertNull(result.accessToken)
        assertNull(result.toSessionState())
        assertEquals("nuevo@test.es", result.email)
    }

    @Test
    fun `signOut envia bearer y devuelve sin excepcion`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        api.signOut("atoken")

        val request: RecordedRequest = server.takeRequest()
        assertEquals("/auth/v1/logout", request.path)
        assertEquals("Bearer atoken", request.getHeader("Authorization"))
    }
}