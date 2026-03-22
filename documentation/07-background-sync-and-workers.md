# Feature: Background Sync & Workers

*Created: 2026-03-02 | Updated: 2026-03-22 | Project: Apex*

---

## Feature Overview

**What it does:**
Apex uses WorkManager for recurring health sync and weekly summary notifications.

The main worker is `SyncWorker`, which:
- reads Health Connect through `HealthDataProvider`
- inserts records into the Room queue
- flushes queued records to the server
- updates summary prefs, sync history, notifications, and the home-screen widget

The secondary worker is `WeeklySummaryWorker`, which posts a weekly local summary notification from cached prefs.

**What it does NOT do:**
- Does not maintain a durable local cache for trends/workouts server reads
- Does not make body measurements incremental yet
- Does not provide atomic all-data-type sync semantics

---

## Worker Inventory

| Worker | Purpose | Schedule |
|--------|---------|----------|
| `SyncWorker` | Health Connect read + queue flush + summary/widget side effects | periodic and one-time |
| `WeeklySummaryWorker` | Sunday 9 AM local health summary notification | periodic |

---

## SyncWorker

### Configuration

| Setting | Value |
|---------|-------|
| Worker type | `CoroutineWorker` |
| Periodic interval | 15 minutes |
| Network constraint | `CONNECTED` required |
| Backoff policy | exponential |
| Backoff initial delay | 1 minute |
| Queue cap | 5,000 rows |
| Upload batch size | 200 records |

### Trigger sources

| Trigger | Entry point |
|---------|------------|
| Periodic background sync | `SyncWorker.schedule()` when auto-sync is enabled |
| Manual full sync | `SyncWorker.runOnce()` |
| Manual filtered sync | `SyncWorker.runOnce(context, dataTypeFilter)` |
| Dashboard quick actions | one-time filtered work |
| Settings "Sync All Now" | one-time full work |

Periodic work name: `health_sync_periodic`  
Manual unique work name: `health_sync_once` or `sync_once_<dataType>`

### Execution stages

1. Validate API key
2. Read Health Connect data
3. Insert normalized records into the Room queue
4. Enforce queue-size cap
5. Flush queued records to server in batches by data type
6. Update summary prefs on per-type success
7. Update `last_sync` only if the run had no failures
8. Record rolling `sync_history`
9. Refresh widget if a widget is pinned

### Incremental read behavior

| Data type | Mode |
|-----------|------|
| Blood pressure | change-token incremental |
| Sleep | change-token incremental |
| HRV | change-token incremental |
| Body | full 30-day read |

If a token is invalid/expired, the worker clears it and does a full read in the same run.

### Result semantics

| Outcome | WorkManager result |
|---------|--------------------|
| No failures | `Result.success()` |
| Any transient failure | `Result.retry()` |
| Any permanent failure | `Result.failure()` |
| Top-level unexpected exception | `Result.retry()` |

Permanent failures are currently driven by non-retryable sync responses such as bad auth or malformed requests.

---

## WeeklySummaryWorker

### Configuration

| Setting | Value |
|---------|-------|
| Worker type | `CoroutineWorker` |
| Cadence | every 7 days |
| Anchor time | next Sunday at 9:00 AM |
| Network constraint | none |
| Data source | `health_sync` SharedPreferences only |

### Behavior

- exits immediately if the app has never synced
- builds a local summary from cached BP, sleep, and HRV
- posts a local notification with a private body and public lock-screen-safe version

This worker is scheduled in `HealthSyncApp.onCreate()`.

---

## WorkManager Initialization

`HealthSyncApp` implements `Configuration.Provider` and provides the app's WorkManager configuration. On app startup it also:
- creates notification channels
- schedules `WeeklySummaryWorker`

This means background infrastructure is initialized from the application layer, not lazily from a single screen.

---

## Related Side Effects

| Side effect | Where it happens |
|-------------|------------------|
| BP anomaly notification | `SyncWorker` after successful BP upload |
| Widget refresh | `SyncWorker` after run completion if widget exists |
| Weekly summary notification | `WeeklySummaryWorker` |
| Summary cache updates | `SyncWorker` per data type |
| Sync history updates | `SyncWorker` every run |

---

## Readiness and Training-Load Integration

The readiness engine (`ReadinessEngine`) does not run inside `SyncWorker`. It is computed on-demand by `DashboardViewModel` and `GeneratedRoutineViewModel` through `ReadinessPayloadBuilder`.

However, readiness depends on `SyncWorker` indirectly:

1. **Health input freshness:** The readiness engine classifies each input's staleness by comparing the `lastUpdatedAt` timestamp (stored in `health_sync` SharedPreferences by `SyncWorker` after successful uploads) against the current time. Inputs older than 12 hours are degraded; older than 24 hours are excluded. If `SyncWorker` has not run recently, readiness inputs become stale and the aggregate score degrades or falls back to "Sync to update readiness."

2. **Training-load input:** The training-load readiness input comes from the server's `GET /api/workouts/progression/summary` endpoint, not from `SyncWorker`. `DashboardViewModel` and `ReadinessPayloadBuilder` fetch this via a `ServerApiClient` call at load time. The training-load weight is activated (minimum 0.10) only when the server returns a valid `trainingLoadScore`. If the server is unreachable or has no cached Hevy workout history, training load is simply excluded from the readiness computation.

3. **Provider seam:** `SyncWorker` reads Health Connect through the `HealthDataProvider` interface (Package 0B). The readiness engine does not read Health Connect directly — it consumes the summary values that `SyncWorker` wrote to `health_sync` SharedPreferences.

---

## Known Gaps

| Gap | Impact |
|-----|--------|
| Body measurements still full-read | Higher HC read volume than the other data types |
| No durable inbound cache | Trends/activity screens still depend on live server availability |
| Partial success is possible | Summary cache may reflect some fresh values even when `last_sync` does not advance |
| Settings-triggered one-time sync is less observed than dashboard-triggered sync | UX consistency gap, not a protocol gap |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Periodic 15-minute sync | ✅ PASS | WorkManager unique periodic work |
| Manual sync | ✅ PASS | full and filtered one-time paths |
| Queue-backed retry safety | ✅ PASS | records survive transient failures |
| Permanent failure handling | ✅ PASS | no longer "always retry" |
| Weekly summary worker | ✅ PASS | local-notification only; no network required |
| Widget refresh after sync | ✅ PASS | gated on pinned widget presence |
| Readiness freshness dependency | ✅ PASS | SyncWorker writes timestamps that ReadinessEngine uses for staleness classification |
| Full incremental coverage | ❌ GAP | body still full-read |
