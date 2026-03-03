# Feature: Background Sync & Workers

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
`SyncWorker.kt` is a coroutine-based `CoroutineWorker` managed by WorkManager. It coordinates Health Connect reads and server sync calls. It runs on a 15-minute periodic schedule or on-demand (manual sync). WorkManager is initialized on-demand via `HealthSyncApp.kt`.

**What it does NOT do:**
- Does not use incremental reads (full 30-day window every run)
- Does not maintain an offline queue (no Room database)
- Does not distinguish sync failure from retriable failure — always retries

---

## Worker Configuration

| Setting | Value |
|---------|-------|
| Worker type | `CoroutineWorker` |
| Periodic interval | 15 minutes |
| Network constraint | `NetworkType.CONNECTED` required |
| Backoff policy | `BackoffPolicy.EXPONENTIAL` |
| Backoff initial delay | 1 minute |
| On exception | Always returns `Result.retry()` — never `Result.failure()` |

---

## Trigger Sources

| Trigger | Entry Point |
|---------|------------|
| Periodic background sync | Auto-enabled when "Auto-sync" toggle is ON in Settings |
| Manual sync (FAB) | Dashboard pulsing FAB calls `SyncWorker.runOnce()` |
| Manual sync (Settings) | Settings "Sync Now" button calls `SyncWorker.runOnce()` |
| Pull-to-refresh | Dashboard and Activity screens call `SyncWorker.runOnce()` |

Periodic job is cancelled via `SyncWorker.cancel()` when the auto-sync toggle is turned OFF in Settings.

---

## Sync Execution Order

1. Check network constraint (WorkManager enforces `CONNECTED` before launch)
2. Read Health Connect — 30-day window for all permitted data types
3. POST blood pressure to server
4. POST sleep to server
5. POST body measurements to server
6. Persist last values per data type to SharedPreferences
7. Update `last_sync` timestamp in SharedPreferences

Each data type syncs independently. A failure on step 3 does not prevent step 4 from running (no transaction semantics).

---

## WorkManager Initialization

`HealthSyncApp.kt` implements `Configuration.Provider` for on-demand WorkManager initialization. The default `WorkManagerInitializer` ContentProvider startup was removed to allow this. WorkManager initializes only when first needed, not at app start.

---

## Known Gaps

| Gap | Impact |
|-----|--------|
| Always retries on exception | WorkManager will retry indefinitely on persistent failures (e.g., bad API key). Never calls `Result.failure()` to stop the job. |
| No offline queue | Syncs that fail due to network unavailability are retried by WorkManager but data is not queued; missed data windows are not recovered. |
| Full 30-day window | High data transfer on every run; HC change token API would allow incremental reads. |
| Partial sync inconsistency | BP success + Sleep failure leaves server and app prefs in inconsistent state; no rollback. |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Periodic 15-min sync | ✅ PASS | WorkManager periodic job |
| On-demand manual sync | ✅ PASS | runOnce() from FAB + Settings + PTR |
| Network constraint | ✅ PASS | CONNECTED required |
| Exponential backoff | ✅ PASS | 1-minute initial, exponential |
| SharedPrefs persistence | ✅ PASS | Last values + timestamp on success |
| On-demand WorkManager init | ✅ PASS | Configuration.Provider in HealthSyncApp |
| Result.failure() on permanent errors | ❌ FAIL | Always returns Result.retry() |
| Offline queue | ❌ FAIL | No Room database; no queuing |
| Incremental sync | ❌ FAIL | Full 30-day window every run |
| Partial sync rollback | ❌ FAIL | No mechanism |
