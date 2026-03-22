# GSD Phase 1

**Phase:** Server Hevy Adapter + Workout Schema
**Status:** Package complete — 144 tests pass, zero lint. Three-state sync_log semantics: `'success'` (real fetch), `'degraded'` (API failure), `'skipped'` (cache fresh). Only `'success'` advances the freshness watermark. Pending: live DB adoption + exercise override real-ID mapping.
**Source:** IMPLEMENTATION-ROADMAP.md Phase 1; ADR-001, ADR-002, ADR-004
**Target repo:** Health-Platform-Desktop
**Estimated effort:** 2-3 sessions
**Hard prerequisite:** Package 0A complete (migration runner operational on live DB)

---

## 1. Objective and Scope

**Objective:** Build the server-side Hevy abstraction layer and create the workout generation schema. After Phase 1, the server caches Hevy workout and exercise template data reliably, and the database schema supports workout generation (Phase 3).

**In scope:**
- Refactor existing `src/services/hevyClient.js` into a caching adapter with rate limit tracking, error classification, and cache-through reads
- Exercise template cache table (`hevy_exercise_cache`)
- Migration 009: 4 workout-generation tables + 1 exercise-template cache table + 1 column addition + 1 bug fix
- Seed `exercise_muscle_overrides` with ~55 curated rows
- Preserve `hevy_routine_id` from Hevy API in `workout_sessions`
- Update `HevyClient.transformWorkout()` to retain `routine_id`
- Refactor `POST /api/sync/hevy/workouts` route to delegate to new adapter

**Out of scope:**
- Workout generation logic or endpoint (Phase 3)
- Readiness engine (Phase 2)
- Client-side changes (Apex Android)
- Design-now-build-later migrations 010-012 (Phase 2)
- Alternative health providers
- Routine creation / push-to-Hevy (conditional on VD-1, Phase 3 scope)
- Full mesocycle generation

---

## 2. Why Phase 1 Is on the Critical Path

Phase 3 (generation MVP) depends on:
1. Cached Hevy workout history for personalization — **Phase 1 delivers this**
2. `exercise_muscle_overrides` seed data for volume attribution — **Phase 1 delivers this**
3. Readiness engine context — Phase 2 delivers this (parallel track)

Without Phase 1, generation has no workout history to draw from.

---

## 3. Current Server State (Code-Grounded)

**Existing Hevy integration:**
- `src/services/hevyClient.js` — `HevyClient` class with `request()`, `getAllWorkouts()`, `getAllExerciseTemplates()`, `getRoutines()`, `transformWorkout()`
- Already has: 3-retry logic, 429 handling with `Retry-After`, 5xx retry, 10s timeout
- `transformWorkout()` drops `routine_id` (confirmed in R-5 and code: line 214-228 maps `hevy_id` but not `routine_id`)
- `src/routes/sync.js` — `POST /hevy/workouts` route creates `HevyClient`, calls `getAllWorkouts()`, inserts to `workout_sessions` + `workout_sets`
- No caching layer — every sync is a live API call
- No exercise template persistence

**Existing tables relevant to Phase 1:**
- `workout_sessions` — has `hevy_id VARCHAR(100)`, no `hevy_routine_id`
- `workout_sets` — FK to `workout_sessions`, includes `exercise_id`, `weight_kg`, `reps`, `rpe`
- `activity_summaries` — has global `UNIQUE(activity_date)` bug

---

## 4. Task Decomposition

### Task 1: Migration 009 — Workout Generation Tables

**Purpose:** Create the schema foundation for workout generation and fix the activity_summaries bug.

**File:** `migrations/009_workout_generation_tables.sql`

**Tables to create:**
1. `exercise_muscle_overrides` — system-scoped refinement (~55 rows), `uq_override_exercise` on `hevy_exercise_id`
2. `generated_routines` — prescribed workout proposals with lifecycle status, nullable `hevy_routine_id`
3. `generated_routine_exercises` — per-exercise details with reasoning (D-05), FK CASCADE to `generated_routines`
4. `progression_snapshots` — weekly volume materialization (derived/optional), JSONB fields
5. `workout_sessions` — ADD COLUMN `hevy_routine_id VARCHAR(100)`, partial index

**Bug fix included:**
- `activity_summaries` — drop global unique, add `(user_id, activity_date)` unique

**All statements use `IF NOT EXISTS` / `DO $$ ... END $$` for idempotency per ADR-002 §2.**

**Completion criteria:**
- `npm run migrate` applies 009 on test DB, creates all tables and indexes
- Re-run is idempotent no-op
- `schema_migrations` shows version 009

**Risk:** Column-level schema decisions (JSONB vs normalized columns for readiness_context, TEXT[] for secondaries) are defined in ADR-004 and locked. No design ambiguity remains.

---

### Task 2: Exercise Override Seed Data

