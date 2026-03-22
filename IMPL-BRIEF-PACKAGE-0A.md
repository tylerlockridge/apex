# Implementation Brief: Package 0A

**Package:** Migration Infrastructure (Server)
**Target repo:** Health-Platform-Desktop
**Module system:** CommonJS (`require` / `module.exports`)
**DB driver:** `pg@^8.16.3` via `src/config/database.js`

---

## 1. Objective

Create a migration tracking table (`schema_migrations`) and an automated runner script (`scripts/migrate.js`) for the Health Platform Desktop server. The runner replaces the current manual `psql -f` workflow, handles three database states (fresh, adoption, steady-state), and enables all future migrations (009+) to be applied automatically.

---

## 2. Scope

**In scope:**
- New file: `migrations/008_schema_migrations.sql`
- New file: `scripts/migrate.js`
- New entry in `package.json` scripts: `"migrate": "node scripts/migrate.js"`
- Runner handles: fresh database, adoption of existing database, steady-state incremental apply
- Verification against disposable test databases

**Out of scope:**
- Migration 009 or any subsequent migration files
- Any changes to existing migration files 001-007
- Any changes to `src/` application code
- Any client-side (Apex Android) work
- Hevy adapter, readiness engine, any feature logic
- Live database adoption (human-gated — see Section 8)

---

## 3. Required Deliverables

### 3.1 Migration file: `migrations/008_schema_migrations.sql`

Creates the `schema_migrations` tracking table. Must follow existing naming convention (`NNN_description.sql`).

Table schema per ADR-002 §1:
```
schema_migrations (
  version    VARCHAR(20) PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  applied_at TIMESTAMPTZ DEFAULT NOW()
)
```

Requirements:
- Use `CREATE TABLE IF NOT EXISTS` (idempotent)
- This file creates the table ONLY — no seed INSERTs for 001-007
- Baseline acknowledgement records are the runner's responsibility

### 3.2 Runner script: `scripts/migrate.js`

A standalone Node.js script (CommonJS) that automates migration application. Uses the `pg` module already in the project's dependencies. No new npm packages.

**Connection:** Use the same DB env vars as `src/config/database.js`:
- `DB_HOST` (default: `'localhost'`)
- `DB_PORT` (default: `5432`)
- `DB_NAME` (default: `'health_platform'`)
- `DB_USER` (required)
- `DB_PASSWORD` (required)
- `DB_SSL` (optional)

The runner should create its own `pg.Pool` or `pg.Client` — it must NOT import from `src/config/database.js` because the runner is a standalone script that may run outside the application lifecycle. Mirror the connection config pattern (env vars + defaults) without importing the module.

### 3.3 Package.json update

Add to the `scripts` section:
```
"migrate": "node scripts/migrate.js"
```

### 3.4 Verification evidence

After implementation, the following must have been demonstrated (on disposable test databases, not live):
- Fresh path: empty DB → all tables created → 8 tracking rows
- Adoption path: pre-populated DB (001-007 applied) → only `schema_migrations` added → 8 tracking rows → no existing data changed
- Steady-state path: new migration file detected and applied
- Re-run: clean no-op in all states

---

## 4. Behavioral Requirements

### 4.1 Fresh database path

**Trigger:** `schema_migrations` table does not exist AND no known application tables exist (e.g., `blood_pressure_readings` is absent).

**Behavior:**
1. Apply migration files 001 through 008 sequentially by version number
2. Read each `.sql` file from the `migrations/` directory, execute its contents
3. Migration 008 creates the `schema_migrations` table
4. After 008 succeeds, insert tracking records for ALL applied migrations (001-008) into `schema_migrations`
5. If any migration fails, stop immediately — do not skip ahead. Log the error and exit non-zero.

**Note on ordering:** Migrations 001-007 must be applied BEFORE 008 because the tracking table doesn't exist until 008 runs. The runner cannot insert tracking records until 008 has been applied. So the sequence is: apply 001-007 in order (no tracking records yet) → apply 008 (creates tracking table) → insert records for 001-008.

**Note on early failure:** If a migration before 008 fails (e.g., 003 fails), the tracking table does not exist yet. The runner should log the failure and exit. On re-run, it will detect no tracking table and no known tables (or partial tables) — this edge case should be handled by the adoption detection logic (see 4.2).

### 4.2 Adoption path

**Trigger:** `schema_migrations` table does not exist AND at least one known application table exists (e.g., `blood_pressure_readings` is present).

