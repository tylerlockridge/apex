# Implementation Brief: Phase 2

**Phase:** Design-Now-Build-Later Schemas + Readiness Engine
**Target repos:** Health-Platform-Desktop and Apex
**Server stack:** Node.js / Express / PostgreSQL raw SQL migrations
**Client stack:** Kotlin / Jetpack Compose / SharedPreferences-backed summary cache
**Test frameworks:** Vitest (server), JUnit/Robolectric (client)

---

## 1. Objective

Implement the server-side empty schemas for future pillars and replace Apex's current hardcoded readiness heuristic with a configurable, testable readiness engine and dashboard display.

This phase is intentionally split:
- **Server:** schema reservation only
- **Client:** readiness computation and presentation only

No active nutrition, supplement, or coaching features ship in this phase.

---

## 2. Scope

**In scope:**
- `migrations/010_nutrition_tables.sql`
- `migrations/011_supplement_tables.sql`
- `migrations/012_coaching_tables.sql`
- Readiness engine and config store in Apex
- Dashboard readiness refactor in Apex
- Tests for readiness behavior and migration idempotency
- Readiness request payload contract for Phase 3

**Out of scope:**
- Server CRUD endpoints, Zod schemas, route files, or import jobs for nutrition/supplements/coaching
- Subjective-feel capture UI
- Training-load readiness activation
- Any change to the existing outbound sync queue design
- Workout generation logic

---

## 3. Required Deliverables

### 3.1 Migration 010: `migrations/010_nutrition_tables.sql`

Header:

```sql
-- PILLAR: nutrition | Status: designed, not yet populated
-- Migration 010: Empty nutrition schema for workout-first MVP
```

Use ADR-002 conventions throughout: idempotent DDL only, no seed inserts.

#### `foods`

Purpose: canonical cached food catalog plus custom foods.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `name` | `TEXT NOT NULL` | | |
| `brand` | `TEXT` | | NULL for generic foods |
| `barcode` | `TEXT` | | UPC/EAN from Open Food Facts |
| `data_source` | `VARCHAR(30) NOT NULL` | | `usda`, `openfoodfacts`, `custom` |
| `source_id` | `TEXT` | | Upstream FDC/OFF identifier |
| `quality_flag` | `VARCHAR(20) NOT NULL DEFAULT 'verified'` | | `verified`, `community`, `custom` |
| `serving_size_g` | `DOUBLE PRECISION` | | |
| `calories` | `DOUBLE PRECISION` | | per serving |
| `protein_g` | `DOUBLE PRECISION` | | |
| `carbs_g` | `DOUBLE PRECISION` | | |
| `fat_g` | `DOUBLE PRECISION` | | |
| `fiber_g` | `DOUBLE PRECISION` | | |
| `sugar_g` | `DOUBLE PRECISION` | | |
| `sodium_mg` | `DOUBLE PRECISION` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Indexes:
- `idx_foods_barcode` on `barcode` where not null
- text-search/name index per current server conventions

#### `food_entries`

Purpose: immutable nutrition log entries with D-13 provenance at the entry level.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `food_id` | `UUID` | `REFERENCES foods(id)` | nullable for quick-add or deleted-food snapshots |
| `food_name` | `TEXT NOT NULL` | | denormalized snapshot |
| `entry_source` | `VARCHAR(20) NOT NULL` | | `verified`, `estimated`, `corrected`, `custom`, `quick_add` |
| `meal_type` | `VARCHAR(20)` | | `breakfast`, `lunch`, `dinner`, `snack` |
| `servings` | `DOUBLE PRECISION NOT NULL DEFAULT 1.0` | | |
| `calories` | `DOUBLE PRECISION` | | snapshot at log time |
| `protein_g` | `DOUBLE PRECISION` | | snapshot at log time |
| `carbs_g` | `DOUBLE PRECISION` | | snapshot at log time |
| `fat_g` | `DOUBLE PRECISION` | | snapshot at log time |
| `logged_at` | `TIMESTAMPTZ NOT NULL` | | |
| `notes` | `TEXT` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Indexes:
- `idx_fe_user_logged` on `(user_id, logged_at DESC)`

