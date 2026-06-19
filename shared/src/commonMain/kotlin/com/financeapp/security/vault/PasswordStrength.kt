package com.financeapp.security.vault

/**
 * Lightweight password policy (not full zxcvbn): a password is acceptable if it is either
 * a passphrase of >= 4 words (each >= 3 chars) or >= 12 characters with some variety, and
 * is neither trivially repetitive nor a well-known common password.
 */
object PasswordStrength {

    data class Result(val acceptable: Boolean, val reason: String?)

    private val COMMON = setOf(
        "password", "password1234", "123456789012", "qwertyuiop12", "letmein12345"
    )

    fun evaluate(password: CharArray): Result {
        val pw = String(password)
        val lower = pw.lowercase()

        if (COMMON.contains(lower)) return Result(false, "This is a commonly used password.")
        if (isRepetitive(pw)) return Result(false, "Too repetitive.")

        val words = pw.trim().split(Regex("\\s+")).filter { it.length >= 3 }
        if (words.size >= 4) return Result(true, null)

        if (pw.length < 12) return Result(false, "Use at least 12 characters or a 4-word passphrase.")

        val variety = listOf(
            pw.any { it.isLowerCase() },
            pw.any { it.isUpperCase() },
            pw.any { it.isDigit() },
            pw.any { !it.isLetterOrDigit() }
        ).count { it }
        if (variety < 2) return Result(false, "Mix letters with numbers or symbols.")

        return Result(true, null)
    }

    private fun isRepetitive(pw: String): Boolean {
        if (pw.isEmpty()) return true
        return pw.toSet().size <= 2
    }
}
