package com.financeapp.security.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class DesktopVaultStore(
    private val file: File = File(System.getProperty("user.home"), ".financeapp/vault.json")
) : VaultStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun read(): VaultFile? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        json.decodeFromString(VaultFile.serializer(), file.readText())
    }

    override suspend fun write(vault: VaultFile) = withContext(Dispatchers.IO) {
        val dir = file.parentFile
        if (dir != null) ensureRestrictedDir(dir)
        val content = json.encodeToString(VaultFile.serializer(), vault)

        // Write to a temp file restricted to owner-only BEFORE writing content, then atomically
        // move it into place, so the vault is never momentarily readable with default perms.
        val tmp = File.createTempFile("vault", ".tmp", dir)
        try {
            restrictOwnerOnly(tmp)
            tmp.writeText(content)
            try {
                Files.move(
                    tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            restrictOwnerOnly(file)
        } finally {
            tmp.delete()
        }
    }

    override suspend fun deleteVault() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun ensureRestrictedDir(dir: File) {
        dir.mkdirs()
        try {
            Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------"))
        } catch (_: Exception) { /* non-POSIX: rely on default ACLs */ }
    }

    private fun restrictOwnerOnly(f: File) {
        val posix = try {
            Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"))
            true
        } catch (_: Exception) { false }
        if (!posix) {
            try {
                f.setReadable(false, false); f.setReadable(true, true)
                f.setWritable(false, false); f.setWritable(true, true)
                f.setExecutable(false, false)
            } catch (_: Exception) { /* best effort */ }
        }
    }
}
