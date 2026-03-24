# Exercise Attribution Refinement Layer: MVP Scope

**Date:** 2026-03-15
**Input:** A-02 validation results, Hevy API exercise template data (431 exercises), RP volume landmark muscle group taxonomy
**Purpose:** Define the minimum viable refinement layer needed before migration 009 can be written.

---

## 1. Problem Statement

### Why Hevy data is insufficient as canonical truth

A-02 validation confirmed that Hevy's `GET /v1/exercise_templates` returns `primary_muscle_group` and `secondary_muscle_groups` on all 431 exercises. The data exists, is structured, and covers compounds with secondary arrays.

However, Hevy's muscle group taxonomy has 20 values:

| Hevy value | Exercise count (primary) |
|-----------|------------------------|
| quadriceps | 52 |
| chest | 49 |
| abdominals | 46 |
| shoulders | 42 |
| full_body | 42 |
| biceps | 32 |
| glutes | 27 |
| triceps | 26 |
| lats | 26 |
| cardio | 22 |
| upper_back | 22 |
| calves | 12 |
| hamstrings | 11 |
| traps | 5 |
| lower_back | 4 |
| forearms | 4 |
| neck | 4 |
| other | 3 |
| abductors | 1 |
| adductors | 1 |

RP publishes separate volume landmark data (MEV/MAV/MRV) for 16 muscle groups:

| RP muscle group | Has separate volume landmarks |
|----------------|------------------------------|
| Chest | Yes |
| **Front Delts** | Yes |
| **Side Delts** | Yes |
| **Rear Delts** | Yes |
| Biceps | Yes |
| Triceps | Yes |
| Forearms | Yes |
| Traps | Yes |
| Lats | Yes |
| Upper Back | Yes |
| Lower Back | Yes |
| Quads | Yes |
| Hamstrings | Yes |
| Glutes | Yes |
| Calves | Yes |
| Abs | Yes |

**The critical gap is shoulders.** Hevy uses a single `shoulders` label for 42 exercises. RP tracks front delts, side delts, and rear delts as three independent muscle groups with different volume landmarks. A Lateral Raise contributes to side delt volume. An Overhead Press contributes to front delt volume. A Face Pull contributes to rear delt volume. Hevy calls all three `shoulders`.

If Apex uses Hevy's taxonomy without refinement:
- Volume tracking against RP landmarks is impossible for shoulders (the third-largest exercise category)
- MRV proximity logic can't distinguish "you're over-volume on side delts but under on rear delts"
- D-05 reasoning can't explain "this exercise is included because your rear delt volume is below MEV" — it can only say "your shoulder volume is below MEV"

Secondary gaps:
- **42 `full_body` exercises** have no useful muscle attribution. Exercises like Kettlebell Swing (primarily glutes/hamstrings), Thruster (quads/shoulders), and Muscle Up (lats/chest) lose all specificity.
- **22 `cardio` exercises** have no muscle attribution. Correct for running/cycling, but some (Battle Ropes) do have meaningful upper-body involvement.
- **`other` (3 exercises)** is a null category.

### Why full first-party mapping of all exercises is unnecessary for MVP

Most Hevy muscle groups map 1:1 to RP muscle groups:

| Hevy group | RP group | Mapping | Override needed? |
|-----------|---------|---------|-----------------|
| chest | Chest | 1:1 | No |
| biceps | Biceps | 1:1 | No |
| triceps | Triceps | 1:1 | No |
| forearms | Forearms | 1:1 | No |
| lats | Lats | 1:1 | No |
| upper_back | Upper Back | 1:1 | No |
| lower_back | Lower Back | 1:1 | No |
| quadriceps | Quads | 1:1 | No |
| hamstrings | Hamstrings | 1:1 | No |
| glutes | Glutes | 1:1 | No |
| calves | Calves | 1:1 | No |
| abdominals | Abs | 1:1 | No |
| traps | Traps | 1:1 | No |
| **shoulders** | Front/Side/Rear Delts | **1:3 split** | **Yes — all 42** |
| **full_body** | Varies | **1:N** | **Selectively** |
| cardio | N/A (excluded from volume) | Excluded | No |
| neck | N/A (no RP landmarks) | Pass-through | No |
| other | N/A | Pass-through | No |
| abductors | N/A (no RP landmarks) | Pass-through | No |
| adductors | N/A (no RP landmarks) | Pass-through | No |

**12 of 20 Hevy groups need zero overrides.** They map directly. This covers ~300 of 431 exercises.

The override layer only needs to handle:
1. **42 shoulder exercises** → split into front/side/rear delt
2. **~10–15 full_body exercises** where the actual primary muscle group is obvious and volume-relevant (e.g., Kettlebell Swing → glutes)
3. The remaining ~27 full_body exercises (Olympic lifts, warm-ups, yoga) can be excluded from volume tracking entirely — they don't have RP landmarks and aren't part of hypertrophy programming

