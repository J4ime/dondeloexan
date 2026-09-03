package com.dondeloexan.data.catalog

import com.dondeloexan.data.remote.api.SupabaseSyncApi
import com.dondeloexan.domain.model.SessionState
import com.dondeloexan.util.AppLogger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Acceso al catálogo global de la nube (sin user_id, RLS "anon").
 * Lecturas: devuelven null si no existe (o si la nube no responde, para que el
 * flujo caiga a las APIs). Escrituras: best-effort (nunca rompen el flujo).
 * El catálogo siempre se accede con la sesión ANÓNIMA (anon key), que nunca
 * caduca y funciona sin login; los datos de usuario usan su propio JWT aparte.
 */
class CloudCatalogRepository(
    private val syncApi: SupabaseSyncApi,
    private val json: Json
) {

    private val payloadJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // El catálogo se lee/escribe siempre con la sesión anónima (anon key).
    suspend fun currentSession(): SessionState = syncApi.anonymousSession()

    // ── Lecturas ───────────────────────────────────────────────────────────

    suspend fun getMovie(contentId: String, session: SessionState): CatalogMovieRow? =
        getRows("movies", "content_id=eq.$contentId", CatalogMovieRow.serializer(), session).firstOrNull()

    suspend fun getTvShow(contentId: String, session: SessionState): CatalogTvShowRow? =
        getRows("tv_shows", "content_id=eq.$contentId", CatalogTvShowRow.serializer(), session).firstOrNull()

    suspend fun getCriticReviews(contentId: String, session: SessionState): CatalogCriticReviewRow? =
        getRows("critic_reviews", "content_id=eq.$contentId", CatalogCriticReviewRow.serializer(), session).firstOrNull()

    suspend fun getFaMovieData(contentId: String, session: SessionState): CatalogFaRow? =
        getRows("fa_movie_data", "content_id=eq.$contentId", CatalogFaRow.serializer(), session).firstOrNull()

    suspend fun getSeason(contentId: String, seasonNumber: Int, session: SessionState): CatalogSeasonRow? =
        getRows(
            "tv_seasons",
            "content_id=eq.$contentId&season_number=eq.$seasonNumber",
            CatalogSeasonRow.serializer(), session
        ).firstOrNull()

    suspend fun getSeasons(contentId: String, session: SessionState): List<CatalogSeasonRow> =
        getRows(
            "tv_seasons",
            "content_id=eq.$contentId&order=season_number.asc",
            CatalogSeasonRow.serializer(), session
        )

    suspend fun getSeasonEpisodes(contentId: String, seasonNumber: Int, session: SessionState): List<CatalogEpisodeRow> =
        getRows(
            "tv_episodes",
            "content_id=eq.$contentId&season_number=eq.$seasonNumber&order=episode_number.asc",
            CatalogEpisodeRow.serializer(), session
        )

    suspend fun getList(contentId: String, listType: String, session: SessionState): List<CatalogListRow> =
        getRows(
            "content_lists",
            "content_id=eq.$contentId&list_type=eq.$listType&order=pos.asc",
            CatalogListRow.serializer(), session
        )

    private suspend fun <T> getRows(
        table: String,
        query: String,
        serializer: KSerializer<T>,
        session: SessionState
    ): List<T> = try {
        val body = syncApi.select(table, query, syncApi.anonymousSession())
        if (body.isBlank()) emptyList()
        else json.decodeFromString(ListSerializer(serializer), body)
    } catch (e: Exception) {
        AppLogger.e("Catalog", "lectura $table?$query falló", e)
        emptyList()
    }

    // ── Escrituras (upsert merge, best-effort) ─────────────────────────────

    suspend fun saveMovies(rows: List<CatalogMovieRow>, session: SessionState) =
        upsert("movies", rows, CatalogMovieRow.serializer(), session)

    suspend fun saveTvShows(rows: List<CatalogTvShowRow>, session: SessionState) =
        upsert("tv_shows", rows, CatalogTvShowRow.serializer(), session)

    suspend fun saveSeasons(rows: List<CatalogSeasonRow>, session: SessionState) =
        upsert("tv_seasons", rows, CatalogSeasonRow.serializer(), session)

    suspend fun saveEpisodes(rows: List<CatalogEpisodeRow>, session: SessionState) =
        upsert("tv_episodes", rows, CatalogEpisodeRow.serializer(), session)

    suspend fun saveCriticReviews(rows: List<CatalogCriticReviewRow>, session: SessionState) =
        upsert("critic_reviews", rows, CatalogCriticReviewRow.serializer(), session)

    suspend fun saveFaMovieData(rows: List<CatalogFaRow>, session: SessionState) =
        upsert("fa_movie_data", rows, CatalogFaRow.serializer(), session)

    suspend fun saveLists(rows: List<CatalogListRow>, session: SessionState) =
        upsert("content_lists", rows, CatalogListRow.serializer(), session)

    private suspend fun <T> upsert(
        table: String,
        rows: List<T>,
        serializer: KSerializer<T>,
        session: SessionState
    ) {
        if (rows.isEmpty()) return
        val rowsJson = payloadJson.encodeToString(ListSerializer(serializer), rows)
        val body = """{"_p_table":"$table","_p_rows":$rowsJson}"""
        try {
            syncApi.rpc("catalog_merge", body, syncApi.anonymousSession())
            AppLogger.d("Catalog", "catalog_merge $table OK (${rows.size} filas)")
        } catch (e: Exception) {
            AppLogger.e("Catalog", "catalog_merge $table falló (${rows.size} filas): ${body.take(1000)}", e)
        }
    }
}