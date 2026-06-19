package com.financeapp.security.vault

class InMemoryVaultStore(private var current: VaultFile? = null) : VaultStore {
    override suspend fun read(): VaultFile? = current
    override suspend fun write(vault: VaultFile) { current = vault }
    override suspend fun deleteVault() { current = null }
}
