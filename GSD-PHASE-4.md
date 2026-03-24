# GSD Phase 4

**Phase:** Polish, Validation, and MVP Ship
**Status:** Complete — v2 MVP shipped 2026-03-23
**Source:** IMPLEMENTATION-ROADMAP.md Phase 4; GSD-PHASE-3.md §10 Handoff; VALIDATION-RUNBOOK.md
**Target repos:** Apex and Health-Platform-Desktop
**Estimated effort:** 1-2 sessions
**Hard prerequisites:** Phases 0A, 0B, 1, 2, 3 complete (code landed and CI-green).
**Note:** Production deployment is required for live validations and end-to-end verification but does NOT block documentation reconciliation, ADR governance, or test stabilization.

---

## 1. Objective and Scope

**Objective:** Close the remaining validation-derived tuning gaps, reconcile stale documentation, verify the real daily loop end-to-end on production, and declare the workout-first MVP shippable.

Phase 4 does NOT add features. It applies evidence from H-06, A-01, and VD-1 validations; reconciles documentation that drifted during Phases 1-3; and verifies the live system works as designed.

**In scope:**

| Area | Work |
|------|------|
| H-06 sync-tier tuning | Apply measured rate-limit results to Hevy adapter config |
| A-01 HRV activation | If validated: set HRV weight to 0.25 in readiness config. If not: document decision to keep at 0 |
| VD-1 Path A add-on | **Only if VD-1 is confirmed positive:** wire push-to-Hevy endpoint + button. Otherwise: no work |
| H-02/H-03 readiness weight tuning | **Conditional:** if 4+ weeks of real use data exist, tune readiness weights from observed correlation. Otherwise: defer with explicit documentation |
| End-to-end verification | Verify the full daily loop on production with real data |
| Documentation reconciliation (mandatory) | Update the 3 roadmap-named docs: `01-architecture-overview.md`, `03-data-sync-protocol.md`, `07-background-sync-and-workers.md` |
| Documentation reconciliation (optional) | Update remaining docs (`02`, `05`, `10`, `11`, `12`) only if drift is discovered during mandatory pass |
| ADR governance cleanup | Human acceptance of ADR-001, ADR-003, ADR-005 per ADR-ACCEPTANCE-RECORD.md |
| Test stabilization | Fix the 1 pre-existing Robolectric SQLite failure if feasible |
| MVP ship declaration | Confirm exit criteria and mark PROJECT.md as v2 MVP shipped |

**Out of scope:**

| Excluded item | Why |
|---------------|-----|
| Nutrition/supplement/coaching feature activation | Post-MVP per D-15 |
| Alternative health providers (WHOOP, Oura, Garmin) | Post-MVP per ADR-005 |
| Full mesocycle planning or periodization | Post-MVP per roadmap |
| Adaptive TDEE or deload scheduling | Post-MVP |
| Subjective readiness input UI | Post-MVP |
| Durable inbound read cache for trends/workouts | Post-MVP architecture improvement |
| Body measurements incremental sync | Post-MVP optimization |
| Repository layer refactor | Post-MVP architecture improvement |
| H-04 HC reliability monitoring | Requires 2+ weeks of daily use post-ship |
| New feature scope of any kind | Phase 4 is stabilization only |

---

## 2. Current State (Code-Grounded)

**Production deployment status (2026-03-22):**
- Migrations 001-013 applied on production. DB connected, `/health` green.
- `exercise_muscle_overrides` seeded: 26 rows with real Hevy template IDs
- `hevy_exercise_cache`: 311 templates. `workout_sessions`: 82. `workout_sets`: 1,580.
- Generation endpoint returns real 201 routines with progression signals and readiness-aware reasoning.

