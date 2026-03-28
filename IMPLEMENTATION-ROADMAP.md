# Apex v2 Implementation Roadmap

**Created:** 2026-03-18 | **Hardened:** 2026-03-18
**Scope:** Workout-first MVP through to implementation-ready state
**Governing ADRs:** ADR-001 through ADR-005 (2 accepted, 3 proposed for acceptance)

---

> Historical note (2026-03-28): this document is the workout-first MVP roadmap used
> to ship the initial Apex v2 slice. It is retained for implementation history, but
> it is superseded for current product planning by
> `IMPLEMENTATION-ROADMAP-FULL-PRODUCT.md`.

---

## 1. Purpose

Convert the accepted architecture decisions into a sequenced build plan. This roadmap covers the workout-first MVP only (D-02). Nutrition, supplements, AI coaching, and photo estimation are designed-now-built-later per D-15 — their schemas ship empty in Phase 2 but their features are post-MVP.

The roadmap optimizes for:
- Reducing integration risk early (Hevy adapter before generation logic)
- Keeping the client shippable at each phase boundary
- Running manual validations in parallel with implementation, not as blockers

---

## 2. Planning Inputs and Locked Constraints

### ADR Decisions Driving Sequencing

| ADR | Key implementation constraint |
|-----|------------------------------|
| ADR-001 | Server-side Hevy adapter must exist before workout generation. Cache tables before sync logic. Sync tier parameterized — H-06 determines config, not architecture. |
| ADR-002 | Migration tracking table (008) and runner script before any new migrations. Pillar-grouped migrations (009-012). Idempotent. Design-now-build-later lifecycle. |
| ADR-003 | Readiness engine is client-side, weighted, configurable. Can build with placeholder weights. A-01 determines HRV inclusion. Training load input deferred until workout data flows. |
| ADR-004 | `generated_routines` + `generated_routine_exercises` + `exercise_muscle_overrides` are VD-1 independent. Progression queries actuals only. Schema safe to finalize now. |
| ADR-005 | HealthDataProvider interface wraps existing HealthConnectReader. Thin refactor. Can ship immediately — no dependency on any validation. |

### Hard Constraints

- **D-02:** Workout generation ships first. No nutrition or coaching features in MVP.
- **D-03:** TDEE and readiness are client-side. Workout generation is server-side.
- **D-11:** Generate → review → execute. No auto-start.
- **D-17:** Progression uses actuals from `workout_sessions` + `workout_sets` only.
- **D-05:** Every generated recommendation shows reasoning.
- **D-15:** Nutrition, supplement, and conversation schemas designed and created empty during workout phase.

### MVP Feature Scope (P-05)

- Volume tracking per muscle group (RP-granularity via `exercise_muscle_overrides`)
- Weight suggestions (2-for-2 rule from workout history)
- MRV proximity flagging (per R-1 volume landmark data)
- Single-workout generation with readiness context
- NOT: full mesocycle auto-generation, periodization planning

---

## 3. Implementation Phases

### Phase 0A: Migration Infrastructure (Server)

**Purpose:** Enable all subsequent server schema work.

**In scope:**
- Migration 008: `schema_migrations` tracking table
- Seed `schema_migrations` with entries for existing migrations 001-008
- Migration runner script (`scripts/migrate.js`) that reads tracking table, applies unapplied SQL files in order, inserts tracking rows
- Runner must handle bootstrap case: existing DB with tables 001-007 applied manually but no tracking table

**Out of scope:** All client work. All new data tables. Hevy adapter. Feature work.

**Hard blockers:** None.
**Soft dependencies:** None.
**Assumptions that do not block start:** Server is accessible via SSH. PostgreSQL connection env vars are configured.

**Exit criteria:**
- `scripts/migrate.js` applies migrations 001-008 idempotently on a fresh database and populates `schema_migrations`
- Running the runner on the existing production database adds only the `schema_migrations` table (008) and seeds it — no data loss, no table recreation
- Runner exits cleanly when all migrations are already applied
- Runner reports which migrations were applied and which were skipped

