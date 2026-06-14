package com.financeapp.security

expect class EncryptionKeyManager {
    /**
     * Returns the database encryption key, creating and persisting it in an OS-backed key
     * store (Keychain / DPAPI / Secret Service) on first use.
     *
     * Throws [KeyStorageException] when no OS key store is available, rather than writing the
     * key to disk in plaintext where it would offer no real protection.
     */
    fun getOrCreateKey(): String
}

/** Thrown when the encryption key cannot be stored in an OS-backed key store. */
class KeyStorageException(message: String) : Exception(message)
