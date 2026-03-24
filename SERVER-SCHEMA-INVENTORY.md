# Server Schema Inventory

**Date:** 2026-03-15
**Server:** Health Platform Desktop (`C:\Users\tyler\Documents\Claude Projects\Health-Platform-Desktop`)
**Purpose:** Inventory current database schema and migration approach to inform ADR-002 (schema migration strategy for multi-pillar design-now-build-later).

---

## Database Engine

- **PostgreSQL 16+** with `uuid-ossp` extension
- **Connection:** Node.js `pg` module with connection pool (max 10)
- **Database name:** `health_platform`
- **Deployment:** Docker Compose on DigitalOcean droplet (165.227.125.102)
- **No ORM.** Raw SQL via `pool.query()` and `withTransaction()` helper.

## Migration Mechanism

- **7 raw SQL migration files** in `migrations/` directory, numbered `001` through `007`
- **No migration runner.** Applied manually via `psql -f`. No tracking table records which migrations have run.
- **Idempotency:** Later migrations (003, 005, 007) use `DO $$ BEGIN ... IF NOT EXISTS ... END $$;` blocks and `IF NOT EXISTS` clauses. Earlier ones (001, 002) do not — they fail on re-run if objects already exist.
- **No rollback files.** No down migrations. No migration metadata table.
- **Validation layer:** Zod schemas in `src/schemas/` mirror DB CHECK constraints. Validation runs at both the API layer (Zod) and DB layer (CHECK constraints).

## Current Tables (11)

### Active — with API routes (7 tables)

| Table | PK | Dedup constraint | Source | Sync pattern |
|-------|-----|-----------------|--------|-------------|
| `blood_pressure_readings` | UUID | `(user_id, measured_at, source)` | Health Connect, manual | `ON CONFLICT DO NOTHING` |
| `body_measurements` | UUID | `(user_id, measured_at, source)` | Health Connect (Withings) | `ON CONFLICT DO NOTHING` |
| `sleep_sessions` | UUID | `(user_id, sleep_start, source)` | Health Connect (Oura) | `ON CONFLICT DO NOTHING` |
| `hrv_readings` | UUID | `(user_id, measured_at, source)` | Health Connect (Oura) | `ON CONFLICT DO NOTHING` |
| `workout_sessions` | UUID | `(user_id, hevy_id)` | Hevy API | `ON CONFLICT DO NOTHING` |
| `workout_sets` | UUID | FK → `workout_sessions` (CASCADE) | Hevy API | Inserted in transaction with parent |
| `sync_log` | UUID | None | Internal | Append-only |

### Schema-only — no API routes (4 tables)

| Table | PK | Status | Notes |
|-------|-----|--------|-------|
| `ai_analyses` | UUID | Has routes (GET/POST/DELETE) | Actually active; stores Claude analysis results (JSONB) |
| `user_settings` | UUID | No routes | `setting_key` UNIQUE; `setting_value` JSONB. Settings UI exists but no backend endpoint |
| `context_entries` | UUID | No routes | Injury/illness/travel notes. Designed for coaching context (D-16 relevant) |
| `activity_summaries` | UUID | No routes | Daily activity totals. **Bug:** `activity_date UNIQUE` is global, not user-scoped |

## Naming Conventions (observed)

| Aspect | Pattern | Consistency |
|--------|---------|------------|
| Table names | `snake_case`, plural for data, singular for settings | Consistent |
| Column names | `snake_case` | Consistent |
| Primary keys | `id UUID DEFAULT uuid_generate_v4()` | Consistent |
| Timestamps | `TIMESTAMPTZ` for events, `DATE` for calendar dates | Consistent |
| User scope | `user_id TEXT NOT NULL DEFAULT 'default-user'` | Consistent (added in migration 004) |
| Created/updated | `created_at TIMESTAMPTZ DEFAULT NOW()`, `updated_at TIMESTAMPTZ DEFAULT NOW()` | Consistent post-005 |
| Ingested | `ingested_at TIMESTAMPTZ DEFAULT NOW()` (server receipt time, immutable) | On data tables only |
| Source tracking | `source VARCHAR(50)`, `device_name VARCHAR(100)` | On all sensor tables |
| Indexes | `idx_<table_abbrev>_<column(s)>` | Mostly consistent; some abbreviations vary |
| Unique constraints | `uq_<table_abbrev>_<columns>` | Consistent post-005 |
| Check constraints | `chk_<table_abbrev>_<description>` | Consistent |