**What must be true before Phase 1 begins:** Migration runner exists, is tested on production DB, and successfully bootstrapped `schema_migrations`.

---

### Phase 0B: Client Health Provider Interface

**Purpose:** Decouple health data reads from Health Connect SDK types. Enable mock-based testing.

**In scope:**
- `HealthDataProvider` interface definition with per-data-type read methods, change token pass-through, availability/permission queries, result metadata
- Project-level domain types (`BpRecord`, `SleepRecord`, `HrvRecord`, `BodyRecord`) as Kotlin data classes — not HC SDK types
- `HealthConnectProvider` adapter that wraps existing `HealthConnectReader`, translating HC types to domain types
- `SyncWorker` refactored to call `HealthDataProvider` instead of `HealthConnectReader` directly
- Unit tests for `HealthConnectProvider` adapter using mocked `HealthConnectReader`

**Out of scope:** All server work. Alternative providers (WHOOP, Oura). Readiness engine changes. Any new data flows.

**Hard blockers:** None.
**Soft dependencies:** None.
**Assumptions that do not block start:** None. All required code is in the existing client repo.

**Exit criteria:**
- `SyncWorker.doWork()` calls `HealthDataProvider` methods — no direct `HealthConnectReader` references remain in SyncWorker
- `HealthConnectProvider` passes all data through unchanged (behavior-preserving refactor)
- All existing health sync behavior unchanged: BP, sleep, body, HRV sync to server, summary prefs updated, widget refreshed
- All existing tests pass
- New unit tests verify adapter delegation
- CI green

**What must be true before Phase 2 client work begins:** Provider interface exists and SyncWorker uses it.

---

### Phase 0 Parallelism

Phase 0A (server) and Phase 0B (client) have **zero coupling**. They can execute simultaneously, sequentially, or in any order. Neither depends on the other. Both must complete before their respective downstream phases begin.

---

### Phase 1: Server Hevy Adapter + Workout Schema

**Purpose:** Build the server-side Hevy abstraction layer and create workout generation tables. After this phase, the server can cache Hevy data reliably and the database schema supports workout generation.

**In scope:**
1. **Hevy adapter module (ADR-001):**
   - Single server-side module owning all Hevy API calls
   - Authentication / API key management
   - Response normalization to internal domain types
   - Error classification (transient / permanent / rate-limited)
   - Rate limit tracking with counter + time window
   - Exponential backoff with jitter on 429 responses
   - Cache-through read pattern: cache first → live fetch if stale → return stale-with-flag if fetch fails
   - Exercise template cache table
   - `fetched_at` timestamp on every cached record
   - Configurable sync interval parameter (default: Tier 2 — 30 min periodic, 1/5min manual cap)
2. **Workout generation schema (migration 009, ADR-004):**
   - `generated_routines` with nullable `hevy_routine_id`
   - `generated_routine_exercises` with `reasoning` field (D-05)
   - `exercise_muscle_overrides` (~55 rows, system-scoped, no `user_id`)
   - `progression_snapshots` (derived/optional)
   - Seed `exercise_muscle_overrides` from EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md + R-1
3. **Preserve `hevy_routine_id` on workout sync:** Add column to `workout_sessions`, update `HevyClient.transformWorkout()` to retain the field

**Out of scope:** Workout generation logic. Progression algorithms. Client generation UI. Design-now-build-later migrations (010-012). Readiness engine.

**Hard blockers:**
- Phase 0A complete (migration runner must exist to apply 009)

**Soft dependencies:**
- H-06 result informs sync interval config. Not a blocker — Tier 2 defaults are the design target.

**Parallelizable:** Phase 2 server migrations (010-012) can start after Phase 0A completes, independent of Phase 1. Phase 2 client work (readiness engine) can start after Phase 0B completes, independent of Phase 1.

