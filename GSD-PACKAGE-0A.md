# GSD Package 0A

**Package:** Migration Infrastructure (Server)
**Status:** Ready for execution
**Source:** IMPLEMENTATION-ROADMAP.md, Phase 0A
**Estimated effort:** 1 session

---

## 1. Objective and Scope

**Objective:** Create a migration tracking table and automated runner script so that all subsequent server schema changes (migrations 009-012 and beyond) have a reliable, automated application path. This replaces the current manual `psql -f` workflow.

**In scope:**
- Migration file `008_schema_migrations.sql` that creates the `schema_migrations` tracking table
- Adoption logic: record existing migrations 001-007 as baseline acknowledgements in the tracking table
- Migration runner script (`scripts/migrate.js`) using the existing `pg` module
- Two database paths: fresh initialization and adoption of an existing database
- Verification on a local or disposable test database before any live environment

**Out of scope:**
- Migration 009 or any subsequent migrations (Phase 1)
- Any client-side work (Package 0B)
- Hevy adapter, readiness engine, any feature work
- Schema design for future tables
- Changes to existing migration files 001-007
- Environment-specific deployment runbooks

---

## 2. Why This Package Comes First

1. **Every server phase depends on it.** Phase 1 needs the runner to apply migration 009. Phase 2 needs it for 010-012. Without the runner, new migrations revert to manual `psql -f` — the exact problem ADR-002 was created to solve.
2. **Zero dependencies.** No client work, no validation results, no external APIs needed. Pure server infrastructure.
3. **Eliminates technical debt.** The server currently has 7 manually-applied migrations with no tracking table and no way to know programmatically which have been applied. This must be resolved before adding more migrations.
4. **Smallest possible unblocking unit.** One SQL file + one script. Maximum downstream leverage.

---

## 3. Migration Adoption Strategy

The runner must handle two fundamentally different database states. These paths have different safety profiles and must be treated separately.

### Path A: Fresh database initialization

A database with no tables. The runner applies all migration files (001 through 008) in order. Each migration creates tables from scratch. After each successful apply, the runner records the version in `schema_migrations`.

Migration 008 creates the `schema_migrations` table itself. On fresh init, the runner must apply 008 first (to create the tracking table), then retroactively record 001-007 and 008 as applied. This is a controlled bootstrap sequence, not a normal migration flow.

**Key property:** On a fresh database, the runner applies real SQL from migration files 001-008 in order. These are actual migrations, not acknowledgements.

### Path B: Adoption of an existing database

A database where migrations 001-007 have already been applied manually via `psql -f`, but no `schema_migrations` table exists. This is the current production state.

The runner must:
1. Detect the adoption case (no `schema_migrations` table exists, but other tables do)
2. Apply migration 008 only (creates `schema_migrations` table)
3. Insert **baseline acknowledgement records** for migrations 001-007 — these are NOT re-applications of those migrations. They are records stating "these migrations were applied before tracking began." The `applied_at` timestamp for 001-007 should reflect the adoption time, not the original application time.
4. Insert a normal tracking record for migration 008 (the one actually applied)

**Key distinction:** In adoption mode, the runner does NOT execute the SQL in files 001-007. It only records that they were already applied. The legacy migration files are not idempotent (001-002 fail on re-run per SERVER-SCHEMA-INVENTORY.md). Re-executing them would be destructive.

### Path C: Already-adopted database

A database where `schema_migrations` exists and contains records. The runner scans for unapplied migrations and applies only those. This is the normal steady-state path for all future migrations (009+).

### Adoption safety invariant

The runner must NEVER apply migration files 001-007 on a database where those tables already exist. The adoption path guarantees this by inserting baseline records without executing the SQL files.

---

## 4. Task Decomposition

### Task 1: Write migration 008

**Purpose:** Create the `schema_migrations` tracking table.

**Dependencies:** None.

**Likely files affected:**
- New: `migrations/008_schema_migrations.sql` (in the Health Platform Desktop server repo)

**What this task produces:**
- A SQL file that creates the `schema_migrations` table per ADR-002 §1:
  - `version VARCHAR(20) PRIMARY KEY`
  - `name VARCHAR(255) NOT NULL`
  - `applied_at TIMESTAMPTZ DEFAULT NOW()`
- Uses `CREATE TABLE IF NOT EXISTS` (idempotent per ADR-002 §2)
- Does NOT contain baseline seed INSERTs for 001-007. Baseline acknowledgement records are the runner's responsibility, not the migration file's. Migration 008's SQL file creates the table only.

**Completion criteria:**
- SQL file executes without error on a database with no `schema_migrations` table (creates it)
- SQL file executes without error on a database where the table already exists (no-op)
- Table matches ADR-002 §1 spec exactly

**Risks:** Minimal. This is a single CREATE TABLE IF NOT EXISTS statement.

---

### Task 2: Build the migration runner script

**Purpose:** Replace manual `psql -f` with an automated script that manages all three database paths (fresh, adoption, steady-state).

