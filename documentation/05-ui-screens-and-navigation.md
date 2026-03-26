# Feature: UI Screens & Navigation

*Created: 2026-03-02 | Updated: 2026-03-26 | Project: Apex*

---

## Feature Overview

**What it does:**
Defines the current Compose app shell, biometric lock flow, top-level navigation, and the major responsibilities of each screen in Apex.

**What it does NOT do:**
- Does not document chart rendering internals in detail (see `06-charts-and-data-visualization.md`)
- Does not act as the source of truth for sync semantics (see `03-data-sync-protocol.md` and `07-background-sync-and-workers.md`)
- Does not document the workout-generation review screen's server API contract (see `03-data-sync-protocol.md`)

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
    ├── dashboard  (label: "Home")
    ├── trends     (label: "Trends")
    ├── activity   (label: "Training")
    ├── settings   (label: "Settings")
    ├── generatedRoutine  (secondary — navigated from Training)
    └── qrscan            (secondary — navigated from Settings)
```

Adaptive behavior:
- compact widths: bottom navigation bar
- medium widths: navigation rail
- larger layouts: navigation drawer via `NavigationSuiteScaffold`

Current top-level destinations (as of UI overhaul 2026-03-26):
- Home (was "Dashboard") — icon: Home
- Trends — icon: TrendingUp
- Training (was "Activity") — icon: FitnessCenter
- Settings — icon: Settings

Secondary routes:
- Generated Routine screen (launched from Training "Generate Workout" CTA)
- QR scan screen (launched from Settings)

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

### Home (was "Dashboard")

Primary role:
- "today" screen: show current readiness, sync freshness, and latest health snapshot

Current surface (after UI overhaul 2026-03-26):
- greeting header with date chip
- **hero card** combining readiness score (240° arc gauge) + readiness input breakdown + sync status line with pulsing dot + "Sync Now" action
- inline permissions banner (when HC permissions missing) or HC unavailable banner
- **2×2 metric grid** (blood pressure, sleep, weight, HRV) — full-width tiles replacing the old horizontal LazyRow carousel
- recent workout snippet when available
- floating sync FAB
- pull-to-refresh that reloads locally cached prefs-backed values
- staggered entrance animations (hero, then grid)

Removed in overhaul:
- separate sync status card (merged into hero)
- separate readiness card (merged into hero)
- Health Connect status card (replaced by inline banner)
- quick-action chips for BP/sleep sync (redundant with FAB and hero sync button)

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
- **summary stats shown before charts** (BP and HRV tabs show StatsRow above chart cards)
- improved empty states with actionable hints
- retry state for failed reads

Notes:
- Trends currently reads live server data through `TrendsViewModel`
- there is still no durable inbound local cache for these server-fed charts

### Training (was "Activity")

Primary role:
- show workout history, training load, and provide the workout generation entry point

Current surface (after UI overhaul 2026-03-26):
- "Training" header with Hevy sync icon
- **hero generate-workout CTA card** with icon, title, subtitle, and chevron — prominently placed above content
- 30-day workout summary card
- progression/training load card with MRV warnings and 2-for-2 signals
- recent workout list with expandable rows
- pull-to-refresh
- empty state with `Sync Hevy` CTA

Current backing behavior:
- workouts and workout stats come from `ServerApiClient`
- `ActivityViewModel` loads both workouts and stats at init
- successful manual Hevy sync reloads activity data
- generate-workout navigates to `generatedRoutine` route

### Generated Routine

Primary role:
- review AI-generated workout routine and accept/reject

Current surface:
- routine title + reasoning summary + readiness context
- collapsible warnings card
- exercise cards with **target chips** (sets, reps, weight, RPE) shown as labeled background pills
- muscle tags per exercise
- flags (e.g., "weight increase") highlighted
- **prominent accept/reject buttons** (accept: green, 52dp tall; reject: outlined)
- post-decision result card with Hevy instructions

### Settings

Primary role:
- control server connection, permissions, sync, security, and local device data

Current sections (reordered for first-use priority, 2026-03-26):
1. **Server** (first-use critical)
   - server connection status with live indicator
   - outdated-server warning
   - `Scan QR Code`
   - API key field with show/hide
   - save button
2. **Health Connect**
   - per-type permission status
   - `Manage Permissions`
3. **Sync**
   - auto-sync toggle
   - sync window info
   - last sync time
   - sync history
   - `Sync All Now`
4. **Security**
   - biometric lock toggle
   - `Lock App Now`
5. **About**
   - app version
   - tagline
6. **Danger Zone** (destructive actions isolated at bottom)
   - `Clear All Data` with red styling

Notes:
- all sections are implemented, not stubs
- destructive actions moved to bottom with "Danger Zone" heading for clarity

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
- Home hero + metric grid staggered entrance animation
- trends tab content crossfade
- pull-to-refresh on Home and Training
- haptic feedback integrated into major taps and sync actions

---

## Known Gaps

| Gap | Current state |
|-----|---------------|
| Durable inbound cache for server-fed screens | Trends and Training still depend on live server reads; no offline read model |
| Subjective readiness input | No UI/data path exists yet (post-MVP) |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Adaptive navigation shell | PASS | `NavigationSuiteScaffold` with Home / Trends / Training / Settings |
| Lock screen | PASS | biometric/device-credential-backed app lock |
| Screenshot protection | PASS | `FLAG_SECURE` in `MainActivity` |
| Home hero + metric grid | PASS | readiness arc, sync status, 2×2 metric tiles, inline HC banners |
| Trends tabs | PASS | BP / Sleep / Body / HRV with summary-first layout |
| Training + workout history | PASS | hero generate CTA, stats, expandable workouts, Hevy sync |
| Generated Routine review | PASS | exercise cards with target chips, accept/reject flow |
| Settings (reordered) | PASS | Server → HC → Sync → Security → About → Danger Zone |
| QR onboarding | PASS | CameraX + ML Kit scanner route |
| Readiness breakdown | PASS | per-input scores + staleness indicators in Home hero card |
