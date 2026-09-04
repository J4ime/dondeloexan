package com.dondeloexan.data.sync

import com.dondeloexan.data.remote.api.AuthResult
import com.dondeloexan.data.remote.api.SupabaseAuthApi
import com.dondeloexan.domain.model.SessionState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionRefresherTest {

    private val sessionStore: SessionStore = mockk()
    private val authApi: SupabaseAuthApi = mockk()

    private val refresher = SessionRefresher(sessionStore, authApi)

    private fun session(access: String = "at", refresh: String = "rt", expiresIn: Long = 3_600_000): SessionState =
        SessionState(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = System.currentTimeMillis() + expiresIn,
            userId = "u1",
            email = "e@e.es"
        )

    @Test
    fun `token no caducado devuelve la sesion actual sin refrescar`() = runTest {
        val current = session()
        coEvery { sessionStore.current() } returns current

        val result = refresher.freshOrNull()

        assertEquals(current, result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
        coVerify(exactly = 0) { sessionStore.save(any()) }
    }

    @Test
    fun `token caducado refresca el token y guarda la nueva sesion`() = runTest {
        val caducada = session(access = "old", expiresIn = -1_000)
        coEvery { sessionStore.current() } returns caducada
        coEvery { authApi.refresh("rt") } returns AuthResult(
            accessToken = "new", refreshToken = "rt2",
            expiresAt = System.currentTimeMillis() + 3_600_000,
            userId = "u1", email = "e@e.es"
        )
        coEvery { sessionStore.save(any()) } just Runs

        val result = refresher.freshOrNull()

        assertTrue(result != null)
        assertEquals("new", result?.accessToken)
        coVerify(exactly = 1) { sessionStore.save(match { it.accessToken == "new" }) }
    }

    @Test
    fun `sin sesion activa devuelve null sin refrescar`() = runTest {
        coEvery { sessionStore.current() } returns null

        val result = refresher.freshOrNull()

        assertNull(result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
    }

    @Test
    fun `si el refresco falla devuelve null`() = runTest {
        val caducada = session(access = "old", expiresIn = -1_000)
        coEvery { sessionStore.current() } returns caducada
        coEvery { authApi.refresh(any()) } throws RuntimeException("red caída")

        val result = refresher.freshOrNull()

        assertNull(result)
        coVerify(exactly = 0) { sessionStore.save(any()) }
    }

    @Test
    fun `token caducado sin refresh_token devuelve la actual (no se puede refrescar)`() = runTest {
        val caducada = session(access = "old", refresh = "", expiresIn = -1_000)
        coEvery { sessionStore.current() } returns caducada

        val result = refresher.freshOrNull()

        assertEquals(caducada, result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
    }
}
