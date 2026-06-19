package com.financeapp.security.vault

import kotlinx.serialization.Serializable

@Serializable
data class Argon2Params(
    val memoryKiB: Int = 65536,   // 64 MiB
    val iterations: Int = 3,
    val parallelism: Int = 4
) {
    companion object {
        val DEFAULT = Argon2Params()
    }
}
