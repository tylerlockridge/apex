# Feature: Testing Strategy

*Created: 2026-03-02 | Updated: 2026-03-19 | Project: Apex*

---

## Feature Overview

**What it does:**
Documents the current unit-test surface, test tooling, and the highest-value remaining gaps.

**What it does NOT do:**
- Does not claim full device/instrumentation coverage
- Does not claim end-to-end Health Connect validation
- Does not replace the latest CI/test run output

---

## Current Test Inventory

As of 2026-03-19, Apex has **12 unit-test files** under `app/src/test/java/com/healthplatform/sync` with **98 `@Test` methods**. No Kotlin test files currently exist under `app/src/androidTest`.

| File | Focus | `@Test` count |
|------|-------|---------------|
| `HealthConnectReaderTest.kt` | HC reader behavior | 12 |
| `SyncQueueDaoTest.kt` | Room queue semantics | 6 |
| `HealthConnectProviderTest.kt` | Package 0B provider adapter | 13 |
| `BiometricLockManagerTest.kt` | biometric enable/auth gating | 6 |
| `SecurePrefsTest.kt` | encrypted prefs behavior | 8 |
| `ApiServiceTest.kt` | outbound sync client | 9 |
| `SyncWorkerTest.kt` | queue/sync worker behavior | 13 |
| `ActivityViewModelTest.kt` | workout/activity state | 5 |
| `DashboardViewModelTest.kt` | dashboard state + readiness heuristic | 10 |
| `ErrorMessageTest.kt` | friendly error mapping | 4 |
| `SettingsViewModelTest.kt` | connectivity/permission/version checks | 5 |
| `TrendsViewModelTest.kt` | trends state flows | 7 |

---

## Test Infrastructure

| Library | Purpose |
|---------|---------|
| JUnit 4 | base test runner |
| Robolectric | Android framework/unit-style tests |
| MockK | mocking |
| MockWebServer | HTTP client tests |
| Turbine | `StateFlow` / coroutine flow assertions |
| `kotlinx-coroutines-test` | coroutine scheduling |
| WorkManager test utilities | worker tests |
| Room testing | DAO/database tests |

Compose UI test dependencies are present, but there are no committed Compose instrumentation tests yet.

---

## What Is Covered Well

| Area | Current strength |
|------|------------------|
| Outbound sync client | `ApiService` request/response behavior |
| Queue mechanics | dedupe, DAO behavior, delete-on-success semantics |
| Sync worker core paths | success/failure handling and provider-based injection |
| Package 0B seam | `HealthConnectProvider` adapter delegation |
| Security helpers | `SecurePrefs`, `BiometricLockManager` |
| ViewModel basics | dashboard/activity/trends/settings state behavior |

---

## Remaining Gaps

| Gap | Why it matters |
|-----|----------------|
| No `androidTest` / Compose instrumentation suite | UI rendering, nav flows, and device-only behavior are not covered by tests in this repo |
| No end-to-end Health Connect/device validation | unit tests cannot prove real HC permissions, background behavior, or device-specific data availability |
| Widget behavior is not directly tested | lock-screen masking and Glance rendering remain effectively manual |
| Weekly summary worker has no dedicated test file | notification cadence/content is only indirectly protected |
| Future Phase 2 readiness engine tests do not exist yet | current dashboard heuristic is tested, but the planned configurable engine is not implemented yet |

---

## Recommended Next Test Priorities

1. **Phase 2 readiness engine tests**
   - fresh/degraded/excluded inputs
   - HRV disabled path
   - all-inputs-stale empty state
   - dashboard viewmodel integration
2. **Widget + weekly summary worker tests**
   - widget masking on lock screen
   - weekly summary notification content gating
3. **Compose instrumentation**
   - dashboard rendering
   - QR onboarding flow
   - lock/unlock flow
   - navigation smoke tests
4. **Device-level validation harness**
   - Health Connect permissions
   - real HC data freshness
   - background sync cadence

---

## Current Risk Posture

| Area | Status | Notes |
|------|--------|-------|
| Unit-test surface | ✅ SOLID | far stronger than the early-March docs claimed |
| Worker coverage | ✅ SOLID | `SyncWorkerTest.kt` exists and is meaningful |
| ViewModel coverage | ✅ PARTIAL | major viewmodels have tests; UI rendering still does not |
| Instrumentation/UI coverage | ❌ GAP | no `androidTest` Kotlin files found |
| Device-specific validation | ❌ GAP | still manual by nature |
