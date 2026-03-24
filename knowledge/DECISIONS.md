# Apex v2 Decisions

### 2026-03-16 ADR-002: Server schema migration strategy

**Context:** Apex v2 adds workout generation, nutrition, supplements, and coaching — each requiring new server tables. D-15 requires designing schemas for future pillars during the workout-first phase. The server has 7 raw SQL migrations with no runner or tracking table.

**Decision:** Sequential idempotent SQL migrations. Add `schema_migrations` tracking table. Group new tables by pillar (one migration per pillar). Separate schema migrations from data migrations. Design-now-build-later tables get full schema but no routes, Zod, or data.

**Rationale:** No ORM needed for ~15 tables. Idempotency prevents re-run failures. Pillar grouping makes the design-now-build-later lifecycle explicit.

**Alternatives considered:**
- Knex: adds dependency for a problem solvable with a 50-line script
- Defer future-pillar schemas: directly contradicts D-15

**Impact:** All future migrations follow this convention. Migration 009 (workout gen) is the first to use it.

---

### 2026-03-16 ADR-004: Workout generation reconciliation model

**Context:** Apex generates prescribed routines; Tyler executes actual workouts in Hevy. These diverge (exercise swaps, weight changes, skipped sets). D-17 requires progression to use actuals only.

**Decision:** Progression uses `workout_sessions` + `workout_sets` exclusively. `generated_routines` is never queried by progression. Unmatched workouts are first-class. Prescribed-to-actual linkage (via Hevy's `routine_id`) is conditional on VD-1 (Hevy routine creation API support) and exists for D-05 explainability only.

**Rationale:** Decoupling progression from generation means progression works on all historical data without bootstrapping. The conditional linkage avoids assuming unvalidated API capabilities.

**Alternatives considered:**
- FK from workout_sessions to generated_routines: most sessions have no corresponding routine
- Progression queries generated_routines: violates D-17

**Impact:** Schema supports both VD-1 paths without branching. Progression features can ship before routine generation.

---

### 2026-03-16 Exercise muscle overrides are system-scoped

**Context:** Hevy's muscle group taxonomy is coarser than RP volume landmarks (e.g., "shoulders" not "front/side/rear delts"). Apex needs a refinement layer for ~55 exercises.

**Decision:** System-scoped `exercise_muscle_overrides` table with no `user_id`, unique by `hevy_exercise_id`. ~55 curated rows shipped as seed data. User-level overrides deferred as a separate future table.

**Rationale:** These are app-level taxonomy refinements, not user preferences. Adding `user_id DEFAULT 'default-user'` would introduce misleading tenancy semantics.

**Impact:** Simple single-lookup resolution (override → Hevy base → exclude). Custom exercises use Hevy attribution directly for MVP.

---

### 2026-03-16 D-05 applies to all algorithmic training recommendations

**Context:** D-05 says "generated workout displays rationale." P-05 scopes MVP to volume tracking, weight suggestions, and MRV flagging — not full routine generation. Ambiguity: does D-05 apply to MVP outputs?

**Decision:** Yes. D-05 applies to any algorithmic training recommendation surfaced to the user, including weight suggestions (2-for-2 rule), MRV flags, and volume-vs-landmark comparisons.

**Rationale:** The evidence behind D-05 (Fitbod opacity, RP double-progression complaints) is about algorithmic opacity in general. The 2-for-2 rule IS the algorithm RP users complained was opaque.

**Impact:** Decision register D-05 description updated with clarification.

---

### 2026-03-16 Hevy-facing IDs are string-typed, not UUID

**Context:** Observed Hevy routine IDs are UUID-formatted strings. Hevy exercise IDs are 8-char alphanumeric. Write-response formats are unvalidated.

**Decision:** All Hevy-facing ID columns use `VARCHAR(100)` (routine IDs) or `VARCHAR(20)` (exercise IDs). Never `UUID` type.

**Rationale:** Matches existing `hevy_id VARCHAR(100)` convention on `workout_sessions`. Write-response format from Hevy POST endpoints (if they exist) is unknown.

**Impact:** Consistent with existing schema. No type mismatch risk with unvalidated API responses.
