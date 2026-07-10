package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dondeloexan.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SearchHistoryEntity>)

    @Query("SELECT * FROM search_history ORDER BY searched_at DESC")
    fun getAllFlow(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searched_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()
}
