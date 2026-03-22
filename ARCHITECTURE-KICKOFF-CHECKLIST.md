# Architecture Kickoff Checklist

**Date:** 2026-03-15
**Purpose:** Practical checklist for the first architecture session. Ensures the planning package is used correctly, locked decisions are not silently rewritten, and hypotheses are not baked in as facts.

This is NOT an architecture design artifact. It contains no schemas, APIs, components, or implementation plans.

---

## 1. Preconditions Before Session Starts

Complete every item before opening any architecture design work.

- [ ] **Planning package is designated as source of truth.** The following documents are the authoritative inputs — not memory, not intuition, not "what seems right":
  - `DECISION-REGISTER.md` (705 lines, 17 decisions, 8 hypotheses, 5 assumptions, 7 preferences)
  - `ARCHITECTURE-ASSUMPTIONS.md` (handoff document with constraint tables and validation checklists)
  - `POST-GATE-REVIEW.md` (adversarial review, GO recommendation, 8 preconditions)
  - `GATE-REVIEW.md` (stress-test, blocker resolutions)

- [ ] **Architecture assumptions document is current.** Confirm `ARCHITECTURE-ASSUMPTIONS.md` reflects all post-gate normalization changes (P-06 → D-17, D-10 split, D-15 extended, thresholds added to H-01/H-02/H-03/H-06, A-01–A-05 registered, P-07 added).

- [ ] **First-week validations are visible.** Three items must be validated this week. Confirm they are scheduled or in progress:
  - H-06: Hevy API rate limits (blocks sync design)
  - A-01: Wearable device + HC data types (blocks readiness design)
  - A-02: Hevy exercise template muscle groups (blocks workout gen scoping)

- [ ] **Unresolved ambiguity is acknowledged.** The following are explicitly uncertain and must not be treated as settled:
  - D-10b: Hevy API fallback display scope (preference, not decision)
  - P-03: Morning briefing pre-generated vs. on-demand (preference, not decision)
  - P-05: Workout generation MVP scope boundary (preference, adjustable)
  - P-01: Readiness staleness 24h threshold (preference, adjustable)
  - OA-1 through OA-5: Open assumptions (see ARCHITECTURE-ASSUMPTIONS.md §10)

- [ ] **Implementation notes are flagged as non-binding.** D-06, D-14, P-03, and D-10a contain implementation notes (EMA smoothing, request/response pattern, batch job timing, exponential backoff). These are starting hypotheses, not constraints. Confirm this is understood before designing around them.

---

## 2. Locked Decisions That Must Be Treated as Hard Constraints

Every item below is a hard constraint. Architecture may choose HOW to satisfy each one but may NOT override, weaken, or silently ignore any of them.

### Product Constraints

- [ ] **D-01: Precision-capable nutrition model.** Schema supports accurate manual logging, macro tracking, adaptive TDEE, body-composition workflows. Photo estimation is secondary convenience, not primary truth. Architecture must not simplify the nutrition model into awareness-only to reduce scope.
- [ ] **D-02: Workout-first execution sequence.** Workout generation, Hevy integration, and readiness are the first features to architect. Architecture must not reprioritize nutrition or coaching ahead of workouts.
- [ ] **D-05: Workout generation shows reasoning.** Every generated workout displays per-exercise rationale. User can override any element. Architecture must not design an opaque generation pipeline that cannot explain its outputs.
- [ ] **D-07: AI safety rails are non-negotiable.** No diagnosis, no fabricated data, always cite data sources, always recommend doctor for BP >= 140/90. Architecture must not defer safety rail implementation to "polish" — it ships with the coaching feature.
- [ ] **D-09: No streak counters or gamification.** Trend visualization instead. Architecture must not introduce daily-obligation mechanics or engagement scores.
- [ ] **D-11: No fully autonomous workout generation.** Semi-autonomous only: generate → review → push. Architecture must not design a "just start" flow without a review step.
- [ ] **D-12: No social features.** No leaderboards, feeds, sharing, or multi-user data paths. Architecture must not include multi-user abstractions.
- [ ] **D-17: Hevy is source of truth for actual workout data.** Progression uses completed Hevy data, not Apex prescriptions. Architecture must not design progression around prescribed data.

