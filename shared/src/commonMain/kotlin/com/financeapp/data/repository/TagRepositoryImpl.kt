package com.financeapp.data.repository

import com.financeapp.db.schema.SplitItems
import com.financeapp.db.schema.Tags
import com.financeapp.db.schema.TransactionTags
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.model.Tag
import com.financeapp.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class TagRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TagRepository {

    // Trigger for reactive tag updates
    private val tagRefreshTrigger = MutableStateFlow(0L)

    override fun notifyTagsChanged() {
        tagRefreshTrigger.value += 1
    }

    override fun getAllTags(): Flow<List<Tag>> =
        tagRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Tags.selectAll()
                        .orderBy(Tags.name to SortOrder.ASC)
                        .map { it.toDomain() }
                }
            }
        }

    override suspend fun getTagById(id: Long): Tag? = withContext(ioDispatcher) {
        transaction(database) {
            Tags.selectAll().where { Tags.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getTagsForTransaction(transactionId: Long): List<Tag> = withContext(ioDispatcher) {
        transaction(database) {
            Tags
                .innerJoin(TransactionTags, { Tags.id }, { TransactionTags.tagId })
                .selectAll().where { TransactionTags.transactionId eq transactionId.toInt() }
                .map { it.toDomain() }
        }
    }

    override suspend fun insertTag(tag: Tag): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            Tags.insert {
                it[name] = tag.name
                it[color] = tag.color
            }[Tags.id].value.toLong()
        }
        notifyTagsChanged()
        id
    }

    override suspend fun updateTag(tag: Tag): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Tags.update({ Tags.id eq tag.id.toInt() }) {
                it[name] = tag.name
                it[color] = tag.color
            }
        }
        notifyTagsChanged()
    }

    override suspend fun deleteTag(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Delete TransactionTag entries first to avoid FK constraint violations
            TransactionTags.deleteWhere { TransactionTags.tagId eq id.toInt() }
            Tags.deleteWhere { Tags.id eq id.toInt() }
        }
        notifyTagsChanged()
    }

    override suspend fun addTagToTransaction(transactionId: Long, tagId: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            TransactionTags.insertIgnore {
                it[TransactionTags.transactionId] = transactionId.toInt()
                it[TransactionTags.tagId] = tagId.toInt()
            }
        }
        // Note: This doesn't change the tag list itself, so no notification needed
    }

    override suspend fun removeTagFromTransaction(transactionId: Long, tagId: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            TransactionTags.deleteWhere {
                (TransactionTags.transactionId eq transactionId.toInt()) and (TransactionTags.tagId eq tagId.toInt())
            }
        }
        // Note: This doesn't change the tag list itself, so no notification needed
    }

    override suspend fun setTransactionTags(transactionId: Long, tagIds: List<Long>): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Clear existing tags
            TransactionTags.deleteWhere { TransactionTags.transactionId eq transactionId.toInt() }

            // Insert new tags
            tagIds.forEach { tagId ->
                TransactionTags.insert {
                    it[TransactionTags.transactionId] = transactionId.toInt()
                    it[TransactionTags.tagId] = tagId.toInt()
                }
            }
        }
        // Note: This doesn't change the tag list itself, so no notification needed
    }

    override suspend fun getSplitsForTransaction(transactionId: Long): List<SplitItem> = withContext(ioDispatcher) {
        transaction(database) {
            SplitItems
                .selectAll().where { SplitItems.transactionId eq transactionId.toInt() }
                .map { it.toSplitDomain() }
        }
    }

    override suspend fun getSplitTransactionIds(transactionIds: List<Long>): Set<Long> = withContext(ioDispatcher) {
        if (transactionIds.isEmpty()) return@withContext emptySet()

        transaction(database) {
            SplitItems
                .select(SplitItems.transactionId)
                .where { SplitItems.transactionId inList transactionIds.map { it.toInt() } }
                .map { it[SplitItems.transactionId].value.toLong() }
                .toSet()
        }
    }

    override suspend fun setSplitsForTransaction(transactionId: Long, splits: List<SplitItem>): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Splits must account for the whole transaction: their amounts have to sum to the
            // parent amount, otherwise the books don't balance (R5). An empty list just clears
            // the splits (the transaction reverts to a single, unsplit line).
            if (splits.isNotEmpty()) {
                val parentAmount = Transactions
                    .selectAll().where { Transactions.id eq transactionId.toInt() }
                    .singleOrNull()
                    ?.get(Transactions.amount)
                    ?: throw IllegalArgumentException("Cannot set splits: transaction $transactionId not found")

                val splitSum = splits.sumOf { it.amount }
                require(splitSum == parentAmount) {
                    "Split amounts must sum to the transaction amount ($parentAmount cents) but summed to $splitSum cents"
                }
            }

            // Clear existing splits
            SplitItems.deleteWhere { SplitItems.transactionId eq transactionId.toInt() }

            // Insert new splits
            splits.forEach { split ->
                SplitItems.insert {
                    it[SplitItems.transactionId] = transactionId.toInt()
                    it[SplitItems.categoryId] = split.categoryId?.toInt()
                    it[SplitItems.amount] = split.amount
                    it[SplitItems.memo] = split.memo
                }
            }
        }
    }

    override suspend fun clearSplitsForTransaction(transactionId: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            SplitItems.deleteWhere { SplitItems.transactionId eq transactionId.toInt() }
        }
    }

    private fun ResultRow.toDomain(): Tag {
        return Tag(
            id = this[Tags.id].value.toLong(),
            name = this[Tags.name],
            color = this[Tags.color]
        )
    }

    private fun ResultRow.toSplitDomain(): SplitItem {
        return SplitItem(
            id = this[SplitItems.id].value.toLong(),
            transactionId = this[SplitItems.transactionId].value.toLong(),
            categoryId = this[SplitItems.categoryId]?.value?.toLong(),
            amount = this[SplitItems.amount],
            memo = this[SplitItems.memo]
        )
    }
}
