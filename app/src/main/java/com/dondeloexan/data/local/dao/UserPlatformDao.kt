package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dondeloexan.data.local.entity.UserPlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPlatformDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(platform: UserPlatformEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(platforms: List<UserPlatformEntity>)

    @Query("SELECT * FROM user_platforms")
    fun getAllFlow(): Flow<List<UserPlatformEntity>>

    @Query("SELECT * FROM user_platforms WHERE is_active = 1")
    fun getActiveFlow(): Flow<List<UserPlatformEntity>>

    @Query("SELECT platform_name FROM user_platforms WHERE is_active = 1")
    suspend fun getActiveNames(): List<String>

    @Query("SELECT * FROM user_platforms")
    suspend fun getAll(): List<UserPlatformEntity>

    @Query("SELECT * FROM user_platforms WHERE platform_name = :name")
    suspend fun getByName(name: String): UserPlatformEntity?

    @Query("UPDATE user_platforms SET is_active = NOT is_active WHERE platform_name = :name")
    suspend fun toggle(name: String)

    @Query("DELETE FROM user_platforms")
    suspend fun deleteAll()
}