### Compute Constraints

- [ ] **D-03: Hybrid compute split.** Client: TDEE, readiness scoring. Server: sync, history, AI orchestration, workout generation. Photo: client-direct-to-AI-API. Architecture must not move TDEE or readiness to server-only, or move workout generation to client-only for v1.
- [ ] **D-06: TDEE client-first.** Ships with static Mifflin-St Jeor; adaptive activates after 4+ weeks. Server receives outputs only. Architecture must not make TDEE depend on server availability.
- [ ] **D-14: Server-side workout generation for v1.** Server returns routine with reasoning; client provides readiness context. Architecture must not require client to have full Hevy history locally for workout generation.

### Data / Provenance Constraints

- [ ] **D-04: Food database quality protection.** USDA primary, OFF secondary (flagged as community-sourced). No unverified user submissions overwrite validated entries. Architecture must not merge USDA and OFF into an undifferentiated data source.
- [ ] **D-13: Food entry provenance field from day one.** Every food entry carries a `source` enum (verified/estimated/corrected/custom/quick_add). Architecture must not defer provenance to a future version.
- [ ] **D-15: Nutrition + supplement schema designed now, built later.** Nutrition AND supplement table schemas defined during this architecture phase. Tables created empty. Architecture must not skip supplement schema design because supplements are 5th in sequence.
- [ ] **D-16: AI coaching context is modular.** Context assembly pipeline supports plugging in nutrition, supplement, and future data sources without restructuring. Architecture must not hardwire the context pipeline to only handle workout-phase data types.

### Integration Constraints

- [ ] **D-08: HC sync with active monitoring.** Permission watchdog in sync cycle. Staleness display in UI. Graceful degradation to last-known data. Architecture must not assume HC permissions are stable.
- [ ] **D-10a: Hevy API abstraction interface.** All Hevy API calls go through the server abstraction layer. Business logic must not reference the Hevy API directly. Architecture must not scatter Hevy API calls across multiple modules without an adapter boundary.

---

## 3. Hypotheses That Must Not Be Baked In as Facts

For each item: architecture may design around the working assumption, but must not make the system depend on it being true.

- [ ] **H-01: Readiness-to-Hevy loop is the primary differentiator.**
  - May assume temporarily: the loop is the highest-value feature and worth building first
  - Must avoid hard-coding: any coupling that makes readiness display, workout gen, or Hevy sync unable to function independently. Each link must have standalone value if the loop fails

- [ ] **H-02: HRV predicts strength training readiness.**
  - May assume temporarily: HRV is a useful input to readiness scoring
  - Must avoid hard-coding: fixed HRV weight in the readiness algorithm. Weights must be configurable. Readiness must function without HRV input

- [ ] **H-03: Readiness algorithm produces meaningful scores.**
  - May assume temporarily: a simple heuristic (equal-weight normalized average + subjective) is a valid starting point
  - Must avoid hard-coding: any assumption that the algorithm is solved. Architecture must treat readiness as an unsolved research problem that will iterate. Display layer must not over-invest before the model is validated

- [ ] **H-04: Health Connect delivers reliable data on Tyler's device.**
  - May assume temporarily: HC works and the watchdog (D-08) is a safety net
  - Must avoid hard-coding: ruling out direct wearable API integrations (WHOOP/Oura OAuth). Architecture should leave a clean seam for an alternative data source

- [ ] **H-05: Adaptive TDEE adds value at n=1.**
  - May assume temporarily: adaptive TDEE is a Phase 2 upgrade after 4+ weeks of data
  - Must avoid hard-coding: any dependency on adaptive TDEE existing. v1 is static only. The TDEE calculation interface should accommodate a future algorithm swap

