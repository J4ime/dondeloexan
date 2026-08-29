package com.dondeloexan.data.sync

import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.entity.BlacklistedEntity
import com.dondeloexan.data.local.entity.CriticReviewEntity
import com.dondeloexan.data.local.entity.FaMovieDataEntity
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.SearchHistoryEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.TvShowProgressEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.data.local.entity.WatchStatus
import com.dondeloexan.data.remote.api.SupabaseSyncApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncManagerTest {

    private val syncApi: SupabaseSyncApi = mockk()
    private val movieDao: MovieDao = mockk()
    private val tvShowDao: TvShowDao = mockk()
    private val tvShowProgressDao: TvShowProgressDao = mockk()
    private val searchHistoryDao: SearchHistoryDao = mockk()
    private val userPlatformDao: UserPlatformDao = mockk()
    private val blacklistDao: BlacklistDao = mockk()
    private val criticReviewDao: CriticReviewDao = mockk()
    private val faMovieDataDao: FaMovieDataDao = mockk()

    private val json = Json { ignoreUnknownKeys = true }
    private val session = SessionState(
        accessToken = "access",
        refreshToken = "refresh",
        expiresAt = System.currentTimeMillis() + 3_600_000,
        userId = "uuid-123",
        email = "usuario@test.es"
    )

    private fun manager() = SyncManager(
        syncApi = syncApi,
        movieDao = movieDao,
        tvShowDao = tvShowDao,
        tvShowProgressDao = tvShowProgressDao,
        searchHistoryDao = searchHistoryDao,
        userPlatformDao = userPlatformDao,
        blacklistDao = blacklistDao,
        criticReviewDao = criticReviewDao,
        faMovieDataDao = faMovieDataDao,
        json = json
    )

    @Test
    fun `syncAll sube todas las tablas y remapea tv_show_id de progreso`() = runTest {
        coEvery { movieDao.getAll() } returns listOf(
            MovieEntity(id = 1, title = "A", liked = true, addedAt = 1000),
            MovieEntity(id = 2, title = "B", liked = false, addedAt = 2000)
        )
        coEvery { tvShowDao.getAll() } returns listOf(
            TvShowEntity(id = 7, title = "S1", status = WatchStatus.POR_VER, addedAt = 3000),
            TvShowEntity(id = 8, title = "S2", status = WatchStatus.YA_VISTA, addedAt = 4000)
        )
        coEvery { tvShowProgressDao.getAll() } returns listOf(
            TvShowProgressEntity(id = 1, tvShowId = 7, season = 1, episode = 1, watchedAt = 5000),
            TvShowProgressEntity(id = 2, tvShowId = 8, season = 1, episode = 2, watchedAt = 6000)
        )
        coEvery { searchHistoryDao.getRecent(any()) } returns listOf(
            SearchHistoryEntity(id = 3, query = "matrix", searchedAt = 7000)
        )
        coEvery { userPlatformDao.getAll() } returns listOf(
            UserPlatformEntity("Netflix", isActive = true),
            UserPlatformEntity("Disney+", isActive = false)
        )
        coEvery { blacklistDao.getAll() } returns listOf(
            BlacklistedEntity("b1", "Rocky", "movie", 8000)
        )
        coEvery { criticReviewDao.getAll() } returns listOf(
            CriticReviewEntity("c1", "{}", 9000)
        )
        coEvery { faMovieDataDao.getAll() } returns listOf(
            FaMovieDataEntity("c1", faId = 42, faRating = 8.5f, platformReleasesJson = null, cachedAt = 9100)
        )

        coEvery {
            syncApi.upsert(any(), any(), any(), any(), any())
        } returns ""

        coEvery {
            syncApi.upsert("tv_shows", listOf("user_id", "local_id"), any(), session, true)
        } returns """[{"id":101,"local_id":7},{"id":102,"local_id":8}]"""

        val summary = manager().syncAll(session)

        val moviesPayload = slot<String>()
        coVerify {
            syncApi.upsert("movies", listOf("user_id", "local_id"), capture(moviesPayload), session, false)
        }
        assertTrue(moviesPayload.captured.contains("\"local_id\":1"))
        assertTrue(moviesPayload.captured.contains("\"user_id\":\"uuid-123\""))
        assertTrue(moviesPayload.captured.contains("\"liked\":1"))
        assertTrue(moviesPayload.captured.contains("\"liked\":0"))
        assertTrue(moviesPayload.captured.contains("\"title\":\"A\""))

        val progressPayload = slot<String>()
        coVerify {
            syncApi.upsert("tv_show_progress", listOf("user_id", "local_id"), capture(progressPayload), session, false)
        }
        assertTrue(progressPayload.captured.contains("\"tv_show_id\":101"))
        assertTrue(progressPayload.captured.contains("\"tv_show_id\":102"))

        coVerify {
            syncApi.upsert(
                "search_history",
                listOf("user_id", "local_id"),
                match<String> { it.contains("\"local_id\":3") && it.contains("\"query\":\"matrix\"") },
                session,
                false
            )
        }
        coVerify {
            syncApi.upsert(
                "user_platforms",
                listOf("user_id", "platform_name"),
                match<String> { it.contains("\"is_active\":1") && it.contains("\"is_active\":0") },
                session,
                false
            )
        }
        coVerify {
            syncApi.upsert(
                "blacklist",
                listOf("user_id", "content_id"),
                match<String> { it.contains("\"content_id\":\"b1\"") },
                session,
                false
            )
        }
        coVerify {
            syncApi.upsert(
                "critic_reviews",
                listOf("user_id", "content_id"),
                match<String> { it.contains("\"reviews_json\":\"{}\"") },
                session,
                false
            )
        }
        coVerify {
            syncApi.upsert(
                "fa_movie_data",
                listOf("user_id", "content_id"),
                match<String> { it.contains("\"fa_id\":42") && it.contains("\"fa_rating\":8.5") },
                session,
                false
            )
        }

        assertEquals(2, summary.movies)
        assertEquals(2, summary.tvShows)
        assertEquals(2, summary.tvShowProgress)
        assertEquals(1, summary.searchHistory)
        assertEquals(2, summary.userPlatforms)
        assertEquals(1, summary.blacklist)
        assertEquals(1, summary.criticReviews)
        assertEquals(1, summary.faMovieData)
        assertEquals(12, summary.total)
    }

    @Test
    fun `syncAll con biblioteca vacia no llama a PostgREST`() = runTest {
        coEvery { movieDao.getAll() } returns emptyList()
        coEvery { tvShowDao.getAll() } returns emptyList()
        coEvery { tvShowProgressDao.getAll() } returns emptyList()
        coEvery { searchHistoryDao.getRecent(any()) } returns emptyList()
        coEvery { userPlatformDao.getAll() } returns emptyList()
        coEvery { blacklistDao.getAll() } returns emptyList()
        coEvery { criticReviewDao.getAll() } returns emptyList()
        coEvery { faMovieDataDao.getAll() } returns emptyList()

        val summary = manager().syncAll(session)

        coVerify(exactly = 0) { syncApi.upsert(any(), any(), any(), any(), any()) }
        assertEquals(0, summary.total)
    }
}