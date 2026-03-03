# Feature: Health Connect Integration

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
`HealthConnectReader.kt` reads 6 health data types from Android Health Connect using paginated queries over a 30-day rolling window. It applies source preference logic (Oura Ring preferred for sleep and HRV) and returns typed data models to `SyncWorker`.

**What it does NOT do:**
- Does not write any data back to Health Connect
- Does not handle partial permission grants with explicit user feedback
- Does not use Health Connect change tokens (full re-read every sync)
- Does not sync HRV data to the server (read only, not forwarded)

---

## Permission Types

| Permission | Type | Required? |
|------------|------|-----------|
| `READ_BLOOD_PRESSURE` | Vital | Required (mandatory) |
| `READ_SLEEP` | Activity | Required (mandatory) |
| `READ_WEIGHT` | Body | Optional (nice-to-have) |
| `READ_BODY_FAT` | Body | Optional (nice-to-have) |
| `READ_LEAN_BODY_MASS` | Body | Optional (nice-to-have) |
| `READ_HEART_RATE_VARIABILITY` | Vital | Optional (nice-to-have) |

`hasAllPermissions()` checks all 6 types. BP + Sleep are enforced as mandatory in sync logic; body/HRV proceed if available and return empty if denied.

`isAvailable()` checks SDK availability before any read is attempted.

Permissions are requested via `requestHealthConnectPermissions()` in the Settings screen, which opens the native Health Connect permission dialog.

---

## Read Methods

### Blood Pressure
- Reads systolic, diastolic, timestamp, and source device name
- 30-day rolling window
- Paginated: loops on `pageToken` until null

### Sleep
- Reads sleep sessions with stage breakdown: deep, REM, light, awake
- **Source preference: Oura Ring** (`com.ouraring.oura` package filter applied first)
- Falls back to other sources if Oura data is absent
- Paginated

### Body Measurements
- Reads weight, body fat percentage, and lean body mass in parallel
- Matches body fat and lean mass records to weight records within a ±1 hour window
- Falls back to weight-only entry if no fat/lean match found within window
- Paginated per data type

### HRV (Heart Rate Variability)
- Reads RMSSD values from Health Connect
- **Source preference: Oura Ring** overnight resting HRV preferred over spot checks
- Paginated
- Data is read and synced to the server via `POST /api/sync/health-connect` with `data_type: "hrv"`

---

## Pagination Pattern

All read methods use the same loop:

```kotlin
var pageToken: String? = null
do {
    val response = client.readRecords(request.copy(pageToken = pageToken))
    results += response.records
    pageToken = response.pageToken
} while (pageToken != null)
```

---

## Known Gaps

- No explicit error surfaced to user when a specific permission type is denied; affected data type returns empty results silently
- No change token usage — always reads full 30-day window regardless of what has already been synced
- No change token usage — always reads full 30-day window regardless of what has already been synced

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Blood pressure read | ✅ PASS | 30-day window, paginated |
| Sleep read with stage breakdown | ✅ PASS | Oura Ring preferred |
| Body measurements (parallel read) | ✅ PASS | ±1h match window |
| HRV read | ✅ PASS | Oura Ring preferred, RMSSD |
| Pagination | ✅ PASS | All 6 read methods loop on pageToken |
| Permission check | ✅ PASS | hasAllPermissions() + isAvailable() |
| Permission denial handling | ⚠️ WARN | Silent empty return; no user feedback |
| HRV sync to server | ✅ PASS | POST to `/api/sync/health-connect` with `data_type: "hrv"` |
| Incremental sync (change tokens) | ❌ FAIL | Full 30-day re-read every run |
