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

<!-- QUICK-RESUME-UPDATED: 2026-03-29 -->
## Quick Resume
**Last Active:** 2026-03-29
**Current Phase:** Active feature development. Nutrition/hydration foundation shipped and server-verified.
**Current Task:** PR #7 (`codex/apex-nutrition-hydration-foundation`) verified against live production. All 14 server endpoints return correct shapes. Full CRUD flow tested via curl against `tyler-health.duckdns.org` (create food, search, add/edit/delete entries, water CRUD, targets). 151 tests pass (0 failures). Client DTOs match production contract exactly, including `PatchField` tri-state for null-vs-absent distinction. CI green.
**Current Branch:** `codex/apex-nutrition-hydration-foundation`
**Blockers:** A-01/BP unresolved (unchanged). No server blockers — migration 014 deployed 2026-03-29.
**Next Action:** Merge PR #7 to master. Then on-device QA (sideload APK, exercise full nutrition/hydration flows with real touch interaction). After that: barcode scanning (USDA + Open Food Facts), quick-add, adaptive TDEE.

### v2 Architecture Artifacts
- `ARCHITECTURE-ASSUMPTIONS.md` — planning-to-architecture handoff
- `ARCHITECTURE-KICKOFF-CHECKLIST.md` — consumed at session start
- `ARCHITECTURE-SESSION-01-OUTPUT.md` — drivers, constraints, ADR candidates, follow-ups
- `SERVER-SCHEMA-INVENTORY.md` — PostgreSQL 16+, 7 raw SQL migrations, 11 tables
- `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md` — system-scoped override table (~55 rows)
- `ADR-001-hevy-abstraction-and-sync-strategy.md` — **accepted** 2026-03-23
- `ADR-002-server-schema-migration-strategy.md` — **accepted** for MVP
- `ADR-003-readiness-scoring-input-architecture.md` — **accepted** 2026-03-23
- `ADR-004-workout-generation-reconciliation-model.md` — **accepted** for MVP
- `ADR-005-health-data-source-abstraction.md` — **accepted** 2026-03-23
- `ADR-ACCEPTANCE-RECORD.md` — governance complete, all 5 ADRs accepted
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
- ADR governance complete: all 5 ADRs accepted. v2 MVP shipped 2026-03-23.

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