**Assumptions that do not block start:**
- H-06 not yet done → use Tier 2 defaults. Adjust config value later.
- VD-1 not yet done → `hevy_routine_id` nullable. Push path deferred.

**Exit criteria:**
- Hevy adapter fetches workouts via `GET /v1/workouts`, caches to PostgreSQL, serves from cache on subsequent reads
- Hevy adapter fetches exercise templates via `GET /v1/exercise_templates`, caches with 24h tolerance
- Adapter handles 429 responses with backoff and returns stale cache with flag
- Existing Hevy sync route (`POST /api/sync/hevy/workouts`) delegates to adapter
- Migration 009 applied: all 4 tables created, `exercise_muscle_overrides` seeded
- `workout_sessions.hevy_routine_id` column exists and is populated from Hevy sync
- Server tests cover adapter cache-through, backoff, and error classification
- CI green

**What must be true before Phase 3 begins:** Hevy adapter is operational and caching workout history. Migration 009 applied with seeded override data.

---

### Phase 2: Design-Now-Build-Later Schemas + Readiness Engine

**Purpose:** Reserve schema space for future pillars. Build client-side readiness scoring. These are independent workstreams that happen to share a phase because neither has feature dependencies on Phase 1.

**In scope:**
1. **Future-pillar migrations (server, ADR-002 + D-15):**
   - Migration 010: Nutrition tables (`foods`, `food_entries`, `nutrition_targets`) with D-13 `source` provenance enum, D-04 quality flag. Created empty — zero endpoints, zero Zod schemas, zero route files.
   - Migration 011: Supplement tables (`supplements`, `supplement_entries`, `supplement_stack`, `supplement_interactions`). Created empty.
   - Migration 012: Coaching tables (`conversations`, `conversation_messages`, `engagement_events`). Created empty.
2. **Readiness engine (client, ADR-003):**
   - Computation module: weighted inputs, configurable weights, staleness handling, graceful degradation on missing inputs
   - Weight configuration stored in SharedPreferences or Room — not hardcoded
   - Initial weights: sleep 0.30, BP 0.20, HRV 0.25 (or 0 if A-01 negative), subjective 0.15, training load 0.10 (dormant until Phase 3)
   - Staleness thresholds: <12h normal, 12-24h degraded, >24h excluded
3. **Readiness display upgrade (client):**
   - Replace `DashboardViewModel.computeReadiness()` with engine-backed computation
   - Show per-input breakdown and individual scores (D-05)
   - Show staleness indicators per input (D-08)
   - Show "sync to update" when all inputs stale >24h

**Out of scope:** Workout generation logic. Nutrition/supplement/coaching endpoints or Zod schemas. Training load readiness input (requires workout data from Phase 3). Any feature work on empty tables.

**Hard blockers:**
- Phase 0A complete (migration runner must exist for 010-012)
- Phase 0B complete (provider interface must exist for readiness engine to read health data through standard types)

**Soft dependencies:**
- A-01 result determines HRV weight. Not a blocker — default HRV weight to 0, enable via config flag when validated.
- Phase 1 NOT required. Migrations 010-012 are independent of 009. Readiness engine reads from existing sync cache, not from Hevy adapter.

**Parallelizable:** Server migrations (010-012) and client readiness engine are independent. Both can run in parallel with Phase 1.

**Assumptions that do not block start:**
- A-01 not yet done → HRV weight = 0. Config flag ready for activation.
- Nutrition/supplement/coaching table schemas are design-ready from DECISION-REGISTER.md (D-04, D-13, D-15, P-07).

**Exit criteria:**
- Migrations 010-012 applied. All empty tables created with full schema including constraints and indexes.
- Each empty-table migration file has header comment: `-- PILLAR: <name> | Status: designed, not yet populated`
- Readiness engine computes a 0-100 score from available inputs with correct re-weighting when inputs are missing
- Dashboard readiness card shows per-input scores, staleness indicators, and aggregate score
- Readiness card shows "sync to update" when all inputs are stale
- Unit tests cover: all-inputs-present, missing-HRV, missing-all, stale-inputs
- CI green

