package com.financeapp.security.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Argon2id password-based key derivation (Bouncy Castle). */
object Argon2 {
    private const val KEY_BYTES = 32

    fun deriveKey(password: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val passwordBytes = charsToUtf8(password)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(params.iterations)
                .withMemoryAsKB(params.memoryKiB)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator().apply { init(parameters) }
            val out = ByteArray(KEY_BYTES)
            generator.generateBytes(passwordBytes, out)
            return out
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun charsToUtf8(chars: CharArray): ByteArray =
        String(chars).encodeToByteArray()
}
