# ADR-002: Server Schema Migration Strategy for Multi-Pillar Design-Now-Build-Later

## Status

**Accepted for MVP** — 2026-03-16 (proposed 2026-03-15)

## Context

Apex v2 is built in a workout-first sequence (D-02), but the planning package requires that schemas for nutrition, supplements, and AI coaching conversations be **designed during the workout-first architecture phase** and tables created empty on the server (D-15, P-07). This prevents migration conflicts when later pillars ship.

The current server (Health Platform Desktop) uses:
- PostgreSQL 16+ with raw `pg` module (no ORM)
- 7 raw SQL migration files (`001` through `007`), applied manually via `psql`
- No migration runner, no tracking table, no rollback files
- Consistent naming conventions (see `SERVER-SCHEMA-INVENTORY.md`)
- 11 tables (7 with active API routes, 4 schema-only)

The current system works for 7 migrations managed by one developer. Apex v2 will add migrations for:
- Workout generation tables (prescribed routines, exercise attribution overrides, progression snapshots)
- Nutrition tables (foods, food_entries, nutrition_targets) — D-15
- Supplement tables (supplements, supplement_entries, supplement_stack, supplement_interactions) — D-15
- Conversation history table — P-07
- Possibly: readiness configuration, engagement tracking (H-07)

Without a defined strategy, each pillar will invent its own migration patterns, and "designed now, built later" tables will have no clear lifecycle convention.

### Planning items this traces to

| ID | Item | Relevance |
|----|------|-----------|
| D-15 | Nutrition + supplement schema designed now, built later | Primary driver — defines the requirement for empty-table creation |
| P-07 | AI coaching conversation history stored server-side | Requires conversation table designed alongside coaching endpoints |
| D-13 | Food entry provenance field from day one | Nutrition schema must include `source` enum even when created empty |
| D-04 | Food database quality protection (USDA + OFF) | Nutrition schema must distinguish data sources |
| D-17 | Hevy as source of truth for actual workout data | Workout schema must separate prescribed vs. actual data |
| D-02 | Workout-first execution sequence | Determines which tables are populated first vs. created empty |

## Decision

### 1. Add a migration tracking table

Create `schema_migrations` as migration `008`:

```
schema_migrations (
  version   VARCHAR(20) PRIMARY KEY,   -- e.g. '008'
  name      VARCHAR(255) NOT NULL,     -- human-readable description
  applied_at TIMESTAMPTZ DEFAULT NOW()
)
```

Seed it with entries for `001` through `008` to represent the current state. All future migrations insert their own row as the final statement.

### 2. Continue sequential numbering with required idempotency