**What must be true before Phase 3 begins:** Readiness engine produces a score that can be included in a workout generation request.

---

### Phase 3: Workout Generation MVP

**Purpose:** Build the core MVP feature: server-side workout generation with client-side review. This is the primary value delivery of v2.

**In scope:**
1. **Server generation endpoint:**
   - Accepts: readiness context (from client), target muscle groups / session type
   - Reads: cached workout history from Hevy adapter, exercise templates + muscle attribution from `exercise_muscle_overrides`
   - Computes: volume tracking per muscle group (trailing 7-day set count), 2-for-2 weight suggestion, MRV proximity per R-1 landmarks
   - Returns: generated routine with exercises, target sets/reps/weight, per-exercise reasoning (D-05), MRV flags
   - Fails explicitly if Hevy cache is empty (no history to personalize from)
2. **Progression logic:**
   - Queries `workout_sessions` + `workout_sets` only (D-17, ADR-004)
   - 2-for-2 rule: if last 2 sets of an exercise hit target reps, suggest weight increase
   - Volume tracking: count sets per muscle group per week via `exercise_muscle_overrides` attribution
   - MRV proximity: compare weekly volume to R-1 MRV ranges, flag when within 80%
3. **Client generation UI:**
   - "Generate workout" button on Dashboard or Activity screen
   - Review screen showing generated routine with per-exercise reasoning
   - Accept / reject actions (D-11)
   - If VD-1 positive: "Push to Hevy" button on accepted routines
   - If VD-1 negative or untested: display-only with "Start in Hevy manually" instruction
4. **Training load readiness input:**
   - Wire volume-vs-MRV proximity into readiness engine as the training load input (weight 0.10)
   - Requires Hevy adapter cache to have workout history

**Out of scope:** Full mesocycle planning. Periodization. Adaptive TDEE. Deload scheduling. Coaching. Any work on empty nutrition/supplement tables.

**Hard blockers:**
- Phase 1 complete (Hevy adapter caching workout history, migration 009 with seeded override data)
- Phase 2 readiness engine complete (provides readiness context for generation request)

**Soft dependencies:**
- VD-1 result determines push path UI. Not a blocker — build Path B (display-only) first. Add push button if VD-1 positive.
- H-06 result should be known by now (affects UX around sync freshness). If still unknown, Tier 2 defaults produce acceptable UX.

**Assumptions that do not block start:**
- Sufficient Hevy workout history exists in cache for personalization. If cache is empty on first use, generation returns a clear error.
- RP volume landmark values from R-1 are correct for seeding MRV thresholds.

**Exit criteria:**
- Client can request workout generation and receive a personalized routine
- Routine includes exercises with target sets/reps/weight and per-exercise reasoning
- Review screen shows routine; accept/reject works
- Volume tracking page/card shows per-muscle-group weekly sets vs RP landmark ranges
- Weight suggestions appear on exercises where the 2-for-2 rule is met
- MRV proximity warnings appear when weekly volume approaches MRV for any muscle group
- Generation returns meaningful error when cache is empty
- If VD-1 positive: accepted routine pushes to Hevy and `hevy_routine_id` links prescribed to actual
- Core generation flow has integration test coverage (request → generate → return → display)
- CI green

**What must be true before Phase 4 begins:** Core generation feature is usable in daily training.

---

### Phase 4: Polish, Validation, and MVP Ship

**Purpose:** Close remaining gaps, apply validation-derived tuning, reconcile documentation, ship.

**Status (2026-03-23):** Complete — v2 MVP shipped.

