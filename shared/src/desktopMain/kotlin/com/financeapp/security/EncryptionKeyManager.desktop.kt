package com.financeapp.security

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

actual class EncryptionKeyManager {
    private val serviceName = "com.financeapp"
    private val accountName = "database-encryption-key"

    // Fallback file for non-macOS systems
    private val keyFile = File(System.getProperty("user.home"), ".financeapp/.dbkey")

    actual fun getOrCreateKey(): String {
        // Try macOS Keychain first
        if (isMacOS()) {
            val keychainKey = getFromKeychain()
            if (keychainKey != null) {
                return keychainKey
            }
            // Generate and store in Keychain
            val newKey = generateKey()
            if (storeInKeychain(newKey)) {
                return newKey
            }
        }

        // Fallback to file-based storage for non-macOS
        return getOrCreateFileKey()
    }

    private fun isMacOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    private fun getFromKeychain(): String? {
        return try {
            val process = ProcessBuilder(
                "security", "find-generic-password",
                "-s", serviceName,
                "-a", accountName,
                "-w" // Output password only
            ).redirectErrorStream(false).start()

            val result = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && result.isNotEmpty()) result else null
        } catch (e: Exception) {
            null
        }
    }

    private fun storeInKeychain(key: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "security", "add-generic-password",
                "-s", serviceName,
                "-a", accountName,
                "-w", key,
                "-U" // Update if exists
            ).redirectErrorStream(true).start()

            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getOrCreateFileKey(): String {
        return if (keyFile.exists()) {
            keyFile.readText().trim()
        } else {
            val key = generateKey()
            keyFile.parentFile?.mkdirs()
            keyFile.writeText(key)
            // Set file permissions to owner only
            try {
                keyFile.setReadable(false, false)
                keyFile.setReadable(true, true)
                keyFile.setWritable(false, false)
                keyFile.setWritable(true, true)
            } catch (e: Exception) {
                // Ignore on systems that don't support this
            }
            key
        }
    }

    private fun generateKey(): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        val secretKey: SecretKey = keyGen.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }
}
