# Feature: UI Screens & Navigation

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
`MainActivity.kt` hosts a bottom navigation bar with 4 tabs and a biometric lock screen composable. Screen transitions use `AnimatedContent` with 200ms fade-in / 150ms fade-out. All screens are Jetpack Compose composables backed by ViewModels.

**What it does NOT do:**
- Does not implement "Clear all data" (button is a stub)
- Does not implement "Re-scan QR code" (button is a stub)
- Does not show a QR code camera scanner for onboarding

---

## Navigation Structure

```
MainActivity
├── Lock Screen (overlay when biometric_enabled + unauthenticated)
└── Bottom Nav (4 tabs)
    ├── Dashboard
    ├── Trends
    ├── Activity
    └── Settings
```

Screen transitions: `AnimatedContent`, 200ms fade-in / 150ms fade-out.

---

## Screen Details

### Dashboard

- Summary cards: latest BP (systolic/diastolic), sleep duration, HRV
- Pull-to-refresh
- Pulsing FAB for manual sync trigger
- Staggered card entrance animations
- Haptic feedback on FAB press (`HapticManager.confirm()`)
- Data sourced from SharedPreferences (last values written by SyncWorker)

### Trends

- 3 sub-tabs: Blood Pressure / Sleep / Body
- Time range selector: 7 / 30 / 90 days
- Line charts with tap-to-tooltip (see `06-charts-and-data-visualization.md`)
- Shimmer skeleton loading while data fetches
- Animated tab transitions

### Activity

- Workout list fetched from server (`ServerApiClient.getWorkouts()`)
- Stats summary header: total workouts, avg duration, volume, sets
- Tap row to expand workout detail
- Pull-to-refresh

### Settings

| Section | Controls |
|---------|---------|
| Sync | Auto-sync toggle, "Sync Now" button, last sync timestamp |
| Health Connect | Per-type permission status display, "Manage Permissions" button (opens HC dialog) |
| Security | Biometric toggle, "Lock Now" button, "Clear All Data" button (TODO) |
| Account | Server URL display, connection status indicator, masked device secret, "Re-scan QR" button (TODO) |
| About | App version, server version string, last sync timestamp |

### Lock Screen

- Shown when `biometric_enabled = true` and user is not authenticated
- "Unlock" button triggers `BiometricPrompt`
- Rendered as a full-screen composable overlay in `MainActivity`

### Splash Screen

- Android 12+ `SplashScreen` API
- App icon on dark background
- Handles cold-start display

---

## Known Gaps

| Gap | Notes |
|-----|-------|
| "Clear all data" | Settings button wired to a TODO callback; does nothing |
| "Re-scan QR" | Settings button wired to a TODO callback; no CameraX implementation |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Bottom navigation (4 tabs) | ✅ PASS | Dashboard / Trends / Activity / Settings |
| Screen transitions | ✅ PASS | AnimatedContent, 200ms/150ms fade |
| Dashboard summary cards | ✅ PASS | BP, sleep, HRV from SharedPrefs |
| Dashboard pull-to-refresh | ✅ PASS | |
| Dashboard FAB manual sync | ✅ PASS | Pulsing animation + haptic |
| Trends 3 sub-tabs | ✅ PASS | BP / Sleep / Body |
| Trends time range selector | ✅ PASS | 7 / 30 / 90 days |
| Trends shimmer loading | ✅ PASS | |
| Activity workout list | ✅ PASS | Expandable rows + stats summary |
| Settings permission status | ✅ PASS | Per-type HC permission display |
| Lock screen biometric prompt | ✅ PASS | |
| Splash screen | ✅ PASS | Android 12+ SplashScreen API |
| "Clear all data" | ❌ FAIL | Stub callback only |
| "Re-scan QR" | ❌ FAIL | Stub callback only |
