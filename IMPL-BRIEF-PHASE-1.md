# Implementation Brief: Phase 1

**Phase:** Server Hevy Adapter + Workout Schema
**Target repo:** Health-Platform-Desktop
**Module system:** CommonJS (`require` / `module.exports`)
**DB driver:** `pg@^8.16.3` via `src/config/database.js`
**Test framework:** Vitest (`npm test`)

---

## 1. Objective

Build the server-side Hevy caching adapter and create the workout generation schema (migration 009). After Phase 1, the server reliably caches Hevy data and the database is ready for workout generation features.

---

## 2. Scope

**In scope:**
- `migrations/009_workout_generation_tables.sql` — 4 workout-generation tables, 1 exercise-template cache table, 1 column addition, 1 constraint fix
- `scripts/seed-exercise-overrides.js` — ~55 curated override rows
- `src/services/hevyClient.js` refactored into caching adapter with rate limit tracking
- `src/routes/sync.js` refactored to use adapter
- Tests for adapter caching, backoff, error classification
- `hevy_exercise_cache` table (can be in migration 009 or separate)

**Out of scope:**
- Workout generation endpoint or logic (Phase 3)
- Client-side changes (Apex Android repo)
- Readiness engine (Phase 2)
- Migrations 010-012 (Phase 2)
- Routine push-to-Hevy
- RP volume landmark seed data (Phase 3)

---

## 3. Required Deliverables

### 3.1 Migration 009: `migrations/009_workout_generation_tables.sql`

Follow ADR-002 conventions: `NNN_description.sql`, all statements idempotent, header comment with pillar tag.

```sql
-- PILLAR: workout_generation | Status: active
-- Migration 009: Workout generation tables, exercise overrides, and related indexes
```

**Tables to create (all use `CREATE TABLE IF NOT EXISTS`):**

#### `exercise_muscle_overrides`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `hevy_exercise_id` | `VARCHAR(20) NOT NULL` | `UNIQUE` (via constraint `uq_override_exercise`) | Hevy IDs are ~8 chars; 20 gives headroom |
| `refined_primary` | `VARCHAR(50) NOT NULL` | | RP-granularity group name |
| `refined_secondaries` | `TEXT[] NOT NULL DEFAULT '{}'` | | Array of secondary muscle groups |
| `source` | `VARCHAR(30) NOT NULL DEFAULT 'system_curated'` | | Future: `user_override`, `ai_suggested` |
| `notes` | `TEXT` | | Optional curation notes |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

**No `user_id` column.** This is system-scoped (ATTR decision, locked).

Index: `idx_override_exercise ON exercise_muscle_overrides(hevy_exercise_id)`