**Validation status (2026-03-23):**
- H-06: **RESOLVED.** Tested 70 requests across 4 rate tiers (12, 30, 60, 120 req/min) plus 20-concurrent burst. Zero 429s. Classification: >= 30 req/min. Decision: Tier 2 defaults remain appropriate for MVP; no config change needed. Existing 429 handling + exponential backoff is correct defensive architecture.
- A-01: **INCONCLUSIVE — shipped with HRV=0.** The v2 APK was installed and used on the physical device (Task 7 passed), but the server database contains zero HRV, sleep, and BP records from Health Connect. The runbook requires HRV present and current within 24h on at least two separate mornings — that evidence was not captured. Without confirmed Health Connect HRV/sleep data meeting the required validation window, the activation criteria are not satisfied. Decision: HRV weight stays `0.0` for MVP per runbook partial/fail path. Re-evaluate post-ship once Health Connect data syncs are observed on the server over multiple days.
- VD-1: **DEFERRED — Path B shipped by design.** VD-1 was never run. Path B (display-only, user starts Hevy manually) is the MVP shipping path per ADR-004. Push-to-Hevy is a post-MVP add-on if VD-1 is later validated positive.

**Documentation status (2026-03-23):**
- Task 5 complete. All 3 mandatory docs updated (`01`, `03`, `07`). Optional docs (`05`, `10`, `11`, `12`) also updated where drift was discovered.

**H-02/H-03 status:** Explicitly deferred — insufficient data at ship time. MVP has been in daily use for < 1 week; 4+ weeks of readiness/workout data required. Current weights ship as-is. Re-evaluate post-MVP.

**Client state:**
- Readiness engine: `ReadinessEngine.kt`, `ReadinessConfigStore.kt`, `ReadinessModels.kt`, `ReadinessPayloadBuilder.kt` all landed
- Generation UI: `GeneratedRoutineScreen.kt`, `GeneratedRoutineViewModel.kt` landed
- Activity progression card landed
- Training-load input wired into readiness engine
- 4 readiness files + 2 generation files + modified Activity/Dashboard/ServerApiClient

**Server state:**
- Hevy adapter with cache-through, backoff, rate-limit tracking: landed
- progressionEngine.js, workoutGenerator.js, generatedRoutines routes: landed
- Migrations 008-012 SQL files: landed
- seed-exercise-overrides.js: landed
- 155 server tests passing

---

## 3. Task Decomposition

### Task 1: Production Deployment (HUMAN-GATED)

**Purpose:** Apply the server schema and seed data that all Phase 1-3 code depends on.

**Owner:** Tyler (requires SSH + production DB access)

**Steps:**
1. SSH to `tyler-health.duckdns.org`
2. Pull latest Health-Platform-Desktop code
3. Run `npm run migrate` — applies migrations 008-012
4. Run `npm run seed-overrides` — seeds exercise_muscle_overrides
5. Restart the server (`docker restart` or equivalent)
6. Verify: `curl https://tyler-health.duckdns.org/api/version` returns expected version
7. Verify: database has `schema_migrations` with 12 rows, `exercise_muscle_overrides` with ~55 rows

**Rollback:** Migrations are additive (new tables + columns). No data loss risk. If issues: roll back server code to pre-v2 commit; new tables remain harmless.

**This task gates:** Tasks 2, 3, 4, 7 (live validations and production verification). Task 6 (H-02/H-03) requires deployment plus 4+ weeks of daily use data. Does NOT gate Tasks 5, 8 (documentation, ADR governance), or test stabilization.

### Task 2: H-06 Sync-Tier Validation and Tuning (HUMAN-GATED)

**Purpose:** Measure real Hevy API rate limits and apply evidence-based config.

**Owner:** Tyler (requires Hevy API key + test execution)

**Procedure:** Follow VALIDATION-RUNBOOK.md §1

**Files likely touched (Claude-executable after results known):**
- Server: `src/services/hevyClient.js` — update `SYNC_INTERVAL_MS`, `MANUAL_RATE_CAP`, `BACKOFF_*` constants if results differ from Tier 2
- Server: `src/config/` or `.env` — if config is externalized

**Decision matrix:**
| H-06 Result | Action |
|-------------|--------|
| >= 30 req/min | Relax toward Tier 1; enable on-demand reads |
| 5-30 req/min | Keep Tier 2 defaults; no code changes |
| < 5 req/min | Tighten to batch-only; update freshness UX copy |
| < 1 req/min | Flag for Phase 3 UX rework before shipping |

