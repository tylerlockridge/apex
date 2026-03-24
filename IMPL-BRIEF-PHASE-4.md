# Implementation Brief: Phase 4

**Phase:** Polish, Validation, and MVP Ship
**Target repos:** Apex and Health-Platform-Desktop
**Server stack:** Node.js / Express / PostgreSQL / migrations 008-012 pending production deployment
**Client stack:** Kotlin / Jetpack Compose / Retrofit / ViewModel
**Test frameworks:** Vitest (server), JUnit/Robolectric (client)

---

## 1. Objective

Stabilize and ship the workout-first MVP by:
- deploying Phase 1-3 server schema to production
- applying validation-derived tuning (H-06, A-01)
- optionally wiring VD-1 Path A if validated
- reconciling the roadmap-named documentation to match as-built v2
- conditionally tuning readiness weights (H-02/H-03) if 4+ weeks of use data exist
- verifying the real daily loop end-to-end on production

This phase is strictly stabilization. No new features, no scope expansion.

---

## 2. Scope

**In scope:**
- production deployment of migrations 008-012 + exercise override seed
- H-06 sync-tier tuning (config adjustment only)
- A-01 HRV weight activation (single constant change)
- VD-1 Path A push-to-Hevy (conditional on positive validation)
- documentation reconciliation of 3 roadmap-named docs (mandatory) + optional follow-up on remaining docs
- H-02/H-03 readiness weight tuning (conditional on 4+ weeks of use data)
- end-to-end production verification
- ADR governance close-out (human signoff)
- MVP ship declaration

**Out of scope:**
- any feature not already built in Phases 0-3
- nutrition/supplement/coaching activation
- alternative health providers
- mesocycle planning, periodization, adaptive TDEE
- subjective readiness input UI
- durable inbound read cache
- body measurements incremental sync
- repository layer refactor
- H-04 HC reliability monitoring (requires 2+ weeks post-ship data)

---

## 3. Required Deliverables

### 3.1 Production Deployment

**Type:** HUMAN-GATED

**Deployment steps (Tyler via SSH):**
```bash
ssh -i ~/.ssh/id_droplet tyler@tyler-health.duckdns.org
cd /path/to/health-platform
git pull origin main
npm run migrate        # applies 008-012
npm run seed-overrides # seeds ~55 exercise_muscle_overrides rows
# restart server (docker restart health-api or pm2 restart)
```

**Verification queries:**
```sql
SELECT count(*) FROM schema_migrations;           -- expect 12
SELECT count(*) FROM exercise_muscle_overrides;    -- expect ~55
SELECT version, name FROM schema_migrations ORDER BY version;
```

**Rollback:** All migrations are additive (CREATE TABLE, ADD COLUMN). Rolling back = reverting server code to pre-v2 commit. New empty tables remain harmless.

---

### 3.2 H-06 Sync-Tier Tuning

**Type:** HUMAN-GATED (validation) then CLAUDE-EXECUTABLE (config change)

**Validation procedure:** VALIDATION-RUNBOOK.md §1

**Likely files to modify (server):**

| File | What changes |
|------|-------------|
| `src/services/hevyClient.js` | `SYNC_INTERVAL_MS`, `MANUAL_RATE_CAP`, `BACKOFF_BASE_MS`, `BACKOFF_MAX_MS` — only if measured tier differs from Tier 2 |

**If >= 30 req/min:** Consider relaxing `MANUAL_RATE_CAP` upward; enable on-demand template refresh.
**If 5-30 req/min:** No changes needed — current Tier 2 is correct.
**If < 5 req/min:** Tighten `MANUAL_RATE_CAP` to 1/min; update `ActivityScreen.kt` freshness copy to set expectations.
**If unresolved at ship time:** Ship with Tier 2. Add "H-06 unresolved" note to close-out docs.

---

### 3.3 A-01 HRV Activation

**Type:** HUMAN-GATED (validation) then CLAUDE-EXECUTABLE (config change)

**Validation procedure:** VALIDATION-RUNBOOK.md §2

**Likely files to modify (Apex):**

| File | What changes |
|------|-------------|
| `readiness/ReadinessConfigStore.kt` | Change `DEFAULT_HRV_WEIGHT` from `0.0` to `0.25` |