---

## 2. Proposed MVP Refinement Model

### Resolution order

```
1. Check exercise_muscle_overrides for this hevy_exercise_id
   → If found: use override's refined_primary and refined_secondaries
2. Else: use Hevy's primary_muscle_group and secondary_muscle_groups directly
3. If resolved group is "cardio", "full_body" (without override), or "other":
   → Exclude from volume landmark tracking
   → Still display in workout history
```

This is a system-level lookup — no user_id in the query. The override table is a curated reference dataset, not a per-user preference store.

### Override strategy

**Targeted overrides only.** Override rows exist only for exercises where Hevy's attribution is too coarse for RP landmark logic. The override table is small (~55 rows), curated once, and rarely changes.

An override replaces Hevy's `primary_muscle_group` and `secondary_muscle_groups` entirely for that exercise. It does not patch or merge — it substitutes. This keeps the resolution logic simple: look up override → if exists, use it; if not, use Hevy.

### Built-in library vs. custom exercise handling

**Built-in exercises (is_custom=false):** Overrides are pre-curated and shipped as seed data. Tyler currently has zero custom exercises, so this covers 100% of the current library.

**Custom exercises (is_custom=true):** When Tyler creates a custom exercise in Hevy, it gets whatever muscle group he assigns in Hevy. For MVP, custom exercises use Hevy's attribution directly — no override mechanism exists for them. If a custom exercise needs a refined attribution (e.g., a custom shoulder exercise needing a delt subdivision), that is the trigger to build a separate user-level override layer. See "What to defer" in Section 6.

**MVP position:** The override table is a system-scoped curated dataset. It does not support per-user overrides. User-specific attribution customization is deferred to post-MVP and would be modeled as a separate table, not mixed into the system override table.

### Fallback behavior

| Scenario | Behavior |
|----------|----------|
| System override exists | Use override's refined values |
| No override, Hevy group maps 1:1 to RP | Use Hevy values directly |
| No override, Hevy group is `shoulders` | **This should not happen at MVP** — all 42 shoulder exercises should have overrides. If it does (new Hevy exercise added), fall back to treating as "front_delts" (most common shoulder primary) and log a warning |
| No override, Hevy group is `full_body` | Exclude from volume tracking; display in workout history |
| No override, Hevy group is `cardio` / `other` | Exclude from volume tracking |
| Hevy group is `neck` / `abductors` / `adductors` | Pass through as-is; no RP landmarks exist but track if user cares |

---

## 3. Schema Design Options

### Option A: System-level flat override table (recommended for MVP)

```sql
CREATE TABLE exercise_muscle_overrides (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hevy_exercise_id VARCHAR(20) NOT NULL,        -- Hevy's exercise_template_id (observed: 8-char alphanumeric, e.g. '3BC06AD3')
    refined_primary VARCHAR(50) NOT NULL,          -- RP-granularity group (e.g. 'front_delts')
    refined_secondaries TEXT[] NOT NULL DEFAULT '{}', -- array of RP-granularity groups
    source VARCHAR(30) NOT NULL DEFAULT 'system_curated', -- origin of this override
    notes TEXT,                                    -- why override exists (curation rationale)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_override_exercise UNIQUE (hevy_exercise_id)
);
```

This is a **system-scoped** table — it contains curated app-level taxonomy refinements for Hevy's built-in exercise library. It has no `user_id` column because:
- The data is a system-curated dataset (like a reference table), not a user preference
- All ~55 rows are identical for every user — `user_id DEFAULT 'default-user'` would add misleading tenancy semantics
- If user-specific overrides are needed later (e.g., Tyler wants to reclassify Arnold Press from front_delts to side_delts for his personal volume tracking), they should be modeled as a separate `user_exercise_overrides` table that layers on top, not mixed into this system table

**Column sizing rationale:**
- `hevy_exercise_id VARCHAR(20)`: Observed Hevy IDs are 8-char alphanumeric (e.g., `3BC06AD3`, `32HKJ34K`). VARCHAR(20) gives 2.5x headroom for potential format changes. Matches the existing `exercise_id VARCHAR(20)` in the `workout_sets` table.
- `refined_primary VARCHAR(50)`: Longest RP group name is `abdominals` (10 chars). VARCHAR(50) matches the existing `source VARCHAR(50)` column convention across all server tables. An enum type or reference table is not justified at ~55 rows — the valid values are enforced by the seed script and, when routes are added, by Zod validation. If the muscle group vocabulary grows significantly post-MVP, a reference table can be introduced without altering this table (just add a FK).
- `source VARCHAR(30)`: Tracks provenance of the override. MVP value is always `system_curated`. Future values could include `user_override` (if the user layer is added to this table instead of a separate table) or `ai_suggested`.

