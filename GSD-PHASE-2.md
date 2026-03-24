# GSD Phase 2

**Phase:** Design-Now-Build-Later Schemas + Readiness Engine
**Status:** Package complete. Discrete scoring thresholds from IMPL-BRIEF (not linear). Label bands: "Good to go" / "Take it easy" / "Recovery day". Per-input breakdown + staleness dots in dashboard. Migrations 010-012 verified: apply + idempotent re-run on fresh DB. Server 144 tests/zero lint. Apex 110 tests (1 pre-existing)/zero new lint/APK builds.
**Source:** IMPLEMENTATION-ROADMAP.md Phase 2; ADR-002, ADR-003, ADR-005; Decision Register D-04, D-13, D-15, P-07
**Target repos:** Health-Platform-Desktop (migrations 010-012), Apex (readiness engine + dashboard)
**Estimated effort:** 2-3 sessions
**Hard prerequisites:** Package 0A complete, Package 0B complete

---

## 1. Objective and Scope

**Objective:** Reserve schema space for nutrition, supplements, and coaching without activating those pillars yet, and replace Apex's current hardcoded readiness heuristic with a configurable readiness engine and richer dashboard display.

**In scope:**
- Migration 010: nutrition tables created empty on the server
- Migration 011: supplement tables created empty on the server
- Migration 012: coaching conversation tables created empty on the server
- Client-side readiness engine with configurable weights and staleness handling
- Dashboard readiness card refactor: aggregate score, per-input breakdown, staleness indicators, and "sync to update" empty state
- Readiness output contract defined for Phase 3 workout-generation requests

**Out of scope:**
- Nutrition, supplement, or coaching CRUD endpoints
- Zod schemas, route files, or data import jobs for the future pillars
- Workout generation logic
- Training-load readiness input activation (depends on Phase 3 workout-history flow)
- Alternative health providers
- New subjective-feel capture UI
- Any work that re-opens the settled client sync architecture

---

## 2. Why Phase 2 Matters

Phase 2 has two independent jobs:

1. Lock the future-pillar server schemas now so later pillars do not force migration churn into the workout-first MVP.
2. Produce a readiness result shape that Phase 3 can consume without re-designing the client/server contract at the handoff.

This phase is not blocked by Phase 1. The server migrations (010-012) and the client readiness work can run in parallel with the Hevy adapter work.

---

## 3. Current State (Code-Grounded)

**Client readiness today:**
- `DashboardViewModel.computeReadiness()` computes a label from BP + sleep + HRV only, using fixed thresholds and no persisted config.
- `DashboardScreen` renders only a label + reason string. There is no per-input breakdown, no staleness treatment, and no "sync to update" state.
- Current readiness data comes from `health_sync` SharedPreferences snapshot keys, not from a repository layer or a persisted inbound read model.

**Client data constraints:**
- Package 0B is complete, so `HealthDataProvider` now exists as a standard health-domain seam.
- The app still has no durable local workout-history read model; `ActivityViewModel` reads workouts live from the server. Training-load input therefore remains dormant in Phase 2.
- No subjective-feel input path exists in the current app code. The engine must support the slot without requiring a new prompt flow in this phase.

**Server schema constraints:**
- D-15 requires design-now-build-later tables for nutrition and supplements.
- P-07 requires conversation-history persistence designed during architecture, even though coaching execution is later.
- ADR-002 requires idempotent, pillar-grouped migrations with status header comments.

---

## 4. Task Decomposition

### Task 1: Migration 010 — Nutrition Tables

**Purpose:** Create the empty nutrition schema with provenance and quality fields from day one.

**File:** `migrations/010_nutrition_tables.sql`

**Tables to create:**
1. `foods`
2. `food_entries`
3. `nutrition_targets`

**Required constraints:**
- D-13 food-entry provenance field exists now, not later
- D-04 source/quality distinction exists now, not later
- Migration header comment: `-- PILLAR: nutrition | Status: designed, not yet populated`

**Completion criteria:**
- Migration applies cleanly on a fresh and adopted DB
- Re-run is idempotent
- No routes, schemas, or seed jobs are introduced

### Task 2: Migration 011 — Supplement Tables

**Purpose:** Create the empty supplement schema now so later supplement work does not back-fit into the workout MVP.

**File:** `migrations/011_supplement_tables.sql`

**Tables to create:**
1. `supplements`
2. `supplement_entries`
3. `supplement_stack`
4. `supplement_interactions`

**Required constraints:**
- Migration header comment: `-- PILLAR: supplements | Status: designed, not yet populated`
- Tables are additive only
- No curated seed data in this phase

**Completion criteria:**
- Migration applies cleanly and idempotently
- Table/index set matches the planning brief
- No supplement routes, client screens, or reminders ship in this phase

### Task 3: Migration 012 — Coaching Tables