**If pass:** One constant change + one test assertion update (if default weight is tested).
**If partial/fail:** No code change. Document decision in Phase 4 close-out.

---

### 3.4 VD-1 Path A Push-to-Hevy (CONDITIONAL)

**Type:** HUMAN-GATED (validation) then CLAUDE-EXECUTABLE (implementation)
**Condition:** Only execute if VD-1 validation is positive per VALIDATION-RUNBOOK.md §3

**Server files to add/modify:**

| File | Change |
|------|--------|
| `src/routes/generatedRoutines.js` | Add `POST /api/generated-routines/:id/push` — calls hevyClient.createRoutine(), stores returned hevy_routine_id, updates status to 'pushed' |
| `src/services/hevyClient.js` | Add `createRoutine(title, exercises)` — POST to Hevy `/v1/routines` |
| `src/schemas/generatedRoutines.js` | Add push request/response Zod schemas |
| `src/__tests__/generatedRoutines.test.js` | Push path success/failure tests |

**Client files to add/modify:**

| File | Change |
|------|--------|
| `service/ServerApiClient.kt` | Add `pushRoutineToHevy(routineId: Int): Result<PushResult>` |
| `ui/GeneratedRoutineScreen.kt` | Add "Push to Hevy" button visible when status == accepted |
| `ui/GeneratedRoutineViewModel.kt` | Add `pushToHevy()` action with loading/error states |
| `ui/GeneratedRoutineViewModelTest.kt` | Push success/failure/loading tests |

**If VD-1 negative:** Zero files touched. Path B is the shipped behavior.

---

### 3.5 Documentation Reconciliation

**Type:** CLAUDE-EXECUTABLE
**Prerequisites:** None — reads landed codebase, not production system.

