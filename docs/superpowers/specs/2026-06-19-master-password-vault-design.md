# Master-Password Vault — Design

**Date:** 2026-06-19
**Status:** Approved (design); pending implementation plan
**Scope:** Replace the OS-keystore-backed database key with password-sealed envelope
encryption, so the finance database is cryptographically unrecoverable without the user's
master password (or a recovery key).

---

## 1. Goal & threat model

### Goal
Make the at-rest protection of `finance.mv.db` match the standard set by reputable local-first
password managers: the user's master password is the **root of trust**, and the database is
sealed by a key derived from it — not merely access-controlled by an OS keystore ACL.

### What changes vs today
Today a *random* AES-256 key is stored in the OS keystore and used as the H2 file password; the
app-lock password is only compared against a PBKDF2 hash and gates the UI. Any process running as
the user can read the key from the keystore and decrypt the DB without knowing the password. This
design removes the keystore from the DB-key path entirely and seals the key under the password.

### Threat model — defended (strong)
- Stolen/copied `finance.mv.db`, stolen disk, leaked backup → useless without the password.
- Other OS user accounts.
- **Same-user malware while the app is closed** — the DEK exists only as a wrapped blob on disk;
  nothing in the keystore can open it.
- Offline brute force → throttled by Argon2id's per-guess cost.
- Vault tampering → detected by AES-GCM authentication.
- Online password guessing → existing lockout/backoff.

### Threat model — explicitly NOT defended (inherent limits, documented for honesty)
1. Malware running as the user **while the app is unlocked** (DEK/plaintext live in JVM heap;
   the JVM cannot truly `mlock` or prevent GC copies). Mitigated, not eliminated, by auto-lock
   and key zeroing.
2. A weak master password (Argon2id raises per-guess cost but cannot rescue low entropy).
   Mitigated by the password-strength policy.
3. Hardware-bound keys (Secure Enclave/TPM) — out of reach on desktop JVM; we use strict
   password instead.
4. Swap/hibernation paging decrypted data to disk — mitigated only by OS full-disk encryption
   (we recommend it).
5. Recovery key stored insecurely by the user.
6. Keyloggers, compromised OS, replaced app binary — out of scope for app-level crypto.

---

## 2. Decisions (locked)

| Decision | Choice | Rationale |
|---|---|---|
| Unlock model | **Strict — master password on every cold start** | Keystore holds nothing that can open the DB; max at-rest security. |
| KDF | **Argon2id** (m=64 MiB, t=3, p=4) via Bouncy Castle | Memory-hard; OWASP #1; resists GPU/ASIC. |
| Recovery | **Optional recovery key** (256-bit, Crockford-Base32) | Avoids permanent data loss on forgotten password. |
| Password policy | **≥12 chars or 4-word passphrase + strength meter** | The password is now the root secret (closes R22 in its new, security-critical context). |
| Auto-lock | **Idle timeout (default ~10 min) + on OS sleep; zero DEK on lock** | Shrinks the unlocked-in-memory window. |

---

## 3. Cryptographic core & vault format

**Envelope encryption.** A random 256-bit **DEK** (Data Encryption Key) is the actual H2
file-encryption password. It is never persisted in the clear — only wrapped copies are stored.

**Vault file** — `~/.financeapp/vault.json`, versioned. No secret is in plaintext, so JSON is
safe and debuggable.

```jsonc
{
  "version": 1,
  "kdf": { "algorithm": "argon2id", "memoryKiB": 65536, "iterations": 3,
           "parallelism": 4, "saltB64": "..." },
  "cipher": "AES-256-GCM",
  "wrappedDek": {
    "password": { "nonceB64": "...", "ctB64": "..." },   // GCM(KEK_pw,  DEK)
    "recovery": { "nonceB64": "...", "ctB64": "..." }     // GCM(KEK_rec, DEK)  (if enabled)
  }
}
```

**Key flow:**
- `KEK_pw = Argon2id(masterPassword, salt, m=64MiB, t=3, p=4)` → 256-bit AES-GCM key.
- `DEK` wrapped under `KEK_pw` with **AES-256-GCM**. The GCM auth tag *is* the password
  verifier — a wrong password fails the tag and yields nothing. No separate hash is stored.
- **Recovery key**: 256 bits of CSPRNG randomness shown to the user as Crockford-Base32
  (`XXXX-XXXX-…`, ~52 chars). Being full-entropy, it is used **directly** as `KEK_rec` (no KDF)
  to wrap a second copy of the DEK.

**H2 fit:** the DEK (encoded as a string) replaces today's keystore key as the H2 `CIPHER=AES`
file password, so the on-disk DB format is unchanged and migration is a re-wrap, not a
re-encrypt.

**Dependency:** Bouncy Castle (`bcprov`) for Argon2id. AES-GCM + CSPRNG are JDK-native.

---

## 4. Components & flows

