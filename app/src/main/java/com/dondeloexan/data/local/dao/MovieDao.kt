package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dondeloexan.data.local.entity.MovieEntity
import com.dondeloexan.data.local.entity.WatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: MovieEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Update
    suspend fun update(movie: MovieEntity)

    @Delete
    suspend fun delete(movie: MovieEntity)

    @Query("SELECT * FROM movies ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY added_at DESC")
    suspend fun getAll(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getById(id: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE content_id = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE status = :status ORDER BY added_at DESC")
    fun getByStatus(status: WatchStatus): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE liked = 1 ORDER BY added_at DESC")
    fun getLiked(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE liked = 1 AND status != 'YA_VISTA' ORDER BY release_date DESC")
    fun getPendingFlow(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE status = 'YA_VISTA' ORDER BY watched_at DESC")
    fun getWatchedMoviesFlow(): Flow<List<MovieEntity>>

    @Query("UPDATE movies SET watched_at = :timestamp WHERE id = :id")
    suspend fun updateWatchedAt(id: Long, timestamp: Long?)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()
}
