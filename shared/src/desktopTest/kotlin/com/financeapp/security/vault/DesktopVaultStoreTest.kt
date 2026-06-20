package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopVaultStoreTest {

    private lateinit var dir: File

    @BeforeTest fun setup() { dir = Files.createTempDirectory("vault-test").toFile() }
    @AfterTest fun teardown() { dir.deleteRecursively() }

    private fun store() = DesktopVaultStore(File(dir, "vault.json"))

    @Test
    fun `read returns null when no vault file exists`() = runTest {
        assertNull(store().read())
    }

    @Test
    fun `write then read round-trips the vault`() = runTest {
        val s = store()
        val vault = VaultFile(
            version = 1,
            kdf = Argon2Params.DEFAULT,
            kdfSaltB64 = "c2FsdA==",
            wrappedDek = WrappedDek(SerializableBox("bm9uY2U=", "Y3Q="), recovery = null)
        )
        s.write(vault)
        assertEquals(vault, s.read())
    }

    @Test
    fun `written vault file is owner-only on POSIX filesystems`() = runTest {
        val s = store()
        s.write(
            VaultFile(
                version = 1,
                kdf = Argon2Params.DEFAULT,
                kdfSaltB64 = "c2FsdA==",
                wrappedDek = WrappedDek(SerializableBox("bm9uY2U=", "Y3Q="), recovery = null)
            )
        )
        val path = File(dir, "vault.json").toPath()
        val view = java.nio.file.Files.getFileAttributeView(
            path, java.nio.file.attribute.PosixFileAttributeView::class.java
        )
        if (view != null) { // only assert on POSIX filesystems
            val perms = java.nio.file.Files.getPosixFilePermissions(path)
            assertEquals(
                setOf(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                ),
                perms
            )
        }
    }
}