- [ ] **H-06: Hevy API rate limits are workable.**
  - May assume temporarily: some level of programmatic Hevy access is feasible
  - Must avoid hard-coding: on-demand Hevy reads into the sync or workout generation path before rate limit testing completes. Architecture must support batch/cached modes without restructuring

- [ ] **H-07: AI coaching sustains engagement beyond 3 months.**
  - May assume temporarily: coaching is worth building with proactive features (morning briefings, trend alerts)
  - Must avoid hard-coding: any assumption that coaching will be used daily forever. Engagement tracking must be built into the coaching feature from day 1

- [ ] **H-08: Supplement-outcome correlation is compelling.**
  - May assume temporarily: the simplest version (overlay graph) is worth building when supplement tracking ships
  - Must avoid hard-coding: any investment beyond the simplest overlay. No complex correlation engine, no causal inference, no recommendation system

---

## 4. Deferred Capabilities with Present-Day Implications

These are deferred features that still require architectural consideration today.

### Abstraction boundaries

- [ ] **Coaching context assembly must be modular (D-16).** Workout-phase coaching ships with health + training context only. Nutrition, supplement, and future data sources plug in later. Architecture must define the context provider interface now, even though only one provider exists at launch.
- [ ] **TDEE calculation must be swappable (H-05 / D-06).** Static Mifflin-St Jeor ships first. Adaptive algorithm replaces it later. Architecture should use a clean calculation interface, not inline the static formula.
- [ ] **Hevy API fallback scope is undecided (D-10b).** The abstraction layer (D-10a) is locked. What happens behind it if the API disappears is a preference to be decided during architecture. Keep the interface boundary clean enough that any fallback option (display UI, text export, nothing) can be added without restructuring.
- [ ] **HC data source may need alternatives (H-04).** If HC is unreliable, direct wearable APIs (WHOOP OAuth, Oura OAuth) may be needed. Architecture should not make HC the only possible health data source at the interface level.

### Data model implications

- [ ] **Nutrition schema designed now, built later (D-15).** Tables for foods, food_entries, nutrition_targets designed during this phase. Created empty on server. Architecture must produce these schemas even though no nutrition code ships in the workout-first phase.
- [ ] **Supplement schema designed now, built later (D-15 extended).** Tables for supplements, supplement_entries, supplement_stack, supplement_interactions designed alongside nutrition. Same rationale: prevent migration conflicts.
- [ ] **Conversation history table designed now (P-07).** AI coaching conversation storage is server-side. Schema designed alongside coaching endpoint design. Client caches recent N messages.

### Provenance

- [ ] **Food entry provenance field is a day-one requirement (D-13).** The `source` enum (verified/estimated/corrected/custom/quick_add) must be in the nutrition schema even though nutrition doesn't ship in the workout-first phase. When the schema is designed (D-15), provenance is part of it.
- [ ] **Prescribed vs. actual workout distinction (D-17).** Data model must clearly separate Apex-generated routines (prescribed) from Hevy-logged workouts (actual). Progression uses actuals only. This separation must be visible in whatever workout data structures architecture defines.

### Sync expectations

- [ ] **Engagement tracking infrastructure for coaching (H-07).** Even though long-term engagement is a post-MVP hypothesis, the tracking hooks (interactions initiated, read rate, recommendations acted on) must exist at coaching launch. Architecture should plan for this instrumentation.
- [ ] **Hevy sync architecture depends on H-06 validation.** Do not finalize sync design until rate limit testing completes. Architecture can proceed on schema design, client structure, and readiness while sync design waits for rate limit data.

---

## 5. External Dependency Validations to Run in Week One