**In scope:**
1. H-06 sync tier finalization (if still at Tier 2 defaults)
2. A-01 HRV activation (if validated but not yet enabled in readiness config)
3. VD-1 push path addition (if validated but not yet built)
4. H-02/H-03 readiness weight tuning (after 4+ weeks of use data)
5. Documentation reconciliation: update `documentation/01-architecture-overview.md`, `03-data-sync-protocol.md`, `07-background-sync-and-workers.md` to reflect Room queue and v2 architecture
6. End-to-end testing: health sync → readiness → generate → review → execute in Hevy → sync back → progression uses actuals → next generation reflects updated history

**Out of scope:** New features. Nutrition/supplement/coaching activation. Adaptive TDEE.

**Hard blockers:** Phases 0-3 complete.
**Soft dependencies:** H-02/H-03 require 4+ weeks of real usage data from Phase 3.

**Exit criteria:**
- ~~All validation-derived config values applied (sync tier, HRV weight, push path)~~ H-06: validated >= 30 req/min, Tier 2 defaults confirmed — no change needed. A-01: inconclusive (no confirmed HC HRV/sleep data meeting runbook validation window), HRV=0 shipped. VD-1: deferred, Path B shipped.
- ~~End-to-end flow tested and working~~ Task 7 passed on real device (2026-03-22)
- ~~Stale v1 documentation updated~~ Task 5 complete — mandatory + optional docs updated
- ~~App is stable for daily use as primary workout companion alongside Hevy~~ Confirmed
- ~~No known regressions from v1 functionality~~ Confirmed
- H-02/H-03: explicitly deferred — insufficient data (< 1 week of daily use; 4+ weeks required)
- ~~ADR-001, ADR-003, ADR-005 human acceptance~~ Accepted by Tyler 2026-03-23. All exit criteria satisfied.

---

## 4. Dependency and Validation Gate Map

### Operational Gate Table

| Validation | Phase/workstream affected | What can proceed before resolution | What becomes blocked if unresolved | Latest responsible moment |
|-----------|--------------------------|-----------------------------------|-----------------------------------|--------------------------|
| **H-06** (Hevy rate limits) | Phase 1: adapter sync interval config | All of Phase 1 — build with Tier 2 defaults (30 min periodic, 1/5min manual cap). Adapter architecture is tier-independent. | Phase 3 ship — if actual tier is worse than Tier 2, UX degradation must be addressed before users encounter it. | **End of Phase 1.** Must be resolved before Phase 3 exits. Best done during Phase 0. |
| **A-01** (wearable HC data) | Phase 2: readiness HRV weight | All of Phase 2 — build with HRV weight = 0 and a config flag. Engine handles missing HRV by design. | Readiness display ships incomplete — sleep + BP only, no HRV. Acceptable but weaker. | **End of Phase 2.** Must be resolved before readiness display ships. Best done during Phase 0. |
| **VD-1** (Hevy routine creation) | Phase 3: push-to-Hevy UI; Phase 1: routine cache table | All of Phase 3 — build Path B (display-only). Schema already has nullable `hevy_routine_id`. | Push button absent from MVP. User starts Hevy routine manually. No prescribed-to-actual linkage. Acceptable per ADR-004. | **During Phase 3.** Can be tested any time. Only affects one UI element and one cache table. |
| **OA-3** (Hevy sync lag) | Phase 1: staleness tolerance config value | All of Phase 1 — use conservative staleness tolerance (1 hour). | "Last synced" display may under-report freshness. Minor UX issue. | **Phase 4 tuning.** Opportunistic during H-06 test. |
| **H-02** (HRV predicts strength) | Phase 4: readiness HRV weight long-term | All of Phases 0-3 and MVP ship. HRV weight is a config value. | HRV input may be noise. Worst case: set weight to 0 in config. | **Post-MVP.** Requires 4+ weeks of data. |
| **H-03** (Algorithm matches feel) | Phase 4: overall readiness weight tuning | All of Phases 0-3 and MVP ship. All weights are config values. | Readiness score may not match subjective feel. Worst case: demote readiness to informational display. | **Post-MVP.** Requires 4+ weeks of data. |
| **H-04** (HC reliability) | Post-MVP: ADR-005 alternative provider need | All of MVP. Provider interface already exists (Phase 0B). | If HC unreliable, must implement alternative provider. Interface is ready; OAuth implementation is non-trivial. | **Post-MVP.** Requires 2+ weeks of monitoring. |

