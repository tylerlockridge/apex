# ADR-004: Workout Generation Reconciliation Model

## Status

**Accepted for MVP** — 2026-03-16

## Context

Apex generates workout recommendations (prescribed routines). Tyler executes workouts in Hevy (actual sessions). These diverge: Tyler swaps exercises, adjusts weights, skips sets, adds exercises, or logs workouts that Apex never generated.

The reconciliation problem: how does the system relate what it suggested to what actually happened, and what does progression use as its source of truth?

### Validated API behavior

| Fact | Status | Evidence |
|------|--------|----------|
| Hevy completed workouts include a `routine_id` field linking back to the source routine | **Confirmed** | `GET /v1/workouts` returns `routine_id` as a string (observed format: `"3f179f7a-0fbb-495a-a859-cad1770fcf98"`) on workouts started from a routine |
| Tyler modifies routines during execution | **Confirmed** | "Push A" routine prescribes Decline Bench Press; actual workout logged Chest Press instead. Set counts and weights also diverge. Normal behavior. |
| `HevyClient.transformWorkout()` drops `routine_id` during sync | **Confirmed** | Code inspection. `workout_sessions` has no column for it. |
| Hevy API supports creating or updating routines | **Not validated** | Only `GET /v1/routines` tested. `POST`/`PUT` availability unknown. See VD-1. |

### Planning traceability

| ID | Constraint | Role in this ADR |
|----|-----------|-----------------|
| D-17 | Hevy is source of truth for actual workout data | Progression uses actuals exclusively |
| D-05 | All algorithmic recommendations show reasoning | Generated routines carry per-exercise reasoning |
| D-11 | Semi-autonomous: generate → review → execute | User always reviews before execution |
| D-14 | Server-side workout generation | Server generates; client reviews |
| P-05 | MVP: volume tracking, weight suggestion, MRV flagging | Progression scope for MVP |
| D-10a | Hevy API abstraction interface | All Hevy interactions through server adapter |

---

## Decision

### 1. What a generated routine represents

A generated routine is Apex's recommendation for a workout. It contains:
- Exercises with target sets, reps, and weights
- Per-exercise reasoning (D-05)
- Readiness context snapshot
- Mesocycle position

A generated routine is **not the source of truth for anything**. It is a historical record of what Apex suggested. Progression never queries it.

Lifecycle: `draft` → `presented` → `accepted` / `rejected`. If Hevy routine creation is available (VD-1), an accepted routine may additionally transition to `pushed` and store the resulting external Hevy routine ID.

### 2. Prescribed-to-actual linkage

The linkage between a generated routine and its executed workout is **for explainability only** (D-05). It is not required for progression correctness.

**Path A — Hevy routine creation available (VD-1 confirmed):**

Apex creates a routine in Hevy via the API and stores the returned Hevy routine ID on `generated_routines.hevy_routine_id`. When the completed workout syncs back, its `routine_id` matches. A join on `hevy_routine_id` connects prescribed to actual. This is a hard, unambiguous link using Hevy-native identifiers.

Workouts whose `routine_id` doesn't match any Apex-generated routine are treated as unmatched actuals.

**Path B — Hevy routine creation unavailable (VD-1 negative):**

Apex displays the generated routine in-app. Tyler manually starts his own Hevy routine. `generated_routines.hevy_routine_id` remains NULL. No prescribed-to-actual linkage exists at MVP.

This is acceptable because:
- Progression operates on actuals only (D-17)
- D-05 reasoning is stored at generation time independent of linkage
- MVP features (volume tracking, weight suggestions, MRV flagging) use actual data exclusively

**Both paths:** soft matching (title, date, exercise overlap) is deferred post-MVP.

### 3. Modified execution

Tyler's observed behavior: exercise swaps, weight changes, set count changes, added exercises, skipped exercises, reordered exercises.

**Rule: modifications are not errors.** Apex uses what was actually done. It never flags divergence as wrong. The user is always right about what they actually did (D-11).

| Modification | Handling |
|-------------|---------|
| Exercise swap | Progression uses actual exercise and actual weight/reps |
| Weight / rep change | Progression uses actual values |
| Set count change | Volume tracking uses actual count |
| Exercise added | Counted normally in actual workout |
| Exercise skipped | No impact on progression — actuals only |
| Reorder | No impact — order is irrelevant to volume |

### 4. Unmatched workouts

An unmatched workout is any synced Hevy session with no corresponding Apex-generated routine. This includes:
- Workouts logged without a routine (`routine_id` null)
- Workouts started from non-Apex Hevy routines
- All of Tyler's existing Hevy history (predates Apex v2)

**Treatment: identical to matched workouts for all progression purposes.** Volume tracking, weight progression, MRV proximity — all use actuals regardless. The only difference: no prescribed-vs-actual comparison is available for D-05 explanations.

### 5. Progression source of truth

**`workout_sessions` + `workout_sets` are the sole source of truth for progression.**

Progression never queries `generated_routines`. It queries:
- `workout_sets`: exercise history (weight, reps, RPE per exercise per session)
- `exercise_muscle_overrides`: muscle group attribution (RP-granularity)
- `workout_sessions`: session metadata (date, duration, total volume)

