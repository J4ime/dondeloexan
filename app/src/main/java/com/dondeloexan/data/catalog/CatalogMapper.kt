package com.dondeloexan.data.catalog

import com.dondeloexan.domain.model.AvailabilityType
import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentSource
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.ExternalLinks
import com.dondeloexan.domain.model.PersonInfo
import com.dondeloexan.domain.model.PlatformReleaseDate
import com.dondeloexan.domain.model.StreamingAvailability
import com.dondeloexan.domain.model.detail.Episode
import com.dondeloexan.domain.model.detail.Season
import com.dondeloexan.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

// ── Content → fila de catálogo ───────────────────────────────────────────────

fun Content.toCatalogMovieRow(now: Long = System.currentTimeMillis()): CatalogMovieRow = CatalogMovieRow(
    contentId = id,
    title = title,
    tmdbId = tmdbId,
    imdbId = imdbId,
    originalTitle = originalTitle,
    year = year,
    releaseDate = releaseDate,
    spanishReleaseDate = spanishReleaseDate,
    digitalReleaseDate = digitalReleaseDate,
    tvReleaseDate = tvReleaseDate,
    durationMinutes = durationMinutes,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    ratingRt = ratingRt,
    ratingMetacritic = ratingMetacritic,
    ratingFilmaffinity = ratingFilmaffinity,
    certification = certification,
    synopsis = synopsis,
    coverUrl = coverUrl,
    backdropUrl = backdropUrl,
    directors = directors.personInfoToJson(),
    writers = writers.stringListToJson(),
    castJson = cast.personInfoToJson(),
    music = music.stringListToJson(),
    cinematography = cinematography.stringListToJson(),
    productionCompanies = productionCompanies.stringListToJson(),
    genres = genres.stringListToJson(),
    countries = countries.stringListToJson(),
    streamingPlatforms = streamingPlatforms.streamingToJson(),
    externalLinks = externalLinks?.toJson(),
    collectionTmdbId = collectionTmdbId,
    updatedAt = now
)

fun Content.toCatalogTvShowRow(now: Long = System.currentTimeMillis()): CatalogTvShowRow = CatalogTvShowRow(
    contentId = id,
    title = title,
    tmdbId = tmdbId,
    imdbId = imdbId,
    originalTitle = originalTitle,
    year = year,
    releaseDate = releaseDate,
    spanishReleaseDate = spanishReleaseDate,
    digitalReleaseDate = digitalReleaseDate,
    tvReleaseDate = tvReleaseDate,
    durationMinutes = durationMinutes,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    ratingRt = ratingRt,
    ratingMetacritic = ratingMetacritic,
    ratingFilmaffinity = ratingFilmaffinity,
    certification = certification,
    synopsis = synopsis,
    coverUrl = coverUrl,
    backdropUrl = backdropUrl,
    directors = directors.personInfoToJson(),
    writers = writers.stringListToJson(),
    castJson = cast.personInfoToJson(),
    music = music.stringListToJson(),
    cinematography = cinematography.stringListToJson(),
    productionCompanies = productionCompanies.stringListToJson(),
    genres = genres.stringListToJson(),
    countries = countries.stringListToJson(),
    streamingPlatforms = streamingPlatforms.streamingToJson(),
    externalLinks = externalLinks?.toJson(),
    totalEpisodes = totalEpisodes,
    numSeasons = null,
    inProduction = null,
    seriesStatus = null,
    nextEpisodeAirDate = null,
    nextEpisodeNumber = null,
    nextEpisodeSeason = null,
    updatedAt = now
)

fun Season.toCatalogSeasonRow(contentId: String) = CatalogSeasonRow(
    contentId = contentId,
    seasonNumber = seasonNumber,
    name = name,
    episodeCount = episodeCount,
    airDate = airDate,
    overview = overview,
    posterPath = posterPath,
    tmdbSeasonId = id
)

fun Episode.toCatalogEpisodeRow(contentId: String) = CatalogEpisodeRow(
    contentId = contentId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    name = name,
    overview = overview,
    airDate = airDate,
    stillPath = stillPath,
    voteAverage = voteAverage,
    episodeType = episodeType
)

