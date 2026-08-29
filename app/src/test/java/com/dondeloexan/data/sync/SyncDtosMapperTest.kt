package com.dondeloexan.data.sync

import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.UserPlatformEntity
import com.dondeloexan.data.local.entity.WatchStatus
import org.junit.jupiter.api.Test

class SyncDtosMapperTest {

    private val userId = "uuid-123"

    @Test
    fun `MovieEntity se mapea a DTO con booleanos 0 o 1 y status`() {
        val entity = MovieEntity(
            id = 9, contentId = "c1", tmdbId = 5, imdbId = "tt1",
            title = "Los cazafantasmas", year = 1984, releaseDate = "1984-06-08",
            posterUrl = "poster", ratingTmdb = 8.0f, ratingImdb = 7.8f,
            certification = "PG", status = WatchStatus.YA_VISTA, liked = true,
            streamingPlatforms = "[]", watchedAt = 1000L, addedAt = 2000L,
            lastRefreshedAt = 3000L, faId = 42
        )

        val dto = entity.toSyncDto(userId)

        org.junit.jupiter.api.Assertions.assertEquals(9L, dto.localId)
        org.junit.jupiter.api.Assertions.assertEquals(userId, dto.userId)
        org.junit.jupiter.api.Assertions.assertEquals("c1", dto.contentId)
        org.junit.jupiter.api.Assertions.assertEquals(5, dto.tmdbId)
        org.junit.jupiter.api.Assertions.assertEquals("tt1", dto.imdbId)
        org.junit.jupiter.api.Assertions.assertEquals("Los cazafantasmas", dto.title)
        org.junit.jupiter.api.Assertions.assertEquals(1984, dto.year)
        org.junit.jupiter.api.Assertions.assertEquals(8.0f, dto.ratingTmdb)
        org.junit.jupiter.api.Assertions.assertEquals("YA_VISTA", dto.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, dto.liked)
        org.junit.jupiter.api.Assertions.assertEquals(1000L, dto.watchedAt)
        org.junit.jupiter.api.Assertions.assertEquals(3000L, dto.lastRefreshedAt)
        org.junit.jupiter.api.Assertions.assertEquals(42, dto.faId)
    }

    @Test
    fun `TvShowEntity con liked false e in_production false mapea a 0`() {
        val entity = TvShowEntity(
            id = 7, title = "Serie", status = WatchStatus.POR_VER,
            liked = false, totalEpisodes = 10, inProduction = false,
            numberOfSeasons = 2, releasedEpisodes = 5
        )

        val dto = entity.toSyncDto(userId)

        org.junit.jupiter.api.Assertions.assertEquals(7L, dto.localId)
        org.junit.jupiter.api.Assertions.assertEquals(0, dto.liked)
        org.junit.jupiter.api.Assertions.assertEquals(0, dto.inProduction)
        org.junit.jupiter.api.Assertions.assertEquals(10, dto.totalEpisodes)
        org.junit.jupiter.api.Assertions.assertEquals(2, dto.numberOfSeasons)
        org.junit.jupiter.api.Assertions.assertEquals(5, dto.releasedEpisodes)
    }

    @Test
    fun `UserPlatformEntity desactivada mapea is_active a 0`() {
        val dto = UserPlatformEntity("Netflix", isActive = false).toSyncDto(userId)
        org.junit.jupiter.api.Assertions.assertEquals(0, dto.isActive)
        org.junit.jupiter.api.Assertions.assertEquals(userId, dto.userId)
        org.junit.jupiter.api.Assertions.assertEquals("Netflix", dto.platformName)
    }
}