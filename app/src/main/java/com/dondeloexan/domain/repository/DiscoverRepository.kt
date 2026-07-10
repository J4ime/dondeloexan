package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.DataResult
import kotlinx.coroutines.flow.Flow

interface DiscoverRepository {
    suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>>
    suspend fun getDetail(contentId: String): Flow<DataResult<Content>>
    suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>>
}