fun ContentPreview.toCatalogListRow(contentId: String, listType: String, pos: Int) = CatalogListRow(
    contentId = contentId,
    listType = listType,
    pos = pos,
    relatedContentId = id,
    relatedData = toJson()
)

// ── fila de catálogo → Content ───────────────────────────────────────────────

fun CatalogMovieRow.toContent(): Content = Content(
    id = contentId,
    source = contentId.sourceFor(),
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = title,
    originalTitle = originalTitle,
    type = ContentType.MOVIE,
    year = year,
    releaseDate = releaseDate,
    spanishReleaseDate = spanishReleaseDate,
    digitalReleaseDate = digitalReleaseDate,
    tvReleaseDate = tvReleaseDate,
    durationMinutes = durationMinutes,
    totalEpisodes = null,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    ratingRt = ratingRt,
    ratingMetacritic = ratingMetacritic,
    ratingFilmaffinity = ratingFilmaffinity,
    certification = certification,
    synopsis = synopsis,
    coverUrl = coverUrl,
    backdropUrl = backdropUrl,
    directors = directors.toPersonInfoList(),
    writers = writers.toStringList(),
    cast = castJson.toPersonInfoList(),
    music = music.toStringList(),
    cinematography = cinematography.toStringList(),
    productionCompanies = productionCompanies.toStringList(),
    genres = genres.toStringList(),
    countries = countries.toStringList(),
    streamingPlatforms = streamingPlatforms.toStreamingList(),
    platformReleaseDates = emptyList(),
    externalLinks = externalLinks.toExternalLinks(),
    collectionTmdbId = collectionTmdbId,
    lastCachedAt = updatedAt
)

fun CatalogTvShowRow.toContent(): Content = Content(
    id = contentId,
    source = contentId.sourceFor(),
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = title,
    originalTitle = originalTitle,
    type = ContentType.SERIES,
    year = year,
    releaseDate = releaseDate,
    spanishReleaseDate = spanishReleaseDate,
    digitalReleaseDate = digitalReleaseDate,
    tvReleaseDate = tvReleaseDate,
    durationMinutes = durationMinutes,
    totalEpisodes = totalEpisodes,
    ratingTmdb = ratingTmdb,
    ratingImdb = ratingImdb,
    ratingRt = ratingRt,
    ratingMetacritic = ratingMetacritic,
    ratingFilmaffinity = ratingFilmaffinity,
    certification = certification,
    synopsis = synopsis,
    coverUrl = coverUrl,
    backdropUrl = backdropUrl,
    directors = directors.toPersonInfoList(),
    writers = writers.toStringList(),
    cast = castJson.toPersonInfoList(),
    music = music.toStringList(),
    cinematography = cinematography.toStringList(),
    productionCompanies = productionCompanies.toStringList(),
    genres = genres.toStringList(),
    countries = countries.toStringList(),
    streamingPlatforms = streamingPlatforms.toStreamingList(),
    platformReleaseDates = emptyList(),
    externalLinks = externalLinks.toExternalLinks(),
    lastCachedAt = updatedAt
)

fun CatalogSeasonRow.toSeason(): Season = Season(
    seasonNumber = seasonNumber,
    name = name,
    episodeCount = episodeCount ?: 0,
    airDate = airDate,
    overview = overview,
    posterPath = posterPath,
    id = tmdbSeasonId
)

fun CatalogEpisodeRow.toEpisode(): Episode = Episode(
    episodeNumber = episodeNumber,
    name = name,
    overview = overview,
    airDate = airDate,
    stillPath = stillPath,
    voteAverage = voteAverage,
    seasonNumber = seasonNumber,
    episodeType = episodeType
)

fun CatalogListRow.toContentPreview(): ContentPreview =
    relatedData?.toContentPreview() ?: ContentPreview(
        id = relatedContentId,
        source = relatedContentId.sourceFor(),
        title = "",
        type = if (relatedContentId.startsWith("tmdb-") || relatedContentId.startsWith("imdb-")) {
            ContentType.MOVIE
        } else ContentType.MOVIE
    )

