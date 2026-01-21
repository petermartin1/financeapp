package com.financeapp.domain.repository

import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    suspend fun getTagById(id: Long): Tag?
    suspend fun insertTag(tag: Tag): Long
    suspend fun updateTag(tag: Tag)
    suspend fun deleteTag(id: Long)

    // Transaction-Tag operations
    suspend fun getTagsForTransaction(transactionId: Long): List<Tag>
    suspend fun addTagToTransaction(transactionId: Long, tagId: Long)
    suspend fun removeTagFromTransaction(transactionId: Long, tagId: Long)
    suspend fun setTransactionTags(transactionId: Long, tagIds: List<Long>)

    // Split operations
    suspend fun getSplitsForTransaction(transactionId: Long): List<SplitItem>
    suspend fun getSplitTransactionIds(transactionIds: List<Long>): Set<Long>
    suspend fun setSplitsForTransaction(transactionId: Long, splits: List<SplitItem>)
    suspend fun clearSplitsForTransaction(transactionId: Long)

    /**
     * Notify that tags have changed, triggering UI refresh
     */
    fun notifyTagsChanged()
}