**If H-06 is still unresolved at ship time:** Ship with Tier 2 defaults. Document as known limitation.

### Task 3: A-01 HRV Activation Decision (HUMAN-GATED)

**Purpose:** Determine whether HRV data flows reliably from Tyler's wearable through Health Connect.

**Owner:** Tyler (requires wearable + Health Connect inspection)

**Procedure:** Follow VALIDATION-RUNBOOK.md §2

**Files likely touched (Claude-executable after results known):**
- Apex: `readiness/ReadinessConfigStore.kt` — change `DEFAULT_HRV_WEIGHT` from `0.0` to `0.25` if A-01 passes
- No architectural changes regardless of outcome

**Decision matrix:**
| A-01 Result | Action |
|-------------|--------|
| Pass (HRV present + current within 24h) | Set HRV weight to 0.25 |
| Partial (sleep present, HRV absent) | Keep HRV weight at 0; document |
| Fail | Keep HRV weight at 0; note in Phase 4 close-out |

### Task 4: VD-1 Path A Add-On (CONDITIONAL — only if VD-1 positive)

**Purpose:** If Hevy routine creation API works, wire the push-to-Hevy path.

**Owner:** Tyler runs the validation; Claude implements if positive

**Procedure:** Follow VALIDATION-RUNBOOK.md §3

**If VD-1 positive — files to touch:**

Server (Health-Platform-Desktop):
- `src/routes/generatedRoutines.js` — add `POST /api/generated-routines/:id/push` endpoint
- `src/services/hevyClient.js` — add `createRoutine()` method
- `src/__tests__/generatedRoutines.test.js` — push path tests

Apex:
- `service/ServerApiClient.kt` — add `pushRoutineToHevy(id)` method
- `ui/GeneratedRoutineScreen.kt` — add "Push to Hevy" button (visible only when status is `accepted`)
- `ui/GeneratedRoutineViewModel.kt` — add `pushToHevy()` action
- `ui/GeneratedRoutineViewModelTest.kt` — push flow tests

**If VD-1 negative or unresolved:** No work. Path B (display-only) is the shipped behavior. Document in close-out.

### Task 5: Documentation Reconciliation (CLAUDE-EXECUTABLE)

**Purpose:** Update the roadmap-named documentation files to reflect the v2 architecture as built.

**Prerequisites:** None — this task uses the landed codebase, not the live production system.

