package com.financeapp.data.repository

import com.financeapp.db.schema.SavingsGoals
import com.financeapp.domain.goals.GoalProgressCalculator
import com.financeapp.domain.model.GoalWithProgress
import com.financeapp.domain.model.SavingsGoal
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.GoalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class GoalRepositoryImpl(
    private val database: Database,
    private val accountRepository: AccountRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : GoalRepository {
    private val goalsChanged = MutableStateFlow(0L)

    override fun getGoalsWithProgress(): Flow<List<GoalWithProgress>> =
        combine(goalsChanged, accountRepository.getAccountsWithBalances()) { _, accountsWithBalances ->
            val goals = withContext(ioDispatcher) {
                transaction(database) {
                    SavingsGoals.selectAll()
                        .orderBy(SavingsGoals.createdAt to SortOrder.ASC, SavingsGoals.id to SortOrder.ASC)
                        .map { it.toDomain() }
                }
            }
            val now = nowMs()
            goals.map { goal ->
                // Inactive (closed) accounts aren't in the balances flow, so a goal on one shows
                // as "needs an account" — deliberately, since it can no longer grow.
                val linked = goal.accountId?.let { accId ->
                    accountsWithBalances.firstOrNull { it.account.id == accId }
                }
                GoalWithProgress(
                    goal = goal,
                    progress = GoalProgressCalculator.calculate(
                        targetCents = goal.targetAmountCents,
                        balanceCents = linked?.balance,
                        createdAtMs = goal.createdAtMs,
                        deadlineMs = goal.deadlineMs,
                        nowMs = now
                    ),
                    accountName = linked?.account?.name
                )
            }
        }

    override suspend fun createGoal(
        name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?
    ): Long = withContext(ioDispatcher) {
        validate(name, targetAmountCents)
        val id = transaction(database) {
            SavingsGoals.insert {
                it[SavingsGoals.name] = name.trim()
                it[targetAmount] = targetAmountCents
                it[SavingsGoals.accountId] = accountId.toInt()
                it[deadline] = deadlineMs
                it[createdAt] = nowMs()
            }[SavingsGoals.id].value.toLong()
        }
        notifyGoalsChanged()
        id
    }

    override suspend fun updateGoal(
        id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?
    ): Boolean = withContext(ioDispatcher) {
        validate(name, targetAmountCents)
        val updated = transaction(database) {
            SavingsGoals.update({ SavingsGoals.id eq id.toInt() }) {
                it[SavingsGoals.name] = name.trim()
                it[targetAmount] = targetAmountCents
                it[SavingsGoals.accountId] = accountId?.toInt()
                it[deadline] = deadlineMs
            }
        } > 0
        if (updated) notifyGoalsChanged()
        updated
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Boolean = withContext(ioDispatcher) {
        val updated = transaction(database) {
            SavingsGoals.update({ SavingsGoals.id eq id.toInt() }) {
                it[SavingsGoals.archived] = archived
            }
        } > 0
        if (updated) notifyGoalsChanged()
        updated
    }

    override suspend fun deleteGoal(id: Long): Boolean = withContext(ioDispatcher) {
        val deleted = transaction(database) {
            SavingsGoals.deleteWhere { SavingsGoals.id eq id.toInt() }
        } > 0
        if (deleted) notifyGoalsChanged()
        deleted
    }

    override fun notifyGoalsChanged() {
        goalsChanged.value += 1
    }

    private fun validate(name: String, targetAmountCents: Long) {
        require(name.isNotBlank()) { "Goal name must not be blank" }
        require(targetAmountCents > 0) { "Goal target must be positive" }
    }

    private fun ResultRow.toDomain(): SavingsGoal = SavingsGoal(
        id = this[SavingsGoals.id].value.toLong(),
        name = this[SavingsGoals.name],
        targetAmountCents = this[SavingsGoals.targetAmount],
        accountId = this[SavingsGoals.accountId]?.value?.toLong(),
        deadlineMs = this[SavingsGoals.deadline],
        createdAtMs = this[SavingsGoals.createdAt],
        archived = this[SavingsGoals.archived]
    )
}