## Foreign Key Structure

Only two FK relationships exist in the entire schema:

| Child | Parent | Column | Cascade |
|-------|--------|--------|---------|
| `workout_sets` | `workout_sessions` | `workout_session_id` | ON DELETE CASCADE |
| `hrv_readings` | `sleep_sessions` | `sleep_session_id` | No cascade (orphans preserved) |

## Dedup Strategy

All sensor data tables use `ON CONFLICT (user_id, measured_at, source) DO NOTHING`. The `sync_log` table tracks `records_synced` and `records_skipped` counts per sync operation.

## Existing Future-Feature Schema

- **`context_entries`** is directly relevant to AI coaching context (D-16). It stores injury/illness/travel notes that should feed the coaching context pipeline.
- **`ai_analyses`** stores Claude analysis results in JSONB. Could serve as a pattern for coaching conversation storage (P-07).
- **`user_settings`** has the infrastructure for user preferences but no API endpoints. Will need endpoints for coaching preferences, notification settings, etc.
- **No nutrition, supplement, or workout-generation tables exist.** No TODO comments or commented-out schema for future pillars.

---

## Constraints and Risks Relevant to D-15

### Risk 1: No migration runner or tracking table

Migrations are applied manually via `psql`. There is no record of which migrations have been applied. Adding 4+ new migration files (for workout gen, nutrition, supplements, conversations) increases the risk of:
- Migrations applied out of order on the server
- Migrations skipped accidentally
- No visibility into current schema state without inspecting the DB directly

**Recommendation:** Add a `schema_migrations` tracking table and a simple runner script before adding more migrations.

### Risk 2: Inconsistent idempotency

Migrations 001–002 are not idempotent (no IF NOT EXISTS). Migrations 003–007 are idempotent. All new migrations should follow the 005 pattern (DO blocks with existence checks).

### Risk 3: No down migrations

There is no rollback capability. If a migration introduces a problem, the fix is a new forward migration. This is acceptable for a single-user app but should be a conscious convention, not an accident.

### Risk 4: `activity_summaries` has a global unique constraint bug

`activity_date UNIQUE` should be `UNIQUE (user_id, activity_date)`. This must be fixed before any new migration touches this table, or it will mask the bug.

### Risk 5: No convention for "designed now, built later" tables

The current schema has no precedent for creating empty tables as placeholders for future features. D-15 requires this. A convention is needed for:
- How to mark tables as "schema-only, not yet populated"
- Whether these tables get Zod schemas and route stubs, or just SQL
- Whether CHECK constraints and indexes should be added now or when the table is populated

---

## Missing Conventions That Should Be Standardized Now

1. **Migration numbering format.** Current: `NNN_description.sql`. No date component. Adequate for 7 files; may cause conflicts if multiple developers (or agents) create migrations concurrently. Consider `NNN_YYYYMMDD_description.sql` or just continue sequential if single-developer.

2. **Migration tracking.** No `schema_migrations` table. Migrations are manually tracked. Needs a tracking mechanism before the migration count grows.

3. **Idempotency requirement.** Should be codified: all new migrations MUST be idempotent (re-runnable without error).

4. **Column ordering convention.** Existing tables follow: PK, domain-specific columns, metadata columns (`source`, `device_name`, `notes`, `user_id`, `created_at`, `updated_at`, `ingested_at`). This pattern should be documented and followed for new tables.

5. **JSONB usage convention.** `ai_analyses` uses JSONB for `insights`, `recommendations`, `anomalies_detected`. `user_settings` uses JSONB for `setting_value`. New tables should document when JSONB is appropriate (flexible/evolving structure) vs. typed columns (stable, queryable).

6. **Design-now-build-later convention.** No precedent exists. Needs explicit rules per ADR-002.

---

*This inventory is an input to ADR-002. It does not propose solutions — it documents current state.*
