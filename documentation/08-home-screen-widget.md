# Feature: Home Screen Widget

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
`HealthGlanceWidget.kt` provides a 4x2 Android home screen widget built with Jetpack Glance (Compose for widgets). It displays the latest BP, sleep duration, HRV, and last sync timestamp read from SharedPreferences. Tapping the widget opens the Dashboard screen.

**What it does NOT do:**
- Does not update immediately after a sync completes (reads stale SharedPreferences)
- Does not fetch data independently from the server
- Does not display charts or historical trends

---

## Widget Layout

| Element | Data Source | Notes |
|---------|-------------|-------|
| Latest BP (systolic/diastolic) | SharedPreferences | Large text; written by SyncWorker on sync |
| Sleep duration | SharedPreferences | Written by SyncWorker on sync |
| HRV | SharedPreferences | Written by SyncWorker on sync |
| Last sync timestamp | SharedPreferences | Written by SyncWorker on completion |

Widget size: 4 columns x 2 rows (standard Android widget grid).

---

## Technical Details

| Property | Value |
|----------|-------|
| Implementation | `HealthGlanceWidget.kt` (Jetpack Glance) |
| Receiver | `HealthGlanceWidgetReceiver` |
| Manifest registration | Registered in `AndroidManifest.xml` |
| Theme | Dark background + Material 3 theme colors |
| Tap action | Opens Dashboard screen in Apex |

---

## Update Mechanism

The widget reads from SharedPreferences, which are updated by `SyncWorker` after each successful sync. However, the widget renders on Glance's own update schedule (typically every 1 hour by default), not immediately when SharedPreferences change. This means the widget may display data that is up to ~1 hour stale relative to the last sync.

The PRD calls for the widget to auto-update immediately after sync completes. This is not implemented — `SyncWorker` does not call `GlanceAppWidgetManager.update()` or send a broadcast to the widget receiver after writing to SharedPreferences.

---

## Known Gaps

| Gap | Notes |
|-----|-------|
| Stale data on widget | Updates on Glance's interval (~1h), not after sync. PRD requires immediate post-sync refresh. |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Widget renders BP / Sleep / HRV / timestamp | ✅ PASS | Reads SharedPreferences |
| 4x2 Glance widget | ✅ PASS | Jetpack Glance (Compose) |
| Tap-to-open Dashboard | ✅ PASS | |
| Dark theme + Material 3 colors | ✅ PASS | |
| AndroidManifest registration | ✅ PASS | |
| Immediate post-sync update | ⚠️ WARN | Not implemented; Glance schedule only (~1h) |
