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

<!-- QUICK-RESUME-UPDATED: 2026-03-22 -->
## Quick Resume
**Last Active:** 2026-03-22
**Current Phase:** v2 implementation — Phase 4 in progress (Task 7 complete, validation/tuning pending)
**Current Task:** Production rollout is fully live from clean branch `codex/health-platform-rollout`. `LOGIN_PASSWORD` was added on the server, the live `DB_NAME` mismatch (`healthplatform` -> `health_platform`) was fixed, `/health` is green with DB connected, migrations 001-013 are applied, and the rollout branch/source now matches production. Production data bootstrap is complete: `hevy_exercise_cache=311`, `workout_sessions=82`, `workout_sets=1580`, `exercise_muscle_overrides=26`, and `/api/generated-routines` returns real 201 routines for both push and pull sessions with progression signals, override attribution, and readiness-aware reasoning. Apex v2 client code is committed on branch `codex/apex-v2-e2e-ready` (`74c31c4`), PR #2 is open, and Android CI run `23413583303` completed successfully with v2 APK artifacts. Phase 4 Task 7 now passes cleanly end-to-end on a real device, including Hevy execution, post-workout sync-back, and re-generation with updated history. The false `Server unreachable` message was fixed by aligning the Apex Hevy sync model with the server contract (`skipped: Boolean`, `degraded: Boolean`, `duplicates_skipped: Int`).
**Blockers:** No core flow blockers. Remaining work is H-06 and A-01 validation/tuning, then Task 9 MVP ship declaration. VD-1 remains optional.
**Next Action:** 1. Run H-06 sync-tier validation and record whether current freshness behavior is acceptable or needs tuning. 2. Run A-01 HRV validation and decide whether to activate non-zero HRV weight. 3. If those pass, complete Task 9 MVP ship declaration.

### v2 Architecture Artifacts
- `ARCHITECTURE-ASSUMPTIONS.md` — planning-to-architecture handoff
- `ARCHITECTURE-KICKOFF-CHECKLIST.md` — consumed at session start
- `ARCHITECTURE-SESSION-01-OUTPUT.md` — drivers, constraints, ADR candidates, follow-ups
- `SERVER-SCHEMA-INVENTORY.md` — PostgreSQL 16+, 7 raw SQL migrations, 11 tables
- `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md` — system-scoped override table (~55 rows)
- `ADR-001-hevy-abstraction-and-sync-strategy.md` — **acceptance-ready**, tiered sync by H-06 result
- `ADR-002-server-schema-migration-strategy.md` — **accepted** for MVP
- `ADR-003-readiness-scoring-input-architecture.md` — **acceptance-ready**, HRV config conditional on A-01
- `ADR-004-workout-generation-reconciliation-model.md` — **accepted** for MVP
- `ADR-005-health-data-source-abstraction.md` — **acceptance-ready**, provider interface already implemented in Package 0B
- `ADR-ACCEPTANCE-RECORD.md` — Codex recommendation for human signoff on ADR-001 / ADR-003 / ADR-005
- `GSD-PHASE-1.md` — Phase 1 execution plan (server Hevy adapter + workout schema)
- `IMPL-BRIEF-PHASE-1.md` — implementation-ready Phase 1 brief
- `GSD-PHASE-2.md` — Phase 2 execution plan (future-pillar schemas + readiness engine)
- `IMPL-BRIEF-PHASE-2.md` — implementation-ready Phase 2 brief
- `GSD-PHASE-3.md` — Phase 3 execution plan (workout generation MVP)
- `IMPL-BRIEF-PHASE-3.md` — implementation-ready Phase 3 brief
- `VALIDATION-RUNBOOK.md` — H-06 / A-01 / VD-1 manual procedures + prompt pack
- `research/rp-volume-landmarks.md` — RP volume landmark availability/licensing research
- `research/v1-client-data-flows.md` — current-state Apex client data flow baseline
- `CODEX-COORDINATION-HANDOFF-2026-03-18.md` — Codex-side summary of prior coordination/protocol/research work for workflow reset
- Governance cleanup remaining: human acceptance of ADR-001, ADR-003, and ADR-005. H-06, A-01, and VD-1 remain implementation/config validations, not ADR blockers.

## Session Summary (2026-03-10)
- **UI overhaul**: All 4 screens polished (Dashboard, Trends, Activity, Settings) — per-metric accent colors, upgraded typography, section labels, card spacing
- **App icon**: Full redesign — steel blue mountain-A lettermark with 4-layer atmospheric glow, deep navy background. Python Pillow script for Play Store PNG.
- **Release signing**: Keystore generated, signingConfigs in build.gradle.kts, CI workflow updated. Credentials backed up to `C:\Users\tyler\Documents\Reference\Apex-Keystore-Credentials.md`
- **API key baked in**: Auto-seeded from `BuildConfig.API_KEY` into `SecurePrefs` on first launch — no manual entry. Key injected via `local.properties` + CI secret `API_KEY`.
- **CI lint fixes**: `backup_rules.xml` and `data_extraction_rules.xml` — `domain="files"` → `domain="file"`, removed invalid `domain="no_backup"`
- **Local build fixed**: `gradle.properties` points to Android Studio JDK 21; `local.properties` sdk.dir uses forward slashes (avoids `\t` escape bug)
- **Hook updates**: `.env` files unblocked for project directories; SSH exception added for `165.227.125.102` (health platform server); CLAUDE.md updated with secrets access policy

## Commits This Session (2026-03-03)
- `e0bb642` — fix: Sprint 1 — 7 bug fixes (H-1, H-2, H-3, L-4, L-6, L-8, L-10)
- `4acfded` — feat: Sprint 2 — 5 features (readiness card, BP anomaly alert, weekly summary, HRV rolling avg, sleep arc gauge)
- `68b58ac` — polish: Sprint 3 — 14 backlog items (H-4, H-5, M-1 through M-7, L-1 through L-3, L-5, L-7, L-9)

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
