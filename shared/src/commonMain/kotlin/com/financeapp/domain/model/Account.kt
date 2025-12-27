package com.financeapp.domain.model

import kotlinx.datetime.Instant

enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    INVESTMENT,
    CASH
}

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val institution: String? = null,
    val accountNumber: String? = null,
    val currency: String = "USD",
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class AccountWithBalance(
    val account: Account,
    val balance: Long, // in cents
    val clearedBalance: Long // in cents
)
