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

    @Query("SELECT * FROM tv_shows ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<TvShowEntity>>

    @Query("SELECT * FROM tv_shows ORDER BY added_at DESC")
    suspend fun getAll(): List<TvShowEntity>

    @Query("SELECT * FROM tv_shows WHERE id = :id")
    suspend fun getById(id: Long): TvShowEntity?

    @Query("SELECT * FROM tv_shows WHERE status = :status ORDER BY added_at DESC")
    fun getByStatus(status: WatchStatus): Flow<List<TvShowEntity>>

    @Query("DELETE FROM tv_shows")
    suspend fun deleteAll()
}