#### `generated_routines`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `readiness_score` | `DECIMAL(4,1)` | | 0-100 score from client |
| `readiness_context` | `JSONB` | | Snapshot of all readiness inputs |
| `mesocycle_week` | `INTEGER` | | Current week in mesocycle |
| `mesocycle_phase` | `VARCHAR(30)` | | e.g., accumulation, deload |
| `title` | `VARCHAR(255) NOT NULL` | | Human-readable routine name |
| `reasoning_summary` | `TEXT` | | Overall generation rationale |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'draft'` | | draft/presented/accepted/rejected/pushed |
| `presented_at` | `TIMESTAMPTZ` | | When shown to user |
| `decided_at` | `TIMESTAMPTZ` | | When user accepted/rejected |
| `hevy_routine_id` | `VARCHAR(100)` | | Nullable — populated only if VD-1 positive and routine pushed |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Indexes:
- `idx_gen_routine_status ON generated_routines(status)`
- `idx_gen_routine_hevy ON generated_routines(hevy_routine_id) WHERE hevy_routine_id IS NOT NULL`

#### `generated_routine_exercises`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `generated_routine_id` | `UUID NOT NULL` | `REFERENCES generated_routines(id) ON DELETE CASCADE` | |
| `exercise_template_id` | `VARCHAR(20) NOT NULL` | | Hevy exercise template ID |
| `exercise_name` | `VARCHAR(255) NOT NULL` | | Denormalized for display |
| `ordering` | `INTEGER NOT NULL` | | Exercise position in routine |
| `target_sets` | `INTEGER NOT NULL` | | |
| `target_reps` | `INTEGER` | | Nullable for time-based exercises |
| `target_weight_kg` | `DECIMAL(6,2)` | | |
| `target_rpe` | `DECIMAL(3,1)` | | |
| `resolved_primary` | `VARCHAR(50) NOT NULL` | | Snapshot of resolved muscle group at gen time |
| `resolved_secondaries` | `TEXT[] NOT NULL DEFAULT '{}'` | | Snapshot at gen time |
| `reasoning` | `TEXT NOT NULL` | | D-05: per-exercise rationale |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Index: `idx_gen_ex_routine ON generated_routine_exercises(generated_routine_id)`

#### `progression_snapshots`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `snapshot_date` | `DATE NOT NULL` | | |
| `period_days` | `INTEGER NOT NULL DEFAULT 7` | | |
| `volume_by_muscle` | `JSONB NOT NULL` | | `{"chest": 12, "front_delts": 6, ...}` |
| `landmark_status` | `JSONB NOT NULL` | | Per-muscle MEV/MAV/MRV status |
| `exercise_signals` | `JSONB NOT NULL` | | Per-exercise 2-for-2 signals |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Unique index: `idx_snapshot_date ON progression_snapshots(user_id, snapshot_date, period_days)`

#### `hevy_exercise_cache`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `hevy_exercise_id` | `VARCHAR(20) NOT NULL UNIQUE` | | |
| `exercise_name` | `VARCHAR(255) NOT NULL` | | |
| `primary_muscle_group` | `VARCHAR(50)` | | Raw Hevy group |
| `secondary_muscle_groups` | `TEXT[]` | | Raw Hevy groups |
| `hevy_response` | `JSONB` | | Full API response for debugging |
| `fetched_at` | `TIMESTAMPTZ NOT NULL` | | When last fetched from API |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Index: `idx_hec_fetched ON hevy_exercise_cache(fetched_at)`

#### Existing table modifications

**`workout_sessions`:**
```sql
ALTER TABLE workout_sessions ADD COLUMN IF NOT EXISTS hevy_routine_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_workout_hevy_routine
    ON workout_sessions(hevy_routine_id) WHERE hevy_routine_id IS NOT NULL;
```

**`activity_summaries` bug fix:**
```sql
-- Drop global unique, add user-scoped unique
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'activity_date_uq' AND table_name = 'activity_summaries')
  THEN ALTER TABLE activity_summaries DROP CONSTRAINT activity_date_uq; END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'uq_activity_summary_user_date' AND table_name = 'activity_summaries')
  THEN ALTER TABLE activity_summaries
    ADD CONSTRAINT uq_activity_summary_user_date UNIQUE (user_id, activity_date); END IF;
