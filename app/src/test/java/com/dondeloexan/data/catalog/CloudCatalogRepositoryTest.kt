package com.dondeloexan.data.catalog

import com.dondeloexan.data.remote.api.SupabaseSyncApi
import com.dondeloexan.data.sync.SessionState
import com.dondeloexan.data.sync.SessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CloudCatalogRepositoryTest {

    private val syncApi: SupabaseSyncApi = mockk()
    private val sessionStore: SessionStore = mockk()

    private val json = Json { ignoreUnknownKeys = true }
    private val session = SessionState(
        accessToken = "access",
        refreshToken = "refresh",
        expiresAt = System.currentTimeMillis() + 3_600_000,
        userId = "uuid-123",
        email = "usuario@test.es"
    )

    private fun repo() = CloudCatalogRepository(syncApi, sessionStore, json)

    @Test
    fun `saveMovies usa el RPC catalog_merge con payload uniforme`() = runTest {
        val rich = CatalogMovieRow(
            contentId = "c1", title = "A", tmdbId = 5, ratingTmdb = 8.0f, updatedAt = 3000L
        )
        val poor = CatalogMovieRow(
            contentId = "c2", title = "B", updatedAt = 0L
        )
        coEvery { syncApi.rpc(any(), any(), any()) } returns ""

        repo().saveMovies(listOf(rich, poor), session)

        val body = slot<String>()
        coVerify { syncApi.rpc("catalog_merge", capture(body), session) }

        val obj = json.parseToJsonElement(body.captured).jsonObject
        assertEquals("movies", (obj["_p_table"] as JsonPrimitive).content)
        val array = obj.getValue("_p_rows").jsonArray
        assertEquals(2, array.size)
        val keySets = array.map { it.jsonObject.keys.sorted() }
        assertEquals(1, keySets.distinct().size)
    }

    @Test
    fun `saveLists con lista vacia no llama al RPC`() = runTest {
        repo().saveLists(emptyList(), session)
        coVerify(exactly = 0) { syncApi.rpc(any(), any(), any()) }
    }

    @Test
    fun `getMovie devuelve la fila del catalogo decodificada`() = runTest {
        coEvery {
            syncApi.select("movies", "content_id=eq.c1", session)
        } returns """[{"content_id":"c1","title":"Matrix","rating_tmdb":8.7,"updated_at":123}]"""

        val row = repo().getMovie("c1", session)

        assertEquals("c1", row?.contentId)
        assertEquals("Matrix", row?.title)
        assertEquals(8.7f, row?.ratingTmdb)
        assertEquals(123L, row?.updatedAt)
    }

    @Test
    fun `lectura con error de la nube no rompe y devuelve null`() = runTest {
        coEvery { syncApi.select("movies", "content_id=eq.missing", session) } throws RuntimeException("offline")

        val movie = repo().getMovie("missing", session)

        assertNull(movie)
    }
}