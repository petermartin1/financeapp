package com.financeapp.domain.model

enum class CategoryType {
    INCOME,
    EXPENSE,
    TRANSFER
}

data class Category(
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val type: CategoryType,
    val icon: String? = null,
    val color: String? = null
)
