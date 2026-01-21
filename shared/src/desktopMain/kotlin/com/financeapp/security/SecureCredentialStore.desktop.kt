package com.financeapp.security

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cross-platform secure credential storage for desktop.
 * - macOS: Uses Keychain via security CLI
 * - Windows/Linux: Uses encrypted file storage with AES-256-GCM
 */
actual class SecureCredentialStore actual constructor() {
    private val serviceName = "com.financeapp.credentials"
    private val credentialsDir = File(System.getProperty("user.home"), ".financeapp/credentials")
    private val masterKeyFile = File(System.getProperty("user.home"), ".financeapp/.credkey")

    /**
     * Validates that a key is safe to use with shell commands.
     * Only allows alphanumeric characters, underscores, and hyphens.
     */
    private fun isValidKey(key: String): Boolean {
        if (key.length > 128) return false
        return key.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    /**
     * Validates that a value doesn't contain characters that could break the command.
     */
    private fun isValidValue(value: String): Boolean {
        if (value.length > 1024) return false
        // Block null bytes and control characters
        return !value.any { it == '\u0000' || (it.code < 32 && it != '\t') }
    }

    actual fun store(key: String, value: String): Boolean {
        if (!isValidKey(key) || !isValidValue(value)) {
            return false
        }
        return if (isMacOS()) {
            storeInKeychain(key, value)
        } else {
            storeInEncryptedFile(key, value)
        }
    }

    actual fun retrieve(key: String): String? {
        if (!isValidKey(key)) {
            return null
        }
        return if (isMacOS()) {
            retrieveFromKeychain(key)
        } else {
            retrieveFromEncryptedFile(key)
        }
    }

    actual fun delete(key: String): Boolean {
        if (!isValidKey(key)) {
            return false
        }
        return if (isMacOS()) {
            deleteFromKeychain(key)
        } else {
            deleteFromEncryptedFile(key)
        }
    }

    /**
     * Store credential using SecureString (more secure - zeros memory).
     */
    actual fun storeSecure(key: String, value: SecureString): Boolean {
        return value.use { charArray ->
            val stringValue = String(charArray)
            try {
                store(key, stringValue)
            } finally {
                // Zero the temporary string's backing array if possible
                // Note: Can't actually zero String in JVM, but we clear SecureString
            }
        }
    }

    /**
     * Retrieve credential as SecureString (more secure - allows explicit memory zeroing).
     */
    actual fun retrieveSecure(key: String): SecureString? {
        val value = retrieve(key) ?: return null
        val secureString = SecureString(value)
        // Note: Original string 'value' will remain in memory until GC
        // This is a JVM limitation, but SecureString allows caller to control when cleared
        return secureString
    }

    private fun isMacOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    private fun storeInKeychain(key: String, value: String): Boolean {
        return try {
            // First try to delete existing entry
            deleteFromKeychain(key)

            val process = ProcessBuilder(
                "security", "add-generic-password",
                "-s", serviceName,
                "-a", key,
                "-w", value
            ).redirectErrorStream(true).start()

            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun retrieveFromKeychain(key: String): String? {
        return try {
            val process = ProcessBuilder(
                "security", "find-generic-password",
                "-s", serviceName,
                "-a", key,
                "-w"
            ).redirectErrorStream(false).start()

            val result = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && result.isNotEmpty()) result else null
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteFromKeychain(key: String): Boolean {
        return try {
            val process = ProcessBuilder(
                "security", "delete-generic-password",
                "-s", serviceName,
                "-a", key
            ).redirectErrorStream(true).start()

            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    // ==============================================================
    // Encrypted File Storage (Windows/Linux)
    // ==============================================================

    /**
     * Stores a credential in an encrypted file using AES-256-GCM.
     * Each credential is stored in a separate file with format: IV(12 bytes) + Ciphertext + Tag(16 bytes)
     */
    private fun storeInEncryptedFile(key: String, value: String): Boolean {
        return try {
            // Ensure credentials directory exists with restricted permissions
            credentialsDir.mkdirs()
            setRestrictedPermissions(credentialsDir)

            // Get or create master encryption key
            val masterKey = getOrCreateMasterKey()

            // Generate random IV (12 bytes for GCM)
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            // Encrypt the value using AES-256-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag
            val secretKey = SecretKeySpec(masterKey, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val valueBytes = value.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(valueBytes)

            // Write IV + ciphertext to file
            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            credentialFile.outputStream().use { out ->
                out.write(iv)
                out.write(ciphertext)
            }

            setRestrictedPermissions(credentialFile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Retrieves and decrypts a credential from encrypted file storage.
     */
    private fun retrieveFromEncryptedFile(key: String): String? {
        return try {
            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            if (!credentialFile.exists()) {
                return null
            }

            // Read IV + ciphertext from file
            val fileBytes = credentialFile.readBytes()
            if (fileBytes.size < 28) { // Minimum: 12 bytes IV + 16 bytes tag
                return null
            }

            val iv = fileBytes.copyOfRange(0, 12)
            val ciphertext = fileBytes.copyOfRange(12, fileBytes.size)

            // Decrypt using master key
            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            val secretKey = SecretKeySpec(masterKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a credential file from encrypted storage.
     */
    private fun deleteFromEncryptedFile(key: String): Boolean {
        return try {
            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            if (credentialFile.exists()) {
                // Overwrite with random data before deletion (secure delete)
                val random = ByteArray(credentialFile.length().toInt())
                SecureRandom().nextBytes(random)
                credentialFile.writeBytes(random)
                credentialFile.delete()
            } else {
                true // Already deleted
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets or creates the master encryption key for credential encryption.
     * The key is derived from a random seed stored in a protected file.
     */
    private fun getOrCreateMasterKey(): ByteArray {
        return if (masterKeyFile.exists()) {
            // Read existing key
            val encodedKey = masterKeyFile.readText().trim()
            Base64.getDecoder().decode(encodedKey)
        } else {
            // Generate new 256-bit key
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)

            // Store key in file with restricted permissions
            masterKeyFile.parentFile?.mkdirs()
            val encodedKey = Base64.getEncoder().encodeToString(key)
            masterKeyFile.writeText(encodedKey)
            setRestrictedPermissions(masterKeyFile)

            key
        }
    }

    /**
     * Sanitizes a key name to be safe for use as a filename.
     * Uses SHA-256 to avoid collisions (hashCode can collide easily).
     */
    private fun sanitizeFilename(key: String): String {
        // Use SHA-256 for collision-resistant hashing
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(key.toByteArray(Charsets.UTF_8))
        val hash = hashBytes.take(16).joinToString("") { "%02x".format(it) }
        return "cred_$hash"
    }

    /**
     * Sets file/directory permissions to owner-only (read/write).
     * This provides basic protection on Unix-like systems.
     */
    private fun setRestrictedPermissions(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)   // Owner read
            file.setWritable(false, false)
            file.setWritable(true, true)   // Owner write
            file.setExecutable(false, false)
        } catch (e: Exception) {
            // Ignore on systems that don't support POSIX permissions (Windows)
        }
    }
}