**Purpose:** Populate `exercise_muscle_overrides` with ~55 curated rows mapping Hevy's coarse muscle groups to RP-granularity groups.

**File:** `scripts/seed-exercise-overrides.js`

**What it produces:**
- Standalone Node.js script (CommonJS, same pattern as `scripts/migrate.js`)
- Connects to DB, inserts ~55 rows using `ON CONFLICT (hevy_exercise_id) DO UPDATE` (idempotent — re-run overwrites with latest curation)
- Covers: 42 shoulder exercises → front/side/rear delt split, ~13 full_body exercises → specific primary groups
- Each row: `hevy_exercise_id`, `refined_primary`, `refined_secondaries[]`, `source = 'system_curated'`, optional `notes`

**Completion criteria:**
- Script runs without error
- `SELECT count(*) FROM exercise_muscle_overrides` returns ~55
- Re-run updates existing rows (idempotent)
- Spot-check: Lateral Raise → `side_delts`, Overhead Press → `front_delts`, Face Pull → `rear_delts`, Kettlebell Swing → `glutes`

**Risk:** This is a data curation task requiring human judgment for ~10 ambiguous exercises. Budget 1-2 hours for exercise-by-exercise review. The Hevy exercise template IDs must be confirmed from the actual API response (use cached templates from Task 3 or live API call).

---

### Task 3: Hevy Adapter — Cache Layer + Rate Limit Tracking

**Purpose:** Extend `HevyClient` into a caching adapter per ADR-001. Add exercise template caching. Add rate limit state tracking.

**Files affected:**
- Modified: `src/services/hevyClient.js` — add caching methods, rate limit state, cache-through reads
- New: exercise template cache table added in migration 009 (or separate in `hevy_exercise_cache` table if not bundled)

**What it produces:**

Extend `HevyClient` (or refactor into `HevyAdapter`) with:
1. **Workout cache-through read:** `getCachedWorkouts(since)` → reads from `workout_sessions` first, fetches from API only if cache is stale beyond tolerance, updates cache
2. **Exercise template cache:** `getCachedExerciseTemplates()` → reads from `hevy_exercise_cache`, fetches from API if cache older than 24h, updates cache
3. **Rate limit state:** track request count per time window, respect `Retry-After` headers, exponential backoff with jitter on 429
4. **Error classification:** transient (timeout, 5xx, network) → retry; rate-limited (429) → backoff + stale cache; permanent (4xx auth) → log + skip
5. **`fetched_at` timestamp** on every cached record
6. **Configurable sync interval** — default Tier 2 (30 min periodic, 1/5 min manual cap). Stored as module-level constants, easy to adjust after H-06.

**Completion criteria:**
- `getCachedWorkouts()` returns data from DB when fresh, fetches from API when stale
- `getCachedExerciseTemplates()` returns 431+ templates from cache after first fetch
- 429 response triggers backoff and returns stale cache with staleness flag
- Connection timeout returns stale cache with staleness flag
- Successful fetch updates `fetched_at` on cached records

**Risk:** Largest implementation task. Build incrementally: workout caching first (critical path), exercise templates second.

---

### Task 4: Preserve `routine_id` in Workout Sync

**Purpose:** Stop dropping the `routine_id` field during Hevy workout transformation.

**Files affected:**
- Modified: `src/services/hevyClient.js` — `transformWorkout()` static method
- Modified: `src/routes/sync.js` — INSERT query for `workout_sessions` includes `hevy_routine_id`

**Changes:**
1. In `transformWorkout()`, add `hevy_routine_id: hevyWorkout.routine_id || null` to the returned `session` object
2. In `src/routes/sync.js`, add `hevy_routine_id` to the INSERT column list and values

**Completion criteria:**
- A workout synced from Hevy that was started from a routine has `hevy_routine_id` populated
- A workout synced from Hevy without a routine has `hevy_routine_id = NULL`
- Existing sync behavior unchanged for all other fields

**Risk:** Low. One field addition. Verify with a real Hevy sync after deployment.

---

### Task 5: Refactor Sync Route to Use Adapter

**Purpose:** The existing `POST /api/sync/hevy/workouts` route creates a raw `HevyClient` and calls the API directly. Refactor to use the adapter's caching layer.

**Files affected:**
- Modified: `src/routes/sync.js` — replace direct `HevyClient` usage with adapter cache-through calls

**What changes:**
- Instead of `hevy.getAllWorkouts(syncSince)` → use adapter's `getCachedWorkouts(syncSince)` which handles caching internally
- The adapter decides whether to call the API or serve from cache
- The route no longer manages API calls directly

**Completion criteria:**
- `POST /api/sync/hevy/workouts` still works end-to-end
- Workouts are cached after sync
- Subsequent reads serve from cache when fresh

**Risk:** Must not break the existing Apex client's manual Hevy sync trigger.

---

### Task 6: Tests

**Purpose:** Cover adapter caching, backoff, error classification, and migration integrity.

