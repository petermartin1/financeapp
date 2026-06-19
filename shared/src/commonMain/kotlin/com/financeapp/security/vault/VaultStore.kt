package com.financeapp.security.vault

/** Persists the (entirely encrypted) vault file. */
interface VaultStore {
    suspend fun read(): VaultFile?
    suspend fun write(vault: VaultFile)
    suspend fun deleteVault()
}