**Detection method:** Query `SELECT to_regclass('public.schema_migrations')` for tracking table. If NULL, query `SELECT to_regclass('public.blood_pressure_readings')` (or equivalent) for an application table. If the application table exists, this is adoption. If neither exists, this is fresh.

**Behavior:**
1. Apply ONLY migration 008 (creates `schema_migrations` table)
2. Insert baseline acknowledgement records for versions 001-007 with descriptive names and `applied_at = NOW()`. These are acknowledgements, not re-applications.
3. Insert a normal tracking record for version 008
4. Do NOT read or execute the SQL in migration files 001-007

**Baseline record names:** Use the existing migration file names stripped of the version prefix:

| Version | Name |
|---------|------|
| 001 | initial_schema |
| 002 | add_indexes |
| 003 | add_unique_constraints |
| 004 | add_user_id |
| 005 | add_integrity_constraints |
| 006 | add_sync_conflict_counter |
| 007 | add_composite_indexes |
| 008 | schema_migrations |

### 4.3 Steady-state path

**Trigger:** `schema_migrations` table exists.

**Behavior:**
1. Read all `version` values from `schema_migrations`
2. Scan the `migrations/` directory for files matching the pattern `NNN_*.sql` (three-digit zero-padded prefix, underscore, descriptive name, `.sql` extension)
3. Identify migration files whose version is NOT in the tracking table
4. Apply unapplied migrations in ascending version order
5. Each migration runs in its own database transaction (via `BEGIN` / `COMMIT`). If a migration fails, roll back that transaction, do NOT insert its tracking record, log the error, and exit non-zero.
6. After each successful migration, insert a tracking record with the version, name (from filename), and current timestamp
7. If no unapplied migrations exist, log "All migrations applied" and exit 0

### 4.4 Idempotency / re-run behavior

Running the script multiple times in any state must produce the same final result:
- Fresh → first run applies all, subsequent runs are no-ops
- Adoption → first run creates tracking table + acknowledgements, subsequent runs are no-ops
- Steady-state → first run with new migration applies it, subsequent runs are no-ops

### 4.5 Failure / abort behavior

| Failure | Runner behavior |
|---------|----------------|
| Cannot connect to database | Log error with connection details (not password). Exit non-zero. |
| Migration SQL execution fails | Roll back that migration's transaction (if in steady-state). Log error with migration version and SQL error. Exit non-zero. Do not proceed to next migration. |
| Unexpected database state (tracking table absent, known tables absent, but some other tables present) | Log warning describing what was found. Exit non-zero. Do not attempt any path. |
| Migrations directory not found or empty | Log error. Exit non-zero. |
| Migration file cannot be read | Log error with filename. Exit non-zero. |

### 4.6 Logging

The runner must log (to stdout):
- Which database path was detected (fresh / adoption / steady-state)
- For each migration: whether it was applied, skipped (already tracked), or acknowledged (adoption baseline)
- Final summary: total applied, total skipped, total acknowledged
- On error: the migration version that failed and the error message

---

## 5. Safety Constraints

These are non-negotiable. Implementation must guarantee all of them.

1. **The runner must NEVER execute SQL from migration files 001-007 on a database where application tables already exist.** The adoption path inserts tracking records only. Violation of this constraint can cause destructive failures (migrations 001-002 are not idempotent).

2. **The runner must not DROP, TRUNCATE, ALTER, or DELETE from any table other than inserting into `schema_migrations`.** The only write operations are: CREATE TABLE (migration 008), INSERT INTO `schema_migrations` (tracking records), and executing SQL from migration files on the appropriate path.

3. **Migration 008's SQL file must contain ONLY `CREATE TABLE IF NOT EXISTS`.** No seed data, no ALTER, no DROP. Baseline records are the runner's responsibility.

4. **Each steady-state migration must run in its own transaction.** A failed migration must not leave partial schema changes applied without a tracking record. Rollback the transaction, do not insert the tracking record, exit non-zero.

5. **The runner must not import from `src/` application code.** It is a standalone script. It creates its own database connection using the same env vars.

6. **The runner must handle the `migrations/` directory path relative to the project root**, not relative to the script's location. Use `path.resolve(__dirname, '..', 'migrations')` or equivalent.

---

## 6. Acceptance Criteria

All must pass on disposable test databases before the package is considered code-complete. Live adoption is a separate human-gated step.

