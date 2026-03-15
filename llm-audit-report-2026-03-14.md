# LLM Audit Report — Apex Android App
**Date:** 2026-03-14
**Auditors:** GPT 5.4 (5/10) · Gemini 2.5 Pro (8/10) · Claude Opus 4.6 (7.5/10)
**Codebase:** 33 Kotlin files, ~7,300 lines
**Previous audit score:** 6.5/10 (2026-03-09, Gemini CLI)

**Model availability notes:**
- `codex-5.3` not available on ChatGPT accounts — used GPT 5.4 (default codex model)
- `gemini-3.1-pro` not found in Google API — used Gemini 2.5 Pro (latest available)

---

## Consensus Score: 7/10

Three-model average: (5 + 8 + 7.5) / 3 = **6.8 → 7/10**

GPT 5.4 was the harshest, finding genuine privacy/security issues the other two
missed. Gemini 2.5 Pro was the most generous, praising the architecture but missing
the "Clear All Data" gap. The remaining findings are a mix of security hardening,
reliability edge cases, and architectural improvements.

---

## FIXES ALREADY APPLIED THIS SESSION

| ID | Fix | Status |
|----|-----|--------|
| H-1 (Opus) | SharedOkHttpClient for ServerApiClient — singleton with host-aware rebuild | ✅ DONE |
| M-2 (Opus) | TrendsViewModel.init calls loadDataForCurrentTab() not loadBp() | ✅ DONE |
| M-6 (Opus) | Settings ping uses /api/version instead of /api/bp?days=1 | ✅ DONE |

Build verified: `assembleDebug` passes.

---

## HIGH Severity — Consensus Findings

### H-1 [SEC] "Clear All Data" doesn't clear Room queue or cancel WorkManager
**Source:** GPT 5.4 (A-03) — unique finding, not caught by other auditors
**Files:** SettingsScreen.kt, SyncQueueDao.kt, SyncWorker.kt
**Issue:** The "Clear All Data" dialog wipes SharedPreferences and SecurePrefs, but
leaves the Room-backed sync queue intact and does not cancel scheduled WorkManager
jobs. Records the user believes were deleted can still be uploaded on next sync after
re-entering credentials. Background sync keeps running with stale credentials.
**Impact:** Privacy violation — user data survives a destructive "clear all" action.
**Fix:** Add `deleteAll()` to SyncQueueDao. In the clear flow: (1) cancel all WorkManager
work, (2) clear Room queue, (3) wipe SecurePrefs, (4) wipe SharedPrefs, (5) update widget.

### H-2 [SEC] Release credentials baked into APK via BuildConfig
**Source:** GPT 5.4 (A-01), Gemini (SEC-1) — confirmed by 2/3 auditors
**Files:** build.gradle.kts:32, Config.kt:11, MainActivity.kt:97
**Issue:** Both `DEVICE_SECRET` and `API_KEY` are compiled into `BuildConfig`. Anyone
with the APK can decompile and extract both credentials. The QR onboarding flow provides
a secure alternative, but the baked-in fallback undermines it.
**Impact:** APK reverse engineering exposes credentials that can be used to impersonate
the device or authenticate to the server.
**Verdict:** ACKNOWLEDGED RISK for a single-user personal health app. The credentials
are unique to one device/user. For a multi-user deployment, this would need to be
replaced with per-device provisioning.

### H-3 [SEC] ApiService hardcodes cert pin hostname — breaks QR-configured servers
**Source:** GPT 5.4 (A-07) — unique finding
**Files:** ApiService.kt:65-81
**Issue:** `ApiService` hardcodes cert pins for `tyler-health.duckdns.org`. If QR
onboarding points to a different server, all sync POSTs will fail at TLS pinning time.
`ServerApiClient` (read client) correctly derives the host from the runtime URL.
**Fix:** Apply the same dynamic host extraction in `ApiService` as already done in
`ServerApiClient`. (Already partially addressed by H-1 fix for ServerApiClient.)

---

## MEDIUM Severity — Consensus Findings

### M-1 [ARCH] HC deletions ignored — server state drifts permanently
**Source:** GPT 5.4 (A-06) — unique finding
**Files:** HealthConnectReader.kt:249, 276, 321
**Issue:** Incremental readers only process `UpsertionChange`. If a user deletes records
in Health Connect, Apex never sends a tombstone — server retains stale data forever.
**Fix:** Process `DeletionChange` events and send delete requests, or use authoritative
replace semantics on full syncs.

### M-2 [REL] Cached ViewModel clients don't invalidate on QR credential change
**Source:** GPT 5.4 (A-04) — unique finding
**Files:** ActivityViewModel.kt:59, TrendsViewModel.kt:81, SettingsViewModel.kt:49
**Issue:** After QR onboarding, existing ViewModels keep using the old API key/host
until they're destroyed. ViewModels survive nav graph back-stack saves.
**Fix:** Centralize client creation in a repository that observes config state changes
and rebuilds lazily on next access.

