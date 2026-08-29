package com.dondeloexan.data.sync

import com.dondeloexan.data.remote.api.AuthResult
import com.dondeloexan.data.remote.api.SupabaseApiException
import com.dondeloexan.data.remote.api.SupabaseAuthApi
import com.dondeloexan.data.remote.api.toSessionState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccountRepositoryImplTest {

    private val authApi: SupabaseAuthApi = mockk()
    private val syncManager: SyncManager = mockk()
    private val sessionStore: SessionStore = mockk()

    private val email = "usuario@test.es"
    private val password = "secreto"

    @BeforeEach
    fun setUp() {
        every { sessionStore.session } returns flowOf(null)
    }

    private fun repository() = AccountRepositoryImpl(authApi, syncManager, sessionStore)

    private fun auth(): AuthResult = AuthResult(
        accessToken = "atoken",
        refreshToken = "rtoken",
        expiresAt = System.currentTimeMillis() + 3_600_000,
        userId = "uuid-1",
        email = email
    )

    private fun summary() = SyncSummary(0, 0, 0, 0, 0, 0, 0, 0)

    @Test
    fun `login con credenciales correctas guarda sesion y sincroniza sin llamar a signup`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } returns auth()
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isSuccess)
        val saved = slot<SessionState>()
        coVerify { sessionStore.save(capture(saved)) }
        assertEquals("uuid-1", saved.captured.userId)
        assertEquals("atoken", saved.captured.accessToken)
        coVerify(exactly = 1) { syncManager.syncAll(any()) }
        coVerify(exactly = 0) { authApi.signUp(any(), any()) }
    }

    @Test
    fun `login de usuario nuevo crea la cuenta con su contrasena y sincroniza`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Invalid login credentials", errorCode = "invalid_grant", statusCode = 400
        )
        coEvery { authApi.signUp(email, password) } returns auth()
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isSuccess)
        coVerify { sessionStore.save(match { it.accessToken == "atoken" }) }
        coVerify(exactly = 1) { syncManager.syncAll(any()) }
    }

    @Test
    fun `login de usuario nuevo sin sesion en signup reintenta signin`() = runTest {
        var signInCalls = 0
        coEvery { authApi.signInWithPassword(email, password) } answers {
            signInCalls++
            if (signInCalls == 1) {
                throw SupabaseApiException(
                    "Invalid login credentials", errorCode = "invalid_grant", statusCode = 400
                )
            }
            auth()
        }
        coEvery { authApi.signUp(email, password) } returns AuthResult(null, null, 0, "uuid-1", email)
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isSuccess)
        coVerify { sessionStore.save(match { it.userId == "uuid-1" }) }
        coVerify(exactly = 2) { authApi.signInWithPassword(email, password) }
        coVerify(exactly = 1) { syncManager.syncAll(any()) }
    }

    @Test
    fun `login con confirmacion pendiente devuelve mensaje claro`() = runTest {
        var signInCalls = 0
        coEvery { authApi.signInWithPassword(email, password) } answers {
            signInCalls++
            throw if (signInCalls == 1) {
                SupabaseApiException(
                    "Invalid login credentials", errorCode = "invalid_grant", statusCode = 400
                )
            } else {
                SupabaseApiException(
                    "Email not confirmed", errorCode = "email_not_confirmed", statusCode = 400
                )
            }
        }
        coEvery { authApi.signUp(email, password) } returns AuthResult(null, null, 0, "uuid-1", email)
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Confirm email"))
        coVerify(exactly = 0) { sessionStore.save(any()) }
        coVerify(exactly = 0) { syncManager.syncAll(any()) }
    }

    @Test
    fun `email_no_confirmado en el primer signin no hace fallback`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Email not confirmed", errorCode = "email_not_confirmed", statusCode = 400
        )
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authApi.signUp(any(), any()) }
        coVerify(exactly = 0) { syncManager.syncAll(any()) }
    }

    @Test
    fun `login de usuario nuevo con error_code invalid_credentials actual crea cuenta y sincroniza`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Invalid login credentials", errorCode = "invalid_credentials", statusCode = 400
        )
        coEvery { authApi.signUp(email, password) } returns auth()
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isSuccess)
        coVerify { sessionStore.save(match { it.accessToken == "atoken" }) }
        coVerify(exactly = 1) { syncManager.syncAll(any()) }
    }

    @Test
    fun `login de usuario nuevo con solo mensaje sin error_code tambien hace fallback`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Invalid login credentials"
        )
        coEvery { authApi.signUp(email, password) } returns auth()
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isSuccess)
        coVerify { sessionStore.save(match { it.accessToken == "atoken" }) }
        coVerify(exactly = 0) { authApi.refresh(any()) }
    }

    @Test
    fun `usuario existente con contrasena incorrecta da error de credenciales`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Invalid login credentials", errorCode = "invalid_grant", statusCode = 400
        )
        coEvery { authApi.signUp(email, password) } throws SupabaseApiException(
            "User already registered", errorCode = "user_already_exists", statusCode = 400
        )
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isFailure)
        assertEquals("Email o contraseña incorrectos", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { sessionStore.save(any()) }
        coVerify(exactly = 0) { syncManager.syncAll(any()) }
    }

    @Test
    fun `signup con error_code email_exists da error de credenciales amigable`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Invalid login credentials", errorCode = "invalid_credentials", statusCode = 400
        )
        coEvery { authApi.signUp(email, password) } throws SupabaseApiException(
            "A user with this email address has already been registered",
            errorCode = "email_exists", statusCode = 422
        )
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertTrue(result.isFailure)
        assertEquals("Email o contraseña incorrectos", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { sessionStore.save(any()) }
        coVerify(exactly = 0) { syncManager.syncAll(any()) }
    }

    @Test
    fun `error inesperado de la api no ejecuta fallback`() = runTest {
        coEvery { authApi.signInWithPassword(email, password) } throws SupabaseApiException(
            "Rate limit", statusCode = 429
        )
        coEvery { sessionStore.save(any()) } just Runs
        coEvery { syncManager.syncAll(any()) } returns summary()

        val result = repository().login(email, password)

        assertFalse(result.isSuccess)
        coVerify(exactly = 0) { authApi.signUp(any(), any()) }
        coVerify(exactly = 0) { syncManager.syncAll(any()) }
    }
}