END $$;
```

---

### 3.2 Seed Script: `scripts/seed-exercise-overrides.js`

Standalone CommonJS script. Same connection pattern as `scripts/migrate.js` (dotenv, own `pg.Client`, no import from `src/`).

**Behavior:**
1. Connect to DB
2. INSERT ~55 rows into `exercise_muscle_overrides` using `ON CONFLICT (hevy_exercise_id) DO UPDATE SET refined_primary = EXCLUDED.refined_primary, refined_secondaries = EXCLUDED.refined_secondaries, source = EXCLUDED.source, notes = EXCLUDED.notes, updated_at = NOW()`
3. Log count of inserted/updated rows
4. Exit

**Idempotent:** Re-run overwrites existing rows with latest curation values.

**Data sources for curation:**
- Hevy exercise template names and IDs (from live API call or `hevy_exercise_cache` after Task 3)
- RP muscle group assignments per EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md §2

---

### 3.3 Hevy Adapter Refactor: `src/services/hevyClient.js`

Extend the existing `HevyClient` class (or rename to `HevyAdapter`). Keep the existing `request()`, retry, and pagination methods. Add:

#### New methods

**`async getCachedWorkouts(since, userId)`**
1. Query `workout_sessions` for workouts since `since` where `source = 'hevy'`
2. Check `sync_log` table for last successful Hevy workout sync (`sync_ended_at`) to determine staleness — this is the single freshness source (resolved: no separate metadata table)
3. If stale (older than sync interval): call `getAllWorkouts(since)`, upsert new workouts to DB, update `fetched_at`
4. If rate-limited or API error: return existing cached data with `{ stale: true }` flag
5. Return workout data

**`async getCachedExerciseTemplates()`**
1. Query `hevy_exercise_cache` for all templates
2. Check oldest `fetched_at` — if any record older than 24h (or cache empty): refresh from API
3. Upsert templates to `hevy_exercise_cache` with current timestamp
4. Return templates

**Rate limit state (module-level):**
```javascript
let rateLimitState = {
  remaining: Infinity,
  resetAt: null,
  backoffUntil: null,
};
```

Update after every API response (read `X-RateLimit-Remaining`, `Retry-After` headers). Before each API call, check `backoffUntil`. If current time < `backoffUntil`, skip the call and return stale cache.

**Sync interval constants:**
```javascript
const SYNC_INTERVAL_MS = 30 * 60 * 1000;   // 30 min (Tier 2 default)
const MANUAL_COOLDOWN_MS = 5 * 60 * 1000;   // 5 min between manual syncs
const TEMPLATE_TTL_MS = 24 * 60 * 60 * 1000; // 24h
```

These are module constants — adjust after H-06 validation.

---

### 3.4 `transformWorkout()` Fix

In `HevyClient.transformWorkout()`, add one field to the returned `session` object:

```javascript
// Line ~214, inside the return statement's session object:
hevy_routine_id: hevyWorkout.routine_id || null,
```

In `src/routes/sync.js`, add `hevy_routine_id` to the INSERT query:
- Add to column list
- Add `$N` parameter
- Pass `session.hevy_routine_id` in the values array

---

### 3.5 Sync Route Refactor: `src/routes/sync.js`

The `POST /hevy/workouts` handler currently:
1. Creates `new HevyClient()`
2. Calls `hevy.getAllWorkouts(syncSince)` directly
3. Transforms and inserts each workout

After refactor:
1. Uses adapter's `getCachedWorkouts()` which handles caching internally
2. The adapter decides whether to call the API or serve from cache
3. The route still handles the INSERT logic for new workouts (adapter returns data, route writes to DB)

Alternative: adapter handles the INSERT too. Either approach is acceptable. The key constraint: the route must not call the Hevy API directly.

---

## 4. Behavioral Requirements

### 4.1 Cache-through reads

- Workout reads: check DB first, API only if stale
- Exercise template reads: check `hevy_exercise_cache` first, API only if >24h old
- Every cached record has `fetched_at` timestamp
- Stale cache returned with flag when API unavailable

### 4.2 Rate limit handling

- Track rate limit state from API response headers
- Exponential backoff with jitter on 429
- During backoff: return stale cache, do not call API
- Log rate limit events

### 4.3 Error classification

| Response | Classification | Action |
|----------|---------------|--------|
| 200 | Success | Update cache |
| 429 | Rate-limited | Backoff + stale cache |
| 4xx (non-429) | Permanent | Log error, skip, don't retry |
| 5xx | Transient | Retry (existing 3-retry logic) |
| Timeout/network | Transient | Retry, then stale cache |

### 4.4 Idempotency

- Migration 009 is idempotent (all `IF NOT EXISTS`)
- Seed script uses `ON CONFLICT DO UPDATE`
- Adapter upserts to cache tables (no duplicate rows)

---

## 5. Safety Constraints

1. **Migration 009 must not break existing workout sync.** All new tables are additive. The `workout_sessions` column addition is `ADD COLUMN IF NOT EXISTS`.
2. **`transformWorkout()` change must be backward-compatible.** Adding one nullable field to the return object. No existing field is removed or renamed.
3. **Adapter refactor must not break the Apex client's manual sync trigger.** `POST /api/sync/hevy/workouts` must continue to work identically from the client's perspective.
4. **Seed script must not truncate or delete existing override data.** Uses `ON CONFLICT DO UPDATE`, not `DELETE + INSERT`.
5. **Cache-through must never block on a failed API call.** Timeout → return stale cache. 429 → return stale cache. Never hang.

---

## 6. Acceptance Criteria

- [ ] Migration 009 creates 4 workout-generation tables + 1 exercise-template cache table + 1 column + 1 constraint fix
- [ ] Migration 009 is idempotent (re-run is no-op)
- [ ] `exercise_muscle_overrides` has ~55 rows after seed script
- [ ] Seed script is idempotent (re-run updates existing rows)
- [ ] Spot-check overrides: Lateral Raise = `side_delts`, Overhead Press = `front_delts`, Face Pull = `rear_delts`
- [ ] Hevy adapter fetches workouts and caches to PostgreSQL with `fetched_at`
- [ ] Hevy adapter fetches exercise templates and caches with 24h TTL
- [ ] 429 response triggers backoff and returns stale cache
- [ ] API timeout returns stale cache with flag
- [ ] `transformWorkout()` returns `hevy_routine_id` from Hevy API response
- [ ] `workout_sessions.hevy_routine_id` populated on sync
- [ ] `POST /api/sync/hevy/workouts` delegates to adapter (no direct API calls in route)
- [ ] All existing Hevy-related tests pass
- [ ] New tests cover: cache-through, backoff, error classification, template caching
- [ ] `npm test` passes
- [ ] Tyler's real Hevy workout history cached and retrievable

---

## 7. Implementation Notes

### Execution order

Write migration 009 first (schema). Then seed script (data). Then adapter refactor (logic). Then route refactor. Then tests. This is the safest order — schema exists before code depends on it.

### Exercise override curation

The ~55 rows require reviewing each of 42 shoulder exercises and ~13 full_body exercises against their RP-granularity primary muscle group. Most are obvious from the exercise name (e.g., "Dumbbell Lateral Raise" → `side_delts`). ~10 exercises need judgment calls. Document ambiguous decisions in the `notes` field.

Hevy exercise IDs can be obtained from:
1. The live Hevy API (if available during curation)
2. The `hevy_exercise_cache` table (after adapter fetches templates)
3. The A-02 validation data from prior research (EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md)

### Adapter module structure

Keep it in `src/services/hevyClient.js` for now (avoid premature restructuring). The class can be renamed later if needed. Key: all API calls go through the existing `request()` method which already has retry + 429 handling. The new caching layer wraps around the existing pagination methods.

### Sync interval as config

Store the sync interval as a module-level constant, not in the database. It's a deployment parameter, not a user setting. Adjust after H-06 by changing the constant.

### What to verify before considering Phase 1 complete

1. `npm run migrate` on a test DB creates all tables and indexes
2. `node scripts/seed-exercise-overrides.js` populates ~55 rows
3. Trigger a Hevy sync via `POST /api/sync/hevy/workouts` — workouts cached
4. Call adapter's `getCachedExerciseTemplates()` — 431+ templates cached
5. Verify `workout_sessions` has `hevy_routine_id` column populated for routine-based workouts
6. `npm test` passes
7. Trigger sync again — should serve from cache (check logs for "serving from cache" vs "fetching from API")

---

## 8. Items Requiring Human Action

| Item | When | Why |
|------|------|-----|
| Exercise override curation (~55 rows) | During Task 2 | Requires judgment on ~10 ambiguous exercises |
| H-06 rate limit test | During or after Phase 1 | Determines sync tier constant |
| Package 0A live adoption | Before Phase 1 deployment | Migration runner must be operational on production DB |
| VD-1 routine creation test | Optional during Phase 1 | Only affects whether `hevy_routine_id` is useful; nullable regardless |
