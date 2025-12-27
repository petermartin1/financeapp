package com.financeapp.security

expect class EncryptionKeyManager {
    fun getOrCreateKey(): String
}