### Summary Rule

No validation blocks any phase from **starting**. H-06 and A-01 should be done during Phase 0 for maximum benefit but their absence degrades config precision, not architectural correctness. VD-1 can wait until Phase 3 without consequence. H-02, H-03, H-04 are post-MVP by definition.

---

## 5. First Implementable Work Package

### Package 0A: Migration Infrastructure (Server)

**Objective:** Create the migration tracking table and runner script so that all subsequent server schema work (migrations 009-012) has a reliable, automated application path.

**Why this should be first:**
- Every server-side phase (1, 2) depends on the migration runner existing.
- It is the smallest possible unit of work that unblocks the most downstream work.
- Zero external dependencies. Zero validation dependencies. Zero client-side coupling.
- The existing server has 7 manually-applied migrations with no tracking — this technical debt must be resolved before adding more migrations.

**Minimum required inputs:**
- ADR-002 §1 (schema_migrations table spec)
- ADR-002 §3 (migration runner requirements)
- `SERVER-SCHEMA-INVENTORY.md` (existing migration file list: 001-007)
- SSH access to production server for bootstrap testing

**Concrete scope boundary:**
- IN: migration 008 SQL file, migration runner script, bootstrap seeding of 001-008 into tracking table
- OUT: all other migrations (009-012), all client work, Hevy adapter, any feature logic

**Likely touched files/components:**
- New: `src/migrations/008_schema_migrations.sql`
- New: `scripts/migrate.js`
- Modified: deployment documentation or README (how to run migrations)
- Tested against: existing production database (bootstrap) and fresh database (full apply)

**Acceptance criteria:**
1. `scripts/migrate.js` connects to database using existing env vars (`DATABASE_URL` or equivalent)
2. On fresh database: creates all tables through 008 and populates `schema_migrations` with 8 rows
3. On existing production database (001-007 applied manually): creates only `schema_migrations` table, seeds with 001-008 entries, touches nothing else
4. On already-bootstrapped database: reports "all migrations applied" and exits cleanly
5. Runner logs which migrations were applied and which were skipped
6. All existing server tests pass
7. Migration 008 is idempotent (`CREATE TABLE IF NOT EXISTS`)

**Explicit non-goals:**
- No migration 009 or higher
- No client-side changes
- No Hevy adapter work
- No schema design decisions — only infrastructure

**Major risk:** Bootstrap on production database must not drop, recreate, or modify any existing table. The runner must detect the pre-tracking-table state and handle it as a one-time seed operation. Test on a database clone before production.

---

## 6. Second Implementable Work Package

### Package 0B: Client Health Provider Interface

**Objective:** Decouple `SyncWorker` from `HealthConnectReader` by introducing a `HealthDataProvider` interface and wrapping the existing reader as `HealthConnectProvider`. Define project-level health domain types.

**Why this should be second (or parallel with 0A):**
- Phase 2 client work (readiness engine) depends on the provider interface and domain types existing.
- It is a behavior-preserving refactor with clear before/after testing.
- Zero server-side dependency. Zero validation dependency. Can run simultaneously with Package 0A.

**Minimum required inputs:**
- ADR-005 (provider interface requirements)
- R-5 (current `HealthConnectReader` methods, `SyncWorker` call sites, existing data flow)
- Current source files: `HealthConnectReader.kt`, `SyncWorker.kt`, `SyncQueueEntity.kt`

**Concrete scope boundary:**
- IN: `HealthDataProvider` interface, domain type data classes, `HealthConnectProvider` adapter, `SyncWorker` refactor to use interface, unit tests for adapter
- OUT: all server work, alternative providers, readiness engine changes, new data flows, any feature work

