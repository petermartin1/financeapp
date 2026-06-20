# Security Documentation

## Database Encryption & Master Password Vault

The local H2 database (`~/.financeapp/finance.mv.db`) is encrypted at rest and **sealed by a key
derived from your master password** — it cannot be opened without that password (or a recovery
key). This is the app's primary at-rest protection.

### How it works (envelope encryption)

- A random 256-bit **Data Encryption Key (DEK)** is the database's encryption secret.
- The DEK is never stored in the clear. It is **wrapped** (encrypted) with **AES-256-GCM** under a
  **Key-Encryption-Key (KEK)** derived from your master password via **Argon2id**
  (memory-hard: 64 MiB, 3 iterations, parallelism 4).
- A separate, optional copy of the DEK is wrapped under a high-entropy **recovery key** so a
  forgotten password is recoverable. The recovery key is shown once at setup; store it safely.
- All of this lives in `~/.financeapp/vault.json` (only encrypted material; written atomically
  with owner-only `0600` permissions). The OS keystore is **no longer used to hold the DB key**.
- On every launch the master password is required: it derives the KEK, unwraps the DEK, and opens
  the database. A wrong password fails the AES-GCM authentication tag and yields nothing.
- The vault auto-locks after idle inactivity, and online guessing is throttled by the existing
  lockout/backoff (5 attempts → exponential backoff).

Implementation: `com.financeapp.security.vault.KeyVault` and the `security.vault` package.

### What this protects against
- A stolen/copied database file, stolen disk, or leaked backup — useless without the password.
- Other OS accounts, and same-user processes **while the app is closed** (nothing on disk or in
  the keystore can open the DB without the password).
- Offline brute force — throttled by Argon2id's per-guess cost.
- Tampering with the vault — detected by AES-GCM authentication.

### Known limitations (inherent to a local desktop app)
- **Malware running as you while the app is unlocked** can read the DEK/plaintext from process
  memory. Auto-lock shrinks this window but cannot eliminate it (the JVM cannot truly `mlock`).
- **A weak master password** caps the real strength — Argon2id raises per-guess cost but cannot
  rescue low entropy. A strength policy enforces a minimum (12 chars or a 4-word passphrase).
- **No hardware-bound keys** (Secure Enclave/TPM) on desktop JVM.
- **Swap/hibernation** can page decrypted data to disk — enable OS full-disk encryption
  (FileVault on macOS, BitLocker on Windows) for defense in depth.
- The recovery key is a second full-power credential; it must be stored securely by the user.

## OFX (Open Financial Exchange) Security

### Protocol Limitations

The OFX protocol has inherent security limitations that users and developers should be aware of:

#### 1. **Credentials in Request Body**

**Limitation**: OFX sends credentials (username and password) in the HTTP request body, not as headers or using modern OAuth flows.

**Why This Matters**:
- Credentials are included in every OFX request
- No token-based authentication or session management
- If HTTPS is compromised (e.g., certificate pinning bypass), credentials are exposed

**Mitigations We Implement**:
- ✅ Always use HTTPS (never HTTP)
- ✅ Certificate pinning for known bank endpoints
- ✅ Rate limiting to prevent brute force attacks
- ✅ Secure credential storage (macOS Keychain, encrypted files for Windows/Linux)
- ✅ Memory protection using SecureString (automatic zeroing)

**What We Cannot Fix**:
- ❌ The protocol itself - OFX specification requires credentials in request body
- ❌ Bank-side security - we rely on banks implementing OFX securely

#### 2. **No Built-in Refresh Tokens**

**Limitation**: Unlike OAuth 2.0, OFX does not support refresh tokens. Credentials must be stored and reused for each request.

**Implications**:
- Long-term credential storage is necessary
- No ability to revoke specific sessions
- Credential rotation requires user to manually update

**Mitigations**:
- ✅ Encrypted credential storage
- ✅ Platform-specific secure storage (Keychain on macOS)
- ✅ Option to delete stored credentials at any time

#### 3. **Limited Error Information**

**Limitation**: OFX error responses often lack detailed information, making it difficult to distinguish between authentication failures, network errors, and bank-side issues.

**Mitigations**:
- ✅ Generic error messages to prevent information leakage
- ✅ Security audit logging for internal troubleshooting
- ✅ Rate limiting prevents probing for valid credentials

#### 4. **Bank-Specific Implementations**

**Limitation**: Each bank implements OFX slightly differently, with varying levels of security.

**Challenges**:
- Some banks may not support HTTPS
- Certificate pinning not universally applicable
- Different authentication error codes
- Varying rate limiting on bank side

