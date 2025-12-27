package com.financeapp.domain.model

import kotlinx.datetime.Instant

data class TransactionTemplate(
    val id: Long = 0,
    val name: String,
    val accountId: Long? = null,
    val payeeId: Long? = null,
    val categoryId: Long? = null,
    val amount: Long? = null,
    val memo: String? = null,
    val createdAt: Instant = Instant.DISTANT_PAST,
    val updatedAt: Instant = Instant.DISTANT_PAST
)

data class TransactionTemplateWithDetails(
    val template: TransactionTemplate,
    val accountName: String? = null,
    val payeeName: String? = null,
    val categoryName: String? = null
)
