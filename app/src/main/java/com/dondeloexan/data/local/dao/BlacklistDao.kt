package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dondeloexan.data.local.entity.BlacklistedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlacklistedEntity)

    @Delete
    suspend fun delete(entity: BlacklistedEntity)

    @Query("SELECT * FROM blacklist ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<BlacklistedEntity>>

    @Query("SELECT content_id FROM blacklist")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM blacklist WHERE content_id = :contentId")
    suspend fun deleteById(contentId: String)
}