#### `nutrition_targets`

Purpose: date-effective calorie/macro targets.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `effective_date` | `DATE NOT NULL` | | |
| `calories` | `INTEGER NOT NULL` | | |
| `protein_g` | `INTEGER NOT NULL` | | |
| `carbs_g` | `INTEGER NOT NULL` | | |
| `fat_g` | `INTEGER NOT NULL` | | |
| `method` | `VARCHAR(20) NOT NULL DEFAULT 'manual'` | | `manual`, `adaptive` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Constraint:
- unique on `(user_id, effective_date)`

### 3.2 Migration 011: `migrations/011_supplement_tables.sql`

Header:

```sql
-- PILLAR: supplements | Status: designed, not yet populated
-- Migration 011: Empty supplement schema for workout-first MVP
```

#### `supplements`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `name` | `TEXT NOT NULL UNIQUE` | | |
| `category` | `VARCHAR(30) NOT NULL` | | |
| `evidence_grade` | `CHAR(1) NOT NULL` | | |
| `dose_min` | `DOUBLE PRECISION` | | |
| `dose_max` | `DOUBLE PRECISION` | | |
| `dose_unit` | `VARCHAR(20) NOT NULL DEFAULT 'mg'` | | |
| `optimal_timing` | `VARCHAR(30)` | | |
| `mechanism` | `TEXT` | | |
| `key_evidence` | `TEXT` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

#### `supplement_entries`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `supplement_id` | `UUID NOT NULL` | `REFERENCES supplements(id)` | |
| `dose` | `DOUBLE PRECISION NOT NULL` | | |
| `dose_unit` | `VARCHAR(20) NOT NULL` | | |
| `taken_at` | `TIMESTAMPTZ NOT NULL` | | |
| `timing_window` | `VARCHAR(30)` | | |
| `notes` | `TEXT` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Index:
- `idx_se_user_taken` on `(user_id, taken_at DESC)`