**Dependencies:** Task 1 (migration 008 file must exist).

**Likely files affected:**
- New: `scripts/migrate.js`
- Possibly modified: `package.json` (add `migrate` script alias)

**What this task produces:**

A Node.js script with this logic:

1. Connect to PostgreSQL using the same connection pattern as the existing server (check `src/db.js` or equivalent for env var names)
2. Check whether `schema_migrations` table exists (query `pg_catalog` or `to_regclass`)
3. **If table does not exist:**
   - Check whether other known tables exist (e.g., `blood_pressure_readings`) to distinguish fresh DB from adoption
   - **Fresh DB (no tables):** Apply migration files 001-008 sequentially. After each successful apply, insert a tracking record. Migration 008 creates the tracking table; once it exists, subsequent inserts go into it normally.
   - **Adoption (tables exist, no tracking table):** Apply ONLY migration 008 (creates tracking table). Then insert baseline acknowledgement records for 001-007 (version, name, current timestamp) WITHOUT executing those migration files. Insert a normal record for 008.
4. **If table exists (steady-state):** Read applied versions. Scan `migrations/` for `NNN_*.sql` files. Apply unapplied files in ascending version order. Each migration runs in its own transaction. Insert tracking record after successful apply.
5. Log every action: which path was taken, which migrations applied, which skipped, any errors
6. Exit 0 on success, non-zero on failure
7. No new npm dependencies — `pg` only

**Completion criteria:**
- Fresh DB path: all migrations 001-008 applied, 8 tracking rows, all tables created
- Adoption path: only migration 008 applied, 001-007 acknowledged, all pre-existing data untouched
- Steady-state path: unapplied migrations detected and applied, already-applied migrations skipped
- Re-run in any state: clean no-op
- Connection failure: logs error, exits non-zero
- Each migration runs in its own transaction; failed migration leaves no tracking record

