package com.financeapp.security.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class DesktopVaultStore(
    private val file: File = File(System.getProperty("user.home"), ".financeapp/vault.json")
) : VaultStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun read(): VaultFile? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        json.decodeFromString(VaultFile.serializer(), file.readText())
    }

    override suspend fun write(vault: VaultFile) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(VaultFile.serializer(), vault))
        restrict(file)
    }

    override suspend fun deleteVault() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun restrict(f: File) {
        try {
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
            f.setExecutable(false, false)
        } catch (_: Exception) { /* non-POSIX */ }
    }
}