**Muscle group enum values (RP taxonomy):**
`front_delts`, `side_delts`, `rear_delts`, `chest`, `biceps`, `triceps`, `forearms`, `traps`, `lats`, `upper_back`, `lower_back`, `quadriceps`, `hamstrings`, `glutes`, `calves`, `abdominals`

Plus pass-through values for non-RP groups: `neck`, `abductors`, `adductors`, `full_body`, `cardio`, `other`

**Tradeoffs:**
- Simple to implement, query, and understand
- Single lookup: `SELECT * FROM exercise_muscle_overrides WHERE hevy_exercise_id = $1`
- D-05 explainability: "Lateral Raise targets side delts (Apex refinement of Hevy's 'shoulders' category)"
- System-scoped: no user-join complexity, no multi-tenancy ambiguity
- **Limitation:** Cannot express exercise-level volume contribution percentages (e.g., "Overhead Press is 70% front delts, 30% triceps"). Primary/secondary is binary, not weighted. This is fine for MVP — RP landmarks are expressed in sets, not weighted fractions.
- **Limitation:** No per-user customization. If Tyler disagrees with a curated attribution, the fix is editing the seed data and re-running the script — not a UI action. Acceptable for a single-user app at MVP.

### Option B: Normalized muscle contributions table (more extensible)

```sql
CREATE TABLE exercise_muscle_contributions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hevy_exercise_id VARCHAR(20) NOT NULL,
    muscle_group VARCHAR(50) NOT NULL,            -- RP-granularity group
    contribution_type VARCHAR(20) NOT NULL,       -- 'primary' or 'secondary'
    contribution_weight DECIMAL(3,2) DEFAULT 1.0, -- 0.0-1.0 fractional
    source VARCHAR(30) NOT NULL DEFAULT 'system_curated',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_contrib_exercise_muscle
        UNIQUE (hevy_exercise_id, muscle_group)
);
```

**Tradeoffs:**
- Supports weighted volume attribution (e.g., count Overhead Press as 0.7 sets for front delts, 0.3 sets for triceps)
- Multiple rows per exercise — more complex queries, more seed data
- **Over-engineering risk:** RP landmarks are expressed as "sets per week" where a set either counts or it doesn't. Fractional volume attribution is a research-grade concept that no consumer app implements. The added complexity is not justified for MVP.
- Could always migrate from Option A to Option B later by splitting rows — the opposite migration (B→A) would lose data.

### Recommendation: Option A

Option A is sufficient for MVP. The primary/secondary distinction is how RP and every competitor (Fitbod, Hevy itself, Strong) model exercise-to-muscle attribution. Weighted contributions are a post-MVP enhancement that may never be needed.

**Where Option A becomes limiting:** If the readiness algorithm (H-03) or a future coaching feature wants to say "you did 14 sets of front delts this week" and an Overhead Press counted as 1 front-delt set even though triceps did ~30% of the work, the set count is slightly inflated. At n=1 with configurable weights, this imprecision is acceptable — Tyler can calibrate mentally. At scale or with precision coaching, Option B would be more defensible.

---

## 4. Scope Sizing

### Exercises needing overrides

| Category | Count | Override needed | Effort |
|----------|-------|----------------|--------|
| `shoulders` → front/side/rear delt split | 42 | All 42 | Primary curation task. Most are obvious from exercise name (Lateral Raise → side delts, Face Pull → rear delts, Overhead Press → front delts). ~5–10 require judgment calls. |
| `full_body` → reclassify to specific primary | ~12–15 | Selective | Kettlebell Swing → glutes, Thruster → quadriceps, Muscle Up → lats, etc. Olympic lifts (Clean, Snatch, etc.) stay excluded. |
| `full_body` → exclude from volume tracking | ~27–30 | No override needed — handled by fallback | Yoga, Warm Up, Pilates, Jumping Jacks, etc. |
| All other categories | ~327 | None | 1:1 mapping to RP groups |

**Total override rows: ~55**
**Curation time estimate: 1–2 hours.** Most shoulder exercises are named unambiguously (Front Raise, Lateral Raise, Rear Delt Reverse Fly). The judgment calls are exercises like Arnold Press (front + side delt hybrid), Upright Row (traps + side delts), and Band Pullaparts (rear delts or upper back?).

### Criteria for which exercises qualify for refinement

An exercise gets an override row if and only if:
1. Hevy's `primary_muscle_group` maps to multiple RP landmark groups (currently only `shoulders` → 3 groups), OR
2. Hevy's `primary_muscle_group` is `full_body` but the exercise has an obvious dominant muscle group relevant to RP volume tracking

