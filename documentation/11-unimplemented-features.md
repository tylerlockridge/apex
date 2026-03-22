# Feature: Unimplemented Features & Known Gaps

*Created: 2026-03-02 | Updated: 2026-03-19 | Project: Apex*

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
| Configurable readiness engine | Dashboard still uses a small hardcoded heuristic in `DashboardViewModel` |
| Per-input readiness breakdown | Dashboard card does not yet show the richer ADR-003 style input-by-input display |
| Subjective readiness input | No UI/data path exists yet |
| Training-load readiness input | Deferred until workout-generation data flow exists |

### Provider / Data Source Flexibility

| Gap | Current state |
|-----|---------------|
| Alternative health providers | Package 0B created the seam, but only `HealthConnectProvider` exists |
| HC reliability fallback | No WHOOP/Oura/Garmin direct provider work exists; that remains validation-dependent |

---

## v2 Roadmap Work Not Yet Implemented

These are not "missing v1 bugs"; they are deliberate v2 roadmap items still ahead of the current client codebase.

| Roadmap area | Current state |
|-------------|---------------|
| Phase 1 server Hevy adapter + workout schema | Planned in `Health-Platform-Desktop`; not part of current Apex runtime |
| Phase 2 readiness engine | Planned; execution artifacts written, code not landed yet |
| Phase 2 future-pillar schemas | Planned in server repo; not client work |
| Phase 3 workout generation flow | Not implemented |
| Push-to-Hevy prescribed routine path | Validation-dependent Phase 3 feature |

---

## Impact Summary

| Severity | Count | Notes |
|----------|-------|-------|
| High | 0 | no obvious client-critical missing feature from the previously shipped scope |
| Medium | 5 | readiness engine, readiness breakdown, durable inbound cache, body incremental sync, strict compatibility enforcement |
| Low / planned | several | roadmap items intentionally not landed yet |

---

## Status

| Area | Status | Notes |
|------|--------|-------|
| Offline queue | ✅ PASS | implemented and durable |
| QR onboarding | ✅ PASS | CameraX + ML Kit flow exists |
| Hevy activity sync trigger | ✅ PASS | server-triggered from Activity screen |
| Server version awareness | ✅ PARTIAL | warning/check exists, not a hard gate |
| Incremental sync | ✅ PARTIAL | body remains full-read |
| Readiness engine (ADR-003-style) | ❌ GAP | planned, not yet implemented |
| Durable inbound read cache | ❌ GAP | trends/workouts remain live-read |
| Alternative providers | ❌ GAP | seam exists; implementations do not |