private fun String.sourceFor(): ContentSource =
    if (startsWith("tmdb-")) ContentSource.TMDB else ContentSource.IMDB

// ── Helpers JSON ─────────────────────────────────────────────────────────────

fun List<PersonInfo>.personInfoToJson(): String {
    val arr = JSONArray()
    for (p in this) {
        val o = JSONObject()
        o.put("name", p.name)
        p.profilePath?.let { o.put("profilePath", it) }
        p.tmdbId?.let { o.put("tmdbId", it) }
        arr.put(o)
    }
    return arr.toString()
}

fun String?.toPersonInfoList(): List<PersonInfo> {
    if (isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PersonInfo(
                name = o.optString("name", ""),
                profilePath = o.optString("profilePath", "").ifBlank { null },
                tmdbId = if (o.has("tmdbId") && !o.isNull("tmdbId")) {
                    runCatching { o.getInt("tmdbId") }.getOrNull()
                } else null
            )
        }
    } catch (e: Exception) {
        AppLogger.e("CatalogMapper", "PersonInfo parse error", e)
        emptyList()
    }
}

fun List<String>.stringListToJson(): String {
    val arr = JSONArray()
    for (s in this) arr.put(s)
    return arr.toString()
}

fun String?.toStringList(): List<String> {
    if (isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        AppLogger.e("CatalogMapper", "List<String> parse error", e)
        emptyList()
    }
}

fun List<StreamingAvailability>.streamingToJson(): String {
    val arr = JSONArray()
    for (p in this) {
        val o = JSONObject()
        o.put("platformName", p.platformName)
        p.platformId?.let { o.put("platformId", it) }
        p.logoUrl?.let { o.put("logoUrl", it) }
        o.put("availabilityType", p.availabilityType.name)
        p.webUrl?.let { o.put("webUrl", it) }
        arr.put(o)
    }
    return arr.toString()
}

fun String?.toStreamingList(): List<StreamingAvailability> {
    if (isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StreamingAvailability(
                platformName = o.optString("platformName", ""),
                platformId = o.optString("platformId", "").ifBlank { null },
                logoUrl = o.optString("logoUrl", "").ifBlank { null },
                availabilityType = runCatching {
                    AvailabilityType.valueOf(o.optString("availabilityType", "SUBSCRIPTION"))
                }.getOrDefault(AvailabilityType.SUBSCRIPTION),
                webUrl = o.optString("webUrl", "").ifBlank { null }
            )
        }
    } catch (e: Exception) {
        AppLogger.e("CatalogMapper", "StreamingAvailability parse error", e)
        emptyList()
    }
}

fun ExternalLinks.toJson(): String {
    val o = JSONObject()
    imdbId?.let { o.put("imdbId", it) }
    wikipediaUrl?.let { o.put("wikipediaUrl", it) }
    facebookId?.let { o.put("facebookId", it) }
    instagramId?.let { o.put("instagramId", it) }
    twitterId?.let { o.put("twitterId", it) }
    youtubeId?.let { o.put("youtubeId", it) }
    homepage?.let { o.put("homepage", it) }
    filmaffinityUrl?.let { o.put("filmaffinityUrl", it) }
    wikidataId?.let { o.put("wikidataId", it) }
    return o.toString()
}

fun String?.toExternalLinks(): ExternalLinks? {
    if (isNullOrBlank()) return null
    return try {
        val o = JSONObject(this)
        ExternalLinks(
            imdbId = o.optString("imdbId", "").ifBlank { null },
            wikipediaUrl = o.optString("wikipediaUrl", "").ifBlank { null },
            facebookId = o.optString("facebookId", "").ifBlank { null },
            instagramId = o.optString("instagramId", "").ifBlank { null },
            twitterId = o.optString("twitterId", "").ifBlank { null },
            youtubeId = o.optString("youtubeId", "").ifBlank { null },
            homepage = o.optString("homepage", "").ifBlank { null },
            filmaffinityUrl = o.optString("filmaffinityUrl", "").ifBlank { null },
            wikidataId = o.optString("wikidataId", "").ifBlank { null }
        )
    } catch (e: Exception) {
        AppLogger.e("CatalogMapper", "ExternalLinks parse error", e)
        null
    }
}