#### `supplement_stack`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `supplement_id` | `UUID NOT NULL` | `REFERENCES supplements(id)` | |
| `daily_dose` | `DOUBLE PRECISION NOT NULL` | | |
| `dose_unit` | `VARCHAR(20) NOT NULL` | | |
| `frequency` | `VARCHAR(20) NOT NULL DEFAULT 'daily'` | | |
| `preferred_time` | `VARCHAR(30)` | | |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` | | |
| `start_date` | `DATE NOT NULL` | | |
| `end_date` | `DATE` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

#### `supplement_interactions`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `supplement_a_id` | `UUID NOT NULL` | `REFERENCES supplements(id)` | |
| `supplement_b_id` | `UUID NOT NULL` | `REFERENCES supplements(id)` | |
| `interaction_type` | `VARCHAR(20) NOT NULL` | | `negative`, `positive`, `neutral` |
| `severity` | `VARCHAR(20) NOT NULL` | | `high`, `moderate`, `low` |
| `description` | `TEXT NOT NULL` | | |
| `recommendation` | `TEXT NOT NULL` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

### 3.3 Migration 012: `migrations/012_coaching_tables.sql`

Header:

```sql
-- PILLAR: coaching | Status: designed, not yet populated
-- Migration 012: Empty coaching schema for workout-first MVP
```

#### `conversations`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `title` | `TEXT` | | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'active'` | | `active`, `archived` |
| `summary` | `TEXT` | | rolling summary for context compaction |
| `started_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |
| `last_message_at` | `TIMESTAMPTZ` | | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Index:
- `idx_conv_user_last_message` on `(user_id, last_message_at DESC)`

#### `conversation_messages`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `conversation_id` | `UUID NOT NULL` | `REFERENCES conversations(id) ON DELETE CASCADE` | |
| `role` | `VARCHAR(20) NOT NULL` | | `user`, `assistant`, `system`, `tool` |
| `content` | `TEXT NOT NULL` | | |
| `model_name` | `VARCHAR(50)` | | optional model trace |
| `structured_payload` | `JSONB` | | optional actions/metadata |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Index:
- `idx_cm_conversation_created` on `(conversation_id, created_at)`

#### `engagement_events`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` | | |
| `user_id` | `TEXT NOT NULL DEFAULT 'default-user'` | | |
| `event_type` | `VARCHAR(50) NOT NULL` | | `coach_prompted`, `coach_opened`, `coach_replied`, etc. |
| `event_source` | `VARCHAR(30)` | | `notification`, `dashboard`, `coach_tab` |
| `metadata` | `JSONB` | | |
| `occurred_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | | |

Index:
- `idx_ee_user_occurred` on `(user_id, occurred_at DESC)`

### 3.4 Client Readiness Engine

Current code reality:
- `DashboardViewModel` reads BP, sleep, HRV, and `last_sync` from `health_sync` prefs.
- `computeReadiness()` is a private fixed-threshold helper.
- No subjective-feel capture path exists.
- No local workout-history cache exists for training-load input.

Implement the engine around that reality, not around a future repository design.

**Likely files to add:**
- `app/src/main/java/com/healthplatform/sync/readiness/ReadinessModels.kt`
- `app/src/main/java/com/healthplatform/sync/readiness/ReadinessEngine.kt`
- `app/src/main/java/com/healthplatform/sync/readiness/ReadinessConfigStore.kt`

**Likely files to modify:**
- `app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt`
- `app/src/main/java/com/healthplatform/sync/ui/DashboardScreen.kt`
- `app/src/main/java/com/healthplatform/sync/SyncPrefsKeys.kt` or a new readiness-keys file

#### Proposed client models

```kotlin
enum class ReadinessInputId {
    SLEEP,
    BLOOD_PRESSURE,
    HRV,
    SUBJECTIVE,
    TRAINING_LOAD
}

enum class ReadinessInputStatus {
    FRESH,
    DEGRADED,
    MISSING,
    EXCLUDED
}

data class ReadinessInputResult(
    val id: ReadinessInputId,
    val status: ReadinessInputStatus,
    val rawLabel: String?,
    val score: Int?,
    val configuredWeight: Double,
    val effectiveWeight: Double,
    val lastUpdatedAt: String?,
    val reason: String
)

