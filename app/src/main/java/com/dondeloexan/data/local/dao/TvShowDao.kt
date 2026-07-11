package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dondeloexan.data.local.entity.TvShowEntity
import com.dondeloexan.data.local.entity.WatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TvShowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tvShow: TvShowEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tvShows: List<TvShowEntity>)

    @Update
    suspend fun update(tvShow: TvShowEntity)

    @Delete
    suspend fun delete(tvShow: TvShowEntity)

    @Query("SELECT * FROM tv_shows ORDER BY last_watched_at DESC, added_at DESC")
    fun getAllFlow(): Flow<List<TvShowEntity>>

    @Query("SELECT * FROM tv_shows ORDER BY last_watched_at DESC, added_at DESC")
    suspend fun getAll(): List<TvShowEntity>

    @Query("SELECT * FROM tv_shows WHERE id = :id")
    suspend fun getById(id: Long): TvShowEntity?

    @Query("SELECT * FROM tv_shows WHERE content_id = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): TvShowEntity?

    @Query("SELECT * FROM tv_shows WHERE status = :status ORDER BY last_watched_at DESC, added_at DESC")
    fun getByStatus(status: WatchStatus): Flow<List<TvShowEntity>>

    @Query("SELECT * FROM tv_shows WHERE liked = 1 ORDER BY last_watched_at DESC, added_at DESC")
    fun getLiked(): Flow<List<TvShowEntity>>

    @Query("UPDATE tv_shows SET last_watched_at = :timestamp WHERE id = :id")
    suspend fun updateLastWatchedAt(id: Long, timestamp: Long?)

    @Query("SELECT DISTINCT ts.* FROM tv_shows ts LEFT JOIN tv_show_progress tsp ON ts.id = tsp.tv_show_id WHERE ts.liked = 1 AND (ts.total_episodes IS NULL OR (SELECT COUNT(*) FROM tv_show_progress WHERE tv_show_id = ts.id) < ts.total_episodes) ORDER BY ts.last_watched_at DESC, ts.added_at DESC")
    fun getInProgressFlow(): Flow<List<TvShowEntity>>

    @Query("SELECT * FROM tv_shows WHERE liked = 1 AND next_episode_air_date IS NOT NULL AND next_episode_air_date >= :today ORDER BY next_episode_air_date ASC")
    fun getUpcomingFlow(today: String): Flow<List<TvShowEntity>>

    @Query("DELETE FROM tv_shows")
    suspend fun deleteAll()
}