**Files affected:**
- Modified: `src/__tests__/hevyClient.test.js` — extend with cache-through and backoff tests
- New: migration 009 verification (run against fresh + adopted test DBs)

**Test coverage:**
- Cache-through: stale cache → live fetch → updated cache
- Cache-through: live fetch fails → stale cache returned with flag
- 429 handling: backoff delays, stale cache fallback
- Error classification: transient vs permanent
- Exercise template caching: 24h TTL respected
- Migration 009: all tables created, indexes present, idempotent re-run

**Completion criteria:**
- All new tests pass
- Existing `hevyClient.test.js` tests still pass
- `npm test` green (Vitest)

---

## 5. Recommended Execution Order

```
Task 1 (migration 009)
  → Task 2 (seed data — needs override table from Task 1)
  → Task 3 (adapter cache layer — can start in parallel with Task 2)
    → Task 4 (routine_id preservation — can be concurrent with Task 3)
    → Task 5 (route refactor — needs adapter from Task 3)
      → Task 6 (tests — needs everything above)
```

Tasks 3 and 4 can run in parallel. Task 2 (seed data curation) is independent of adapter work and can run concurrently.

---

## 6. H-06 and VD-1 Interaction

### H-06 (Hevy Rate Limits)

| If resolved before Phase 1 | If unresolved during Phase 1 |
|----------------------------|------------------------------|
| Set sync interval to measured tier | Use Tier 2 defaults (30 min periodic, 1/5 min manual cap) |
| Adjust backoff parameters | Conservative exponential backoff with jitter |
| No code changes needed — tier is a config constant | Adjust constant after H-06 completes |

### VD-1 (Hevy Routine Creation)

| If VD-1 positive | If VD-1 negative or untested |
|------------------|------------------------------|
| `hevy_routine_id` column populated on push path | Column exists but always NULL |
| Routine cache table useful | Routine cache deferred |
| No Phase 1 code changes — push is Phase 3 | No Phase 1 code changes |

**Neither H-06 nor VD-1 blocks Phase 1 start or completion.** Both affect config values and optional features.

---

## 7. Definition of Done

Phase 1 is complete when ALL of the following are true:

- [ ] Migration 009 applied: 4 workout-generation tables + 1 exercise-template cache table + 1 column + 1 bug fix, all idempotent
- [ ] `exercise_muscle_overrides` seeded with ~55 curated rows
- [ ] Hevy adapter caches workouts to PostgreSQL with `fetched_at` timestamps
- [ ] Hevy adapter caches exercise templates with 24h staleness tolerance
- [ ] Adapter handles 429 with exponential backoff + stale cache fallback
- [ ] `transformWorkout()` preserves `routine_id` → `hevy_routine_id`
- [ ] `POST /api/sync/hevy/workouts` delegates to adapter
- [ ] `npm test` passes (existing + new tests)
- [ ] Tyler's real Hevy workout history confirmed cached and retrievable via API
- [ ] No new lint warnings

---

## 8. Explicit Non-Goals

- No workout generation logic (Phase 3)
- No generation API endpoint (Phase 3)
- No client-side UI changes
- No readiness engine integration (Phase 2)
- No routine push-to-Hevy (conditional on VD-1, Phase 3 scope)
- No nutrition/supplement/coaching schema (Phase 2 migrations 010-012)
- No RP volume landmark seeding (Phase 3 — generation needs it, not the adapter)

---

## 9. Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Exercise override curation takes longer than expected | Medium | Budget 2 hours. Ambiguous exercises (~10) can default to Hevy's group initially and be refined later. |
| Hevy API response format changes | Medium | Adapter normalizes responses. Raw JSONB stored in cache for debugging. |
| Cache invalidation bugs (stale served as fresh) | High | Every record carries `fetched_at`. Staleness check is explicit, not implicit. |
| Adapter complexity balloons | Medium | Build incrementally: workouts first, templates second. Keep adapter methods small and testable. |
| `activity_summaries` constraint fix fails on existing data | Low | Only applies if duplicate `(activity_date)` rows exist across users. Current app is single-user — no duplicates possible. |

---

## 10. Handoff to Phase 2 + Phase 3

### What Phase 1 enables for Phase 3

- Generation endpoint reads cached workouts from adapter (no live Hevy calls)
- Exercise attribution resolved via `exercise_muscle_overrides` + Hevy template cache
- `generated_routines` + `generated_routine_exercises` tables ready for generation output
- `workout_sessions.hevy_routine_id` ready for prescribed-to-actual linkage

### What Phase 1 enables for Phase 2

- Migration runner proven with 009 (confirms runner handles real migration, not just bootstrap)
- Database schema established for all workout generation tables

### After Phase 1

Phase 2 (readiness engine + empty pillar schemas) and Phase 3 (generation MVP) can proceed. Phase 3 depends on both Phase 1 (this) and Phase 2 (readiness context).