data class ReadinessResult(
    val aggregateScore: Int?,
    val label: String?,
    val summary: String,
    val inputs: List<ReadinessInputResult>
)
```

#### Config requirements

Config store values:
- sleep weight
- BP weight
- HRV weight
- subjective weight
- training-load weight
- staleness thresholds (`normalHours`, `degradedHours`)
- label bands

Default values for Phase 2:
- sleep `0.30`
- BP `0.20`
- HRV `0.00` until A-01 is positive
- subjective `0.00` until a capture path exists
- training load `0.00` until Phase 3

This keeps the engine structurally aligned with ADR-003 while staying code-grounded in the current app.

#### Scoring rules

Use the current dashboard heuristic as the first engine implementation:

- **BP score**
  - systolic `< 120` -> `100`
  - `120-129` -> `70`
  - `130-139` -> `40`
  - `>= 140` -> `10`

- **Sleep score**
  - `>= 420 min` -> `100`
  - `360-419 min` -> `70`
  - `< 360 min` -> `25`

- **HRV score**
  - `>= 60 ms` -> `100`
  - `30-59 ms` -> `70`
  - `< 30 ms` -> `25`

Staleness rules:
- `< 12h` -> full weight
- `12-24h` -> 50% weight
- `> 24h` -> excluded

All-inputs-missing/stale behavior:
- `aggregateScore = null`
- UI shows `sync to update readiness`

### 3.5 Dashboard Integration

`DashboardViewModel` should:
1. Continue reading the existing summary prefs
2. Build input snapshots from `LAST_BP_TIME`, `LAST_SLEEP_TIME`, `LAST_HRV_TIME`
3. Call `ReadinessEngine`
4. Expose a richer readiness state to the UI

`DashboardScreen` should:
- keep the current card placement
- replace the single reason string with:
  - aggregate label/score
  - per-input rows
  - stale badges / last updated labels
  - empty state when no usable inputs exist

Do **not** add a new repository abstraction in this phase. The dashboard remains prefs-backed because that is the current source of truth for user-visible readiness inputs.

### 3.6 Phase 3 Request Contract

Define the readiness payload now so Phase 3 does not invent it ad hoc.

```json
{
  "aggregateScore": 74,
  "label": "Take it easy",
  "computedAt": "2026-03-19T08:15:00Z",
  "inputs": [
    {
      "id": "sleep",
      "status": "fresh",
      "score": 70,
      "effectiveWeight": 0.30,
      "lastUpdatedAt": "2026-03-19T06:40:00Z",
      "reason": "Under 7h sleep"
    },
    {
      "id": "blood_pressure",
      "status": "fresh",
      "score": 100,
      "effectiveWeight": 0.20,
      "lastUpdatedAt": "2026-03-19T06:42:00Z",
      "reason": "BP in normal range"
    },
    {
      "id": "hrv",
      "status": "excluded",
      "score": null,
      "effectiveWeight": 0.0,
      "lastUpdatedAt": null,
      "reason": "HRV unavailable"
    }
  ]
}
```

The Phase 3 server consumes this payload. It does not recompute readiness.

---

## 4. Safety Constraints

1. Migrations 010-012 stay schema-only. No routes, services, or active features.
2. Readiness engine refactor must preserve the dashboard's current ability to work entirely from local cached data.
3. No part of Phase 2 may depend on Phase 1 being finished.
4. HRV absence must degrade cleanly without crashing the dashboard or producing fake scores.
5. Training-load and subjective slots must be dormant, not removed, so Phase 3 can extend the engine without reshaping it.

---

## 5. Acceptance Criteria

- [ ] Migration 010 creates `foods`, `food_entries`, and `nutrition_targets` with provenance/quality fields
- [ ] Migration 011 creates supplement catalog/log/stack/interaction tables
- [ ] Migration 012 creates conversation/history/engagement tables
- [ ] All three migrations are idempotent and have pillar status headers
- [ ] No server endpoints or Zod schemas are added for the future pillars
- [ ] Dashboard no longer uses inline `computeReadiness()` logic
- [ ] Readiness engine computes a score from available fresh/degraded inputs
- [ ] HRV can be enabled by config only
- [ ] All-inputs-stale state shows `sync to update readiness`
- [ ] Unit tests cover fresh, degraded, missing, and all-stale cases
- [ ] Phase 3 request payload shape is documented in code/docs

---

## 6. Verification Plan

**Server:**
- apply migrations 010-012 on fresh DB
- apply migrations 010-012 on adopted DB
- re-run to confirm no-op behavior

**Client:**
- unit-test the engine in isolation
- verify dashboard state for:
  - good-to-go
  - take-it-easy
  - recovery-day
  - HRV missing
  - all inputs stale
- confirm existing non-readiness dashboard cards still render from prefs

---

## 7. Main Risks

1. **Readiness scope creep:** adding subjective input UI or training-load plumbing in this phase will slow the core readiness refactor without improving the Phase 3 contract.
2. **Schema overreach:** the future-pillar tables should be complete enough to avoid migration churn, but not so detailed that they turn into feature implementation by stealth.
3. **Dashboard regression risk:** replacing a small inline helper with a richer model can accidentally break existing summary rendering if the UI and state changes sprawl.

---

## 8. Execution Notes

- Keep the server migrations and the client readiness work as separate commits/workstreams if possible.
- On the client, prefer new readiness files plus a focused dashboard refactor over a broader architecture cleanup.
- Treat `PROJECT.md` as continuity infrastructure: update it after the phase lands so later sessions do not fall back to stale planning assumptions.