The 2-for-2 rule (P-05): queries the last N `workout_sets` rows for a given `exercise_template_id`. Whether Apex generated the routine is irrelevant.

Volume landmark tracking (P-05): counts `workout_sets` in the trailing 7 days, attributed via `exercise_muscle_overrides`. Whether the workout was prescribed or ad-hoc is irrelevant.

---

## Schema

### Safe to implement now

These tables and fields are independent of VD-1. They can be finalized in migration 009.

#### `generated_routines`

```sql
CREATE TABLE generated_routines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Generation context
    readiness_score DECIMAL(4,1),
    readiness_context JSONB,
    mesocycle_week INTEGER,
    mesocycle_phase VARCHAR(30),

    -- Content
    title VARCHAR(255) NOT NULL,
    reasoning_summary TEXT,

    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    presented_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,

    -- Hevy linkage (nullable — utility depends on VD-1)
    hevy_routine_id VARCHAR(100),

    -- Metadata
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gen_routine_status ON generated_routines(status);
CREATE INDEX idx_gen_routine_hevy ON generated_routines(hevy_routine_id)
    WHERE hevy_routine_id IS NOT NULL;
```

All fields except `hevy_routine_id` are stable. `hevy_routine_id` is safe to create (nullable `VARCHAR(100)` with a partial index costs nothing when empty) but its utility depends on VD-1.

#### `generated_routine_exercises`

```sql
CREATE TABLE generated_routine_exercises (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    generated_routine_id UUID NOT NULL
        REFERENCES generated_routines(id) ON DELETE CASCADE,

    -- Exercise identity
    exercise_template_id VARCHAR(20) NOT NULL,
    exercise_name VARCHAR(255) NOT NULL,
    ordering INTEGER NOT NULL,

    -- Targets
    target_sets INTEGER NOT NULL,
    target_reps INTEGER,
    target_weight_kg DECIMAL(6,2),
    target_rpe DECIMAL(3,1),

    -- Muscle attribution (resolved at generation time via override layer)
    resolved_primary VARCHAR(50) NOT NULL,
    resolved_secondaries TEXT[] NOT NULL DEFAULT '{}',

    -- D-05: per-exercise reasoning
    reasoning TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gen_ex_routine ON generated_routine_exercises(generated_routine_id);
```

All fields are stable. No VD-1 dependency.

#### `workout_sessions` — add column

```sql
ALTER TABLE workout_sessions
    ADD COLUMN IF NOT EXISTS hevy_routine_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_workout_hevy_routine
    ON workout_sessions(hevy_routine_id) WHERE hevy_routine_id IS NOT NULL;
```

This column is **stable** — it captures `routine_id` from the validated read API (`GET /v1/workouts`) regardless of whether routine creation is supported. `HevyClient.transformWorkout()` must be updated to preserve this field instead of dropping it.

`VARCHAR(100)` matches the existing `hevy_id VARCHAR(100)` convention on `workout_sessions`. No FK to `generated_routines` — the join is application-level via `hevy_routine_id`. Most workout sessions will have values that don't correspond to any Apex-generated routine.

#### `progression_snapshots` (derived / optional persistence)

```sql
CREATE TABLE progression_snapshots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    snapshot_date DATE NOT NULL,
    period_days INTEGER NOT NULL DEFAULT 7,

    -- { "chest": 12, "front_delts": 6, ... }
    volume_by_muscle JSONB NOT NULL,

    -- { "chest": { "sets": 12, "mev": 8, "mav": 14, "mrv": 20, "status": "in_range" }, ... }
    landmark_status JSONB NOT NULL,

    -- [{ "exercise_id": "79D0BB3A", "last_weight_kg": 80, "consecutive_target_hits": 2, "suggestion": "increase_weight" }, ...]
    exercise_signals JSONB NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_snapshot_date ON progression_snapshots(snapshot_date, period_days);
```

This table is **derived** — a materialized cache of computations over `workout_sessions` + `workout_sets` + `exercise_muscle_overrides`. It is optional persistence for query convenience, not a foundational progression store. Progression correctness does not depend on it. Never references `generated_routines`.

### Conditional on VD-1

| Field / behavior | If VD-1 positive | If VD-1 negative |
|-----------------|-----------------|-----------------|
| `generated_routines.hevy_routine_id` | Populated on push; used for reconciliation join | Permanently NULL; harmless but unused |
| `generated_routines.status = 'pushed'` | Valid lifecycle state after API push | Not used; lifecycle is `draft → presented → accepted / rejected` |
| Prescribed-to-actual join | Available via `hevy_routine_id` match | Not available at MVP |
| D-05 comparison explanations | "We suggested X, you did Y" | "We suggest X because of your recent history" (no comparison to specific prior suggestion) |
| D-10b fallback scope | Deferred — push works | Immediately relevant — Apex must display routines in-app |

No schema branching is needed. The same columns work for both paths.

---

## Validation Dependency: VD-1

### Hevy API routine creation support

