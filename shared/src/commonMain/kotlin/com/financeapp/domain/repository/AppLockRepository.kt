package com.financeapp.domain.repository

interface AppLockRepository {
    suspend fun isLockSetUp(): Boolean
    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
    suspend fun clearLock()
    fun getFailedAttempts(): Int
    fun incrementFailedAttempts()
    fun resetFailedAttempts()
}
