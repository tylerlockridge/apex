# Feature: Data Sync Protocol

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Defines the outbound sync (Apex → server) and inbound read (server → Apex UI) HTTP protocols. `ApiService.kt` handles Health Connect data upload via POST. `ServerApiClient.kt` handles display data retrieval via GET.

**What it does NOT do:**
- Does not deduplicate records client-side before sending
- Does not implement incremental sync (no change tokens or cursor tracking)
- Does not maintain an offline queue (sync fails if network is unavailable)
- Does not sync HRV data to the server despite reading it from Health Connect

---

## Outbound Sync — ApiService.kt

### Endpoints

| Method | Path | Data Type |
|--------|------|-----------|
| POST | `/api/sync/health-connect` | Blood pressure |
| POST | `/api/sync/health-connect` | Sleep |
| POST | `/api/sync/health-connect` | Body measurements |

### Request Format

```json
{
  "device_secret": "<device_secret from SecurePrefs>",
  "data_type": "blood_pressure | sleep | body_measurements",
  "records": [ ... ]
}
```

### Authentication

All outbound requests include:
- `Authorization: Bearer <api_key>` header (from SecurePrefs)
- `device_secret` field in the request body

---

## Inbound Read — ServerApiClient.kt

### Endpoints

| Method | Path | Parameters | Purpose |
|--------|------|-----------|---------|
| GET | `/api/bp` | `days=30` | Blood pressure history |
| GET | `/api/sleep` | `days=30` | Sleep history |
| GET | `/api/body` | `days=30` | Body measurements history |
| GET | `/api/workouts` | `limit`, `offset` | Paginated workout list |
| GET | `/api/workouts/stats/summary` | `days=30` | Workout aggregate stats |

---

## Error Handling

| Condition | Behavior |
|-----------|---------|
| HTTP 401 | Returns error: "Sync failed: 401" |
| HTTP 500 | Mapped to friendly error string |
| 200 OK with null body | Throws "Empty sync response body" |
| Network exception | Mapped via `toFriendlyMessage()` extension |
| Offline / no network | SyncWorker catches exception → `Result.retry()` → WorkManager reschedules |

---

## Post-Sync Persistence

After each successful sync run, `SyncWorker` persists last sync values per data type to `SharedPreferences`. Dashboard reads from these prefs for display without making a server call.

Last sync timestamp is also updated in prefs on completion.

---

## Server-Side Deduplication

Apex does not deduplicate records before sending. Deduplication is handled server-side using a composite key:

```
measured_at + data_type + source_device
```

---

## Known Gaps

| Gap | Impact |
|-----|--------|
| No client-side deduplication | Sends duplicate records on overlapping 30-day windows |
| Full 30-day window every run | High data transfer; HC change tokens would reduce this |
| No offline queue | Sync silently fails if network unavailable; WorkManager retries but no record of missed data |
| HRV not synced | HRV read from Health Connect but no POST call sends it |
| Partial sync inconsistency | BP success + Sleep failure leaves server in inconsistent state; no rollback |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Blood pressure sync | ✅ PASS | POST with device_secret + Bearer |
| Sleep sync | ✅ PASS | POST with device_secret + Bearer |
| Body measurements sync | ✅ PASS | POST with device_secret + Bearer |
| Auth header on all requests | ✅ PASS | Bearer token on all outbound |
| Inbound BP/Sleep/Body read | ✅ PASS | GET with days parameter |
| Inbound workouts read | ✅ PASS | Paginated + stats summary |
| Error mapping | ✅ PASS | 401/500/null/network all handled |
| HRV sync | ❌ FAIL | Not wired to POST endpoint |
| Offline queue | ❌ FAIL | No Room; retries only via WorkManager |
| Incremental sync | ❌ FAIL | Full 30-day window every run |
| Partial sync rollback | ❌ FAIL | No mechanism |
