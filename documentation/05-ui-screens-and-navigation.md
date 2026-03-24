# Feature: UI Screens & Navigation

*Created: 2026-03-02 | Updated: 2026-03-19 | Project: Apex*

---

## Feature Overview

**What it does:**
Defines the current Compose app shell, biometric lock flow, top-level navigation, and the major responsibilities of each screen in Apex.

**What it does NOT do:**
- Does not document chart rendering internals in detail (see `06-charts-and-data-visualization.md`)
- Does not act as the source of truth for sync semantics (see `03-data-sync-protocol.md` and `07-background-sync-and-workers.md`)
- Does not yet include the planned Phase 3 workout-generation review route

---

## Navigation Structure

`MainActivity.kt` is the single activity host. It uses:
- `NavigationSuiteScaffold` for adaptive navigation
- `NavHost` for route switching
- `AnimatedVisibility` to switch between the lock screen and the main app shell

Current routes:

```
MainActivity
├── LockScreen (shown when biometric lock is enabled and user is unauthenticated)
└── NavigationSuiteScaffold
    ├── dashboard
    ├── trends
    ├── activity
    ├── settings
    └── qrscan
```

Adaptive behavior:
- compact widths: bottom navigation bar
- medium widths: navigation rail
- larger layouts: navigation drawer via `NavigationSuiteScaffold`

Current top-level destinations:
- Dashboard
- Trends
- Activity
- Settings

Secondary route:
- QR scan screen launched from Settings

---

## App Shell and Security Flow

### MainActivity

`MainActivity.kt` is responsible for:
- installing the splash screen
- applying `FLAG_SECURE` to block screenshots/Recents thumbnails
- initializing `HealthConnectReader`
- initializing `BiometricLockManager`
- restoring auth state across configuration changes
- rendering either `LockScreen` or the main app shell based on auth state

### Lock screen behavior

The lock screen is shown when:
- biometric lock is enabled
- the app is not currently authenticated

Current behavior:
- biometric/device-credential prompt can unlock the app
- app re-locks after 5 minutes in the background
- auth state survives rotation via saved instance state
- process death returns to locked state unless biometric lock is disabled

### Splash screen

Splash behavior is standard Android 12+ `SplashScreen` API:
- app icon launch surface
- no separate custom splash route

---

## Screen Details

### Dashboard

Primary role:
- show the latest synced health snapshot and quick sync actions

Current surface:
- greeting header
- sync status card
- readiness card based on current dashboard heuristic
- horizontally scrolling health metric cards:
  - blood pressure
  - sleep
  - weight
  - HRV
- recent workout snippet when available
- Health Connect status card
- quick-action chips for BP and sleep sync
- floating sync FAB
- pull-to-refresh that reloads locally cached prefs-backed values

Notes:
- Dashboard data is still local-summary driven, not a repository-backed read model
- readiness is still the pre-Phase-2 heuristic and not yet the richer engine-backed breakdown

### Trends

Primary role:
- show historical server-read trends for synced health data

Current surface:
- adaptive chart screen with four metric tabs:
  - BP
  - Sleep
  - Body
  - HRV
- 7 / 30 / 90 day range selector
- shimmer loading skeletons
- animated tab content transitions
- per-tab stats summaries
- retry state for failed reads

Notes:
- Trends currently reads live server data through `TrendsViewModel`
- there is still no durable inbound local cache for these server-fed charts

### Activity

Primary role:
- show workout history and let the user pull fresh Hevy workout data

Current surface:
- 30-day workout summary card
- recent workout list with expandable rows
- pull-to-refresh
- top-right manual Hevy sync trigger
- empty state with `Sync Hevy` CTA when no workouts exist

Current backing behavior:
- workouts and workout stats come from `ServerApiClient`
- `ActivityViewModel` loads both workouts and stats at init
- successful manual Hevy sync reloads activity data

Notes:
- Activity is the natural entry point for the planned Phase 3 workout-generation flow
- no generated-routine review screen exists yet

### Settings

Primary role:
- control sync, security, Health Connect permissions, server configuration, and local device data

Current sections:
- Sync
  - auto-sync toggle
  - last sync time
  - sync history
  - `Sync All Now`
- Security
  - biometric lock toggle
  - `Lock App Now`
- Health Connect
  - per-type permission status
  - `Manage Permissions`
- Server
  - `Scan QR Code`
  - API key field with show/hide
  - save button
- Data
  - `Clear All Data`
- About
  - app version
  - server connection status
  - outdated-server warning when compatibility check fails

Notes:
- `Clear All Data` is implemented, not a stub
- QR onboarding entry is implemented, not a stub
- server compatibility is warning-based, not enforced as a hard block

### QR Scan Screen

Primary role:
- onboarding/configuration from a QR code

Current behavior:
- requests camera permission
- uses CameraX preview + ML Kit barcode scanning
- expects JSON payload:
  - `serverUrl`
  - `apiKey`
  - `deviceSecret`
- enforces HTTPS server URLs
- returns values to `MainActivity`, which persists them and navigates back

---

## Interaction and Motion Notes

Current navigation/screen polish:
- adaptive navigation shell via `NavigationSuiteScaffold`
- fade transitions between top-level routes
- lock/unlock transitions via `AnimatedVisibility`
- dashboard staggered entrance animation for metric cards
- trends tab content crossfade
- pull-to-refresh on Dashboard and Activity
- haptic feedback integrated into major taps and sync actions

---

## Known Gaps

| Gap | Current state |
|-----|---------------|
| Phase 2 readiness UI | Dashboard still shows a simple label/reason card rather than the planned per-input breakdown |
| Phase 3 generation route | No dedicated generated-workout review screen exists yet |
| Durable inbound cache for server-fed screens | Trends and Activity still depend on live server reads |
| Activity generation CTA | Not yet implemented; planned for Phase 3 |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Adaptive navigation shell | PASS | `NavigationSuiteScaffold` with Dashboard / Trends / Activity / Settings |
| Lock screen | PASS | biometric/device-credential-backed app lock |
| Screenshot protection | PASS | `FLAG_SECURE` in `MainActivity` |
| Dashboard summary experience | PASS | sync status, metric cards, recent workout snippet, quick actions |
| Trends tabs | PASS | BP / Sleep / Body / HRV |
| Activity workout history | PASS | stats + expandable recent workouts + manual Hevy sync |
| Settings data management | PASS | clear-all-data flow is implemented |
| QR onboarding | PASS | CameraX + ML Kit scanner route exists |
| Rich readiness breakdown | GAP | planned for Phase 2 |
| Workout-generation review flow | GAP | planned for Phase 3 |