| # | Item | Why It Matters | Architectural Decision It Influences | Due |
|---|------|---------------|-------------------------------------|-----|
| 1 | **Hevy API rate limits (H-06)** | Unknown rate limits determine whether sync is on-demand, cached, or batch-only | Sync architecture, caching strategy, workout generation latency model, Hevy abstraction layer design | Before sync design finalizes |
| 2 | **Wearable device + HC data types (A-01)** | If wearable doesn't write HRV to HC, the readiness pipeline loses its most prominent input | Readiness algorithm input set, HRV-related hypothesis scope (H-02 may become moot), whether to design for direct wearable API bypass | First week |
| 3 | **Hevy exercise template muscle groups (A-02)** | If API doesn't return muscle group data, Apex must build a curated mapping table (~200–500 exercises) | Workout generation MVP scope, initial data work before any workout gen can ship, exercise data model | First week |
| 4 | **Hevy workout completion sync lag (OA-3)** | If Hevy data appears slowly after logging, progression data freshness (D-17) is affected | Sync timing, stale data policy for progression, whether to show "data pending" states | First week (opportunistic — test during H-06 ramp) |

---

## 6. Questions Architecture IS Allowed to Answer

Architecture has full authority over implementation within the planning constraints. Specifically:

- [ ] **How to satisfy each locked decision.** What schemas, data structures, interfaces, and patterns implement D-01 through D-17. The constraint is the WHAT; architecture decides the HOW.
- [ ] **How to preserve food entry provenance (D-13).** What the `source` enum looks like in the actual schema. What tables carry it. How it flows through the system.
- [ ] **How to separate prescribed vs. actual workout data (D-17).** What tables, foreign keys, or data structures distinguish Apex-generated routines from Hevy-logged actuals.
- [ ] **How to support offline expectations (D-03, D-06).** What data Room caches for client-side TDEE and readiness. How staleness is tracked. How the client handles server unavailability.
- [ ] **How to isolate the Hevy API behind an abstraction (D-10a).** What the adapter interface looks like. How business logic references workout data without knowing it came from Hevy.
- [ ] **How to make coaching context modular (D-16).** What the context provider interface looks like. How nutrition and supplement data sources plug in later.
- [ ] **How to make readiness weights configurable (H-02, H-03).** What configuration mechanism allows tuning weights without code changes.
- [ ] **How to implement the permission watchdog (D-08).** What the monitoring cycle looks like. What staleness thresholds trigger alerts. What "graceful degradation" means in the UI.
- [ ] **How to design nutrition + supplement schemas for future use (D-15).** What tables to create. What relationships to define. What indexes to include. How to keep them compatible with the workout-first schema.
- [ ] **How to batch or cache Hevy data (contingent on H-06).** Once rate limits are known, architecture chooses the caching/batching strategy.
- [ ] **How to implement AI safety rails (D-07).** What system prompt structure, constraint injection mechanism, and output validation approach to use.
- [ ] **How to structure the morning briefing (P-03).** Whether to pre-generate, on-demand, or hybrid. This is a preference — architecture decides.
- [ ] **What the Hevy API fallback scope should be (D-10b).** Full display UI, text export, clipboard, or minimal — architecture recommends based on cost/risk analysis.
- [ ] **What the workout generation MVP scope boundary is (P-05).** Volume tracking, weight suggestions, MRV flagging — architecture recommends what's feasible for v1 vs. v2.

---

## 7. Questions Architecture is NOT Allowed to Answer Unilaterally

These require explicit user decision. Architecture may propose, recommend, and argue — but may not silently adopt a different answer.

- [ ] **Changing the product wedge.** Workout-first (D-02) is locked. Architecture must not reprioritize nutrition-first or coaching-first because it would be "easier" or "more natural." If architecture discovers a strong reason to change sequencing, it must surface the argument and get explicit approval.

- [ ] **Rewriting MVP exclusions.** No streaks (D-09), no autonomous generation (D-11), no social (D-12) are locked. Architecture must not introduce "lightweight" versions of excluded features (e.g., "just a small activity feed," "just a simple auto-start option").

