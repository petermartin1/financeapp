package com.financeapp.security.vault

import java.security.SecureRandom

/** A 256-bit recovery secret and its human-readable Crockford-Base32 form. */
class RecoveryKey private constructor(val bytes: ByteArray, val display: String) {

    companion object {
        private const val KEY_BYTES = 32
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford, no I L O U
        private const val GROUP = 4

        fun generate(): RecoveryKey {
            val bytes = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
            return RecoveryKey(bytes, group(encode(bytes)))
        }

        /** Decode user-entered text (tolerant of case, spaces, dashes, and I/L/O confusables). */
        fun decode(input: String): ByteArray? {
            val cleaned = input.uppercase()
                .replace('I', '1').replace('L', '1').replace('O', '0')
                .filter { it in ALPHABET }
            if (cleaned.length != encodedLength(KEY_BYTES)) return null
            val out = ByteArray(KEY_BYTES)
            var buffer = 0L
            var bits = 0
            var idx = 0
            for (c in cleaned) {
                buffer = (buffer shl 5) or ALPHABET.indexOf(c).toLong()
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    out[idx++] = ((buffer shr bits) and 0xFF).toByte()
                }
            }
            return if (idx == KEY_BYTES) out else null
        }

        private fun encodedLength(byteCount: Int): Int = (byteCount * 8 + 4) / 5

        private fun encode(bytes: ByteArray): String {
            val sb = StringBuilder()
            var buffer = 0L
            var bits = 0
            for (b in bytes) {
                buffer = (buffer shl 8) or (b.toLong() and 0xFF)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    sb.append(ALPHABET[((buffer shr bits) and 0x1F).toInt()])
                }
            }
            if (bits > 0) sb.append(ALPHABET[((buffer shl (5 - bits)) and 0x1F).toInt()])
            return sb.toString()
        }

        private fun group(s: String): String =
            s.chunked(GROUP).joinToString("-")
    }
}
