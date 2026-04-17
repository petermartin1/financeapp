package com.financeapp.security

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cross-platform secure credential storage for desktop.
 * - macOS: Uses Keychain via security CLI
 * - Windows: Uses DPAPI via PowerShell (tied to Windows user account)
 * - Linux: Uses freedesktop Secret Service via secret-tool, with encrypted file fallback
 */
actual class SecureCredentialStore actual constructor() {
    private val serviceName = "com.financeapp.credentials"
    private val credentialsDir = File(System.getProperty("user.home"), ".financeapp/credentials")
    private val masterKeyFile = File(System.getProperty("user.home"), ".financeapp/.credkey")

    private fun isValidKey(key: String): Boolean {
        if (key.length > 128) return false
        return key.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    private fun isValidValue(value: String): Boolean {
        if (value.length > 1024) return false
        return !value.any { it == '\u0000' || (it.code < 32 && it != '\t') }
    }

    actual fun store(key: String, value: String): Boolean {
        if (!isValidKey(key) || !isValidValue(value)) {
            return false
        }
        return when {
            isMacOS() -> storeInKeychain(key, value)
            isWindows() -> storeWithDpapi(key, value)
            else -> storeInEncryptedFile(key, value)
        }
    }

    actual fun retrieve(key: String): String? {
        if (!isValidKey(key)) {
            return null
        }
        return when {
            isMacOS() -> retrieveFromKeychain(key)
            isWindows() -> retrieveWithDpapi(key)
            else -> retrieveFromEncryptedFile(key)
        }
    }

    actual fun delete(key: String): Boolean {
        if (!isValidKey(key)) {
            return false
        }
        return when {
            isMacOS() -> deleteFromKeychain(key)
            isWindows() -> deleteFromEncryptedFile(key)
            else -> deleteFromEncryptedFile(key)
        }
    }

    actual fun storeSecure(key: String, value: SecureString): Boolean {
        return value.use { charArray ->
            val stringValue = String(charArray)
            try {
                store(key, stringValue)
            } finally {
                // Note: Can't zero String in JVM, but SecureString is cleared by use {}
            }
        }
    }

    actual fun retrieveSecure(key: String): SecureString? {
        val value = retrieve(key) ?: return null
        return SecureString(value)
    }

    // ==============================================================
    // Platform Detection
    // ==============================================================

    private fun isMacOS(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    // ==============================================================
    // macOS Keychain
    // ==============================================================

    private fun storeInKeychain(key: String, value: String): Boolean {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "security", "add-generic-password",
                    "-s", serviceName,
                    "-a", key,
                    "-w", value,
                    "-U"
                ),
                mergeStderr = true
            )
            result.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun retrieveFromKeychain(key: String): String? {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "security", "find-generic-password",
                    "-s", serviceName,
                    "-a", key,
                    "-w"
                )
            )
            if (result.exitCode == 0 && result.stdout.isNotEmpty()) result.stdout else null
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteFromKeychain(key: String): Boolean {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "security", "delete-generic-password",
                    "-s", serviceName,
                    "-a", key
                ),
                mergeStderr = true
            )
            result.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    // ==============================================================
    // Windows DPAPI via PowerShell
    // ==============================================================

    private fun runPowerShellCommand(script: String): Pair<Int, String> {
        val encodedCommand = Base64.getEncoder().encodeToString(
            script.toByteArray(Charsets.UTF_16LE)
        )
        val result = ProcessRunner.run(
            listOf(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encodedCommand
            )
        )
        return result.exitCode to result.stdout
    }

    private fun storeWithDpapi(key: String, value: String): Boolean {
        return try {
            credentialsDir.mkdirs()
            setRestrictedPermissions(credentialsDir)

            // Base64-encode the value first to safely embed it in the PowerShell script
            val valueBase64 = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
            val script = """
                Add-Type -AssemblyName System.Security
                ${'$'}bytes = [Convert]::FromBase64String('$valueBase64')
                ${'$'}enc = [System.Security.Cryptography.ProtectedData]::Protect(${'$'}bytes, ${'$'}null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                [Convert]::ToBase64String(${'$'}enc)
            """.trimIndent()

            val (exitCode, output) = runPowerShellCommand(script)
            if (exitCode != 0 || output.isEmpty()) {
                // Fall back to encrypted file storage if DPAPI fails
                return storeInEncryptedFile(key, value)
            }

            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            credentialFile.writeText(output)
            setRestrictedPermissions(credentialFile)
            true
        } catch (e: Exception) {
            // Fall back to encrypted file storage
            storeInEncryptedFile(key, value)
        }
    }

    private fun retrieveWithDpapi(key: String): String? {
        return try {
            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            if (!credentialFile.exists()) return null

            val encryptedBase64 = credentialFile.readText().trim()
            if (encryptedBase64.isEmpty()) return null

            // Validate file contents are safe Base64 before interpolating into PowerShell
            if (!isValidBase64(encryptedBase64)) return null

            val script = """
                Add-Type -AssemblyName System.Security
                ${'$'}enc = [Convert]::FromBase64String('$encryptedBase64')
                ${'$'}bytes = [System.Security.Cryptography.ProtectedData]::Unprotect(${'$'}enc, ${'$'}null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                [Convert]::ToBase64String(${'$'}bytes)
            """.trimIndent()

            val (exitCode, output) = runPowerShellCommand(script)
            if (exitCode != 0 || output.isEmpty()) {
                // Try encrypted file fallback (might be legacy format)
                return retrieveFromEncryptedFile(key)
            }

            String(Base64.getDecoder().decode(output), Charsets.UTF_8)
        } catch (e: Exception) {
            // Try encrypted file fallback
            retrieveFromEncryptedFile(key)
        }
    }

    // ==============================================================
    // Encrypted File Storage (Linux fallback)
    // ==============================================================

    private fun storeInEncryptedFile(key: String, value: String): Boolean {
        return try {
            credentialsDir.mkdirs()
            setRestrictedPermissions(credentialsDir)

            val masterKey = getOrCreateMasterKey()

            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            val secretKey = SecretKeySpec(masterKey, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val valueBytes = value.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(valueBytes)

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

    private fun retrieveFromEncryptedFile(key: String): String? {
        return try {
            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            if (!credentialFile.exists()) return null

            val fileBytes = credentialFile.readBytes()
            if (fileBytes.size < 28) return null

            val iv = fileBytes.copyOfRange(0, 12)
            val ciphertext = fileBytes.copyOfRange(12, fileBytes.size)

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

    private fun deleteFromEncryptedFile(key: String): Boolean {
        return try {
            val credentialFile = File(credentialsDir, sanitizeFilename(key))
            if (credentialFile.exists()) {
                val random = ByteArray(credentialFile.length().toInt())
                SecureRandom().nextBytes(random)
                credentialFile.writeBytes(random)
                credentialFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getOrCreateMasterKey(): ByteArray {
        // On Linux, try secret-tool first
        if (!isMacOS() && !isWindows() && LinuxSecretService.isAvailable()) {
            val existing = LinuxSecretService.lookup("credential-master-key")
            if (existing != null) {
                return try {
                    Base64.getDecoder().decode(existing)
                } catch (e: Exception) {
                    // Invalid data in secret service, fall through to file-based
                    generateAndStoreMasterKey()
                }
            }
            // No key in secret service yet
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            val encoded = Base64.getEncoder().encodeToString(key)
            if (LinuxSecretService.store("credential-master-key", "FinanceApp Credential Key", encoded)) {
                // Migrate: if old plaintext file exists, securely delete it
                if (masterKeyFile.exists()) {
                    secureDeleteFile(masterKeyFile)
                }
                return key
            }
            // secret-tool store failed, fall through to file-based
        }

        return if (masterKeyFile.exists()) {
            val encodedKey = masterKeyFile.readText().trim()
            Base64.getDecoder().decode(encodedKey)
        } else {
            generateAndStoreMasterKey()
        }
    }

    private fun generateAndStoreMasterKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)

        masterKeyFile.parentFile?.mkdirs()
        val encodedKey = Base64.getEncoder().encodeToString(key)
        masterKeyFile.writeText(encodedKey)
        setRestrictedPermissions(masterKeyFile)

        return key
    }

    private fun sanitizeFilename(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(key.toByteArray(Charsets.UTF_8))
        val hash = hashBytes.take(16).joinToString("") { "%02x".format(it) }
        return "cred_$hash"
    }

    private fun setRestrictedPermissions(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
        } catch (e: Exception) {
            // Ignore on systems that don't support POSIX permissions
        }
    }

    private fun secureDeleteFile(file: File) {
        try {
            if (file.exists()) {
                val random = ByteArray(file.length().toInt())
                SecureRandom().nextBytes(random)
                file.writeBytes(random)
                file.delete()
            }
        } catch (e: Exception) {
            // Best-effort secure deletion
        }
    }
}
