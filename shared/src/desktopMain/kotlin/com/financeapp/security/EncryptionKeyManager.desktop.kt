package com.financeapp.security

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

actual class EncryptionKeyManager {
    private val serviceName = "com.financeapp"
    private val accountName = "database-encryption-key"

    private val keyFile = File(System.getProperty("user.home"), ".financeapp/.dbkey")

    actual fun getOrCreateKey(): String {
        // macOS: use Keychain
        if (isMacOS()) {
            val keychainKey = getFromKeychain()
            if (keychainKey != null) return keychainKey
            val newKey = generateKey()
            if (storeInKeychain(newKey)) return newKey
        }

        // Windows: use DPAPI
        if (isWindows()) {
            val dpapiKey = getFromDpapi()
            if (dpapiKey != null) return dpapiKey
            val newKey = generateKey()
            if (storeInDpapi(newKey)) return newKey
        }

        // Linux: try secret-tool, then file fallback
        return getOrCreateFileKey()
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

    private fun getFromKeychain(): String? {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "security", "find-generic-password",
                    "-s", serviceName,
                    "-a", accountName,
                    "-w"
                )
            )
            if (result.exitCode == 0 && result.stdout.isNotEmpty()) result.stdout else null
        } catch (e: Exception) {
            null
        }
    }

    private fun storeInKeychain(key: String): Boolean {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "security", "add-generic-password",
                    "-s", serviceName,
                    "-a", accountName,
                    "-w", key,
                    "-U"
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

    private fun getFromDpapi(): String? {
        if (!keyFile.exists()) return null
        return try {
            val encryptedBase64 = keyFile.readText().trim()
            if (encryptedBase64.isEmpty()) return null

            // Validate file contents are safe Base64 before interpolating into PowerShell
            if (!isValidBase64(encryptedBase64)) return null

            val script = """
                Add-Type -AssemblyName System.Security
                ${'$'}enc = [Convert]::FromBase64String('$encryptedBase64')
                ${'$'}bytes = [System.Security.Cryptography.ProtectedData]::Unprotect(${'$'}enc, ${'$'}null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                [System.Text.Encoding]::UTF8.GetString(${'$'}bytes)
            """.trimIndent()

            val (exitCode, output) = runPowerShellCommand(script)
            if (exitCode == 0 && output.isNotEmpty()) {
                output
            } else {
                // DPAPI decrypt failed — attempt migration only if it looks like a plaintext AES key
                migratePlaintextKeyToDpapi(encryptedBase64)
            }
        } catch (e: Exception) {
            // Do not attempt migration on exception — too risky
            null
        }
    }

    private fun migratePlaintextKeyToDpapi(candidateKey: String): String? {
        return try {
            val decoded = Base64.getDecoder().decode(candidateKey)
            // AES-256 key must be exactly 32 bytes
            if (decoded.size != 32) {
                // Not a valid plaintext AES-256 key — likely a corrupted DPAPI blob
                return null
            }
            if (storeInDpapi(candidateKey)) {
                candidateKey
            } else {
                // DPAPI store failed but we validated it's a real AES key
                candidateKey
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun storeInDpapi(key: String): Boolean {
        return try {
            keyFile.parentFile?.mkdirs()

            val keyBase64 = Base64.getEncoder().encodeToString(key.toByteArray(Charsets.UTF_8))
            val script = """
                Add-Type -AssemblyName System.Security
                ${'$'}bytes = [Convert]::FromBase64String('$keyBase64')
                ${'$'}enc = [System.Security.Cryptography.ProtectedData]::Protect(${'$'}bytes, ${'$'}null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                [Convert]::ToBase64String(${'$'}enc)
            """.trimIndent()

            val (exitCode, output) = runPowerShellCommand(script)
            if (exitCode != 0 || output.isEmpty()) return false

            keyFile.writeText(output)
            setRestrictedPermissions(keyFile)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==============================================================
    // Linux: Secret Service with File-Based Fallback
    // ==============================================================

    private fun getOrCreateFileKey(): String {
        // Try secret-tool first on Linux
        if (!isMacOS() && !isWindows() && LinuxSecretService.isAvailable()) {
            val existing = LinuxSecretService.lookup("database-encryption-key")
            if (existing != null) return existing

            // Generate and store in secret service
            val newKey = generateKey()
            if (LinuxSecretService.store("database-encryption-key", "FinanceApp Database Key", newKey)) {
                // Migrate: if old plaintext file exists, securely delete it
                if (keyFile.exists()) {
                    secureDeleteFile(keyFile)
                }
                return newKey
            }
            // secret-tool store failed, fall through to file-based
        }

        keyFile.parentFile?.mkdirs()

        return if (keyFile.createNewFile()) {
            val key = generateKey()
            keyFile.writeText(key)
            setRestrictedPermissions(keyFile)
            key
        } else {
            keyFile.readText().trim()
        }
    }

    // ==============================================================
    // Shared Utilities
    // ==============================================================

    private fun generateKey(): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        val secretKey: SecretKey = keyGen.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }

    private fun setRestrictedPermissions(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        } catch (e: Exception) {
            // Ignore on systems that don't support this
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