**Mitigations**:
- ✅ Bank-specific configurations (see `BankConfigs.kt`)
- ✅ Optional certificate pinning per bank
- ✅ Client-side rate limiting (always active)

## Security Best Practices

### For Users

1. **Use Strong Passwords**: Your bank password protects your financial data
2. **Keep Software Updated**: Security patches are released regularly
3. **Verify HTTPS**: Ensure connections show secure indicators
4. **Review Audit Logs**: Check security events regularly
5. **Revoke Access**: If you suspect compromise, delete stored credentials and change your bank password

### For Developers

1. **Never Log Passwords**: Use `SecurityAuditLogger` which auto-sanitizes
2. **Use SecureString**: For password handling in memory
3. **Test Security**: Run security test suite regularly
4. **Monitor Certificate Pins**: Use `CertificatePinMonitor.checkPinHealth()`
5. **Respect Rate Limits**: Don't bypass the `RateLimiter`

## Threat Model

### Threats We Protect Against

| Threat | Protection | Effectiveness |
|--------|------------|---------------|
| Brute force attacks | Rate limiting + exponential backoff | ✅ High |
| Credential theft from storage | Encrypted storage + Keychain | ✅ High |
| Memory dumps | SecureString (auto-zeroing) | ✅ Medium |
| Network interception | HTTPS + certificate pinning | ✅ High |
| Command injection | Input validation + tests | ✅ High |
| Unauthorized access | Encrypted database | ✅ High |

### Threats Outside Our Control

| Threat | Why | Recommendation |
|--------|-----|----------------|
| Compromised bank servers | Bank-side security | Monitor account for suspicious activity |
| Phishing attacks | User behavior | Verify you're using legitimate app |
| Keyloggers/malware | OS-level compromise | Use antivirus, keep OS updated |
| Physical access to unlocked device | User responsibility | Use OS-level screen lock |

## Security Features Implemented

### Authentication & Access Control
- ✅ Encrypted database (H2 with AES), sealed by a master-password-derived key (see top section)
- ✅ Argon2id key derivation for the master password (64 MiB, t=3, p=4); envelope-encrypted DEK
- ✅ Recovery key for forgotten-password recovery
- ✅ Master password required on every launch + idle auto-lock
- ✅ Secure credential storage for OFX/bank logins (Keychain/encrypted files)
- ✅ Rate limiting (5 attempts, exponential backoff up to 15-min lockout)

### Network Security
- ✅ HTTPS-only connections
- ✅ Certificate pinning (optional, per-bank)
- ✅ Certificate pin validation (SHA-256)
- ✅ Network timeouts (10s connect, 30s socket, 60s request)
- ✅ No automatic redirect following

### Data Protection
- ✅ SecureString (memory protection)
- ✅ Encrypted credential files (AES-256-GCM)
- ✅ Passwords never in database
- ✅ Sensitive data sanitization in logs

### Audit & Monitoring
- ✅ Security event logging
- ✅ Automatic log sanitization
- ✅ Event categorization (AUTH, CONNECTION, SECURITY)
- ✅ Severity levels (INFO, WARNING, ERROR, CRITICAL)
- ✅ Certificate pin expiration monitoring

### Input Validation
- ✅ Shell command injection prevention
- ✅ Path traversal prevention
- ✅ Null byte filtering
- ✅ Control character filtering
- ✅ Length limits on inputs
- ✅ Comprehensive security test suite (19 tests)

## Compliance Notes

### Data Storage
- **Location**: Local only (no cloud sync)
- **Encryption**: AES-256 (database), AES-256-GCM (credential files)
- **Passwords**: Never stored in database, only in OS keychain or encrypted files

### Network Communication
- **Protocol**: HTTPS only
- **Pinning**: Optional per-bank certificate pinning
- **Credentials**: Transmitted in OFX request body (protocol limitation)

### Logging
- **Passwords**: Never logged
- **User IDs**: Masked (first 3 chars + ***)
- **Events**: Stored in memory only (max 1000 events)
- **Retention**: Cleared on app restart

## Reporting Security Issues

If you discover a security vulnerability:

1. **Do NOT** open a public GitHub issue
2. Email security concerns to: [Add contact email]
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## Security Roadmap

### Future Enhancements (Optional)
- [ ] OAuth-based bank connections (via Plaid API)
- [ ] Hardware security module (HSM) support
- [ ] Biometric authentication (TouchID/FaceID)
- [ ] Persistent audit log files
- [ ] Two-factor authentication for app access

### Industry Limitations
- OFX protocol cannot be made more secure without bank-side changes
- Modern alternative: OAuth-based APIs (Plaid, Yodlee) - not yet implemented

## References

- OFX Specification: https://www.ofx.net/
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- Certificate Pinning Guide: https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning
