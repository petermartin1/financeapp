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

    actual fun getOrCreateKey(): String = when {
        isMacOS() -> keyFromKeystore(
            keystoreName = "macOS Keychain",
            lookup = ::getFromKeychain,
            store = ::storeInKeychain,
            keystoreIsFile = false
        )
        isWindows() -> keyFromKeystore(
            keystoreName = "Windows DPAPI",
            lookup = ::getFromDpapi,
            store = ::storeInDpapi,
            // DPAPI persists the (encrypted) key in keyFile itself, so it is not a separate
            // plaintext file to delete after migration.
            keystoreIsFile = true
        )
        LinuxSecretService.isAvailable() -> keyFromKeystore(
            keystoreName = "Secret Service",
            lookup = { LinuxSecretService.lookup(LINUX_KEY_ATTRIBUTE) },
            store = { LinuxSecretService.store(LINUX_KEY_ATTRIBUTE, "FinanceApp Database Key", it) },
            keystoreIsFile = false
        )
        else -> throw KeyStorageException(NO_KEYSTORE_MESSAGE)
    }

    /**
     * Resolves the key from the OS key store, migrating a legacy plaintext key file into it
     * when present (so existing encrypted databases stay readable). Refuses to fall back to a
     * plaintext key on disk: if the key store write fails, it throws.
     */
    private fun keyFromKeystore(
        keystoreName: String,
        lookup: () -> String?,
        store: (String) -> Boolean,
        keystoreIsFile: Boolean
    ): String {
        lookup()?.let { return it }

        // No key in the store yet: adopt an existing plaintext key if one is present (to
        // preserve access to an already-encrypted database), otherwise mint a fresh one.
        val legacyKey = readLegacyPlaintextKey()
        val key = legacyKey ?: generateKey()

        if (!store(key)) {
            throw KeyStorageException(
                "Failed to store the database encryption key in $keystoreName; refusing to " +
                    "write it to disk in plaintext."
            )
        }

        // Remove the now-migrated plaintext file (unless the key store *is* that file, as with
        // DPAPI, where store() already overwrote it with the protected blob).
        if (legacyKey != null && !keystoreIsFile && keyFile.exists()) {
            secureDeleteFile(keyFile)
        }
        return key
    }

    /** Reads a pre-existing plaintext AES-256 key file (legacy format), or null if absent/invalid. */
    private fun readLegacyPlaintextKey(): String? {
        if (!keyFile.exists()) return null
        val contents = keyFile.readText().trim()
        if (contents.isEmpty()) return null
        return try {
            // A valid AES-256 key is exactly 32 bytes; a DPAPI-protected blob is longer.
            if (Base64.getDecoder().decode(contents).size == 32) contents else null
        } catch (e: IllegalArgumentException) {
            null
        }
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
            // On any decrypt failure return null; the caller decides whether to migrate a
            // legacy plaintext key or mint a new one (it never falls back to plaintext).
            if (exitCode == 0 && output.isNotEmpty()) output else null
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

    /** Returns the existing keystore key without creating one, or null if absent. */
    fun peekExistingKey(): String? = when {
        isMacOS() -> getFromKeychain()
        isWindows() -> getFromDpapi()
        LinuxSecretService.isAvailable() -> LinuxSecretService.lookup(LINUX_KEY_ATTRIBUTE)
        else -> null
    }

    /** Removes the keystore key after a successful migration to the password vault. */
    fun deleteKey() {
        try {
            when {
                isMacOS() -> ProcessRunner.run(
                    listOf("security", "delete-generic-password", "-s", serviceName, "-a", accountName)
                )
                isWindows() -> if (keyFile.exists()) secureDeleteFile(keyFile)
                LinuxSecretService.isAvailable() -> LinuxSecretService.clear(LINUX_KEY_ATTRIBUTE)
            }
        } catch (_: Exception) { /* best effort */ }
    }

    private companion object {
        private const val LINUX_KEY_ATTRIBUTE = "database-encryption-key"
        private const val NO_KEYSTORE_MESSAGE =
            "No OS key store is available to protect the database encryption key " +
                "(Secret Service / Keychain / DPAPI). Refusing to write the key to disk in " +
                "plaintext. On Linux, install gnome-keyring or KeePassXC and ensure secret-tool " +
                "is on the PATH, then restart the app."
    }
}
