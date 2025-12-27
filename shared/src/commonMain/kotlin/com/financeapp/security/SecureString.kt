package com.financeapp.security

/**
 * Secure wrapper for sensitive string data (passwords, credentials, etc.)
 *
 * Uses CharArray internally to allow explicit memory zeroing, preventing
 * sensitive data from lingering in memory after use. Strings in Kotlin/Java
 * are immutable and may remain in memory until garbage collected.
 *
 * IMPORTANT: Always call clear() or use within use {} block to ensure
 * the sensitive data is zeroed from memory.
 *
 * Example usage:
 * ```
 * val password = SecureString("myPassword")
 * password.use { securePassword ->
 *     // Use securePassword.value here
 *     doSomethingWith(securePassword.value)
 * } // Automatically cleared after use block
 * ```
 */
class SecureString : AutoCloseable {
    private var data: CharArray?
    private var isCleared = false

    /**
     * Create from String. WARNING: The original string will still exist in memory.
     * Prefer creating from CharArray when possible.
     */
    constructor(value: String) {
        this.data = value.toCharArray()
    }

    /**
     * Create from CharArray. The array is copied, so the original should be
     * zeroed by the caller if needed.
     */
    constructor(value: CharArray) {
        this.data = value.copyOf()
    }

    /**
     * Get the value as CharArray. Returns a copy to prevent external modification.
     * WARNING: Caller is responsible for zeroing the returned array.
     */
    val value: CharArray
        get() {
            check(!isCleared) { "SecureString has been cleared and cannot be accessed" }
            return data?.copyOf() ?: charArrayOf()
        }

    /**
     * Get the length of the secure string.
     */
    val length: Int
        get() {
            check(!isCleared) { "SecureString has been cleared and cannot be accessed" }
            return data?.size ?: 0
        }

    /**
     * Check if the secure string is empty.
     */
    val isEmpty: Boolean
        get() {
            check(!isCleared) { "SecureString has been cleared and cannot be accessed" }
            return data?.isEmpty() ?: true
        }

    /**
     * Convert to String. Use with caution as this defeats the security purpose.
     * Only use when interfacing with APIs that require String.
     */
    fun toUnsafeString(): String {
        check(!isCleared) { "SecureString has been cleared and cannot be accessed" }
        return data?.let { String(it) } ?: ""
    }

    /**
     * Execute a block with access to the CharArray value, then automatically clear it.
     */
    inline fun <R> use(block: (CharArray) -> R): R {
        return try {
            val valueArray = value
            try {
                block(valueArray)
            } finally {
                valueArray.fill('\u0000')
            }
        } finally {
            clear()
        }
    }

    /**
     * Zero out the internal data and mark as cleared.
     * After calling this, the SecureString cannot be used anymore.
     */
    fun clear() {
        if (!isCleared && data != null) {
            data?.fill('\u0000')
            data = null
            isCleared = true
        }
    }

    /**
     * AutoCloseable implementation - calls clear()
     */
    override fun close() {
        clear()
    }

    /**
     * Ensure cleanup happens if clear() wasn't called explicitly.
     * Note: This is a safety net but shouldn't be relied upon.
     * GC timing is unpredictable.
     */
    protected fun finalize() {
        clear()
    }

    companion object {
        /**
         * Create an empty SecureString
         */
        fun empty(): SecureString = SecureString(charArrayOf())

        /**
         * Create from String and immediately zero the original if possible.
         * Note: Cannot actually zero the String (immutable), but this makes
         * the API clearer about intent.
         */
        fun fromString(value: String): SecureString {
            return SecureString(value)
        }
    }
}