### M-3 [REL] Manual sync can race the periodic worker
**Source:** GPT 5.4 (A-05)
**Issue:** `runOnce()` deduplicates one-time work but doesn't prevent concurrent
execution with the periodic worker. Both could upload the same queued records.
**DashboardViewModel** checks `periodicRunning` state, but SettingsScreen doesn't.
**Fix:** Use a single unique work name for all sync operations, or add an in-worker
mutex via SharedPreferences.

### M-4 [PERF] Sequential Health Connect reads
**Source:** Opus (M-5), Gemini (PERF-1) — confirmed by 2/3 auditors
**Files:** HealthConnectReader.kt:116-179, SyncWorker.kt:79-163
**Issue:** Body measurements read Weight/BodyFat/LeanMass sequentially. SyncWorker
reads BP/Sleep/Body/HRV sequentially. All are independent.
**Fix:** Use `coroutineScope { async {} }` for parallel reads.

---

## LOW Severity — Consensus Findings

### L-1 [UX] BP anomaly alerts repeat for the same reading
**Source:** GPT 5.4 (A-09) — unique finding
**File:** SyncWorker.kt:195-204
**Issue:** Every sync checks if latest BP ≥ 140/90 and posts a notification. The same
unchanged reading triggers repeated alerts across sync cycles.
**Fix:** Store the last alerted measuredAt timestamp; only notify on new readings.

### L-2 [UX] Widget bypasses biometric lock
**Source:** GPT 5.4 (A-08) — unique finding
**File:** HealthGlanceWidget.kt
**Issue:** Widget shows BP/sleep/HRV values even when biometric lock is enabled.
User can "lock" the app but health data remains visible on the home screen.
**Fix:** Check biometric-enabled state in widget; redact values when locked.

### L-3 [REL] fallbackToDestructiveMigration() risks queue data loss on upgrade
**Source:** Gemini (REL-1) — unique finding
**File:** ApexDatabase.kt:33
**Issue:** If a schema change occurs, all queued records are wiped.
**Fix:** Add explicit Migration objects before incrementing the DB version.

### L-4 [ARCH] Duplicate cert pin declarations in 3 places
**Source:** Opus (L-3), Gemini (SEC-2) — confirmed by 2/3 auditors
**Files:** ApiService.kt, ServerApiClient.kt, SettingsViewModel.kt
**Fix:** Extract to Config constants or shared factory.

### L-5 [UX] recreate() on biometric re-auth destroys Compose state
**Source:** Gemini (UX-1), earlier Gemini run (M-3) — confirmed by 2/3 auditors
**File:** MainActivity.kt:160-162
**Fix:** Use state-driven approach instead of recreate().

### L-6 [UX] No PIN/device credential fallback for biometric lockout
**Source:** Earlier Gemini run (L-4)
**Fix:** Add `DEVICE_CREDENTIAL` to allowed authenticators.

---

## Cross-Reference: False Positives

### "Change token saved before server ACK → data loss"
**Raised by:** Gemini (both runs), GPT 5.4 (A-02 variant)
**Verdict: FALSE POSITIVE.** Records are durably queued in Room before the token
advances. If Phase 2 fails, records stay in Room and retry on next sync. Room's
hash-based dedup prevents double-queuing on re-read. The token advancing is safe.

GPT 5.4's variant (A-02) about the gap between full read and token acquisition is
theoretically valid but practically impossible — HC records written during the
sub-second gap between `readBloodPressure()` completing and `getBpChangesToken()`
would need to be inserted by another app in that exact instant.

### "Lack of Dependency Injection"
**Raised by:** Gemini (ARCH-1, rated HIGH)
**Verdict: ACKNOWLEDGED but NOT HIGH.** Hilt/Koin is best practice but adds
significant complexity. The current manual DI with `@VisibleForTesting` constructors
is adequate for a single-developer app with 14 test files.

---

## Priority Action Plan

| # | ID | Effort | Finding | Confirmed By |
|---|-----|--------|---------|-------------|
| 1 | H-1 | Medium | Clear All Data incomplete | GPT 5.4 only |
| 2 | H-3 | Small | ApiService hardcoded cert pin host | GPT 5.4 only |
| 3 | L-1 | Trivial | BP alert dedup | GPT 5.4 only |
| 4 | L-2 | Small | Widget biometric bypass | GPT 5.4 only |
| 5 | M-1 | Large | HC deletion sync | GPT 5.4 only |
| 6 | M-4 | Small | Parallel HC reads | Opus + Gemini |
| 7 | L-3 | Small | Room migration safety | Gemini only |
