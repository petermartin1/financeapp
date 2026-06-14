package com.financeapp.domain.model

data class AppLockState(
    val isSetUp: Boolean = false,
    val isLocked: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long? = null
)