**Purpose:** Reserve server storage for conversation history and engagement tracking before coaching ships.

**File:** `migrations/012_coaching_tables.sql`

**Tables to create:**
1. `conversations`
2. `conversation_messages`
3. `engagement_events`

**Required constraints:**
- Migration header comment: `-- PILLAR: coaching | Status: designed, not yet populated`
- Conversation storage is server-side per P-07
- No Claude orchestration or endpoints in this phase

**Completion criteria:**
- Migration applies cleanly and idempotently
- Parent/child FKs are correct
- No active coaching runtime is introduced

### Task 4: Readiness Engine and Config Store

**Purpose:** Replace the inline dashboard heuristic with a testable, configurable computation unit.

**Files affected:**
- New: readiness engine package/files in Apex
- Modified: `DashboardViewModel.kt`
- Modified: SharedPreferences key/config wiring

**What it produces:**
1. Engine-owned input model with slots for sleep, BP, HRV, subjective, training load
2. Config store for weights, staleness thresholds, and score bands
3. Staleness handling:
   - `< 12h` normal
   - `12-24h` degraded weight
   - `> 24h` excluded
4. Correct re-weighting when an input is missing or excluded
5. Phase-2-safe defaults:
   - HRV weight `0` until A-01 is positive
   - subjective slot supported but dormant if no value exists
   - training-load slot present but dormant until Phase 3

**Completion criteria:**
- Engine returns a 0-100 aggregate score when at least one eligible input exists
- Engine returns a no-score state when all inputs are stale/missing
- Engine preserves current label semantics at a high level (`Good to go`, `Take it easy`, `Recovery day`) while making the calculation inspectable

### Task 5: Dashboard Readiness Display Upgrade

**Purpose:** Make readiness explain itself instead of showing only a single label and one reason string.

**Files affected:**
- Modified: `DashboardViewModel.kt`
- Modified: `DashboardScreen.kt`

**What changes:**
- Replace direct use of `computeReadiness()` with engine-backed state
- Show per-input cards/rows with:
  - score
  - freshness/staleness state
  - contribution reason
- Show "sync to update readiness" when all inputs are excluded
- Keep current dashboard flow prefs-backed; do not introduce a new repository layer in this phase

**Completion criteria:**
- Dashboard renders aggregate score + breakdown
- Stale inputs are visible, not silently folded into the score
- No regressions to the rest of the dashboard summary cards

### Task 6: Tests

**Purpose:** Lock the new behavior down before Phase 3 starts depending on it.

**Files affected:**
- New: readiness engine unit tests
- Modified: dashboard viewmodel tests
- Optional: migration verification coverage in Health-Platform-Desktop

**Required coverage:**
- all-inputs-present
- missing-HRV
- missing-all
- stale-inputs
- config override / weight re-weighting
- migrations 010-012 apply cleanly and idempotently

---

## 5. Recommended Execution Order

```
Server track:
  Task 1 (010)
  Task 2 (011)
  Task 3 (012)

Client track:
  Task 4 (engine + config)
    -> Task 5 (dashboard display)
      -> Task 6 (tests)
```

The server track and client track can run in parallel. The only shared dependency is that both rely on already-complete Phase 0 infrastructure.

---

## 6. A-01 Interaction

| If A-01 resolves positive before/during Phase 2 | If A-01 remains unresolved or negative |
|------------------------------------------------|---------------------------------------|
| Set HRV weight to 0.25 in config defaults | Keep HRV weight at 0 |
| Display HRV as a scored readiness input | Display HRV only if data exists; otherwise exclude cleanly |
| No architecture changes required | No architecture changes required |

**A-01 does not block Phase 2 start.** It changes an initial config value, not the readiness architecture.

---

## 7. Definition of Done

Phase 2 is complete when ALL of the following are true:

- [ ] Migration 010 creates empty nutrition tables with provenance/quality fields
- [ ] Migration 011 creates empty supplement tables
- [ ] Migration 012 creates empty coaching tables
- [ ] All three migrations are idempotent and use pillar status headers
- [ ] Readiness engine computes a 0-100 score from available inputs
- [ ] HRV can be enabled/disabled by config without code changes
- [ ] Dashboard shows per-input readiness breakdown and staleness
- [ ] Dashboard shows "sync to update readiness" when all inputs are stale or missing
- [ ] Training-load input remains dormant without breaking the engine
- [ ] Unit tests cover engine and dashboard behavior
- [ ] CI is green

---

## 8. Handoff to Phase 3

Phase 3 can begin once:
- the readiness engine produces a stable request payload shape for workout generation,
- the client can explain readiness input-by-input,
- and the future-pillar migrations are applied so later feature work does not reopen schema planning.

The next contract to preserve is simple:
- Phase 2 owns the readiness payload shape.
- Phase 3 consumes it without recomputing readiness on the server.
