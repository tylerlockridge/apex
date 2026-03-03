# Feature: Business Rules & Edge Cases

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Documents the authoritative business rules baked into Apex's implementation — data source preferences, time windows, matching tolerances, security thresholds, and transmission requirements. Also documents which edge cases are handled and which are known gaps.

**What it does NOT do:**
- Does not document server-side rules (server enforces its own deduplication and schema)
- Does not cover test coverage for these rules (see `10-testing-strategy.md`)

---

## Business Rules

### Data Source Preference

| Rule | Detail |
|------|--------|
| Oura Ring preferred for sleep | `HealthConnectReader` filters for `com.ouraring.oura` package first; falls back to other sources if absent |
| Oura Ring preferred for HRV | RMSSD overnight resting data from `com.ouraring.oura` preferred over spot checks from other sources |

### Sync Window

| Rule | Detail |
|------|--------|
| 30-day rolling window | `SyncWorker` reads the last 30 days from Health Connect on every sync run |
| Not incremental | Change tokens are not used; all 30 days are read regardless of prior syncs |

### Body Measurement Matching

| Rule | Detail |
|------|--------|
| ±1 hour match window | Weight records are matched with body fat and lean body mass records within a 1-hour time window |
| Weight-only fallback | If no body fat or lean mass record falls within the ±1h window, a weight-only entry is created |

### Biometric Authentication

| Rule | Detail |
|------|--------|
| BIOMETRIC_STRONG enforced | Class 1 (weak) biometrics rejected; fingerprint and face unlock only |
| 5-minute inactivity threshold | `SystemClock.elapsedRealtime()` checked in `onResume()`; if elapsed time >= 5 minutes since last interaction, lock screen is shown |
| Exact threshold | Uses `>=` comparison — exactly 5 minutes triggers re-auth (not `>`) |

### Timestamps

| Rule | Detail |
|------|--------|
| Storage format | All timestamps stored and transmitted as ISO 8601 strings via `Instant.toString()` |
| Display format | Displayed in `ZoneId.systemDefault()` (device local timezone) |

### Authentication Requirements

| Rule | Detail |
|------|--------|
| Dual credential requirement | Every sync payload requires BOTH `Authorization: Bearer <api_key>` header AND `device_secret` in request body |
| Server-side deduplication key | Composite key: `measured_at + data_type + source_device` |

### Permission Requirements

| Rule | Detail |
|------|--------|
| BP + Sleep are mandatory | Sync proceeds only if these two permissions are granted |
| HRV + Body are optional | Sync proceeds without them; affected data types return empty results silently |

---

## Edge Cases Handled

| Scenario | Behavior |
|----------|---------|
| Empty data from server | Charts show "No data available"; dashboard cards show "—"; all prefs reads check `contains()` before accessing |
| Network failure during sync | `SyncWorker` catches all exceptions → `Result.retry()` → WorkManager reschedules with exponential backoff (1-min initial) |
| Server ping timeout | 4-second connect timeout + 4-second read timeout in SettingsScreen ping; exception caught → red status indicator shown |
| Null response body on 200 OK | Throws exception: "Empty sync response body" |
| Partial sync failure | Each data type syncs independently; BP success + Sleep failure leaves inconsistent state (no rollback) |
| Biometric disabled | Lock screen composable is skipped entirely in MainActivity; no prompt shown |
| Missing SharedPreferences key | All reads call `contains()` before asserting; returns null if key absent |

---

## Edge Cases NOT Handled (Known Gaps)

| Scenario | Current Behavior | Risk |
|----------|-----------------|------|
| Invalid ISO 8601 string from server | `Instant.parse()` throws uncaught exception | App crash |
| Partial sync rollback | No mechanism; state becomes inconsistent | Data integrity |
| API key rotation | No refresh flow; must re-scan QR (stub) | Auth failure until manual re-entry |
| Widget stale data | No immediate update after sync | Displays outdated values up to ~1h |
| Server schema version mismatch | Not validated; app may silently malfunction | Silent data errors |

---

## Status

| Rule / Edge Case | Status | Notes |
|-----------------|--------|-------|
| Oura Ring preference (sleep + HRV) | ✅ PASS | Package filter applied in HealthConnectReader |
| 30-day rolling window | ✅ PASS | Consistent across all data types |
| ±1h body measurement matching | ✅ PASS | Weight-only fallback implemented |
| BIOMETRIC_STRONG enforcement | ✅ PASS | Class 1 biometrics rejected |
| 5-minute inactivity re-auth | ✅ PASS | elapsedRealtime() >= check in onResume() |
| ISO 8601 timestamps | ✅ PASS | Instant.toString() + ZoneId.systemDefault() display |
| Dual credential requirement | ✅ PASS | Bearer + device_secret on all sync POSTs |
| Empty data handling (charts + cards) | ✅ PASS | All charts + prefs reads handle missing data |
| Network failure retry | ✅ PASS | Result.retry() + WorkManager backoff |
| Server ping timeout | ✅ PASS | 4s + 4s; exception → red indicator |
| Null body exception | ✅ PASS | Explicit throw on 200 + null body |
| Partial sync rollback | ❌ FAIL | No transaction semantics |
| Invalid ISO 8601 crash | ❌ FAIL | Instant.parse() unguarded |
| API key refresh | ❌ FAIL | No refresh flow |
| Widget stale data | ⚠️ WARN | Stale until Glance interval (~1h) |
| Server schema validation | ❌ FAIL | Version displayed but not validated |