**Likely touched files/components:**
- New: `data/health/HealthDataProvider.kt`
- New: `data/health/HealthConnectProvider.kt`
- New: `data/health/model/BpRecord.kt`, `SleepRecord.kt`, `HrvRecord.kt`, `BodyRecord.kt`
- Modified: `service/SyncWorker.kt` (replace `HealthConnectReader` calls with `HealthDataProvider` calls)
- Unchanged: `data/HealthConnectReader.kt` (internal logic untouched — wrapped by adapter)

**Acceptance criteria:**
1. `SyncWorker.doWork()` contains zero direct references to `HealthConnectReader` — all reads go through `HealthDataProvider`
2. `HealthConnectProvider` delegates every call to existing `HealthConnectReader` methods
3. Domain types (`BpRecord`, etc.) are independent of HC SDK — no HC imports in domain model package
4. All existing health sync behavior preserved: BP, sleep, body, HRV sync works end-to-end
5. All existing tests pass without modification (behavior-preserving)
6. New unit tests: mock `HealthConnectReader`, verify `HealthConnectProvider` passes data through correctly
7. CI green

**Explicit non-goals:**
- No readiness engine work (Phase 2)
- No alternative provider implementations
- No changes to sync queue format
- No new data types beyond the 4 domain records

**Major risk:** `SyncWorker` is the most complex single file in the client (~490 lines per R-5). The refactor must be strictly behavior-preserving. Integration test with a real device sync (or Robolectric + mock HC client) is essential to catch regressions.

---

## 7. Sequencing Failure Warnings

### 1. Hevy adapter scope creep (Phase 1)

The Hevy adapter is the largest new server-side component. It includes caching, rate limiting, backoff, normalization, and error classification — each of which could expand into a sub-project. **Warning:** the adapter must be built incrementally. Implement workout caching first (the critical path for Phase 3). Exercise template caching second. Routine caching third (only if VD-1 positive). Do not build the complete adapter as a monolith before shipping any cached data.

### 2. Exercise muscle override seed data (Phase 1)

Seeding `exercise_muscle_overrides` with ~55 rows requires mapping each of the 42 Hevy shoulder exercises to front/side/rear delt and ~13 full_body exercises to their actual primary muscle groups. This is a data curation task disguised as a migration step. **Warning:** it requires human judgment per exercise. Budget time for this explicitly. Do not treat it as trivial "just insert some rows."

### 3. Readiness engine vs generation integration (Phase 2 → Phase 3)

The readiness engine (Phase 2) produces a score. The generation endpoint (Phase 3) consumes it. The data contract between them (what fields the client sends, what the server expects) is not yet defined. **Warning:** if the readiness output shape and the generation input shape are designed independently, integration at Phase 3 start will require rework. Define the readiness-context contract before Phase 2 client work and Phase 3 server work diverge.

### 4. Migration 009 column-level decisions

Migration 009 creates 4 tables with specific columns, types, and constraints. ADR-004 defines the table purposes but does not specify every column. **Warning:** column-level schema design will surface during Phase 1 implementation. Some decisions (JSONB vs normalized columns for readiness context, TEXT vs VARCHAR for reasoning) are not yet made. These are implementation decisions, not architecture decisions, but they will consume time.

### 5. Phase 2 server migrations look trivial but aren't

Migrations 010-012 create "empty" tables, but each table needs: correct column types, constraints, indexes, provenance enums (D-13 for nutrition), quality flags (D-04 for food sources), and foreign key relationships. Designing these schemas from DECISION-REGISTER.md entries is real design work. **Warning:** "empty tables" does not mean "trivial migrations." Budget 1-2 sessions for schema design, not just SQL file creation.

### 6. Single-point-of-failure: Hevy workout cache for Phase 3

