package com.financeapp.data.repository

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.Categories
import com.financeapp.db.schema.Payees
import com.financeapp.db.schema.TransactionTemplates
import com.financeapp.domain.model.TransactionTemplate
import com.financeapp.domain.model.TransactionTemplateWithDetails
import com.financeapp.domain.repository.TemplateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class TemplateRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TemplateRepository {

    // Trigger for reactive updates
    private val templateRefreshTrigger = MutableStateFlow(0L)

    override fun notifyTemplatesChanged() {
        templateRefreshTrigger.value += 1
    }

    override fun getAllTemplates(): Flow<List<TransactionTemplateWithDetails>> =
        templateRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    // Fetch lookup tables once
                    val accounts = Accounts.selectAll().associate { it[Accounts.id].value.toLong() to it[Accounts.name] }
                    val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }
                    val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }

                    // Get all templates
                    TransactionTemplates
                        .selectAll()
                        .orderBy(TransactionTemplates.name to SortOrder.ASC)
                        .map { row ->
                            val template = row.toDomain()
                            TransactionTemplateWithDetails(
                                template = template,
                                accountName = template.accountId?.let { accounts[it] },
                                payeeName = template.payeeId?.let { payees[it] },
                                categoryName = template.categoryId?.let { categories[it] }
                            )
                        }
                }
            }
        }

    override suspend fun getTemplateById(id: Long): TransactionTemplate? = withContext(Dispatchers.IO) {
        transaction(database) {
            TransactionTemplates.selectAll().where { TransactionTemplates.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun insertTemplate(template: TransactionTemplate): Long = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = transaction(database) {
            TransactionTemplates.insert {
                it[name] = template.name
                it[accountId] = template.accountId?.toInt()
                it[payeeId] = template.payeeId?.toInt()
                it[categoryId] = template.categoryId?.toInt()
                it[amount] = template.amount
                it[memo] = template.memo
                it[createdAt] = now
                it[updatedAt] = now
            }[TransactionTemplates.id].value.toLong()
        }
        notifyTemplatesChanged()
        id
    }

    override suspend fun updateTemplate(template: TransactionTemplate): Unit = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        transaction(database) {
            TransactionTemplates.update({ TransactionTemplates.id eq template.id.toInt() }) {
                it[name] = template.name
                it[accountId] = template.accountId?.toInt()
                it[payeeId] = template.payeeId?.toInt()
                it[categoryId] = template.categoryId?.toInt()
                it[amount] = template.amount
                it[memo] = template.memo
                it[updatedAt] = now
            }
        }
        notifyTemplatesChanged()
    }

    override suspend fun deleteTemplate(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            TransactionTemplates.deleteWhere { TransactionTemplates.id eq id.toInt() }
        }
        notifyTemplatesChanged()
    }

    private fun ResultRow.toDomain(): TransactionTemplate {
        return TransactionTemplate(
            id = this[TransactionTemplates.id].value.toLong(),
            name = this[TransactionTemplates.name],
            accountId = this[TransactionTemplates.accountId]?.value?.toLong(),
            payeeId = this[TransactionTemplates.payeeId]?.value?.toLong(),
            categoryId = this[TransactionTemplates.categoryId]?.value?.toLong(),
            amount = this[TransactionTemplates.amount],
            memo = this[TransactionTemplates.memo],
            createdAt = Instant.fromEpochMilliseconds(this[TransactionTemplates.createdAt]),
            updatedAt = Instant.fromEpochMilliseconds(this[TransactionTemplates.updatedAt])
        )
    }
}
