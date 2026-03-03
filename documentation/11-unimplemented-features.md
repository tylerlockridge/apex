# Feature: Unimplemented Features & Known Gaps

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Tracks all features that are called for in the PRD or referenced in code (stub buttons, TODO comments) but have not been implemented. This is the canonical reference for what is missing from Apex.

**What it does NOT do:**
- This document does not track test coverage gaps (see `10-testing-strategy.md`)
- This document does not track security hardening gaps (see `04-security-implementation.md`)

---

## Unimplemented Features

### Data & Sync

| Feature | Gap | Location |
|---------|-----|---------|
| ~~**Offline queue**~~ | ✅ **Implemented 2026-03-02.** Room DB (`ApexDatabase`, `SyncQueueEntity`, `SyncQueueDao`) added. `SyncWorker` now does two-phase sync: HC read → Room queue (IGNORE duplicates); Room queue → server (delete on success, keep on failure for retry). | Closed |
| **Incremental sync (change tokens)** | Full 30-day fetch on every sync run. Health Connect change tokens would allow reading only new records since last sync. | `HealthConnectReader.kt`, `SyncWorker.kt` |
| ~~**HRV sync to server**~~ | ✅ **Implemented 2026-03-02.** `ApiService.syncHrv()` added; `SyncWorker` calls it; `GET /api/hrv/recent` added to `ServerApiClient`; Trends HRV tab added. | Closed |
| ~~**Hevy gym data sync**~~ | ✅ **Implemented 2026-03-03.** `ActivityScreen` + `ActivityViewModel` display workouts from server. `ServerApiClient.triggerHevySync()` added; Activity screen has Sync icon button (header) that POSTs to `/api/sync/hevy/workouts`, then reloads the list. `ActivityViewModelTest` added. | Closed |

### UI & Onboarding

| Feature | Gap | Location |
|---------|-----|---------|
| **QR code onboarding / Re-scan QR** | No CameraX or ML Kit integration. Settings "Re-scan QR" button is wired to a TODO callback that does nothing. Initial onboarding also has no QR flow. | `SettingsScreen.kt` — stub callback |
| ~~**"Clear all data"**~~ | ✅ **Implemented 2026-03-02.** Confirmation `AlertDialog` added; clears `SecurePrefs` (API key, biometric) and all `SharedPreferences` sync state. | Closed |
| ~~**Sync history detail**~~ | ✅ **Implemented 2026-03-02.** `SyncWorker` records last 10 sync events (timestamp + success/fail) to `SharedPreferences` as JSON; `SettingsScreen` displays them with icons. | Closed |
| ~~**Widget auto-update after sync**~~ | ✅ **Implemented 2026-03-02.** `SyncWorker.doWork()` calls `HealthGlanceWidget().updateAll(applicationContext)` after every sync. | Closed |

### Security

| Feature | Gap | Location |
|---------|-----|---------|
| ~~**Certificate pinning**~~ | ✅ **Implemented 2026-03-02.** ISRG Root X1 + Root X2 pins added to both `ApiService` and `ServerApiClient` `OkHttpClient` builders via `CertificatePinner`. | Closed |
| ~~**HMAC request signing**~~ | ✅ **Implemented + hardened 2026-03-02.** HMAC-SHA256 signing with replay protection. Client sends `X-Signature: sha256=HMAC(secret, "${ts}\n${body}")` and `X-Timestamp: <unix seconds>`. Server enforces both headers (no fallback), validates ±5 min timestamp freshness, fails closed if `DEVICE_SECRET` not set. `device_secret` removed from request body entirely. | Closed |

### Server Compatibility

| Feature | Gap | Location |
|---------|-----|---------|
| **Server version validation** | Settings screen displays the server version string fetched from the server, but no compatibility check is performed. If server schema changes, the app may silently malfunction. | `SettingsScreen.kt` / `ServerApiClient.kt` |

---

## Impact Summary

| Severity | Count | Items |
|----------|-------|-------|
| High | 0 | — all closed |
| Medium (feature completeness) | 2 | QR onboarding, Server version validation |
| Low (polish) | 1 | Incremental sync |

---

## Status

| Feature | Status | Notes |
|---------|--------|-------|
| HRV sync to server | ✅ PASS | Implemented 2026-03-02 |
| Certificate pinning | ✅ PASS | ISRG Root X1 + X2, both OkHttp clients |
| "Clear all data" | ✅ PASS | Implemented 2026-03-02 |
| Sync history detail | ✅ PASS | Last 10 events with status icons |
| Widget auto-update after sync | ✅ PASS | GlanceWidget.updateAll() called after each sync |
| Offline queue (Room) | ✅ PASS | Two-phase sync: HC→queue, queue→server; delete on success |
| QR code onboarding | ❌ FAIL | No CameraX; stub button |
| Hevy gym data sync | ✅ PASS | ActivityScreen + triggerHevySync() implemented 2026-03-03 |
| AI analysis on device | ❌ FAIL | Backend only; app links to web dashboard |
| Incremental sync (change tokens) | ❌ FAIL | Full 30-day every run |
| HMAC request signing | ✅ PASS | Client interceptor + server verifyHmac middleware |
| Server version validation | ❌ FAIL | Display only; no check |