Phase 3 (workout generation) has a hard dependency on cached Hevy workout history from Phase 1. If the Hevy adapter is buggy, slow to cache, or if the API is less reliable than expected, Phase 3 cannot produce personalized workouts. **Warning:** there is no fallback for generation without workout history. If Hevy cache fails, generation fails. Test the adapter thoroughly before Phase 3 begins. Consider having Tyler's real workout history cached and verified as a Phase 1 exit criterion.

---

## 8. Critical Path and Delivery Risks

### Critical Path

```
Package 0A (migration infra) → Phase 1 (Hevy adapter + schema 009) → Phase 3 (generation MVP) → Phase 4 (ship)
```

The parallel track feeds into Phase 3:
```
Package 0B (provider interface) → Phase 2 client (readiness engine) ──→ Phase 3
Package 0A (migration infra) → Phase 2 server (migrations 010-012)     (not on critical path)
```

Phase 2 server work (empty migrations) is NOT on the critical path. It can happen any time after Package 0A and before post-MVP pillar activation.

### Biggest Risk to Delivery Sequencing

**The Hevy adapter (Phase 1)** is the highest-complexity, highest-uncertainty implementation task. It introduces a new server-side caching layer, rate limit tracking, backoff logic, and response normalization that don't exist in the current server. If the adapter takes longer than expected or reveals unexpected API behavior, Phase 3 is delayed.

Mitigation: H-06 testing during Phase 0 gives early signal on API behavior. Build adapter incrementally (workouts first, exercise templates second). Verify Tyler's real workout data is cached correctly as a Phase 1 exit criterion.

### Single-Subsystem Over-Dependency

Phase 3 depends on three upstream subsystems:
1. Hevy adapter caching workout history (Phase 1) — **hard dependency, no fallback**
2. `exercise_muscle_overrides` seed data correct (Phase 1) — **hard dependency, no fallback**
3. Readiness engine producing context (Phase 2) — **soft dependency** — generation can proceed without readiness context, just loses one input

### Manual Validation Timing

| Validation | Ideal timing | Consequence of delay |
|-----------|-------------|---------------------|
| H-06 | During Phase 0 | Phase 1 adapter built with Tier 2 defaults. May need config adjustment later. Worst case: UX surprise at Phase 3 ship. |
| A-01 | During Phase 0 | Phase 2 readiness engine built with HRV=0. Readiness ships weaker than it could be. |
| VD-1 | During Phase 1 or early Phase 3 | Phase 3 ships display-only (Path B). Push button added later. Acceptable. |

---

## 9. Recommended Transition to GSD

When ready to begin implementation:

1. **Create a GSD project** using `/gsd:new-project` or manually initialize `.planning/` state.
2. **Create a milestone** corresponding to "v2 Workout-First MVP" (Phases 0-3).
3. **Create phases** matching this roadmap:
   - Phase 0A: Migration infrastructure (server)
   - Phase 0B: Health provider interface (client)
   - Phase 1: Hevy adapter + workout schema
   - Phase 2: Future-pillar schemas + readiness engine
   - Phase 3: Workout generation MVP
   - Phase 4: Polish + ship
4. **Plan Package 0A first** using `/gsd:plan-phase`. This roadmap's First Implementable Work Package section provides the inputs.
5. **Execute Package 0A** using `/gsd:execute-plan`.
6. **Plan Package 0B** (can overlap with 0A execution if desired).
7. After Phase 0, plan Phase 1 and Phase 2 in parallel.

### Ralph Loop Compatibility

Each phase is sized for a single Ralph loop iteration:
- Package 0A: ~1 session (migration SQL + runner script + production bootstrap)
- Package 0B: ~1 session (interface + adapter + SyncWorker refactor)
- Phase 1: ~2-3 sessions (Hevy adapter module + migration 009 + seed data)
- Phase 2: ~2-3 sessions (3 empty migration schemas + readiness engine + display)
- Phase 3: ~3-5 sessions (generation endpoint + progression logic + client UI + integration)
- Phase 4: ~1-2 sessions (tuning + testing + doc cleanup)

Total estimated: 10-15 implementation sessions for MVP.
