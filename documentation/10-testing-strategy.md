# Feature: Testing Strategy

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Documents the current test coverage, tooling, and priority roadmap for expanding tests. Apex currently has 10 tests across 2 files, covering error message formatting and `ApiService` sync calls.

**What it does NOT do:**
- Does not have tests for Health Connect reader logic
- Does not have tests for SyncWorker behavior
- Does not have tests for any ViewModel or UI screen

---

## Current Test Inventory

### ErrorMessageTest.kt (4 tests)

Tests the `toFriendlyMessage()` extension function on network exceptions.

| Test Case | Covers |
|-----------|--------|
| Read timeout | `SocketTimeoutException` on read |
| Connection refused | `ConnectException` |
| Connect timeout | `SocketTimeoutException` on connect |
| Unknown exception | Fallback message |

### ApiServiceTest.kt (6 tests)

Tests `syncBloodPressure()` via `MockWebServer`.

| Test Case | Covers |
|-----------|--------|
| Success (200) | Happy path sync |
| 401 response | Auth failure error |
| 500 response | Server error |
| Null body on 200 | "Empty sync response body" exception |
| syncSleep() success | Sleep POST happy path |
| Auth header verification | Confirms `Authorization: Bearer` sent |

---

## Test Infrastructure

| Library | Version | Purpose |
|---------|---------|---------|
| JUnit 4 | — | Test runner |
| MockK | — | Kotlin mock library |
| MockWebServer (OkHttp) | — | HTTP server for Retrofit tests |
| Compose Testing | — | UI instrumentation (dependency present; no tests written) |

---

## Estimated Coverage

~5% — only error mapping and basic `ApiService` POST paths are covered. All application logic, state management, and UI are untested.

---

## Critical Gaps (0 tests)

| Area | Why Critical | Specific Test Targets |
|------|-------------|----------------------|
| `HealthConnectReader` | Core data collection; pagination bugs could silently drop records | Pagination loop termination, Oura Ring filter logic, empty result handling, ±1h body match window |
| `SyncWorker` | Retry logic is broken (always retries); network constraint behavior untested | Exception → Result.retry() path, successful persist to prefs, data type independence |
| ViewModels (4) | All app state flows are untested | State transitions, server fetch error handling, empty data state, loading state |
| UI Screens | No rendering, navigation, or interaction tests | Dashboard card rendering, pull-to-refresh trigger, tab navigation, lock screen |
| `BiometricLockManager` | Auth bypass risk if prompt callback is wired incorrectly | Success callback, failure callback, inactivity timer trigger |
| `SecurePrefs` | Encryption and migration logic untested | First-launch migration from plain prefs, read/write round-trip, missing key returns null |

---

## Recommended Test Priority

Priority order for new test work:

1. **`HealthConnectReader`** — mock HC API, test pagination loop termination, Oura Ring preference logic, body measurement ±1h match window
2. **`SyncWorker`** — mock `HealthConnectReader` + `ApiService`, verify 30-day window sent, verify backoff behavior, verify prefs written on success
3. **ViewModels** — state transitions, error handling, empty data, loading indicator states
4. **`ApiService` expansion** — HMAC signing (once implemented), timeout scenarios, syncBodyMeasurements()
5. **UI Instrumentation** — Dashboard rendering, pull-to-refresh, bottom nav tab switching, lock screen → unlock flow

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| ErrorMessageTest (4 tests) | ✅ PASS | All passing |
| ApiServiceTest (6 tests) | ✅ PASS | All passing |
| HealthConnectReader tests | ❌ FAIL | 0 tests; pagination/Oura logic untested |
| SyncWorker tests | ❌ FAIL | 0 tests; retry behavior broken + untested |
| ViewModel tests | ❌ FAIL | 0 tests across all 4 ViewModels |
| UI / Compose tests | ❌ FAIL | 0 tests; dependency present but unused |
| BiometricLockManager tests | ❌ FAIL | 0 tests |
| SecurePrefs tests | ❌ FAIL | 0 tests |
| Overall coverage | ⚠️ WARN | ~5% estimated |