- **Format:** `NNN_short_description.sql` (e.g., `009_workout_generation_tables.sql`)
- **All migrations MUST be idempotent.** Use `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, and `DO $$ BEGIN ... IF NOT EXISTS ... END $$;` blocks for constraints. This matches the pattern established in migrations 005–007.
- **No down migrations.** Rollback is via forward migration. Acceptable for a single-user app with one production database.
- **Single-developer sequential numbering is sufficient.** No date prefix needed until concurrent development requires it.

### 3. Add a migration runner script

Create `scripts/migrate.sh` (or `.js`):
- Connects to the database using existing env vars
- Reads `schema_migrations` to determine which migrations have been applied
- Applies unapplied migrations in order
- Inserts tracking rows
- Reports what was applied

This replaces the current manual `psql -f` workflow. It does not require a new dependency (no Knex, no Prisma). It uses the existing `pg` module.

### 4. Define the "design-now-build-later" table lifecycle

Tables created for future pillars follow this lifecycle:

**Phase 1 — Design-now (during workout-first architecture):**
- Migration creates the table with full schema: columns, types, constraints, indexes
- Table is created **empty** — no seed data, no CRUD endpoints, no Zod schemas, no route files
- Migration file includes a header comment: `-- PILLAR: nutrition | Status: designed, not yet populated`
- CHECK constraints and indexes are added now. They cost nothing on empty tables and prevent having to add them in a separate migration later.
- The `source` enum column (D-13) and any provenance fields are included in the initial schema, not deferred.

**Phase 2 — Activate (when the pillar ships):**
- A new migration may ALTER the table if requirements have changed during development. This is expected — empty tables are cheap to alter.
- Zod schemas, route files, and CRUD endpoints are created.
- Seed data (e.g., USDA food database cache) is loaded via a data migration or import script, NOT via a schema migration.
- The table's header comment is updated to `-- PILLAR: nutrition | Status: active`

**Phase 3 — Evolve (ongoing):**
- Standard migration process. No special handling.

### 5. Group new tables by pillar in migration files

Each pillar gets one migration file that creates all of its tables:

| Migration | Pillar | Tables | Status at creation |
|-----------|--------|--------|--------------------|
| `009` | Workout generation | Prescribed routines, exercise attribution overrides, progression snapshots (derived), readiness config (contingent on ADR-003) | Active (workout-first) |
| `010` | Nutrition | Foods, food_entries, nutrition_targets, food_cache | Designed, not populated |
| `011` | Supplements | Supplements, supplement_entries, supplement_stack, supplement_interactions | Designed, not populated |
| `012` | Coaching | Conversations, conversation_messages, engagement_events | Designed, not populated |

**Rationale:** Grouping by pillar (not by table) makes it easy to see which pillar introduced which tables. One migration per pillar keeps the migration count manageable and makes the "designed now, built later" intent explicit in the filename.

**Exception:** Migration `009` (workout generation) includes the `activity_summaries` user-scoped constraint fix since that table is relevant to the workout-first phase.

### 6. Follow existing naming conventions exactly

All new tables and columns follow the patterns established in the existing schema:

| Aspect | Convention | Example |
|--------|-----------|---------|
| Table names | `snake_case`, plural | `food_entries`, `supplement_entries` |
| Column names | `snake_case` | `measured_at`, `food_name`, `serving_size_g` |
| Primary keys | `id UUID DEFAULT uuid_generate_v4()` | All tables |
| User scope | `user_id TEXT NOT NULL DEFAULT 'default-user'` | All data tables |
| Timestamps | `TIMESTAMPTZ` for events, `DATE` for calendar dates | `logged_at`, `entry_date` |
| Metadata columns | `created_at`, `updated_at`, `ingested_at` | Appended to all data tables |
| Source tracking | `source VARCHAR(50)` | On tables that receive data from multiple sources |
| Indexes | `idx_<table_abbrev>_<column(s)>` | `idx_fe_user_logged` |
| Unique constraints | `uq_<table_abbrev>_<columns>` | `uq_fe_user_logged_food` |
| Check constraints | `chk_<table_abbrev>_<description>` | `chk_fe_calories_positive` |
| Foreign keys | `<parent_singular>_id UUID REFERENCES <parent_table>(id)` | `conversation_id UUID REFERENCES conversations(id)` |
| Cascade policy | CASCADE only for strict parent-child (sets within workouts). No cascade for associative links. | `workout_sets` cascades; `hrv_readings.sleep_session_id` does not |

### 7. Separate schema migrations from data migrations

- **Schema migrations** (in `migrations/`): DDL only — CREATE TABLE, ALTER TABLE, CREATE INDEX, ADD CONSTRAINT. Never INSERT data.
- **Data migrations** (in `scripts/` or `data/`): Import scripts for seed data (USDA food database, RP volume landmarks, exercise-to-muscle mappings). These are run separately, are repeatable, and use `ON CONFLICT DO NOTHING` or `UPSERT` patterns.

**Rationale:** Schema migrations are one-time, ordered, and tracked. Data imports may need to be re-run (new USDA data release, corrected mappings) and should not be coupled to the schema migration sequence.

### 8. Fix the `activity_summaries` constraint bug

Migration `009` fixes the global `activity_date UNIQUE` constraint to `UNIQUE (user_id, activity_date)` before any new workout-phase work touches this table. This is a bugfix, not a new feature.

## Consequences

### Positive

- **Migration tracking prevents drift.** `schema_migrations` table makes current schema state queryable. Migration runner prevents skipped or out-of-order migrations.
- **Empty tables are cheap.** Creating nutrition and supplement tables now costs nothing — empty tables with constraints and indexes consume negligible space and zero runtime resources.
- **Pillar-grouped migrations are readable.** One migration per pillar makes it clear when each feature area was introduced and what its current status is.
- **Schema/data separation prevents migration bloat.** USDA food imports (potentially thousands of rows) don't pollute the migration sequence.
- **Forward-only is simple.** No down migrations means no risk of accidentally rolling back production data. Consistent with the existing approach.

### Negative

- **No rollback capability.** If a migration introduces a problem, the fix requires a new forward migration. For a single-user app, this is acceptable — the blast radius is limited.
- **Empty tables may need alteration.** If nutrition or supplement requirements change during the workout-first phase (before those pillars ship), the empty tables may need ALTER statements. This is expected and cheap, but it does mean the "designed now" schemas are not final.
- **Migration runner is new infrastructure.** The existing manual `psql` workflow has worked for 7 migrations. Adding a runner script is a small investment that pays off as the migration count grows, but it is new code to maintain.

### Neutral

- **No ORM adoption.** This decision intentionally does not adopt Knex, Prisma, or any migration framework. The existing raw SQL approach is working. Adding framework overhead for a single-user app with ~15 tables is not justified. If the table count grows significantly (>30), reconsidering an ORM may be warranted.

## Alternatives Considered

### Alternative 1: Adopt Knex for migration management

Knex provides a migration runner, tracking, up/down migrations, and a query builder. It would solve the tracking and runner problems out of the box.

**Rejected because:**
- Adds a dependency (knex + knex CLI) for a problem solvable with a 50-line script
- Requires converting existing 7 SQL migrations to Knex format or skipping them and seeding the Knex tracking table
- The existing raw SQL migrations are well-structured and working
- Knex's query builder would go unused since all queries are raw SQL via `pg`

### Alternative 2: Defer nutrition/supplement schema design until those pillars are in scope

Wait to design nutrition and supplement tables until those features are about to ship.

**Rejected because:**
- Directly contradicts D-15: "Nutrition AND supplement tables designed now, built later"
- Risks migration conflicts when later pillars need tables that interact with workout-phase tables (e.g., food_entries linked to body_measurements via date, coaching conversations referencing nutrition data)
- The planning package resolved this question explicitly; architecture should not re-litigate it

### Alternative 3: Single monolithic migration for all new tables

One large migration file creates all workout, nutrition, supplement, and coaching tables.

**Rejected because:**
- Obscures which tables belong to which pillar
- Makes it harder to review and reason about the "designed now, built later" lifecycle
- Mixing active tables (workout gen) with placeholder tables (nutrition) in one file conflates their status

## Open Questions / Follow-ups

### Q1: What specific tables does workout generation need?

Migration `009` is identified as the workout generation pillar, but the exact tables depend on:
- ~~Whether Hevy provides muscle group data (A-02)~~ **RESOLVED 2026-03-15:** Hevy provides `primary_muscle_group` and `secondary_muscle_groups` on all ~440 exercises. However, Hevy's taxonomy is coarser than RP volume landmarks require (e.g., "shoulders" vs. front/side/rear delts). Apex needs a refinement/override layer — likely an `exercise_muscle_overrides` table with ~30–50 rows, not a full canonical mapping table. Hevy data is the base; overrides refine it.
- The workout generation MVP scope (P-05) — progression source of truth is `workout_sessions`/`workout_sets`; `progression_snapshots` is derived/optional persistence only
- The readiness scoring input architecture (ADR-003) — determines whether readiness config needs a table or can use `user_settings`

**Follow-up:** Define migration `009` table list after ADR-003 is complete. A-02 is no longer a blocker — the muscle group data model shape is clear (Hevy base + Apex overrides).

### Q2: What level of detail should the nutrition and supplement schemas have?

D-15 says "designed now, built later." But how detailed? Should the nutrition schema include:
- Specific USDA field mappings (nutrient IDs, serving size units)?
- Photo estimation result columns (D-01, P-04)?
- Adaptive TDEE output columns (D-06, H-05)?

**Follow-up:** Draft nutrition and supplement table schemas at the level of: table names, column names and types, primary/foreign keys, unique constraints, and provenance fields. Defer: specific USDA field mappings, photo estimation columns, and adaptive TDEE outputs until those features are in scope. The "designed now" bar is "enough to prevent migration conflicts," not "complete final schema."

### Q3: Should the migration runner be a shell script or a Node.js script?

A shell script (`scripts/migrate.sh`) using `psql` is simpler. A Node.js script (`scripts/migrate.js`) reuses the existing `pg` connection config and can be called from `npm run migrate`.

**Follow-up:** Implementation decision. Lean toward Node.js for consistency with the existing codebase and env var handling. Not architecture-significant.

### Q4: How should Zod schemas be structured for design-now-build-later tables?

Currently, every active table has a Zod schema in `src/schemas/`. Should empty tables get Zod schemas now (as documentation) or only when they become active?

**Follow-up:** Lean toward "Zod schemas when active" — Zod schemas exist to validate API input, and empty tables have no API input. If architecture review disagrees, the cost of adding Zod schemas now is low.

---

*This ADR establishes the migration infrastructure and conventions. It does not define specific table schemas — those are the output of subsequent architecture sessions for each pillar.*
