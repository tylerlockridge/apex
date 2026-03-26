# Feature: Unimplemented Features & Known Gaps

*Created: 2026-03-02 | Updated: 2026-03-26 | Project: Apex*

---

## Feature Overview

**What it does:**
Tracks the meaningful client-side gaps that still exist in Apex and the major v2 roadmap work that is planned but not yet implemented.

**What it does NOT do:**
- Does not duplicate test-gap tracking (see `10-testing-strategy.md`)
- Does not duplicate security hardening details (see `04-security-implementation.md`)
- Does not act as the authoritative multi-repo implementation roadmap; for that see `IMPLEMENTATION-ROADMAP.md`

---

## Recently Closed

These items were previously listed as missing and are now implemented:

- Offline Room sync queue
- HRV sync to server
- Hevy workout display + manual Hevy sync trigger
- QR code onboarding / scan flow
- Certificate pinning
- HMAC request signing
- Sync history detail
- Widget refresh after sync
- Clear-all-data flow

---

## Active Client Gaps

### Data & Sync

| Gap | Current state |
|-----|---------------|
| Body incremental sync | BP/sleep/HRV use change tokens; body measurements still do a full 30-day read |
| Durable inbound cache | Trends and Activity still rely on live server reads; no local persisted read model exists for those screens |
| Full transactional sync semantics | Per-type uploads can succeed or fail independently |
| Strict server compatibility enforcement | Settings checks server version and can warn, but compatibility is not enforced as a hard gate |

### Readiness

| Gap | Current state |
|-----|---------------|
| ~~Configurable readiness engine~~ | ✅ Implemented — `ReadinessEngine.kt` + `ReadinessConfigStore.kt` with configurable weights via SharedPreferences |
| ~~Per-input readiness breakdown~~ | ✅ Implemented — Home hero card shows per-input scores, staleness indicators, and aggregate score |
| HRV readiness activation | HRV weight is `0.0` for MVP (A-01 inconclusive). Config-ready for `0.25` when validated. |
| Subjective readiness input | No UI/data path exists yet (post-MVP) |
| ~~Training-load readiness input~~ | ✅ Implemented — wired into readiness engine from workout generation data |

### Provider / Data Source Flexibility

| Gap | Current state |
|-----|---------------|
| Alternative health providers | Package 0B created the seam, but only `HealthConnectProvider` exists |
| HC reliability fallback | No WHOOP/Oura/Garmin direct provider work exists; that remains validation-dependent |

---

## v2 Roadmap Work — Status as of MVP Close-Out (2026-03-23)

| Roadmap area | Status |
|-------------|--------|
| ~~Phase 1 server Hevy adapter + workout schema~~ | ✅ Implemented — `hevyClient.js` with cache-through, backoff, rate-limit tracking. Migrations 008-009. 155 server tests. |
| ~~Phase 2 readiness engine~~ | ✅ Implemented — `ReadinessEngine.kt`, `ReadinessConfigStore.kt`, `ReadinessModels.kt`, `ReadinessPayloadBuilder.kt` |
| ~~Phase 2 future-pillar schemas~~ | ✅ Implemented — Migrations 010-012 (nutrition, supplement, coaching tables created empty) |
| ~~Phase 3 workout generation flow~~ | ✅ Implemented — `GeneratedRoutineScreen.kt`, `GeneratedRoutineViewModel.kt`, server-side `workoutGenerator.js`, `progressionEngine.js` |
| Push-to-Hevy prescribed routine path | Deferred (VD-1 unvalidated). Path B (display-only, manual Hevy start) shipped. Post-MVP add-on if VD-1 passes. |

---

## Impact Summary

| Severity | Count | Notes |
|----------|-------|-------|
| High | 0 | no client-critical missing features |
| Medium | 3 | durable inbound cache, body incremental sync, strict compatibility enforcement |
| Low / deferred | 3 | HRV activation (config-ready), subjective readiness UI, push-to-Hevy (VD-1 dependent) |

---

## Status

| Area | Status | Notes |
|------|--------|-------|
| Offline queue | ✅ PASS | implemented and durable |
| QR onboarding | ✅ PASS | CameraX + ML Kit flow exists |
| Hevy activity sync trigger | ✅ PASS | server-triggered from Training screen |
| Server version awareness | ✅ PARTIAL | warning/check exists, not a hard gate |
| Incremental sync | ✅ PARTIAL | body remains full-read |
| Readiness engine (ADR-003-style) | ✅ PASS | implemented with configurable weights, staleness, per-input breakdown |
| Workout generation (Phase 3) | ✅ PASS | server-side generation + client review/accept flow |
| Durable inbound read cache | ❌ GAP | trends/workouts remain live-read (post-MVP) |
| Alternative providers | ❌ GAP | seam exists; implementations do not (post-MVP, H-04 dependent) |
