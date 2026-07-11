package com.dondeloexan.domain.repository

import com.dondeloexan.domain.model.Content
import com.dondeloexan.domain.model.ContentPreview
import com.dondeloexan.domain.model.ContentType
import com.dondeloexan.domain.model.DataResult
import kotlinx.coroutines.flow.Flow

interface DiscoverRepository {
    suspend fun search(query: String): Flow<DataResult<List<ContentPreview>>>
    suspend fun search(query: String, page: Int): Flow<DataResult<List<ContentPreview>>>
    suspend fun getDetail(contentId: String, contentType: ContentType = ContentType.MOVIE): Flow<DataResult<Content>>
    suspend fun getTrending(): Flow<DataResult<List<ContentPreview>>>
    suspend fun getTrending(page: Int): Flow<DataResult<List<ContentPreview>>>
}