### `KeyVault` (new) — single source of truth for the unlocked DEK
Platform-agnostic crypto core in `commonMain`; vault-file I/O via the existing platform file
access. Public surface:

- `status(): NoVault | Locked | Unlocked`
- `setUp(password): RecoveryKey` — generate DEK + recovery key, write vault, return the recovery
  key **once** for display.
- `unlock(password): Dek` — or fail (feeds the existing lockout).
- `unlockWithRecovery(code): Dek` — then require `changePassword`.
- `changePassword(old, new)` / `resetPasswordWithRecovery(code, new)` — re-wrap the **same** DEK
  (no DB re-encryption).
- `lock()` — zero the DEK buffer, drop it, return to `Locked`.

### Bootstrap reordering (critical change)
Today `single<Database> { factory.createDriver() }` opens eagerly and the lock screen is
cosmetic. New ordering:

```
App start → KeyVault.status()
  NoVault   → Setup screen  → setUp() → show recovery key → open DB with DEK
  Locked    → Unlock screen → unlock()/recovery → open DB with DEK
  Unlocked  → MainContent
```

The `Database` and every repo/VM that depends on it must **not** be constructed until the DEK
exists. Audit and gate everything that touches the DB pre-unlock — notably `AppViewModel`'s
startup of `SnapshotScheduler` (N5) and `PriceRefreshService`, which currently start at
bootstrap; these move to *after* unlock. `DatabaseDriverFactory` takes the DEK from `KeyVault`
instead of `EncryptionKeyManager`.

### Migration (zero re-encryption)
On first launch with the old setup (keystore key present, no vault):
1. One-time "Secure your data" screen.
2. If an app-lock password exists → user enters it (verified against the legacy PBKDF2 hash);
   else → user creates a master password.
3. Read the existing keystore key, **adopt it as the DEK**, wrap under the new `KEK_pw`, generate
   and show a recovery key, write the vault, then **delete the key from the OS keystore**.
4. Because the DEK is the old key, the existing `finance.mv.db` opens unchanged.

### Scope boundaries
- The OS keystore (`EncryptionKeyManager`) is retired **for the DB key only**.
- `SecureCredentialStore` continues to use the keystore for OFX/bank credentials — untouched.
- The existing lockout/backoff (5 tries → exponential, persisted) is preserved and now guards
  the `unlock` path.

---

## 5. UI

- **Setup** (first run / migration): create master password with a strength meter + policy;
  confirm; then a recovery-key screen. A recovery key is **generated by default** (recommended):
  it is displayed once, the user must confirm they saved it (re-enter or explicit tick), with a
  copy button and a "only time you'll see it" warning, plus a one-line OS-full-disk-encryption
  recommendation. The user may explicitly **skip** recovery after an unambiguous "you can never
  recover a forgotten password" warning; if skipped, the `wrappedDek.recovery` entry is omitted.
- **Unlock** (extends `PinUnlockScreen`): password → `unlock()`; existing lockout countdown; a
  "Forgot password?" link → recovery screen (enter recovery key → set new password).
- **Change password** (settings): old → new (+ strength meter); re-wraps the DEK.

---

## 6. Hardenings (baked in)

1. **Strong master-password policy** — minimum 12 characters or a 4-word passphrase, gated by a
   zxcvbn-style strength estimator that rejects "weak". Implemented as a pure, unit-testable
   `PasswordStrength` function. Supersedes the bare min-8 (R22) now that the password is the root
   cryptographic secret.
2. **Auto-lock + key zeroing** — lock on idle timeout (default ~10 min) and on OS sleep/
   screen-lock where detectable; on lock, zero the DEK buffer and drop it from `KeyVault`,
   returning to the Unlock screen.
3. **OS full-disk-encryption guidance** — shown on setup and in `SECURITY.md`.

---

## 7. Test plan (TDD, `commonTest`, real crypto, no mocks)

- **Crypto round-trip:** `setUp` → `unlock` returns the same DEK; DEK is 256-bit and random
  across runs.
- **Wrong password:** GCM tag fails → `unlock` rejects, DEK never exposed.
- **Recovery:** `unlockWithRecovery` returns the DEK; `resetPasswordWithRecovery` makes the new
  password unlock and the old one fail.
- **Tamper detection:** flipping a byte in `ctB64`/`nonceB64` → unlock fails closed.
- **Change password:** same DEK after change; old password rejected; recovery still works.
- **Migration:** legacy keystore key + app-lock hash → first run adopts the key as DEK, writes
  the vault, the existing DB opens, the keystore entry is deleted.
- **Lockout integration:** repeated wrong passwords trip the existing backoff.
- **Password strength:** table-driven — strong accepted, weak/short rejected.
- **Vault format:** version field present; unknown future version fails safe.

---

## 8. Out of scope (explicit)

- Defending an already-unlocked session against same-user malware.
- Hardware-bound keys (Secure Enclave/TPM).
- Changing the OFX `SecureCredentialStore`.
- Any non-desktop platform (the app is desktop-only by decision).