An exercise does NOT get an override if:
- Hevy's group maps 1:1 to an RP group (even if secondaries could be refined)
- The exercise is truly full-body with no dominant primary (Olympic lifts, burpees)
- The exercise is cardio

### Schema task vs. schema + curated data task

**Both.** Migration 009 creates the table (schema task). A separate seed script in `scripts/` loads the ~55 override rows (data task). Per ADR-002, schema migrations and data migrations are separate.

---

## 5. Migration 009 Implications

### Tables likely needed in migration 009 (workout generation pillar)

| Table | Purpose | Status at creation |
|-------|---------|-------------------|
| `exercise_muscle_overrides` | Apex refinement layer for Hevy muscle group attributions | Active — populated via seed script |
| `generated_routines` | Apex-prescribed workout proposals (D-17 prescribed vs. actual separation) | Active |
| `generated_routine_exercises` | Exercises within a generated routine, with per-exercise reasoning (D-05) | Active |
| `progression_snapshots` | Weekly volume-per-muscle snapshots for landmark tracking (P-05) | Derived — optional persistence, not foundational progression store |
| `readiness_config` | Configurable readiness algorithm weights (H-02, H-03) | Contingent on ADR-003 — may alternatively use `user_settings` table |

**Note:** These are the *likely* tables. The exact list depends on ADR-003 (readiness scoring input architecture) and the workout generation contract (ADR-004). The override table is the only one fully scoped by this document.

### Seed/curation script required

Yes. `scripts/seed-exercise-overrides.js` (or `.sql`):
- Inserts ~55 rows into `exercise_muscle_overrides`
- Uses `ON CONFLICT (hevy_exercise_id) DO UPDATE` for idempotent corrections (upsert pattern — allows re-running after curation fixes)
- Source data: a JSON or CSV file in `data/exercise-overrides.json` with curated attributions
- All rows have `source = 'system_curated'`

### What remains unknown before implementation

1. **The exact 42 shoulder exercise attributions.** Most are obvious from names but ~5–10 need judgment calls. This is a curation task, not an architecture task.
2. **Which ~12–15 full_body exercises get reclassified.** Requires reviewing each one and deciding if a dominant primary exists.
3. **Whether `readiness_config` should be a table or use `user_settings`.** Depends on ADR-003.
4. **The `generated_routines` and `generated_routine_exercises` schema.** Depends on the workout generation contract (ADR-004) and P-05 MVP scope.

---

## 6. Recommendation

### MVP recommendation

- **Use Option A** (flat override table) with ~55 curated rows
- **Hevy data is the base layer**, not replaced wholesale
- **Override only where Hevy's granularity is insufficient** for RP landmark logic — primarily the 42 shoulder exercises and ~12–15 reclassifiable full_body exercises
- **Exclude `full_body` (without override), `cardio`, and `other`** from volume tracking entirely
- **Ship the override table in migration 009** alongside other workout generation tables
- **Curate the seed data** as a separate task (1–2 hours, JSON file + seed script)

### What to defer until post-MVP

- **User-level override layer.** The MVP override table is system-scoped — it has no `user_id` column. If Tyler wants to personally reclassify an exercise (e.g., "I feel Arnold Press in my side delts more than front delts"), that requires a separate `user_exercise_overrides` table that layers on top of the system table. Resolution order would become: user override → system override → Hevy base. This is deferred because (a) the system curation covers the known gaps, (b) Tyler can edit the seed data directly for now, and (c) a user-override UI is a feature unto itself.
- **Weighted volume contributions (Option B).** Not needed unless precision coaching demands fractional set attribution. Revisit if H-03 (readiness algorithm) or coaching feedback suggests set counts are misleadingly inflated.
- **Secondary muscle group refinement.** MVP overrides only refine the primary group (and optionally the secondary array). Refining Hevy's secondary attributions (e.g., splitting "shoulders" in a secondary array into "front_delts") is a quality improvement but not MVP-critical — secondary muscles are used for volume awareness, not landmark thresholds.
- **Custom exercise attribution UI.** MVP has no mechanism for custom exercise overrides — custom exercises use Hevy's attribution directly. The trigger to build this is Tyler creating a custom shoulder exercise in Hevy that needs delt subdivision.
- **Hevy data drift detection.** If Hevy changes an exercise's muscle group, Apex won't know. A periodic reconciliation script (compare cached Hevy templates against live API) is a post-MVP reliability improvement.
- **Volume contribution percentages.** The question "does an Overhead Press count as 1 front-delt set or 0.7?" is a coaching-quality improvement. MVP counts it as 1. Refinement comes from personal experience and H-03 validation data.

---

*This document scopes the refinement layer. It does not define the full workout generation data model — that depends on ADR-003 (readiness), ADR-004 (workout generation contract), and P-05 (MVP scope). The override table is the one component fully scoped and ready for migration 009.*