- [ ] `migrations/008_schema_migrations.sql` exists, contains only `CREATE TABLE IF NOT EXISTS`, is idempotent
- [ ] `scripts/migrate.js` exists, uses CommonJS, imports only `pg`, `fs`, `path` (Node built-ins + existing dependency)
- [ ] `package.json` has `"migrate": "node scripts/migrate.js"` in scripts
- [ ] **Fresh path:** On an empty PostgreSQL database, `npm run migrate` creates all tables from migrations 001-008 and `schema_migrations` contains 8 rows with correct version/name pairs
- [ ] **Adoption path:** On a database with migrations 001-007 already applied (no tracking table), `npm run migrate` creates ONLY `schema_migrations`, inserts 8 rows, does NOT execute SQL from 001-007, does NOT modify any existing table or data
- [ ] **Adoption path data safety:** Row counts in existing tables are identical before and after adoption run
- [ ] **Steady-state path:** After adoption, placing a new `009_test.sql` file (containing a harmless `CREATE TABLE IF NOT EXISTS test_table (id INT)`) in `migrations/` and running `npm run migrate` applies it and records version 009
- [ ] **Idempotency:** Running `npm run migrate` a second time in any state produces "All migrations applied" and exits 0
- [ ] **Failure handling:** A migration file with invalid SQL causes the runner to roll back that migration, log the error, and exit non-zero without applying subsequent migrations
- [ ] **Connection failure:** Running with invalid DB credentials logs an error and exits non-zero
- [ ] **Logging:** Runner output clearly states which path was taken, which migrations were applied/skipped/acknowledged

---

## 7. Implementation Notes

### Sequencing

Write migration 008 first (trivial — one SQL statement). Then build the runner. The runner is the substantive work.

### Adoption detection

The critical logic decision is how to detect adoption vs fresh. Recommended approach:
1. Check `SELECT to_regclass('public.schema_migrations')` — if not null, this is steady-state
2. If null, check `SELECT to_regclass('public.blood_pressure_readings')` — if not null, this is adoption
3. If both null, this is fresh

This is simple, deterministic, and testable. Do not use try/catch on table creation or information_schema queries that may behave differently across PostgreSQL versions.

### Fresh path transaction handling

On a fresh database, migrations 001-007 run before the tracking table exists. Two options:
- **Option A:** Apply 001-008 sequentially without per-migration transactions. If any fails, the DB is in a partial state. On re-run, adoption detection sees partial tables and enters adoption mode, which acknowledges what's there and applies 008. This is simple but leaves partial state on failure.
- **Option B:** Apply 008 first (out of order) to create the tracking table, then apply 001-007 in order with normal tracking. This breaks the sequential numbering assumption but avoids the partial-state problem.

**Recommended: Option A.** The fresh path is used only for development/testing databases. Partial state on a disposable DB is acceptable. The adoption path serves as a recovery mechanism. Option B introduces complexity (out-of-order application) for a scenario that doesn't affect production.

### File scanning

Scan the `migrations/` directory for files matching `/^\d{3}_.*\.sql$/`. Extract the version number from the first 3 characters. Sort by version ascending. This matches the existing naming convention (001-007 all follow this pattern).

### Connection management

Create a `pg.Client` (not Pool) for the runner — it needs a single connection for transactional work, not a pool. Connect at script start, disconnect at script end (in a `finally` block).

### Existing test framework

The server uses Vitest (`"test": "vitest run"`). If adding runner tests, use the same framework. However, runner tests that require a live PostgreSQL instance may be better served as manual verification steps (Task 3 in GSD-PACKAGE-0A.md) rather than automated test files that need a running database in CI.

### What to verify before considering implementation complete

1. Run `npm run migrate` on a fresh DB — inspect all table schemas match what migrations 001-007 create
2. Run `npm run migrate` on an adoption-simulated DB — inspect that ONLY `schema_migrations` was added
3. Run `npm run migrate` again — confirm clean no-op
4. Add a test migration file, run again — confirm it's detected and applied
5. Remove the test file and its tracking row

---

## 8. Human Confirmation Required Before Live Adoption

These items require human action. They are NOT part of the coding implementation. They gate the transition from "code complete" to "adopted on live."

1. **Review runner source for safety.** Confirm the adoption path contains no SQL execution for versions 001-007 — only INSERT statements into `schema_migrations`.
2. **Confirm data recoverability.** Either: a database backup exists, OR all data in the live database is reconstructible from upstream sources (Health Connect re-sync, Hevy re-sync). This determines the risk tolerance for live adoption.
3. **Run preflight checks from GSD-PACKAGE-0A.md §4.1.** All go/no-go criteria must be met before executing the runner against the live database.
4. **Execute live adoption and verify.** Run the runner, then run the post-run validation queries from GSD-PACKAGE-0A.md §4.1 to confirm success.