**Mandatory files (named in IMPLEMENTATION-ROADMAP.md Phase 4 scope item #5):**

| File | Required updates |
|------|-----------------|
| `01-architecture-overview.md` | Add: readiness package (`readiness/` with 4 files), `ReadinessPayloadBuilder`, generation UI route (`generated_routine`), `GeneratedRoutineScreen`/`GeneratedRoutineViewModel`, training-load integration path. Update source footprint count. Update "Current v2 State" section. |
| `03-data-sync-protocol.md` | Add: `GET /api/workouts/progression/summary` endpoint, `POST /api/generated-routines` request/response, generation request/response wire format, training-load payload shape |
| `07-background-sync-and-workers.md` | Add: training-load readiness integration note (readiness engine consumes progression summary from server) |

**Optional follow-up files (update only if drift discovered during mandatory pass):**

| File | Likely drift |
|------|-------------|
| `05-ui-screens-and-navigation.md` | Missing `generated_routine` route, GeneratedRoutineScreen, Activity progression card |
| `10-testing-strategy.md` | Stale test count (98 → higher after Phase 2/3) |
| `11-unimplemented-features.md` | Phase 1/2/3 items still listed as "not implemented" |
| `02-health-connect-integration.md` | Minor: readiness payload data flow |
| `12-business-rules-and-edge-cases.md` | Minor: 2-for-2 progression rule, MRV thresholds |

**Files likely unchanged:** `04-security-implementation.md`, `06-charts-and-data-visualization.md`, `08-home-screen-widget.md`, `09-theme-and-styling.md`

---

### 3.6 H-02/H-03 Readiness Weight Tuning (CONDITIONAL)

**Type:** HUMAN-GATED (data availability check) then CLAUDE-EXECUTABLE (weight adjustment)
**Condition:** Only execute if MVP has been in daily use for 4+ weeks AND readiness/workout data exists to evaluate H-02 and H-03.
**Roadmap basis:** IMPLEMENTATION-ROADMAP.md Phase 4 scope item #4: "H-02/H-03 readiness weight tuning (after 4+ weeks of use data)". Soft dependency per §4.

**If sufficient data exists:**

| Validation | Analysis | Likely file change |
|------------|----------|--------------------|
| H-02 (HRV predicts strength) | Compare HRV readiness input scores with workout performance across 4+ weeks | `readiness/ReadinessConfigStore.kt` — increase or decrease HRV weight based on correlation |
| H-03 (Algorithm matches feel) | Compare aggregate readiness scores with Tyler's subjective assessment | `readiness/ReadinessConfigStore.kt` — adjust relative weights across all inputs |

**If insufficient data at Phase 4 execution time:**
No code changes. Add to Phase 4 close-out documentation:
> "H-02/H-03 deferred — fewer than 4 weeks of daily-use data available at Phase 4 execution. Re-evaluate when data threshold is met. Current weights remain safe defaults."

**This does not block MVP ship.** Current weights are functional; tuning improves accuracy but is not required for the generation loop to work.

---

### 3.7 End-to-End Production Verification

**Type:** HUMAN-GATED (Tyler executes with real device)

**Full verification sequence:**

| Step | Action | Expected result |
|------|--------|----------------|
| 1 | Open Apex → Dashboard | Sync completes; summary metrics appear |
| 2 | Check readiness card | Per-input breakdown visible; staleness indicators correct |
| 3 | Navigate to Activity → "Generate workout" | Server returns personalized routine with reasoning |
| 4 | Review screen → Accept | Routine status updates to accepted |
| 5 | Start routine in Hevy (Path B) or push (Path A) | Workout available in Hevy |
| 6 | Complete workout → trigger Hevy sync in Activity | Completed workout appears in Activity |
| 7 | Generate another workout | New generation reflects just-completed volume |
| 8 | Verify widget + notifications | Widget shows latest sync; BP anomaly fires if applicable |

**Pass criteria:** All 8 steps succeed with real production data.
**If any step fails:** Diagnose, fix within Phase 4 scope, re-verify.

---

### 3.8 Test Stabilization

**Type:** CLAUDE-EXECUTABLE

**Known issue:** 1 pre-existing Robolectric SQLite failure in debounce test (has existed since before Phase 1).

**Action:** Investigate root cause. If fixable without major refactor, fix it. If Robolectric/SQLite incompatibility, document as known and skip (`@Ignore` with reason).

**Files likely touched:**
- Whichever test file contains the debounce test (likely `DashboardViewModelTest.kt`)

---

### 3.9 ADR Governance and MVP Close-Out

**Type:** HUMAN-GATED

**ADR acceptance (Tyler):**
- Update status field in `ADR-001-hevy-abstraction-and-sync-strategy.md` → accepted
- Update status field in `ADR-003-readiness-scoring-input-architecture.md` → accepted
- Update status field in `ADR-005-health-data-source-abstraction.md` → accepted

**PROJECT.md update (Claude-executable after all above):**
- Quick Resume: "v2 MVP shipped"
- Current Phase: "v2 shipped — post-MVP monitoring"
- Blockers: cleared
- Next Action: "H-02/H-03 weight tuning when 4+ weeks of data available" (or already applied if data existed at Phase 4 execution)

---

## 4. Prompt Pack

### 4.1 Claude Prompt — Documentation Reconciliation

Use in the **Apex** repo — can run before production deployment:

```text
Read these files first:

1. GSD-PHASE-4.md (Task 5 file list)
2. IMPL-BRIEF-PHASE-4.md (§3.5 reconciliation table)
3. documentation/01-architecture-overview.md
4. documentation/03-data-sync-protocol.md
5. documentation/07-background-sync-and-workers.md
6. app/src/main/java/com/healthplatform/sync/readiness/ (all 4 files)
7. app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineScreen.kt
8. app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineViewModel.kt

Task:
Update the 3 mandatory documentation files per the reconciliation table in IMPL-BRIEF-PHASE-4.md §3.5.
After completing the mandatory files, check the optional follow-up files for drift. Only update those where actual drift is found.

Constraints:
- Only update what is listed in the table. Do not restructure or rewrite docs.
- Keep the existing format and section structure of each doc.
- Update dates to today.
- Do not add speculative future content.
- Do not remove or modify accurate existing content.

Return:
- List of mandatory files updated with summary of changes per file
- List of optional files updated (if any) with drift found
- List of optional files checked and found current (no update needed)
```

### 4.2 Claude Prompt — H-06 Config Application

Use in **Health-Platform-Desktop** after Tyler reports H-06 results:

```text
H-06 validation result: [TYLER FILLS IN: tier classification and measured rate]

Read: src/services/hevyClient.js

Task:
Apply the H-06 result to the Hevy adapter configuration.
If result is Tier 2 (5-30 req/min): no changes needed, confirm current values are correct.
If result differs: update SYNC_INTERVAL_MS, MANUAL_RATE_CAP, and BACKOFF constants to match the measured tier.

Return: files changed (if any) and updated constant values.
```

### 4.3 Claude Prompt — VD-1 Path A Implementation

Use in **both repos** only if Tyler confirms VD-1 is positive:

```text
Read these files first:

Server:
1. src/routes/generatedRoutines.js
2. src/services/hevyClient.js
3. src/schemas/generatedRoutines.js

Client:
4. app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt
5. app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineScreen.kt
6. app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineViewModel.kt

Task:
Implement VD-1 Path A: push accepted generated routines to Hevy.

Server:
- Add POST /api/generated-routines/:id/push endpoint
- Add hevyClient.createRoutine() method
- Update status to 'pushed' and store hevy_routine_id
- Add tests

Client:
- Add pushRoutineToHevy() in ServerApiClient
- Add "Push to Hevy" button on accepted routines in review screen
- Add pushToHevy() action in GeneratedRoutineViewModel
- Add tests

Constraints:
- Path A is additive only. Do not modify Path B behavior.
- Push button only visible when routine status is 'accepted'.
- Handle push failures gracefully (show error, keep routine in accepted state).

Return: files changed, tests added, checks run.
```

### 4.4 Codex Prompt — Phase 4 Audit

Use after Phase 4 work is complete:

```text
Read the roadmap, GSD-PHASE-4.md, IMPL-BRIEF-PHASE-4.md, and the actual repo diff since Phase 3 completion.

Task:
Audit Phase 4 execution against its stated scope.

Return:
1. Scope drift (if any)
2. Documentation gaps remaining
3. Whether MVP ship criteria are met
4. Whether any Phase 4 work inadvertently introduced post-MVP scope
5. Accept/reject recommendation for MVP ship declaration
```

---

## 5. Critical Path

```
PARALLEL TRACK A (no deployment dependency):
  Task 5: Doc reconciliation (CLAUDE)
  Task 8: ADR governance (HUMAN)
  Test stabilization (CLAUDE)

PARALLEL TRACK B (deployment-gated):
  Task 1: Production deployment (HUMAN)
    ├── Task 2: H-06 validation (HUMAN)
    ├── Task 3: A-01 validation (HUMAN)
    │   ├── Apply H-06 tuning (CLAUDE, after Task 2)
    │   ├── Apply A-01 config (CLAUDE, after Task 3)
    │   └── Task 4: VD-1 Path A (CLAUDE, only if positive)
    │
    └── Task 7: End-to-end verification (HUMAN + CLAUDE)

CONDITIONAL TRACK (deployment + 4 weeks daily use data):
  Task 6: H-02/H-03 tuning (HUMAN data check + CLAUDE weight adjustment)

FINAL:
  Task 9: MVP ship declaration (HUMAN)
```

**Minimum path (no validations, no VD-1, no H-02/H-03 data):**
Tasks 5+8 (parallel, immediate) + Task 1 → Task 7 → Task 9
**Estimated: 1 session**

**Maximum path (all validations + VD-1 positive + H-02/H-03 data available):**
Tasks 5+8 (parallel) + Task 1 → Tasks 2+3 → apply tuning → Task 4 → Task 7 → Task 6 → Task 9
**Estimated: 2 sessions**

---

## 6. Human-Gated vs Claude-Executable Summary

| Task | Human-gated? | Claude-executable? | Requires deployment? |
|------|-------------|-------------------|---------------------|
| Task 1: Production deployment | Yes (SSH + DB) | No | N/A — is the deployment |
| Task 2: H-06 validation | Yes (API testing) | Config change after results | Yes |
| Task 3: A-01 validation | Yes (device inspection) | Config change after results | Yes |
| Task 4: VD-1 Path A | Yes (validation) | Implementation if positive | Yes |
| Task 5: Documentation reconciliation | No | Yes — full autonomous | **No** |
| Task 6: H-02/H-03 tuning | Yes (data availability) | Weight adjustment if data exists | Yes + 4 weeks daily use |
| Task 7: End-to-end verification | Yes (device + production) | Analysis of failures | Yes |
| Task 8: ADR governance | Yes (signoff) | No | **No** |
| Task 9: MVP ship declaration | Yes (final call) | PROJECT.md update | Yes (after Task 7) |
