package com.dondeloexan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dondeloexan.data.local.entity.CriticReviewEntity

@Dao
interface CriticReviewDao {

    @Query("SELECT * FROM critic_reviews WHERE content_id = :contentId LIMIT 1")
    suspend fun getByContentId(contentId: String): CriticReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: CriticReviewEntity)

    @Query("DELETE FROM critic_reviews WHERE content_id = :contentId")
    suspend fun deleteByContentId(contentId: String)

    @Query("DELETE FROM critic_reviews")
    suspend fun deleteAll()
}
