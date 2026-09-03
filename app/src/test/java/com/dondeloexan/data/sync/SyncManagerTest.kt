package com.dondeloexan.data.sync

import com.dondeloexan.data.catalog.CloudCatalogRepository
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncManagerTest {

    private val syncApi: SupabaseSyncApi = mockk()
    private val cloudCatalog: CloudCatalogRepository = mockk()
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

    private val userTables = listOf(
        "user_movies",
        "user_tv_shows",
        "tv_show_progress",
        "search_history",
        "user_platforms",
        "blacklist"
    )

    private fun manager() = SyncManager(
        syncApi = syncApi,
        cloudCatalog = cloudCatalog,
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
    fun `syncAll alimenta el catalogo y re-sube las tablas de usuario`() = runTest {
        coEvery { syncApi.deleteTableRows(any(), any(), any()) } returns Unit
        coEvery { syncApi.insertAll(any(), any(), any(), any()) } returns ""
        coEvery { cloudCatalog.saveMovies(any(), any()) } returns Unit
        coEvery { cloudCatalog.saveTvShows(any(), any()) } returns Unit
        coEvery { cloudCatalog.saveCriticReviews(any(), any()) } returns Unit
        coEvery { cloudCatalog.saveFaMovieData(any(), any()) } returns Unit

        coEvery { movieDao.getAll() } returns listOf(
            MovieEntity(id = 1, contentId = "m1", title = "A", liked = true, addedAt = 1000),
            MovieEntity(id = 2, contentId = "m2", title = "B", liked = false, addedAt = 2000)
        )
        coEvery { tvShowDao.getAll() } returns listOf(
            TvShowEntity(id = 7, contentId = "showA", title = "S1", status = WatchStatus.POR_VER, addedAt = 3000),
            TvShowEntity(id = 8, contentId = "showB", title = "S2", status = WatchStatus.YA_VISTA, addedAt = 4000)
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

        val summary = manager().syncAll(session)

        // Catálogo global: merge, nunca se borra.
        userTables.forEach { table ->
            coVerify { syncApi.deleteTableRows(table, session.userId, session) }
        }
        coVerify(exactly = 0) { syncApi.deleteTableRows("movies", any(), any()) }
        coVerify(exactly = 0) { syncApi.deleteTableRows("tv_shows", any(), any()) }
        coVerify { cloudCatalog.saveMovies(any(), session) }
        coVerify { cloudCatalog.saveTvShows(any(), session) }
        coVerify { cloudCatalog.saveCriticReviews(any(), session) }
        coVerify { cloudCatalog.saveFaMovieData(any(), session) }

        // Relaciones de usuario con content_id.
        val moviesPayload = slot<String>()
        coVerify { syncApi.insertAll("user_movies", capture(moviesPayload), session, false) }
        assertTrue(moviesPayload.captured.contains("\"user_id\":\"uuid-123\""))
        assertTrue(moviesPayload.captured.contains("\"content_id\":\"m1\""))
        assertTrue(moviesPayload.captured.contains("\"liked\":1"))
        assertTrue(moviesPayload.captured.contains("\"liked\":0"))
        assertTrue(moviesPayload.captured.contains("\"title\"") == false)

        val seriesPayload = slot<String>()
        coVerify { syncApi.insertAll("user_tv_shows", capture(seriesPayload), session, false) }
        assertTrue(seriesPayload.captured.contains("\"status\":\"YA_VISTA\""))

        val progressPayload = slot<String>()
        coVerify { syncApi.insertAll("tv_show_progress", capture(progressPayload), session, false) }
        assertTrue(progressPayload.captured.contains("\"content_id\":\"showA\""))
        assertTrue(progressPayload.captured.contains("\"content_id\":\"showB\""))

        coVerify {
            syncApi.insertAll(
                "search_history",
                match<String> { it.contains("\"query\":\"matrix\"") },
                session,
                false
            )
        }
        coVerify {
            syncApi.insertAll(
                "user_platforms",
                match<String> { it.contains("\"is_active\":1") && it.contains("\"is_active\":0") },
                session,
                false
            )
        }
        coVerify {
            syncApi.insertAll(
                "blacklist",
                match<String> { it.contains("\"content_id\":\"b1\"") },
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
    fun `syncAll con biblioteca vacia no sube nada y no toca el catalogo`() = runTest {
        coEvery { syncApi.deleteTableRows(any(), any(), any()) } returns Unit
        coEvery { movieDao.getAll() } returns emptyList()
        coEvery { tvShowDao.getAll() } returns emptyList()
        coEvery { tvShowProgressDao.getAll() } returns emptyList()
        coEvery { searchHistoryDao.getRecent(any()) } returns emptyList()
        coEvery { userPlatformDao.getAll() } returns emptyList()
        coEvery { blacklistDao.getAll() } returns emptyList()
        coEvery { criticReviewDao.getAll() } returns emptyList()
        coEvery { faMovieDataDao.getAll() } returns emptyList()

        val summary = manager().syncAll(session)

        userTables.forEach { table ->
            coVerify(exactly = 1) { syncApi.deleteTableRows(table, session.userId, session) }
        }
        coVerify(exactly = 0) { syncApi.insertAll(any(), any(), any(), any()) }
        coVerify(exactly = 0) { cloudCatalog.saveMovies(any(), any()) }
        coVerify(exactly = 0) { cloudCatalog.saveTvShows(any(), any()) }
        coVerify(exactly = 0) { cloudCatalog.saveCriticReviews(any(), any()) }
        coVerify(exactly = 0) { cloudCatalog.saveFaMovieData(any(), any()) }
        assertEquals(0, summary.total)
    }

    @Test
    fun `payload de user_movies emite todas las claves aunque haya nulls (evita PGRST100)`() = runTest {
        coEvery { syncApi.deleteTableRows(any(), any(), any()) } returns Unit
        coEvery { movieDao.getAll() } returns listOf(
            MovieEntity(
                id = 1, contentId = "c1", tmdbId = 5, imdbId = "tt1",
                title = "A", year = 1984, releaseDate = "1984-06-08",
                posterUrl = "p", ratingTmdb = 8.0f, ratingImdb = 7.8f,
                certification = "PG", status = WatchStatus.YA_VISTA, liked = true,
                streamingPlatforms = "[]", watchedAt = 1000L, addedAt = 2000L,
                lastRefreshedAt = 3000L, faId = 42
            ),
            MovieEntity(id = 2, contentId = "c2", title = "B", status = WatchStatus.POR_VER, liked = false, addedAt = 4000L)
        )
        coEvery { tvShowDao.getAll() } returns emptyList()
        coEvery { tvShowProgressDao.getAll() } returns emptyList()
        coEvery { searchHistoryDao.getRecent(any()) } returns emptyList()
        coEvery { userPlatformDao.getAll() } returns emptyList()
        coEvery { blacklistDao.getAll() } returns emptyList()
        coEvery { criticReviewDao.getAll() } returns emptyList()
        coEvery { faMovieDataDao.getAll() } returns emptyList()
        coEvery { syncApi.insertAll(any(), any(), any(), any()) } returns ""
        coEvery { cloudCatalog.saveMovies(any(), any()) } returns Unit

        manager().syncAll(session)

        val moviesPayload = slot<String>()
        coVerify { syncApi.insertAll("user_movies", capture(moviesPayload), session, false) }
        val array = json.parseToJsonElement(moviesPayload.captured).jsonArray
        val keySets = array.map { it.jsonObject.keys.sorted() }
        assertEquals(1, keySets.distinct().size)
        assertTrue(moviesPayload.captured.contains("\"status\":\"POR_VER\""))
        assertTrue(moviesPayload.captured.contains("\"faId\"") == false)
    }

    @Test
    fun `progreso cuya serie no tiene content_id se omite`() = runTest {
        coEvery { syncApi.deleteTableRows(any(), any(), any()) } returns Unit
        coEvery { movieDao.getAll() } returns emptyList()
        coEvery { tvShowDao.getAll() } returns emptyList()
        coEvery { tvShowProgressDao.getAll() } returns listOf(
            TvShowProgressEntity(id = 5, tvShowId = 99, season = 1, episode = 1, watchedAt = 1000)
        )
        coEvery { searchHistoryDao.getRecent(any()) } returns emptyList()
        coEvery { userPlatformDao.getAll() } returns emptyList()
        coEvery { blacklistDao.getAll() } returns emptyList()
        coEvery { criticReviewDao.getAll() } returns emptyList()
        coEvery { faMovieDataDao.getAll() } returns emptyList()
        coEvery { syncApi.insertAll(any(), any(), any(), any()) } returns ""

        val summary = manager().syncAll(session)

        coVerify(exactly = 0) {
            syncApi.insertAll("tv_show_progress", any(), session, any())
        }
        assertEquals(0, summary.tvShowProgress)
    }

    @Test
    fun `syncSingleTvShow borra por content_id y re-sube solo esa serie y su progreso`() = runTest {
        coEvery { syncApi.deleteTableRowsByContent(any(), any(), any()) } returns Unit
        coEvery { syncApi.insertAll(any(), any(), any(), any()) } returns ""
        coEvery { tvShowProgressDao.getByTvShowId(7) } returns listOf(
            TvShowProgressEntity(id = 1, tvShowId = 7, season = 1, episode = 1, watchedAt = 5000),
            TvShowProgressEntity(id = 2, tvShowId = 7, season = 2, episode = 1, watchedAt = 6000)
        )
        val show = TvShowEntity(id = 7, contentId = "showA", title = "S1", status = WatchStatus.YA_VISTA, addedAt = 3000)

        manager().syncSingleTvShow(session, show)

        coVerify(exactly = 1) { syncApi.deleteTableRowsByContent("user_tv_shows", "showA", session) }
        coVerify(exactly = 1) { syncApi.deleteTableRowsByContent("tv_show_progress", "showA", session) }
        val userPayload = slot<String>()
        coVerify { syncApi.insertAll("user_tv_shows", capture(userPayload), session, false) }
        assertTrue(userPayload.captured.contains("\"content_id\":\"showA\""))
        val progressPayload = slot<String>()
        coVerify { syncApi.insertAll("tv_show_progress", capture(progressPayload), session, false) }
        assertTrue(progressPayload.captured.contains("\"content_id\":\"showA\""))
        coVerify(exactly = 0) { syncApi.insertAll("user_movies", any(), any(), any()) }
        coVerify(exactly = 0) { cloudCatalog.saveTvShows(any(), any()) }
    }

    @Test
    fun `syncSingleTvShow sin content_id se omite sin subir nada`() = runTest {
        coEvery { syncApi.deleteTableRowsByContent(any(), any(), any()) } returns Unit
        coEvery { syncApi.insertAll(any(), any(), any(), any()) } returns ""
        val show = TvShowEntity(id = 9, title = "Sin contenido", status = WatchStatus.POR_VER)

        manager().syncSingleTvShow(session, show)

        coVerify(exactly = 0) { syncApi.deleteTableRowsByContent(any(), any(), any()) }
        coVerify(exactly = 0) { syncApi.insertAll(any(), any(), any(), any()) }
    }
}
