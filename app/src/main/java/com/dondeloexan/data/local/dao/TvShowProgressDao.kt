package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dondeloexan.data.local.entity.TvShowProgressEntity

@Dao
interface TvShowProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: TvShowProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progressItems: List<TvShowProgressEntity>)

    @Query("SELECT * FROM tv_show_progress WHERE tv_show_id = :tvShowId ORDER BY season ASC, episode ASC")
    suspend fun getByTvShowId(tvShowId: Long): List<TvShowProgressEntity>

    @Query("SELECT COUNT(*) FROM tv_show_progress WHERE tv_show_id = :tvShowId")
    suspend fun getEpisodeCount(tvShowId: Long): Int

    @Query("SELECT * FROM tv_show_progress ORDER BY tv_show_id ASC, season ASC, episode ASC")
    suspend fun getAll(): List<TvShowProgressEntity>

    @androidx.room.Delete
    suspend fun delete(progress: TvShowProgressEntity)

    @Query("DELETE FROM tv_show_progress WHERE tv_show_id = :tvShowId AND season = :season AND episode = :episode")
    suspend fun deleteEpisode(tvShowId: Long, season: Int, episode: Int)

    @Query("DELETE FROM tv_show_progress WHERE tv_show_id = :tvShowId")
    suspend fun deleteByTvShowId(tvShowId: Long)

    @Query("DELETE FROM tv_show_progress")
    suspend fun deleteAll()
}
