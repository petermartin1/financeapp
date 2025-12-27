package com.financeapp.ui.performance

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

/**
 * Performance optimization utilities for LazyColumn/LazyRow
 */

/**
 * Content types for LazyColumn items to optimize recomposition
 */
object ListContentTypes {
    const val HEADER = "header"
    const val ITEM = "item"
    const val FOOTER = "footer"
    const val DIVIDER = "divider"
    const val LOADING = "loading"
    const val EMPTY_STATE = "empty_state"
    const val SECTION_HEADER = "section_header"
}

/**
 * Optimized items extension that automatically adds keys and content types
 */
inline fun <T> LazyListScope.optimizedItems(
    items: List<T>,
    contentType: String = ListContentTypes.ITEM,
    crossinline key: (T) -> Any = { it.hashCode() },
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit
) {
    items(
        count = items.size,
        key = { index -> key(items[index]) },
        contentType = { contentType }
    ) { index ->
        itemContent(items[index])
    }
}

/**
 * Optimized items with index
 */
inline fun <T> LazyListScope.optimizedItemsIndexed(
    items: List<T>,
    contentType: String = ListContentTypes.ITEM,
    crossinline key: (index: Int, item: T) -> Any = { _, item -> item.hashCode() },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit
) {
    items(
        count = items.size,
        key = { index -> key(index, items[index]) },
        contentType = { contentType }
    ) { index ->
        itemContent(index, items[index])
    }
}

/**
 * Helper to create stable keys for domain objects
 */
object StableKeys {
    fun forTransaction(transactionId: Long) = "transaction_$transactionId"
    fun forAccount(accountId: Long) = "account_$accountId"
    fun forCategory(categoryId: Long) = "category_$categoryId"
    fun forPayee(payeeId: Long) = "payee_$payeeId"
    fun forBudget(budgetId: Long) = "budget_$budgetId"
    fun forDate(date: String) = "date_$date"
    fun forSection(sectionName: String) = "section_$sectionName"
}

/**
 * Performance tips and best practices
 */
object LazyListPerformanceTips {
    /**
     * Always provide keys for items to help Compose understand which items changed.
     * Without keys, Compose may recreate items unnecessarily.
     */
    fun usingKeys() {
        """
        LazyColumn {
            items(
                items = transactions,
                key = { it.id } // Use stable unique ID
            ) { transaction ->
                TransactionCard(transaction)
            }
        }
        """.trimIndent()
    }

    /**
     * Use contentType to group similar items and improve recycling.
     * Items with the same content type can be recycled more efficiently.
     */
    fun usingContentTypes() {
        """
        LazyColumn {
            item(contentType = "header") {
                HeaderContent()
            }

            items(
                items = transactions,
                contentType = { "transaction" }
            ) { transaction ->
                TransactionCard(transaction)
            }

            item(contentType = "footer") {
                FooterContent()
            }
        }
        """.trimIndent()
    }

    /**
     * Avoid complex calculations in item content.
     * Move expensive operations to remember blocks or view models.
     */
    fun avoidComplexCalculations() {
        """
        // BAD: Complex calculation in item
        items(transactions) { transaction ->
            val complexValue = expensiveCalculation(transaction)
            TransactionCard(transaction, complexValue)
        }

        // GOOD: Pre-calculate in ViewModel or remember
        val transactionsWithValues = remember(transactions) {
            transactions.map { it to expensiveCalculation(it) }
        }
        items(transactionsWithValues) { (transaction, value) ->
            TransactionCard(transaction, value)
        }
        """.trimIndent()
    }

    /**
     * Use derivedStateOf for filtered/sorted lists to minimize recompositions
     */
    fun usingDerivedState() {
        """
        val filteredTransactions = remember(transactions, searchQuery) {
            derivedStateOf {
                transactions.filter { it.matches(searchQuery) }
            }
        }.value

        LazyColumn {
            items(filteredTransactions, key = { it.id }) { transaction ->
                TransactionCard(transaction)
            }
        }
        """.trimIndent()
    }

    /**
     * Minimize nested compositions in items
     */
    fun minimizeNesting() {
        """
        // BAD: Deep nesting causes multiple recompositions
        items(transactions) { transaction ->
            Card {
                Column {
                    Row {
                        Column {
                            // Multiple nested layers
                        }
                    }
                }
            }
        }

        // GOOD: Extract to separate composable
        items(transactions, key = { it.id }) { transaction ->
            TransactionCard(transaction) // Flat, optimized component
        }
        """.trimIndent()
    }
}

/**
 * Example of properly optimized LazyColumn
 */
object OptimizedLazyColumnExample {
    fun example() {
        """
        @Composable
        fun OptimizedTransactionList(
            transactions: List<Transaction>,
            onTransactionClick: (Transaction) -> Unit
        ) {
            // Group by date for section headers
            val groupedTransactions = remember(transactions) {
                transactions.groupBy { it.date }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedTransactions.forEach { (date, dayTransactions) ->
                    // Date header with stable key
                    item(
                        key = StableKeys.forDate(date),
                        contentType = ListContentTypes.SECTION_HEADER
                    ) {
                        DateHeader(date)
                    }

                    // Transaction items with stable keys
                    optimizedItems(
                        items = dayTransactions,
                        contentType = ListContentTypes.ITEM,
                        key = { StableKeys.forTransaction(it.id) }
                    ) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            onClick = { onTransactionClick(transaction) }
                        )
                    }
                }
            }
        }
        """.trimIndent()
    }
}
