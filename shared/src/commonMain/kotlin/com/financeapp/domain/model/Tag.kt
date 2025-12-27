package com.financeapp.domain.model

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: String? = null  // hex color code
)

data class SplitItem(
    val id: Long = 0,
    val transactionId: Long,
    val categoryId: Long?,
    val amount: Long,  // in cents
    val memo: String? = null
)
