# Apex LLM Audit Action Plan — 2026-02-28

Sorted by cross-provider confidence then severity.
Format: `[SEVERITY] [CONFIDENCE] Provider(s) — Description — File(s)`

---

## CONFIRMED (2+ Providers)

### 1. [HIGH] [CONFIRMED/ALL-3] Gate logging interceptor on BuildConfig.DEBUG
- **File:** `app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt:107`
- `HttpLoggingInterceptor(Level.BASIC)` runs in production — logs `Authorization: Bearer <token>` to Logcat
- **Fix:** Wrap interceptor in `if (BuildConfig.DEBUG)` block
- **Status:** ✅ IMPLEMENTED (2026-02-28)

### 2. [HIGH] [CONFIRMED/ALL-3] Enable release build minification
- **File:** `app/build.gradle.kts:31`
- `isMinifyEnabled = false` → APK trivially decompilable, DEVICE_SECRET exposed in BuildConfig
- **Fix:** Set `isMinifyEnabled = true` for release buildType, add ProGuard rules if needed
- **Status:** ✅ IMPLEMENTED (2026-02-28)

### 3. [MEDIUM-HIGH] [CONFIRMED/ALL-3] Enforce BIOMETRIC_STRONG in auth prompt
- **File:** `app/src/main/java/com/healthplatform/sync/security/BiometricLockManager.kt:46`
- No `setAllowedAuthenticators()` → 2D face unlock (weak) can satisfy the health data gate
- **Fix:** Add `setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)`
- **Status:** ✅ IMPLEMENTED (2026-02-28)

### 4. [HIGH] [CODEX/FULL-REPO] Fix response.body()!! unsafe null assertion
- **File:** `app/src/main/java/com/healthplatform/sync/service/ApiService.kt:63,81,99`
- `response.body()!!` after `isSuccessful` will crash on 200 OK + empty body
- **Fix:** Replace `!!` with `?: throw Exception("Empty sync response body")`
- **Status:** ✅ IMPLEMENTED (2026-02-28)

### 5. [HIGH] [CONFIRMED/ALL-3] Migrate api_key to EncryptedSharedPreferences
- **Files:** `SyncWorker.kt:24`, `BiometricLockManager.kt:10`, any SharedPreferences reader
- `"api_key"` stored plaintext; `security-crypto` dep is already in Gradle
- **Fix:** Added `SecurePrefs.kt` singleton (AES256_GCM); one-time migration from plain prefs; updated 7 call sites across 5 files; `BiometricLockManager` now delegates entirely to `SecurePrefs`
- **Status:** ✅ IMPLEMENTED (2026-02-28, commit `8b55273`)

### 6. [CRITICAL] [CONFIRMED/ALL-3] Add test coverage
- **Files:** `app/src/test/`, `app/src/androidTest/` (both empty)
- Priority test targets: SyncWorker (sync/retry logic), ApiService (payload mapping), HealthConnectReader (permission handling)
- **Fix:** Created `ErrorMessageTest` (4 tests, all `toFriendlyMessage()` exception branches) + `ApiServiceTest` (6 tests via MockWebServer: success, 401, 500, null-body crash fix, auth header). Added `mockwebserver:4.12.0` + `coroutines-test:1.7.3` deps.
- **Status:** ✅ IMPLEMENTED (2026-02-28, commit `12b2534`)

### 7. [MEDIUM] [CONFIRMED/ALL-3] Implement Health Connect read pagination
- **File:** `app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt`
- All `readRecords()` calls are single-shot — silently drops records beyond first page
- **Fix:** All 4 read methods (`readBloodPressure`, `readSleep`, `readWeight` ×3 sub-requests, `readHeartRateVariability`) now loop on `pageToken` until null; Oura filtering applied after all pages accumulated
- **Status:** ✅ IMPLEMENTED (2026-02-28, commit `2ea0e53`)

---

## LIKELY (Codex-Only — Trusted)

### 8. [LOW] Update README minSdk documentation
- **File:** `README.md`
- README states "API 28+" but `build.gradle.kts` sets `minSdk = 34`
- **Status:** PENDING

---

## UNCONFIRMED (Single Browser Provider — Verify Manually)

### 9. [LOW] Hard-coded SERVER_URL may complicate dev/prod switching
- **Provider:** Monica only
- Current: single constant in `Config.kt`
- **Status:** VERIFY — acceptable for personal app; low priority

---

## Score Targets (post-fix projections)

| Dimension | Current | After top-4 fixes | Target (6-month) |
|-----------|---------|------------------|-----------------|
| Architecture | 5.0 | 5.0 | 7.0 (add DI) |
| Code Quality | 5.4 | 6.5 | 7.5 |
| Testing | 1.0 | 3.0 | 7.0 (expand coverage) |
| Security | 2.4 | 7.0 | 7.5 |
| Performance | 5.3 | 6.5 | 6.5 |
| Documentation | 3.4 | 3.5 | 6.0 |
| **Overall** | **3.8** | **6.0** | **7.0** |
