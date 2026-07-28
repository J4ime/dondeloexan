package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dondeloexan.data.local.entity.FaMovieDataEntity

@Dao
interface FaMovieDataDao {

    @Query("SELECT * FROM fa_movie_data WHERE content_id = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): FaMovieDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(data: FaMovieDataEntity)

    @Query("DELETE FROM fa_movie_data WHERE content_id = :contentId")
    suspend fun deleteByContentId(contentId: String)

    @Query("DELETE FROM fa_movie_data")
    suspend fun deleteAll()
}