fun ContentPreview.toJson(): String {
    val o = JSONObject()
    o.put("id", id)
    o.put("source", source.name)
    tmdbId?.let { o.put("tmdbId", it) }
    imdbId?.let { o.put("imdbId", it) }
    o.put("title", title)
    o.put("type", type.name)
    year?.let { o.put("year", it) }
    releaseDate?.let { o.put("releaseDate", it) }
    coverUrl?.let { o.put("coverUrl", it) }
    if (directors.isNotEmpty()) o.put("directors", directors.stringListToJson())
    ratingImdb?.let { o.put("ratingImdb", it) }
    if (genres.isNotEmpty()) o.put("genres", genres.stringListToJson())
    if (streamingPlatforms.isNotEmpty()) o.put("streamingPlatforms", streamingPlatforms.streamingToJson())
    if (platformReleaseDates.isNotEmpty()) o.put("platformReleaseDates", platformReleaseDates.platformReleaseToJson())
    totalEpisodes?.let { o.put("totalEpisodes", it) }
    voteCount?.let { o.put("voteCount", it) }
    if (isAdult) o.put("isAdult", true)
    return o.toString()
}

fun String?.toContentPreview(): ContentPreview? {
    if (isNullOrBlank()) return null
    return try {
        val o = JSONObject(this)
        ContentPreview(
            id = o.getString("id"),
            source = runCatching { ContentSource.valueOf(o.optString("source", "TMDB")) }
                .getOrDefault(ContentSource.TMDB),
            tmdbId = if (o.has("tmdbId")) o.getInt("tmdbId") else null,
            imdbId = o.optString("imdbId", "").ifBlank { null },
            title = o.optString("title", ""),
            type = runCatching { ContentType.valueOf(o.optString("type", "MOVIE")) }
                .getOrDefault(ContentType.MOVIE),
            year = if (o.has("year")) o.getInt("year") else null,
            releaseDate = o.optString("releaseDate", "").ifBlank { null },
            coverUrl = o.optString("coverUrl", "").ifBlank { null },
            directors = o.optString("directors", "").toStringList(),
            ratingImdb = if (o.has("ratingImdb")) o.getDouble("ratingImdb").toFloat() else null,
            genres = o.optString("genres", "").toStringList(),
            streamingPlatforms = o.optString("streamingPlatforms", "").toStreamingList(),
            platformReleaseDates = o.optString("platformReleaseDates", "").toPlatformReleaseDates(),
            totalEpisodes = if (o.has("totalEpisodes")) o.getInt("totalEpisodes") else null,
            voteCount = if (o.has("voteCount")) o.getInt("voteCount") else null,
            isAdult = o.optBoolean("isAdult", false)
        )
    } catch (e: Exception) {
        AppLogger.e("CatalogMapper", "ContentPreview parse error", e)
        null
    }
}

fun List<PlatformReleaseDate>.platformReleaseToJson(): String {
    val arr = JSONArray()
    for (r in this) {
        val o = JSONObject()
        o.put("platformName", r.platformName)
        o.put("dateLabel", r.dateLabel)
        r.releaseDate?.let { o.put("releaseDate", it) }
        arr.put(o)
    }
    return arr.toString()
}

fun String?.toPlatformReleaseDates(): List<PlatformReleaseDate> {
    if (isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PlatformReleaseDate(
                platformName = o.optString("platformName", ""),
                dateLabel = o.optString("dateLabel", ""),
                releaseDate = o.optString("releaseDate", "").ifBlank { null }
            )
        }
    } catch (e: Exception) {
        AppLogger.e("CatalogMapper", "PlatformReleaseDate parse error", e)
        emptyList()
    }
}