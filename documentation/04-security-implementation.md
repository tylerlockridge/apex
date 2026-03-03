# Feature: Security Implementation

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Apex implements encrypted credential storage via `SecurePrefs.kt` and biometric authentication via `BiometricLockManager.kt`. API keys are transmitted as Bearer tokens. Debug HTTP logging is gated to debug builds only. Release builds apply ProGuard minification.

**What it does NOT do:**
- Does not implement certificate pinning (noted in TODO comments)
- Does not implement HMAC request signing (PRD requirement, not implemented)
- Does not support token refresh — API key must be manually re-scanned if rotated

---

## SecurePrefs.kt

- Singleton
- Encryption: AES256_GCM via `EncryptedSharedPreferences` + `MasterKey`
- Stored values:
  - `api_key` — Bearer token for all server API requests
  - `biometric_enabled` — boolean flag for lock screen behavior
- One-time migration from plain `SharedPreferences` on first launch (existing unencrypted prefs are migrated automatically)

---

## BiometricLockManager.kt

- Enforces `BIOMETRIC_STRONG` class — fingerprint or face only; class 1 (weak) biometrics are rejected
- Presents `BiometricPrompt` when authentication is required
- Lock screen is shown in `MainActivity` when:
  - `biometric_enabled = true` in SecurePrefs AND
  - User has not yet authenticated in the current session

### Inactivity Re-Auth

- 5-minute inactivity timer: measured via `SystemClock.elapsedRealtime()`
- Check fires in `onResume()` — if elapsed time since last interaction >= 5 minutes, lock screen is shown again
- Uses `>=` (not `>`), so exactly 5 minutes triggers re-auth

---

## API Key Transmission

- All outbound HTTP requests include `Authorization: Bearer <api_key>`
- `device_secret` is also sent in the request body for Health Connect sync POSTs (separate credential from API key)

---

## Build-Time Security Controls

| Control | Status |
|---------|--------|
| HTTP logging interceptor | DEBUG builds only; disabled in release |
| ProGuard minification | Enabled on release builds |
| ProGuard scope | Gson data classes preserved; rest minified |

---

## Known Gaps

| Gap | Notes |
|-----|-------|
| Certificate pinning | ISRG Root X1 planned; TODO comment in OkHttp config; not configured |
| HMAC request signing | PRD calls for signed request bodies; not implemented in `ApiService.kt` |
| Token refresh | No mechanism; if API key is rotated, user must manually re-scan QR code (which is also a stub — see `11-unimplemented-features.md`) |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Encrypted credential storage | ✅ PASS | AES256_GCM via EncryptedSharedPreferences |
| Biometric auth (STRONG class) | ✅ PASS | Class 1 biometrics rejected |
| 5-minute inactivity re-auth | ✅ PASS | elapsedRealtime() check in onResume() |
| Debug-only HTTP logging | ✅ PASS | Release builds have no HTTP logging |
| Release minification | ✅ PASS | ProGuard with Gson rules |
| Bearer token on all requests | ✅ PASS | api_key from SecurePrefs |
| Certificate pinning | ❌ FAIL | TODO only; not implemented |
| HMAC request signing | ❌ FAIL | PRD requirement; not implemented |
| Token refresh | ❌ FAIL | No refresh flow; manual re-scan only |
