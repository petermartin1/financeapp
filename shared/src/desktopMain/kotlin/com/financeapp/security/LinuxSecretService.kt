package com.financeapp.security

/**
 * Wrapper around the freedesktop.org `secret-tool` CLI for Linux keyring integration.
 * Falls back gracefully if secret-tool is not installed.
 */
internal object LinuxSecretService {
    private var available: Boolean? = null

    fun isAvailable(): Boolean {
        available?.let { return it }
        val result = try {
            ProcessRunner.run(listOf("which", "secret-tool"), timeoutSeconds = 5)
        } catch (e: Exception) {
            return false.also { available = it }
        }
        return (result.exitCode == 0).also { available = it }
    }

    fun store(attribute: String, label: String, value: String): Boolean {
        return try {
            val result = ProcessRunner.runWithStdin(
                command = listOf(
                    "secret-tool", "store",
                    "--label", label,
                    "application", "com.financeapp",
                    "attribute", attribute
                ),
                stdinContent = value,
                timeoutSeconds = 10
            )
            result.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun lookup(attribute: String): String? {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "secret-tool", "lookup",
                    "application", "com.financeapp",
                    "attribute", attribute
                ),
                timeoutSeconds = 10
            )
            if (result.exitCode == 0 && result.stdout.isNotEmpty()) result.stdout else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(attribute: String): Boolean {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    "secret-tool", "clear",
                    "application", "com.financeapp",
                    "attribute", attribute
                ),
                timeoutSeconds = 10
            )
            result.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