**Risks:**
- **Adoption vs fresh detection logic.** The runner must correctly distinguish "no tables at all" from "tables exist but no tracking table." A false detection would either try to re-apply 001-007 on an existing DB (destructive) or skip them on a fresh DB (incomplete). Detection should check for a known existing table (e.g., `blood_pressure_readings`) — if it exists, this is adoption; if not, this is fresh.
- **Legacy migration idempotency gap.** Migrations 001-002 are NOT idempotent. The adoption path must never execute them. The fresh path can execute them safely because no conflicting objects exist.
- **Transaction scope on fresh path.** Migrations 001-008 on fresh DB must apply in order. If migration 003 fails, the runner should stop — not skip ahead. The tracking table may not yet exist (it's created by 008), so early failures must be handled without assuming the tracking table is available.

---

### Task 3: Verify on a disposable test database

**Purpose:** Validate all three runner paths before touching any live environment.

**Dependencies:** Tasks 1 and 2 complete.

**Likely files affected:** None (verification only).

**What this task produces:**
- Confirmed behavior for fresh, adoption, and steady-state paths
- Confidence to proceed with live adoption

**Verification sequence:**

1. **Fresh path test:** Create a temporary empty PostgreSQL database. Run the runner. Verify: all tables created, 8 tracking rows, tables match expected schema.
2. **Adoption path test:** Create a temporary database and manually apply migrations 001-007 via `psql -f` (simulating the current production state). Run the runner. Verify: only `schema_migrations` table added, 8 tracking rows present, no existing table modified, no existing data changed.
3. **Steady-state test:** Add a dummy `999_test.sql` migration to the `migrations/` directory. Run the runner. Verify: dummy migration applied and tracked. Remove dummy file and delete its tracking row.
4. **Re-run test:** Run the runner again on the adoption-path database. Verify: clean no-op, all 8 versions still tracked.

**Completion criteria:**
- All four verification steps pass
- No data loss or schema mutation on the adoption-path test database

**Risks:** Minimal — this runs against disposable databases only.

---

### Task 4: Adopt the live database

**Purpose:** Bootstrap migration tracking on the live server database.

**Dependencies:** Task 3 complete (all verification paths pass).

**This task is a live database mutation.** It requires explicit go/no-go criteria (see Section 4.1 below).

**What this task produces:**
- `schema_migrations` table on the live database
- Baseline acknowledgement records for 001-008
- Confirmation that no existing data was modified

**Completion criteria:**
- `schema_migrations` table exists with 8 rows
- All pre-existing tables remain intact with data unchanged
- Re-run produces clean no-op
- A dummy migration file is detected and would be applied (test with `--dry-run` flag if implemented, or verify by log output without applying)

---

### 4.1 Go / No-Go Criteria for Live Adoption (Task 4)

**Preflight — all must be true before execution:**

| Check | How to verify |
|-------|---------------|
| Task 3 verification passed on a disposable database | Adoption path test completed without error |
| Runner source code reviewed for destructive operations | No DROP, TRUNCATE, DELETE, or ALTER statements outside migration 008 |
| Migration 008 SQL reviewed | Contains only CREATE TABLE IF NOT EXISTS for `schema_migrations` |
| Runner adoption path confirmed: does NOT execute SQL from files 001-007 | Code review of adoption branch — only INSERTs into `schema_migrations` |
| Live database is accessible and responsive | Connection test succeeds |
| Recent database backup exists or data loss is recoverable | Verify backup or confirm the data is reconstructible from Health Connect + Hevy re-sync |

**Abort conditions — stop and investigate if any occur:**

| Condition | Action |
|-----------|--------|
| Runner detects neither fresh nor adoption state (unexpected table set) | Abort. Investigate manually. |
| Runner attempts to execute SQL from migration files 001-007 during adoption | Abort immediately. Runner logic is wrong. |
| Any existing table reports a schema change after runner completes | Abort. Investigate what mutation occurred. |
| Connection drops mid-execution | Runner should exit non-zero. Re-run after connection is restored (idempotent). |

**Post-run validation — all must be true to declare success:**

| Check | How to verify |
|-------|---------------|
| `schema_migrations` table exists | `SELECT * FROM schema_migrations ORDER BY version;` returns 8 rows |
| Pre-existing table count unchanged | `SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';` equals previous count + 1 |
| Pre-existing table data intact | Spot-check: `SELECT count(*) FROM blood_pressure_readings;` matches pre-run count |
| Runner re-run is clean no-op | Run again, verify "all migrations applied" output |

---

## 5. Recommended Execution Order

```
Task 1 (write migration 008)
  → Task 2 (build runner script)
    → Task 3 (verify on disposable test DB)
      → Task 4 (adopt live database — requires go/no-go)
```

**Sequential.** Task 2 needs the migration file. Task 3 needs the runner. Task 4 needs Task 3 to pass.

Tasks 1 and 2 are the creative work (~70% of effort). Task 3 is verification (~20%). Task 4 is a controlled live operation (~10%).

---

## 6. Definition of Done

Package 0A is complete when ALL of the following are true:

- [ ] `migrations/008_schema_migrations.sql` exists and is idempotent
- [ ] `scripts/migrate.js` exists, uses only existing `pg` dependency, requires no new npm packages
- [ ] Runner correctly handles all three database states: fresh (apply all), adoption (apply 008 + acknowledge 001-007), steady-state (apply unapplied only)
- [ ] Runner NEVER executes SQL from files 001-007 during adoption (code-level guarantee)
- [ ] All Task 3 verification steps pass on a disposable test database
- [ ] Task 4 go/no-go criteria met and live database adopted
- [ ] `schema_migrations` table exists on live database with 8 rows (versions 001-008)
- [ ] All pre-existing tables remain intact with data unchanged
- [ ] Re-run on live database produces clean no-op
- [ ] Runner logs clearly indicate which path was taken and which migrations were applied/skipped

---

## 7. Open Questions

| Question | Impact on Package 0A | Recommended resolution |
|----------|---------------------|----------------------|
| Does the server's Node.js process have direct filesystem access to the `migrations/` directory, or are migrations deployed separately? | Affects how the runner locates SQL files. | Inspect server repo structure before Task 2. If paths differ, accept a `--migrations-dir` flag. |
| Does the existing server use `DATABASE_URL` or individual `PG_*` env vars? | Affects runner connection setup. | Check `src/db.js` or equivalent. Mirror the same pattern. |
| Is a database backup or snapshot available before live adoption? | Affects Task 4 risk tolerance. | Check during Task 4 preflight. If no backup, verify that all data is reconstructible from upstream sources (Health Connect re-sync, Hevy re-sync). |
| Should the runner support a `--dry-run` flag? | Useful for preflight but adds scope. | Optional. If trivial to add during Task 2, include it. If not, omit — Task 3 disposable DB testing serves the same purpose. |

None of these questions block starting. All can be resolved during early Task 2 execution.

---

## 8. Handoff to Next Package

### What Package 0A enables

- **Phase 1 (Hevy adapter + migration 009):** The runner can now apply `009_workout_generation_tables.sql` automatically. Without Package 0A, Phase 1 would revert to manual `psql -f`.
- **Phase 2 (design-now-build-later migrations 010-012):** Same runner applies these. Each migration self-registers in `schema_migrations`.
- **All future schema changes:** Any new `.sql` file in `migrations/` is automatically detected and applied. The manual migration workflow is permanently retired.

### Relationship to Package 0B

Package 0B (client health provider interface) has **zero dependency** on Package 0A. They can run in parallel, in either order, or in the same session. Package 0A is recommended first only because it is simpler and its downstream value is higher (unblocks all server phases). Package 0B can start immediately regardless of 0A's status.

### After both packages complete

Phase 1 (Hevy adapter + workout schema) and Phase 2 (readiness engine + empty schemas) can both begin. Phase 1 is on the critical path to MVP.
