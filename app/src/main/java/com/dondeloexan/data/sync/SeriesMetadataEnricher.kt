package com.dondeloexan.data.sync

import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.remote.api.TmdbApi
import com.dondeloexan.data.remote.mapper.toDomain
import com.dondeloexan.data.remote.mapper.toStreamingAvailability
import com.dondeloexan.util.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Rellena la ficha técnica rica (directores, reparto, géneros, sinopsis,
 * ratings, plataformas...) de una serie en la entidad local, para que la
 * subida a la nube viaje completa en vez de con campos null.
 *
 * Se usa de forma "lazy" (al abrir el detalle o al marcar capítulos) y de forma
 * "batch" desde Ajustes → Sincronizar, recorriendo solo las series que aún no
 * tienen ficha. Best-effort: los fallos no rompen el flujo.
 */
class SeriesMetadataEnricher(
    private val tmdbApi: TmdbApi,
    private val tvShowDao: TvShowDao
) {
    /** True si la serie ya dispone de ficha rica mínima (repaso + géneros). */
    fun isSparse(show: TvShowEntity): Boolean =
        show.tmdbId != null && (show.castJson.isNullOrBlank() && show.genres.isNullOrBlank())

    /**
     * Batch (Ajustes → Sincronizar nube): enriquece la ficha de todas las
     * series locales que aún carecen de ella. Devuelve cuántas se actualizaron.
     */
    suspend fun enrichAll(): Int {
        val shows = tvShowDao.getAll()
        var updated = 0
        shows.filter { isSparse(it) }.forEach { show ->
            if (enrich(show)) updated++
        }
        return updated
    }

    suspend fun enrich(show: TvShowEntity): Boolean {
        val tmdbId = show.tmdbId ?: return false
        return try {
            val existing = tvShowDao.getById(show.id) ?: return false

            val content = fetchContent(tmdbId)

            val updated = existing.copy(
                totalEpisodes = content.totalEpisodes ?: existing.totalEpisodes,
                releasedEpisodes = existing.releasedEpisodes,
                nextEpisodeAirDate = existing.nextEpisodeAirDate,
                nextEpisodeNumber = existing.nextEpisodeNumber,
                nextEpisodeSeasonNumber = existing.nextEpisodeSeasonNumber,
                seriesStatus = existing.seriesStatus,
                inProduction = existing.inProduction,
                numberOfSeasons = existing.numberOfSeasons
            ).let { content.toTvShowEntity(it) }

            tvShowDao.update(updated)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("Enricher", "enrich falló para ${show.title} (tmdb=$tmdbId)", e)
            false
        }
    }

    private suspend fun fetchContent(tmdbId: Int) = run {
        val tv = tmdbApi.getTvDetail(tmdbId)
        val credits = tmdbApi.getTvCredits(tmdbId).also {
            AppLogger.d("Enricher", "credits tv=$tmdbId OK")
        }
        val providers = tmdbApi.getTvWatchProviders(tmdbId)
        val platforms = providers.results?.get("ES")?.toStreamingAvailability().orEmpty()

        val externalLinks = try {
            val social = tmdbApi.getTvExternalIds(tmdbId)
            com.dondeloexan.domain.model.ExternalLinks(
                imdbId = social.imdbId,
                facebookId = social.facebookId,
                instagramId = social.instagramId,
                twitterId = social.twitterId,
                youtubeId = social.youtubeId,
                wikidataId = social.wikidataId
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("Enricher", "externalIds falló para tv $tmdbId", e)
            null
        }

        tv.toDomain(null, platforms, credits, externalLinks)
    }
}
