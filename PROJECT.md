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

<!-- QUICK-RESUME-UPDATED: 2026-02-28 -->
## Quick Resume
**Last Active:** 2026-02-28
**Current Phase:** LLM Audit complete + top security fixes applied
**Current Task:** Done. Triple-provider audit (Codex + Gemini 3 Pro + GPT-5.2) run. 4 security fixes committed. CI pending.
**Audit Score:** 3.8/10 weighted overall (Security 2.4, Testing 1.0 are critical gaps)
**Blockers:** None. BP/sleep/body tables still empty on server. New HC syncs will populate going forward.
**Next Action:** Check CI is green. Then tackle: (5) EncryptedSharedPreferences for api_key, (6) add test coverage, (7) HealthConnectReader pagination.

## Commits This Session (2026-02-27)
- `82197aa` — feat: new Apex icon — steel blue gradient + mountain-A lettermark
- `b0126fd` — feat: comprehensive UI polish — steel blue palette, backdrop watermark, haptics, nav transitions

## UI Overhaul Summary (b0126fd)
- **Color system**: Teal → steel blue (#5B9BD5 primary). New ApexSleepLight (#3A5A80) token.
- **Backdrop watermark**: `ApexWatermarkCanvas` draws chevron-A at 5% opacity. Box wraps all Scaffolds (transparent).
- **Haptics**: `HapticManager` + `rememberApexHaptic()`. tick() on tabs/switches, click() on buttons, confirm() on sync, reject() on errors. VibrationEffect.Composition for sync complete/error.
- **Nav transitions**: 200ms fade-in / 150ms fade-out between all NavHost destinations.
- **TrendsScreen**: `AnimatedContent` crossfade between BP/Sleep/Body tabs. Shimmer skeleton replaces loading spinner.
- **minSdk**: 28 → 34 (enables all VibrationEffect.Composition primitives).
- **Bug fix**: `Text("Save API Key", color = ApexBackground)` → `color = ApexOnPrimary` (was invisible white text on white bg).

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