| Aspect | Detail |
|--------|--------|
| **What to test** | Whether `POST /v1/routines` (or equivalent write endpoint) exists and accepts routine definitions |
| **How — Step 1** | Send `POST /v1/routines` with a minimal body (title + 1 exercise + 1 set). Record the response status and any returned routine ID. |
| **How — Step 2** | If Step 1 succeeds: open Hevy, start the created routine, complete the workout, then call `GET /v1/workouts?page=1&pageSize=1` and confirm the completed workout's `routine_id` matches the ID returned in Step 1. |
| **How — Step 3** | If Step 2 confirms the round-trip: test `PUT /v1/routines/{id}` to verify routine updates (needed if Apex regenerates a routine for the same training day). |
| **Success criteria** | Steps 1 + 2 pass: Apex can create a routine, Tyler can start it in Hevy, and the completed workout's `routine_id` traces back to the Apex-created routine. |
| **Blocks** | Whether Hevy push / routine-based linkage is operationally usable. Whether `generated_routines.status` includes `pushed`. Does NOT block migration 009 schema finalization — `hevy_routine_id` fields are safe to create now as nullable columns. |
| **Timing** | Can run in parallel with H-06 rate limit testing. Does not gate migration 009. |
| **If negative** | MVP ships without prescribed-to-actual linkage. All progression features work. Explainability is self-contained (reasoning stored at generation time). D-10b fallback scope decision is activated. |

---

## Deferred Post-MVP

| Item | Why deferred | Prerequisite |
|------|-------------|-------------|
| Soft matching (title + date + exercise overlap) | Progression doesn't need the link; exact matching via VD-1 is preferred if available | VD-1 negative AND user demand for comparison |
| Adherence scoring | Requires linkage + enough matched pairs | VD-1 positive + 4 weeks of data |
| Routine versioning | MVP treats edits as new generations | User demand |
| Mesocycle planning | P-05 scopes MVP to single-workout generation | v2 |
| Prescribed-vs-actual diff UI | Data model supports it; UI is a coaching enhancement | VD-1 positive + coaching feature |
| Exercise substitution analysis | Override layer enables it; analysis logic is additional work | Post-MVP coaching |
| Snapshot retention policy | MVP keeps all snapshots | Storage concern |

---

## Consequences

### Stable (true regardless of VD-1)

- **Progression is independent of generation.** Queries actual Hevy data only. Works on all existing workout history without bootstrapping.
- **Unmatched workouts are first-class.** Tyler's months of pre-Apex Hevy data enters progression normally.
- **D-05 reasoning is stored at generation time.** Preserved even if the algorithm changes. Does not depend on matching to an actual workout.
- **Modifications are expected.** No "your workout didn't match the plan" friction. The user is always right about what they did.
- **Schema works for both VD-1 outcomes.** No migration branching. Same columns, different behavioral paths.

### Conditional on VD-1 positive

- **Hard prescribed-to-actual linkage** via Hevy-native `routine_id`. No heuristics.
- **Richer D-05 explanations** comparing suggestions to actuals.
- **Adherence analysis** becomes possible.

### Negative

- **No FK between `workout_sessions` and `generated_routines`.** Application-level join via `hevy_routine_id`. Intentional: most sessions won't have a corresponding generated routine.
- **JSONB in `progression_snapshots`.** Trades analytical query flexibility for simplicity. Sufficient for MVP queries ("get latest snapshot").
- **If VD-1 negative:** `hevy_routine_id` on `generated_routines` is permanently NULL. Explainability limited to forward-looking reasoning without backward comparison to specific prior suggestions. D-10b fallback scope becomes immediately relevant.

### Neutral

- **`HevyClient.transformWorkout()` requires a one-line change** to preserve `routine_id`. Captures data from the validated read API regardless of VD-1.

---

## Alternatives Considered

### Alternative 1: FK from `workout_sessions` to `generated_routines`

Rejected. Most sessions have no corresponding generated routine. The FK would be permanently NULL for all historical and non-Apex workouts. If VD-1 is negative, no Apex-side `hevy_routine_id` exists to match on.

### Alternative 2: Separate `routine_workout_links` table

Rejected. If VD-1 positive, exact matching via `hevy_routine_id` resolves without ambiguity — no link table needed. If VD-1 negative, no linkage mechanism exists to populate it. Can be introduced post-MVP if soft matching is built.

### Alternative 3: Progression queries `generated_routines`

Rejected. Violates D-17. Creates a dependency between progression and generation. Tyler's existing Hevy data has no generated routines.

---

## Open Questions

### Q1: Hevy routine creation support

Covered by VD-1 above. Highest-priority open question. Architecture is stable regardless; linkage capability depends on it. Overlaps with H-06 rate limit testing window.

### Q2: RP volume landmark reference values

`progression_snapshots.landmark_status` compares volume to MEV/MAV/MRV. Reference values need a source — likely a `volume_landmarks` reference table or `user_settings` with RP defaults. Scoped by OA-2. Not blocked by this ADR.

### Q3: Snapshot computation frequency

MVP: weekly, triggered after Hevy sync. Implementation detail, not architecture-significant.

---

*This ADR defines the reconciliation model between prescribed and actual workout data. It does not define the generation algorithm, coaching UX, or Hevy push mechanism.*