**Mandatory files (named in IMPLEMENTATION-ROADMAP.md Phase 4 scope item #5):**

| File | What needs updating |
|------|-------------------|
| `documentation/01-architecture-overview.md` | Add readiness engine package, generation flow, ReadinessPayloadBuilder, training-load integration, generation UI route. Update source footprint count. Update "Current v2 State" section. |
| `documentation/03-data-sync-protocol.md` | Add generation request/response protocol, progression summary endpoint, training-load payload shape |
| `documentation/07-background-sync-and-workers.md` | Add training-load readiness integration note |

**Optional follow-up files (update only if drift is discovered during the mandatory pass):**

| File | Likely drift |
|------|-------------|
| `documentation/05-ui-screens-and-navigation.md` | Missing `generated_routine` route, GeneratedRoutineScreen, Activity progression card |
| `documentation/10-testing-strategy.md` | Stale test count (98 → higher after Phase 2/3) |
| `documentation/11-unimplemented-features.md` | Phase 1/2/3 items still listed as "not implemented" |
| `documentation/02-health-connect-integration.md` | Minor: readiness payload data flow |
| `documentation/12-business-rules-and-edge-cases.md` | Minor: 2-for-2 progression rule, MRV thresholds |

**Files likely unchanged:** `04-security-implementation.md`, `06-charts-and-data-visualization.md`, `08-home-screen-widget.md`, `09-theme-and-styling.md`

### Task 6: H-02/H-03 Readiness Weight Tuning (CONDITIONAL)

**Purpose:** If sufficient real-use data exists (4+ weeks since MVP deployment), tune readiness input weights based on observed correlation between readiness score and training performance.

**Condition:** Only execute if the MVP has been in daily use for 4+ weeks AND enough readiness/workout data exists to evaluate H-02 (HRV predicts strength) and H-03 (algorithm matches subjective feel).

**Owner:** Tyler evaluates data availability; Claude applies weight adjustments

**If sufficient data exists:**
- Analyze readiness scores vs training outcomes over the data window
- H-02: determine whether HRV input correlates with strength output. If not, reduce HRV weight (potentially to 0)
- H-03: determine whether aggregate readiness matches Tyler's subjective feel. If not, adjust relative weights
- Apply updated weights to `ReadinessConfigStore.kt`

**Files likely touched:**
- Apex: `readiness/ReadinessConfigStore.kt` — weight constants
- Apex: `readiness/ReadinessEngineTest.kt` — updated assertions if weights change

**If insufficient data:** Explicitly defer. Document in Phase 4 close-out: "H-02/H-03 deferred — insufficient data at ship time. Re-evaluate after 4+ weeks of daily use." This is consistent with the roadmap's soft dependency: "H-02/H-03 require 4+ weeks of real usage data from Phase 3."

**This task does NOT block MVP ship.** The roadmap lists it as a soft dependency. MVP ships with current weights regardless.

### Task 7: End-to-End Production Verification (HUMAN-GATED with Claude analysis)

**Purpose:** Verify the real daily loop works on production with Tyler's actual data.

**Verification sequence:**
1. **Health sync:** Open Apex → Dashboard → verify sync completes and summary updates
2. **Readiness:** Verify readiness card shows per-input breakdown with correct staleness indicators
3. **Workout generation:** Navigate to Activity → tap "Generate workout" → verify server responds with a personalized routine
4. **Review flow:** Verify review screen shows per-exercise reasoning → accept the routine
5. **Hevy execution:** Start the routine in Hevy manually (Path B) or push (Path A if VD-1 positive) → complete the workout
6. **Sync back:** Trigger Hevy sync in Activity → verify completed workout appears
7. **Progression update:** Request another generation → verify it reflects the just-completed workout in volume/progression summary
8. **Widget/notification:** Verify widget updates after sync; verify BP anomaly notification fires if applicable

**Pass criteria:** All 8 steps complete without error. Generation returns meaningful reasoning grounded in real workout history.

**Failure handling:** If any step fails, diagnose root cause before declaring MVP. File as a bug fix within Phase 4 scope, not a feature addition.

### Task 8: ADR Governance Close-Out (HUMAN-GATED)

**Purpose:** Formal human acceptance of the three remaining acceptance-ready ADRs.

**Owner:** Tyler

**Action:** Per ADR-ACCEPTANCE-RECORD.md, mark as accepted:
- ADR-001-hevy-abstraction-and-sync-strategy.md
- ADR-003-readiness-scoring-input-architecture.md
- ADR-005-health-data-source-abstraction.md

**This is governance, not code.** No files change except the ADR documents themselves (status field).

### Task 9: MVP Ship Declaration (HUMAN-GATED)

**Purpose:** Update PROJECT.md and close out v2 MVP milestone.

**Files to update:**
- `PROJECT.md` — Quick Resume section updated to "v2 MVP shipped"
- `IMPLEMENTATION-ROADMAP.md` — Phase 4 exit criteria marked complete
- `GSD-PHASE-4.md` — all checklist items marked done
- `documentation/11-unimplemented-features.md` — final pass

---

## 4. Recommended Execution Order

```
IMMEDIATELY (no deployment dependency):
  Task 5 (mandatory doc reconciliation) — reads landed code, not production
  Task 8 (ADR governance) — human signoff, no code dependency
  Test stabilization

AFTER DEPLOYMENT (Task 1):
  Task 2 (H-06 validation) — requires live Hevy API testing
  Task 3 (A-01 validation) — requires device + Health Connect
  Apply H-06 config changes (if any)
  Apply A-01 config changes (if any)
  Task 4 (VD-1 Path A) — only if VD-1 positive

AFTER VALIDATIONS:
  Task 7 (end-to-end verification) — requires production + tuned config

CONDITIONAL (requires deployment + 4 weeks of daily use data):
  Task 6 (H-02/H-03 tuning) — only if sufficient readiness/workout data exist

FINAL:
  Task 9 (MVP ship declaration) — after Task 7 passes
```

**Minimum critical path:** Tasks 5+8 (parallel) → Task 1 → Task 7 → Task 9
**Maximum critical path:** Tasks 5+8 (parallel) + Task 1 → Tasks 2+3 → apply tuning → Task 4 (if VD-1+) → Task 7 → Task 6 (if data exists) → Task 9

---

## 5. Validation Interaction Summary

| Validation | Phase 4 action if positive | Phase 4 action if negative/unresolved | Ship impact |
|------------|---------------------------|---------------------------------------|-------------|
| H-06 | Tune sync constants | Ship Tier 2 defaults; document | Minimal — Tier 2 is acceptable |
| A-01 | Set HRV weight to 0.25 | Keep HRV weight at 0; document | Readiness slightly weaker but functional |
| VD-1 | Wire Path A push-to-Hevy | Ship Path B only; document | User starts Hevy manually — acceptable per ADR-004 |
| H-02/H-03 | Tune readiness weights from observed data (Task 6) | Defer with documentation — insufficient data | Does not block MVP ship; conditional Phase 4 work per roadmap soft dependency |
| H-04 | Post-MVP only | N/A | Requires 2+ weeks of monitoring |

---

## 6. Risks and Rollback

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Production migration fails | High | `migrate.js` handles adoption state; migrations are additive; rollback = revert server code |
| Hevy API behavior worse than Tier 2 | Medium | Ship with conservative batch defaults; generation still works with stale cache |
| End-to-end loop fails on production | High | Diagnose and fix within Phase 4; do not ship until loop passes |
| Exercise override seed data has wrong IDs | Medium | Overrides use placeholder slugs until real Hevy template IDs are mapped; verify after first template sync |
| Pre-existing Robolectric test failure | Low | Known issue; does not indicate regression |

---

## 7. Definition of Done

Phase 4 is complete when ALL of the following are true:

- [x] Migrations 008-013 applied on production (2026-03-22)
- [x] exercise_muscle_overrides seeded on production — 26 rows with real Hevy IDs (2026-03-22)
- [x] H-06 resolved: >= 30 req/min validated; Tier 2 defaults confirmed appropriate; no config change needed (2026-03-23)
- [x] A-01 shipped with HRV=0 + documented: inconclusive — no confirmed Health Connect HRV/sleep data meeting runbook's repeat-morning validation window; re-evaluate post-ship (2026-03-23)
- [x] VD-1 shipped with Path B only + documented: deferred by design per ADR-004 (2026-03-23)
- [x] End-to-end daily loop verified on production — all 8 Task 7 steps passed on real device including Hevy execution, sync-back, and re-generation with updated history (2026-03-22)
- [x] Mandatory documentation reconciled: `01`, `03`, `07` updated; optional docs `05`, `10`, `11`, `12` also updated (Task 5 complete)
- [x] H-02/H-03 explicitly deferred — insufficient data at ship time (< 1 week of daily use; 4+ weeks required) (2026-03-23)
- [x] ADR-001, ADR-003, ADR-005 human-accepted by Tyler (2026-03-23)
- [x] PROJECT.md updated to reflect v2 MVP shipped (2026-03-23)
- [x] No known regressions from v1 functionality
- [x] CI green in both repos (Android CI run `23413583303` passed; 155 server tests passing)
- [x] App is stable for daily use as primary workout companion alongside Hevy

---

## 8. Explicit Non-Goals

- No new features
- No nutrition/supplement/coaching activation
- No alternative health providers
- No mesocycle or periodization logic
- No repository layer refactor
- No durable inbound cache implementation
- No adaptive TDEE
