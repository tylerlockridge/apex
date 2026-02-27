---
status: Active
phase: Standalone project — separated from Health-Platform-Desktop 2026-02-26
sourcePath: "C:\\Users\\tyler\\Documents\\Claude Projects\\Apex"
repoUrl: "https://github.com/tylerlockridge/apex"
repoSubdir: ""
ralph: false
testCmd: ""
qualityGates: ["./gradlew test", "./gradlew lint"]
lastRalphRun: ""
ralphRuns: 0
---

# Apex

Android app for syncing health data (blood pressure, body measurements, sleep, workouts)
from Android Health Connect to the Health Platform Desktop server.

Previously lived at `health-platform/android-app/`. Separated 2026-02-26 into its own
standalone repository for clearer AI assistant context and independent versioning.

<!-- QUICK-RESUME-UPDATED: 2026-02-27 -->
## Quick Resume
**Last Active:** 2026-02-27
**Current Phase:** Complete — all screens verified with live server data
**Current Task:** Done. Server fixed, all 4 screens verified end-to-end with live data.
**Blockers:** BP/sleep/body tables are empty on server (historical data lost in DB migration). Workouts (68 records) intact. New HC syncs will populate going forward.
**Next Action:** None — app is fully deployed and functional. Sync fresh Health Connect data (BP readings, sleep) to populate Trends.

## Commits This Session (2026-02-27)
- `ecfdea0` — fix: deep audit — UI polish, sync auth, and code quality (11 files)
- `dc59532` — polish: replace raw exception messages with user-friendly error strings

## What Was Fixed
- **SyncWorker**: API key was empty string `""` — all syncs were 401. Now reads from SharedPrefs.
- **StackedBarChart**: Light sleep bars were invisible (same color as card bg). Fixed.
- **LineChart**: Hardcoded tooltip color replaced with theme token.
- **TrendsScreen**: Removed Activity stub tab. Now BP/Sleep/Body only.
- **All screens**: `padding(top = 56.dp)` → `statusBarsPadding().padding(top = 16.dp)` (edge-to-edge).
- **SettingsScreen**: Lock App Now now actually locks (calls `onLock()`). Server ping on IO dispatcher.
- **AndroidManifest**: Removed 5 unused Health Connect permissions.
- **Error messages**: Raw exceptions (`"Read timed out"`) → `"Server unreachable — check your connection"`.
- **Build**: CI (GitHub Actions JDK 17). APK installed via `adb`. SharedPrefs (API key) preserved across reinstalls.

## Project Info
- **App Name:** Apex
- **Package:** `com.healthplatform`
- **Server:** Health Platform Desktop at `165.227.125.102` (tyler-health.duckdns.org)
- **Data Source:** Android Health Connect API
- **Repo:** https://github.com/tylerlockridge/apex
