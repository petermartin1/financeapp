package com.financeapp.domain.model

data class Payee(
    val id: Long = 0,
    val name: String,
    val defaultCategoryId: Long? = null
)

data class PayeeWithStats(
    val payee: Payee,
    val transactionCount: Long,
    val totalAmount: Long = 0,
    val firstTransaction: Long? = null,
    val lastTransaction: Long? = null
)
