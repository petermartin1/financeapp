package com.financeapp.security

/**
 * Platform-specific secure storage for sensitive credentials like bank passwords.
 * Uses macOS Keychain on desktop, Android Keystore on Android.
 */
expect class SecureCredentialStore() {
    /**
     * Store a credential securely using SecureString.
     * @param key Unique identifier for the credential
     * @param value The sensitive value to store (will be cleared after storing)
     * @return true if stored successfully
     */
    fun storeSecure(key: String, value: SecureString): Boolean

    /**
     * Retrieve a stored credential as SecureString.
     * @param key Unique identifier for the credential
     * @return The stored value as SecureString, or null if not found.
     *         Caller must call clear() or use within use {} block.
     */
    fun retrieveSecure(key: String): SecureString?

    /**
     * Store a credential securely (String variant - less secure).
     * Prefer storeSecure() with SecureString when possible.
     * @param key Unique identifier for the credential
     * @param value The sensitive value to store
     * @return true if stored successfully
     */
    fun store(key: String, value: String): Boolean

    /**
     * Retrieve a stored credential (String variant - less secure).
     * Prefer retrieveSecure() which returns SecureString.
     * @param key Unique identifier for the credential
     * @return The stored value, or null if not found
     */
    fun retrieve(key: String): String?

    /**
     * Delete a stored credential.
     * @param key Unique identifier for the credential
     * @return true if deleted successfully
     */
    fun delete(key: String): Boolean
}