- [ ] **Converting hypotheses into decisions.** H-01 through H-08 are hypotheses with defined validation methods. Architecture must not treat any of them as confirmed facts. Specifically:
  - Must not assume the readiness-to-Hevy loop works end-to-end (H-01)
  - Must not hardcode HRV as a reliable readiness predictor (H-02)
  - Must not assume the readiness algorithm is solved (H-03)
  - Must not assume HC is reliable on Tyler's device (H-04)
  - Must not assume adaptive TDEE is needed for v1 (H-05)
  - Must not assume on-demand Hevy reads are feasible (H-06)

- [ ] **Expanding AI coaching scope beyond workout-phase context.** D-16 limits coaching to health + training context until nutrition ships. Architecture must not add nutrition or supplement context "since we're designing the schema anyway."

- [ ] **Promoting preferences to decisions.** P-01 (staleness threshold), P-02 (supplement audit style), P-03 (briefing pre-generation), P-04 (photo estimation sequence), P-05 (workout gen MVP scope), and D-10b (fallback scope) are preferences. Architecture may recommend specific choices but must not lock them without explicit confirmation.

- [ ] **Dropping the "designed now, built later" requirement.** D-15 requires nutrition AND supplement schema design during this architecture phase even though those features don't ship first. Architecture must not defer schema design to "when we need it."

- [ ] **Silently removing the provenance field.** D-13 requires the `source` enum on food entries from day one. Architecture must not simplify the nutrition schema by dropping provenance.

- [ ] **Changing the compute split.** D-03 locked the hybrid split. Architecture must not move TDEE to server-only or workout gen to client-only without explicit justification and approval.

---

## 8. Exit Criteria for the First Architecture Session

The first architecture session is successful when ALL of the following are true:

### Constraints acknowledged
- [ ] All 17 locked decisions have been reviewed and their architectural implications are understood
- [ ] All 8 hypotheses have been reviewed and their "must avoid hard-coding" constraints are noted in architecture plans
- [ ] All 5 assumptions (A-01 through A-05) have been reviewed for current status
- [ ] The 4 implementation notes (D-06, D-14, P-03, D-10a) are explicitly treated as starting hypotheses, not constraints

### Validations scheduled
- [ ] H-06 (Hevy rate limits) validation is in progress or scheduled for this week
- [ ] A-01 (wearable HC data types) validation is in progress or scheduled for this week
- [ ] A-02 (Hevy muscle group data) validation is scheduled — a single API call
- [ ] Validation-dependent areas are explicitly marked in architecture work as "pending [H-06/A-01/A-02]"

### Blockers identified
- [ ] Any new ambiguity discovered during the session is logged (in ARCHITECTURE-ASSUMPTIONS.md §10 or equivalent)
- [ ] Any architectural concern that could change a locked decision is surfaced explicitly — not silently worked around
- [ ] If H-06 rate limit testing reveals < 5 req/min, sync architecture is flagged as blocked pending reassessment

### No product decisions silently changed
- [ ] No locked decision has been weakened, deferred, or reinterpreted without explicit discussion
- [ ] No hypothesis has been treated as a confirmed fact in the architecture design
- [ ] No preference has been locked without explicit confirmation
- [ ] No excluded feature has been reintroduced in any form
- [ ] The normalization note (implementation notes ≠ constraints) has been respected — architecture has not treated EMA smoothing, request/response patterns, batch job timing, or exponential backoff as locked requirements

### Design-now items addressed
- [ ] Nutrition schema design is scoped or in progress (D-15)
- [ ] Supplement schema design is scoped or in progress (D-15 extended)
- [ ] Conversation history schema design is scoped or in progress (P-07)
- [ ] These are design artifacts only — no CRUD endpoints, no data population, no feature implementation

---

*This checklist is consumed at the start of the first architecture session and reviewed at its end. It does not persist as an ongoing governance document — that role belongs to ARCHITECTURE-ASSUMPTIONS.md §11 (Change Control).*
