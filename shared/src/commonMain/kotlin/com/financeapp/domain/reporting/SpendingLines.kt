package com.financeapp.domain.reporting

import com.financeapp.domain.model.SpendingSource
import com.financeapp.domain.model.SplitItem

/** One categorized money movement contributing to spending math (amount in cents, sign preserved). */
data class SpendingLine(val categoryId: Long?, val amount: Long)

/** Lightweight [SpendingSource] for repositories that only load a few columns. */
data class SimpleSpendingSource(
    override val id: Long,
    override val categoryId: Long?,
    override val amount: Long,
    override val transferId: Long?
) : SpendingSource

/**
 * Expands transactions into spending lines so category reporting reflects how money was actually
 * allocated:
 * - a transaction with split items contributes one line per split (each split's own category and
 *   amount),
 * - an unsplit transaction contributes a single line from its own category and amount,
 * - transfers are excluded entirely.
 *
 * Because splits are required to sum to the parent amount (see TagRepository.setSplitsForTransaction),
 * expanding them preserves totals. Callers apply their own sign/category-type filters on the result,
 * so an unsplit transaction produces exactly the line they would have computed directly.
 */
fun expandSpendingLines(
    transactions: List<SpendingSource>,
    splitsByTransactionId: Map<Long, List<SplitItem>>
): List<SpendingLine> =
    transactions
        .filter { it.transferId == null }
        .flatMap { txn ->
            val splits = splitsByTransactionId[txn.id]
            if (splits.isNullOrEmpty()) listOf(SpendingLine(txn.categoryId, txn.amount))
            else splits.map { SpendingLine(it.categoryId, it.amount) }
        }
