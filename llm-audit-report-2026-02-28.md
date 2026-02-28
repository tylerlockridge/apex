# LLM Audit Report — Apex Android App

**Date:** 2026-02-28
**Providers:** Codex (GPT-4o, full repo) + Gemini 3 Pro (browser) + GPT-5.2 via Monica (browser)
**Mode:** Combined (3-provider pipeline)
**Tool:** llm-audit-v2 (Claude-in-Chrome)

---

## Score Comparison

| Dimension | Codex (1.5x) | Gemini Pro (1x) | Monica/GPT-5.2 (1x) | Weighted Avg |
|-----------|-------------|----------------|---------------------|-------------|
| Architecture | 5 | 4 | 6 | **5.0** |
| Code Quality | 6 | 5 | 5 | **5.4** |
| Testing | 1 | 1 | 1 | **1.0** |
| Security | 3 | 2 | 2 | **2.4** |
| Performance | 5 | 5 | 6 | **5.3** |
| Documentation | 4 | 3 | 3 | **3.4** |
| **Overall** | **4** | **3** | **4** | **3.8** |

> Weighted formula: (Codex×1.5 + Gemini×1.0 + Monica×1.0) / 3.5

---

## Cross-Provider Agreement Findings

> Confirmed by 2+ providers = high confidence. Severity escalated one level if all 3 agree.

### [CRITICAL — ALL 3] Zero test coverage
- **Files:** `app/src/test/`, `app/src/androidTest/` — empty
- Test dependencies declared in `build.gradle.kts` but no source files exist
- CI runs `testDebugUnitTest` but has nothing to execute
- **Impact:** Sync, auth, and data mapping regressions ship silently to production

### [HIGH — ALL 3] isMinifyEnabled = false in release build
- **File:** `app/build.gradle.kts:31`
- Release APK is trivially decompilable — `BuildConfig.DEVICE_SECRET` is extractable
- No R8 shrinking means larger APK surface for reverse engineering
- **Fix:** `isMinifyEnabled = true` for release build type

### [HIGH — ALL 3] API key stored in plaintext SharedPreferences
- **Files:** `SyncWorker.kt:24`, `BiometricLockManager.kt:10`
- The `"api_key"` credential is stored/read from standard (unencrypted) SharedPreferences
- `androidx.security:security-crypto:1.1.0-alpha06` **is already in dependencies** — unused
- `androidx.datastore:datastore-preferences:1.0.0` **is also already in dependencies** — unused
- **Fix:** Migrate to `EncryptedSharedPreferences` using the already-included security-crypto lib

### [MEDIUM-HIGH — ALL 3] Biometric auth allows weak modalities
- **File:** `BiometricLockManager.kt:46-50`
- No `setAllowedAuthenticators()` call in `PromptInfo.Builder`
- Allows 2D face unlock (weak) in addition to fingerprint (strong)
- No `CryptoObject` binding — auth is software-only gate, no cryptographic enforcement
- **Fix:** Add `setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)`

### [MEDIUM — ALL 3] No pagination in HealthConnectReader
- **File:** `data/HealthConnectReader.kt`
- All `readRecords()` calls are single-shot with no `pageToken` iteration
- Can silently drop records if server returns paginated results
- Worker always fetches full 30-day window every run (expensive)
- **Fix:** Implement pagination loop using `response.nextPageToken`

### [MEDIUM — ALL 3] Logging interceptor active in all build types
- **File:** `ServerApiClient.kt:107-109`
- `HttpLoggingInterceptor(Level.BASIC)` runs in release builds, logging URLs + Authorization headers
- **Corrected:** Gemini/Monica reported Level.BODY but Codex (full repo) confirms it's Level.BASIC
- BASIC still logs `Authorization: Bearer <api_key>` header in production logcat
- **Fix:** Wrap in `if (BuildConfig.DEBUG)` check

### [MEDIUM — ALL 3] No dependency injection / manual wiring
- **Files:** `SyncWorker.kt:25`, UI ViewModels
- `ApiService(Config.SERVER_URL, Config.DEVICE_SECRET, apiKey)` constructed inline in Worker
- Makes unit testing impossible (can't swap implementations)
- All components share one `"health_sync"` SharedPreferences file
- **Fix:** Introduce Hilt or Koin (already have the architectural intent, needs execution)

---

## Provider-Unique Findings

### Codex-Only (trusted — full repo access)
- **[HIGH]** `ApiService.kt:63,81,99` — `response.body()!!` unsafe null assertion after `isSuccessful` check. Server can return 200 OK with empty body (e.g., 204 responses, partial proxy errors). This is a production crash risk.
- **[LOW]** README documents minSdk as "API 28+" but `build.gradle.kts` sets minSdk = 34. Stale docs.
- **[POSITIVE]** `SyncWorker` already has `Result.retry()` (not `Result.failure()`) + `BackoffPolicy.EXPONENTIAL` — Gemini/Monica were wrong about missing retry logic (they worked from incomplete prompt description)
- **[POSITIVE]** Logging confirmed as `Level.BASIC` not `Level.BODY` — Gemini/Monica reported incorrectly

### Gemini-Only (single source — verify manually)
- No Gemini-exclusive findings not already covered above

### Monica-Only (single source — verify manually)
- Hard-coded `SERVER_URL` may cause dev/prod environment brittleness (low)
- Biometric lock described as "security theater" — no new finding beyond the shared biometric weakness

---

## Strengths (Confirmed by 2+ Providers)

- **Modern stack:** Kotlin + Jetpack Compose + WorkManager + CoroutineWorker — correct primitives
- **WorkManager retry:** Already has `BackoffPolicy.EXPONENTIAL` + network constraints configured (Codex confirmed)
- **Package separation:** `data/`, `service/`, `security/`, `widget/`, `ui/` — clean modular layout
- **Biometric re-auth intent:** 5-minute inactivity lock in `MainActivity.kt` is good UX for health data
- **Secret injection pattern:** `DEVICE_SECRET` via `local.properties` + `BuildConfig` keeps it out of source control
- **Health Connect scope:** Explicit `requiredPermissions` set in `HealthConnectReader` — good permission hygiene

---

## Risks

1. **Data leakage:** Authorization tokens appear in production logcat (Level.BASIC logs headers)
2. **APK decompilation:** `isMinifyEnabled = false` exposes `BuildConfig.DEVICE_SECRET` to anyone who downloads the APK
3. **Crash risk:** `response.body()!!` in `ApiService.kt` will crash on 200 OK + empty body (uncommon but real)
4. **Weak biometric:** 2D face unlock can satisfy the biometric gate on health data
5. **Silent data drop:** No pagination means large Health Connect datasets may be truncated silently
6. **No regression safety:** Zero tests means any refactor is a production gamble

---

## Action Items

See `llm-audit-action-plan.md` for prioritized implementation list.
