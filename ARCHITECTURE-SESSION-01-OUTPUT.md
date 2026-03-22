# Architecture Session 1 Output

**Date:** 2026-03-15
**Input:** ARCHITECTURE-ASSUMPTIONS.md, ARCHITECTURE-KICKOFF-CHECKLIST.md, DECISION-REGISTER.md, POST-GATE-REVIEW.md

---

## 1. Session Objective

Identify the architecture drivers, constraint zones, uncertainty zones, and decision areas for Apex v2 — then produce a concrete follow-up work list. This session is intentionally NOT finalizing schemas, APIs, component designs, deployment topology, or implementation plans. It is mapping the decision space so that subsequent sessions can make architecture decisions in the right order, with the right information, and without silently overriding the planning package.

---

## 2. Top Architecture Drivers

These are the planning-derived forces that most strongly shape what architecture must look like. They are not decisions themselves — they are the reasons architecture must have certain properties.

### Driver 1: Workout-first sequencing with future-pillar schema obligations

The system ships workout generation first, but must carry designed (empty) schemas for nutrition, supplements, and coaching conversations from day one. Architecture cannot optimize solely for the workout-first phase — it must make schema and migration decisions that accommodate four future pillars without rework.

- **Why it matters:** Designing only for the current phase risks migration conflicts later (the exact problem D-15 was created to prevent). But over-designing for all five pillars risks analysis paralysis and premature abstraction.
- **Source:** D-02, D-15, P-07

### Driver 2: Hevy API as both the highest-value integration and the highest-fragility dependency

Hevy is the source of truth for actual workout data (D-17), a conditional target for routine pushes if VD-1 validates write support (H-01), and the data source for workout generation (D-14). It is also undocumented, explicitly disclaimed by Hevy, rate-limited to an unknown degree (H-06), and could change without notice.

- **Why it matters:** Every core workout-phase feature depends on Hevy. The abstraction layer (D-10a) is locked, but the abstraction's behavior under constraint (caching depth, sync frequency, fallback scope) cannot be finalized until rate limits are known. This is the single largest source of architectural uncertainty.
- **Source:** D-10a, D-10b, D-14, D-17, H-06, A-02, A-03

### Driver 3: Readiness scoring is an unsolved research problem, not a display problem

The readiness pipeline depends on three unvalidated hypotheses (H-01, H-02, H-03), one unconfirmed hardware assumption (A-01), and one reliability hypothesis (H-04). Architecture must support iterating the algorithm without restructuring the system, and must ensure each component (readiness display, workout gen, Hevy sync) retains standalone value if any link fails.

- **Why it matters:** The temptation is to architect readiness as a solved data-flow problem. The planning package is explicit that it is not. Architecture must build for iteration, configurable weights, and graceful degradation — not for a fixed algorithm.
- **Source:** H-01, H-02, H-03, H-04, A-01, D-03, D-05 (clarified: transparency applies to all algorithmic recommendations)

### Driver 4: Hybrid compute split with offline expectations

Client owns TDEE and readiness (must work offline). Server owns workout generation, coaching, and sync (acceptable to be unavailable offline). This creates a data partitioning problem: the client needs enough cached data in Room to compute readiness and TDEE without the server, while the server needs enough Hevy and health history to generate workouts and coaching.

- **Why it matters:** This is not a simple "client calls server" architecture. Two independent compute contexts must stay consistent enough for their outputs to be coherent (e.g., client-side readiness informs server-side workout generation). The boundary must be clean enough that offline mode is a natural state, not an error-handling path.
- **Source:** D-03, D-06, D-14, P-03

### Driver 5: Provenance and data quality as first-class architectural concerns

Food entry provenance (D-13), USDA vs. OFF data quality distinction (D-04), prescribed vs. actual workout separation (D-17), and HC data staleness display (D-08) all require the architecture to track where data came from and how trustworthy it is. This is not metadata — it affects algorithm behavior (TDEE weighting, progression calculations, readiness staleness).

- **Why it matters:** If provenance is bolted on later, it requires touching every data path. If it's designed in from the start, it's a schema field and a data-flow convention. The planning package is unambiguous: provenance is day-one, not post-launch.
- **Source:** D-04, D-08, D-13, D-17

---

## 3. Hard Constraints Architecture Must Honor

### Product / scope constraints

| Constraint | What it forbids | Source |
|-----------|----------------|--------|
| Precision-capable nutrition model | Simplifying nutrition to awareness-only to reduce scope | D-01 |
| Workout-first execution sequence | Reprioritizing nutrition or coaching ahead of workouts | D-02 |
| All algorithmic training recommendations show reasoning | Opaque generation pipeline; any recommendation without user-visible rationale | D-05 (clarified) |
| AI safety rails ship with coaching, not after | Deferring safety constraints to "polish" | D-07 |
| No streak counters or gamification | Daily-obligation mechanics, engagement scores | D-09 |
| Semi-autonomous workout generation only | "Just start" flow without review step | D-11 |
| No social features | Multi-user data paths, leaderboards, feeds | D-12 |
| Hevy is source of truth for actual workout data | Progression based on prescribed data; ignoring user modifications in Hevy | D-17 |

### Compute placement constraints

| Constraint | What it forbids | Source |
|-----------|----------------|--------|
| TDEE computed client-side, static first | Server-dependent TDEE; shipping adaptive before static | D-03, D-06 |
| Readiness scoring computed client-side | Server-only readiness scoring | D-03 |
| Workout generation runs server-side for v1 | Client-side workout gen requiring full Hevy history locally | D-14 |
| Photo estimation client-direct-to-AI-API | Routing photo estimation through Apex server | D-03 |

### Provenance / data constraints

| Constraint | What it forbids | Source |
|-----------|----------------|--------|
| Food entry provenance enum from day one | Deferring provenance to a future version | D-13 |
| USDA primary, OFF secondary with quality flag | Merging both into an undifferentiated data source | D-04 |
| Nutrition + supplement + conversation schemas designed now | Skipping future-pillar schema design during workout-first phase | D-15, P-07 |
| Coaching context pipeline is modular | Hardwiring context assembly to workout-phase data types only | D-16 |
| Prescribed vs. actual workout data clearly separated | Conflating Apex-generated routines with Hevy-logged actuals | D-17 |

### External dependency constraints

| Constraint | What it forbids | Source |
|-----------|----------------|--------|
| All Hevy API calls through server abstraction layer | Scattering Hevy API calls across modules without adapter boundary | D-10a |
| HC sync with permission watchdog and staleness display | Assuming HC permissions are stable | D-08 |
| HC data source may need alternatives | Ruling out direct wearable API integrations at the interface level | H-04 |

### MVP exclusion constraints

| Constraint | What it forbids | Source |
|-----------|----------------|--------|
| No fully autonomous workout generation | Auto-start flows without user review | D-11 |
| No social features | Any multi-user abstractions | D-12 |
| No streak counters | Any daily-obligation mechanics | D-09 |
| Full mesocycle auto-generation is v2 | Shipping full auto-gen in MVP; MVP is volume tracking + weight suggestion + MRV flagging | P-05 |
| No LLM interaction checking | AI-validating-AI patterns | Exclusion list |

---

## 4. Validation-Dependent Design Areas

These are areas where architecture must stay flexible because upstream validation has not completed.

### Area 1: Hevy sync and caching strategy

- **Currently assumed:** Some level of programmatic Hevy access is feasible
- **Still unknown:** Exact rate limit threshold. Whether on-demand reads are possible or everything must be batched/cached
- **Validation:** H-06 — ramp requests until 429s. Thresholds: ≥ 30 req/min (on-demand OK), 5–30 (caching needed), < 5 (batch only), < 1 (reassess dependency)
- **Must remain provisional:** Sync frequency, caching depth, whether workout generation uses live or cached Hevy data, backoff strategy. Do NOT finalize sync architecture before H-06 results

### Area 2: Readiness algorithm input set

- **Currently assumed:** HRV is a useful input; wearable writes it to HC
- **Still unknown:** Whether Tyler's specific wearable writes HRV to HC at all (A-01). Whether HRV predicts strength readiness (H-02 — validated during MVP, not now)
- **Validation:** A-01 — identify wearable, check HC data types, confirm HRV availability. First week
- **Must remain provisional:** Which health metrics feed the readiness algorithm. If no HRV, readiness uses sleep + BP + subjective only. Readiness weights must be configurable, not hardcoded. H-02 may become moot before architecture is finalized

### ~~Area 3: Exercise-to-muscle-group data source~~ — RESOLVED

**A-02 validated 2026-03-15.** Hevy returns `primary_muscle_group` and `secondary_muscle_groups` on all ~431 exercises. Granularity is coarser than RP landmarks require (e.g., "shoulders" not "front/side/rear delts"). Resolution: Hevy data is the base layer; a system-scoped `exercise_muscle_overrides` table (~55 rows) provides RP-granularity refinement. See `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md`.

### Area 4: Hevy workout completion sync lag

- **Currently assumed:** Completed workout data is available promptly after user finishes logging in Hevy
- **Still unknown:** Actual lag between Hevy log completion and API data availability (OA-3)
- **Validation:** Test during H-06 ramp — opportunistic, first week
- **Must remain provisional:** Stale data policy for progression (D-17). Whether to show "data pending" states. Whether post-workout readiness updates are timely enough to be useful

### Area 5: HC reliability on Tyler's device

- **Currently assumed:** HC works; watchdog (D-08) is a safety net
- **Still unknown:** Whether permissions drop, data goes stale, or HRV data is incomplete on Tyler's device (H-04)
- **Validation:** 2-week HC sync monitoring with logging. Early MVP timing — does not block architecture start
- **Must remain provisional:** Whether HC is the only health data source or whether a direct wearable API bypass (WHOOP/Oura OAuth) is needed. Architecture should leave a clean seam for alternative data sources

---

## 5. Candidate Architecture Decision Areas

These are architecture-significant decisions that likely deserve formal ADRs (Architecture Decision Records). They are identified here, not finalized.

### ADR-1: Hevy abstraction layer design and sync strategy

- **Why it matters:** This is the single most consequential architecture decision for the workout-first phase. D-10a locks the abstraction principle. H-06 determines whether sync is on-demand, cached, or batch-only. D-17 requires actual workout data from Hevy. D-14 requires Hevy history for server-side workout gen. The abstraction layer is the seam where all Hevy-dependent features connect to the Hevy API — its design determines the caching model, fallback behavior (D-10b), and how gracefully the system handles rate limits, API changes, and outages.
- **Traces to:** D-10a, D-10b, D-14, D-17, H-06, A-02, A-03
- **Timing:** Partially now (interface shape), fully after H-06 validation (sync strategy)
- **What would make it premature:** Finalizing the sync strategy (caching depth, refresh frequency, batch vs. on-demand) before H-06 rate limit data arrives. The interface boundary can be decided now; the behavior behind it cannot.

### ADR-2: Server schema migration strategy for multi-pillar design-now-build-later

- **Why it matters:** D-15 requires designing nutrition, supplement, and conversation schemas now, creating tables empty, and populating them when each pillar ships. This is unusual — most schema work designs for what ships immediately. The migration strategy (how to structure migration files for empty tables, how to version schemas across pillars, how to handle future schema revisions when tables have been sitting empty) is architecture-significant because a mistake here causes the exact migration conflicts D-15 was designed to prevent.
- **Traces to:** D-15, P-07, D-13, D-04
- **Timing:** Now. No validation dependency blocks this decision. Schema design is the one architecture workstream that can proceed fully in parallel with H-06 testing.
- **What would make it premature:** Nothing — this is ready to decide. The only risk is over-designing schemas for pillars whose requirements are still hypothetical (e.g., supplement-outcome correlation graphs per H-08). Design for known requirements; leave extension points for hypothetical ones.

### ADR-3: Readiness scoring input architecture

- **Why it matters:** Readiness is computed client-side (D-03), uses multiple health inputs with configurable weights (H-02, H-03), must function without any single input (H-02 failure path), must handle stale data gracefully (D-08, P-01), and must be treated as an unsolved research problem that will iterate (Post-Gate §3.1). The input architecture — how health data feeds into the scoring function, how weights are stored and modified, how missing inputs are handled, how the algorithm is swapped or tuned without code changes — is architecture-significant.
- **Traces to:** D-03, D-05, D-08, H-01, H-02, H-03, H-04, A-01, P-01
- **Timing:** Interface design now. Weight configuration mechanism now. Specific algorithm deferred to MVP iteration.
- **What would make it premature:** Committing to a specific algorithm or specific weights before H-02 and H-03 validation data exists. Committing to HRV as a required input before A-01 confirms availability.

### ADR-4: Client-server data contract for workout generation

- **Why it matters:** D-14 locks server-side workout gen. D-05 (clarified) requires reasoning on all algorithmic outputs. D-17 requires using actuals. P-05 scopes the MVP to volume tracking, weight suggestions, and MRV flagging — not full routine generation. The contract between client and server must carry: readiness context (from client), Hevy workout history (already on server), exercise-to-muscle mapping (source TBD per A-02), and generated output with per-item reasoning (back to client). The shape of this contract determines how workout gen MVP features (P-05) connect to the semi-autonomous review flow (D-11).
- **Traces to:** D-05, D-11, D-14, D-17, P-05, A-02
- **Timing:** Soon — after A-02 confirms muscle group data availability. Contract shape can be roughed out now; data fields depend on A-02.
- **What would make it premature:** Finalizing the contract before knowing whether Hevy provides muscle group data (A-02) or before rate limits are known (H-06 affects whether the server has fresh or cached Hevy data at generation time).

### ADR-5: Health data source abstraction (HC vs. direct wearable APIs)

- **Why it matters:** HC is the current integration layer, but H-04 flags device-specific reliability concerns and the post-gate review identified the wearable as an unregistered dependency (A-01). If HC is unreliable, direct wearable APIs (WHOOP OAuth, Oura OAuth) may be needed. The question is whether to abstract health data behind a provider interface now (low cost, prevents lock-in) or wait for H-04 validation (avoids premature abstraction if HC is fine).
- **Traces to:** D-08, H-04, A-01
- **Timing:** Decide the principle now (abstract or not). The cost of a simple provider interface is low. The cost of retrofitting one after building directly against HC is moderate.
- **What would make it premature:** Designing specific WHOOP or Oura OAuth integrations before H-04 validates whether HC is reliable. The ADR should decide the interface boundary, not the alternative implementations.

---

## 6. Decisions Architecture is NOT Allowed to Make Yet

| Decision | Why premature | Blocked by |
|----------|--------------|-----------|
| Hevy sync frequency and caching depth | Rate limits unknown | H-06 — must test before finalizing |
| Whether readiness algorithm includes HRV | Wearable data availability unconfirmed | A-01 — must identify device and check HC data types |
| ~~Whether Apex needs a curated exercise-to-muscle-group mapping table~~ | ~~Hevy API response fields unknown~~ | **RESOLVED.** A-02 validated. Apex needs a system-scoped refinement table (~55 rows), not a full mapping table. See `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md`. |
| Specific readiness algorithm weights | No personal correlation data exists yet | H-02, H-03 — validated during MVP, not architecture |
| Adaptive TDEE algorithm design | Not needed for v1; no logging data to calibrate against | H-05 — post-MVP |
| Hevy API fallback display scope (full UI vs. text export vs. nothing) | Cost/benefit depends on rate limit findings and API stability assessment | H-06 results + architecture cost estimation |
| AI coaching conversation cache size (how many messages client-side) | No usage data; implementation detail | P-07 — decide during coaching implementation |
| Supplement-outcome correlation compute placement | 5th in sequence; data volume and complexity unknown | H-08 — post-MVP |
| Whether to build direct WHOOP/Oura integrations | HC reliability unknown on Tyler's device | H-04 — validated during early MVP |
| Morning briefing pre-gen vs. on-demand | Freshness tradeoff can't be assessed without usage | P-03 — decide during coaching implementation |

---

## 7. Recommended Immediate Follow-Up Work

### Validation / spike tasks

| # | Task | Why it exists | Dependency / trigger | Output expected |
|---|------|--------------|---------------------|-----------------|
| V-1 | **Hevy API rate limit test** | H-06: unknown rate limits block sync architecture | None — can start immediately | Document: requests/minute threshold, 429 response behavior, whether limits are per-endpoint or global. Map result to H-06 tiered thresholds (≥30, 5–30, <5, <1) |
| V-2 | **Identify wearable device and confirm HC data types** | A-01: readiness pipeline input set is unconfirmed | None — requires Tyler's device information | Document: device model, which HC data types it writes (HRV, sleep stages, resting HR, SpO2), HRV recording conditions (continuous vs. sleep-only), data freshness observed |
| ~~V-3~~ | ~~**Inspect Hevy exercise template API response**~~ | ~~A-02~~ | ~~Hevy Pro API key~~ | **DONE 2026-03-15.** API returns `primary_muscle_group` + `secondary_muscle_groups` on all 431 exercises. Granularity insufficient for RP landmarks → system-scoped override table scoped in `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md`. |
| V-4 | **Test Hevy workout completion sync lag** | OA-3: progression data freshness (D-17) depends on how quickly Hevy makes completed workout data available via API | Run during V-1 rate limit testing | Document: time between workout completion in Hevy and data appearing in API response |

### Architecture clarification tasks

| # | Task | Why it exists | Dependency / trigger | Output expected |
|---|------|--------------|---------------------|-----------------|
| ~~C-1~~ | ~~**Inventory existing server schema and migration approach**~~ | ~~D-15~~ | ~~None~~ | **DONE 2026-03-15.** PostgreSQL 16+, raw SQL migrations (7 files), no runner, no tracking table. 11 tables, consistent naming. See `SERVER-SCHEMA-INVENTORY.md`. |
| C-2 | **Map existing Apex v1 client data flows** | D-03 hybrid compute split and D-06 client-side TDEE require understanding what data Room already caches, what SharedPreferences stores, and what the current HC → Room → server sync pipeline looks like | None — read existing codebase | Document: current Room entities, SharedPreferences keys, WorkManager jobs, data freshness model |
| C-3 | **Catalog RP volume landmark reference data** | OA-2: workout gen MVP (P-05) uses RP's MEV/MAV/MRV per muscle group. Need to confirm these are publicly available and in a machine-readable format | None — web research | Document: source URL(s), data format, number of muscle groups covered, any licensing concerns |

### ADR drafting tasks

| # | Task | Why it exists | Dependency / trigger | Output expected |
|---|------|--------------|---------------------|-----------------|
| ~~A-1~~ | ~~**Draft ADR: Server schema migration strategy**~~ | ~~ADR-2~~ | ~~After C-1~~ | **DONE 2026-03-15.** See `ADR-002-server-schema-migration-strategy.md` — accepted for MVP. |
| A-2 | **Draft ADR: Readiness scoring input architecture** | ADR-3 from Section 5. Interface design can proceed; algorithm specifics deferred | After V-2 completes (need to know available inputs) | ADR document: input provider interface, weight configuration mechanism, missing-input handling, algorithm swap strategy |
| A-3 | **Draft ADR: Health data source abstraction** | ADR-5 from Section 5. Low-cost decision with moderate retrofit cost if deferred | After V-2 completes (wearable identification informs whether abstraction is likely needed) | ADR document: provider interface yes/no, interface shape if yes, HC as default implementation |
| A-4 | **Draft ADR: Hevy abstraction layer design** | ADR-1 from Section 5. Interface shape can start now; sync strategy waits for V-1 | Interface: now. Sync strategy: after V-1 completes | ADR document (two-phase): (1) adapter interface boundary, (2) sync/caching strategy based on rate limit findings |
| ~~A-5~~ | ~~**Draft ADR: Client-server workout generation contract**~~ | ~~ADR-4~~ | ~~After V-3~~ | **DONE 2026-03-16.** See `ADR-004-workout-generation-reconciliation-model.md` — accepted for MVP. Scope broadened to cover reconciliation model (prescribed vs. actual). |

---

## 8. Session Exit Check

### Clear constraints inventory
**Achieved.** All 17 locked decisions cataloged with what-they-forbid statements (Section 3). Grouped by type (product, compute, data/provenance, integration, exclusion). Implementation notes from D-06/D-14/P-03/D-10a explicitly flagged as non-binding starting hypotheses, not constraints.

### Clear uncertainty inventory
**Achieved.** Five validation-dependent areas identified with specific unknowns, validation items, and thresholds (Section 4). Ten premature decisions explicitly listed with their blockers (Section 6).

### ADR candidate list
**Achieved.** Five ADR candidates identified with planning traceability, timing assessment, and prematurity conditions (Section 5). No ADR attempts to finalize a design — all are scoped to the decision, not the implementation.

### GSD-ready follow-up list
**Achieved.** Twelve tasks across three categories (4 validation, 3 clarification, 5 ADR drafting) with explicit dependencies, triggers, and expected outputs (Section 7). All are concrete enough to schedule.

### Weaknesses to acknowledge
- **V-1 (rate limit test) is still the critical path.** ADR-1 (Hevy abstraction layer) cannot be completed without it. Sync architecture is blocked until rate limits are known.
- ~~C-1 (existing server schema inventory) was not performed during this session.~~ **RESOLVED.** See `SERVER-SCHEMA-INVENTORY.md`.
- **OA-2 (RP volume landmark data availability) is unchecked.** If RP landmarks are not available in a usable format, the workout gen MVP (P-05) may need to define its own volume targets.

### Post-session progress (2026-03-15 — 2026-03-16)
- **C-1 completed.** Server schema inventoried: PostgreSQL 16+, 7 raw SQL migrations, 11 tables, no migration runner.
- **V-3 / A-02 completed.** Hevy exercise templates validated: muscle group data exists but is coarse. System-scoped refinement layer scoped (~55 overrides).
- **ADR-002 accepted.** Migration strategy: sequential idempotent SQL, `schema_migrations` tracking table, pillar-grouped migrations, schema/data separation.
- **ADR-004 accepted.** Reconciliation model: actuals-first progression, conditional prescribed-to-actual linkage via Hevy `routine_id`, no FK between `generated_routines` and `workout_sessions`.
- **VD-1 identified.** Hevy routine creation support is unvalidated. VD-1 determines whether Hevy push / routine-based linkage is operationally usable, but does not block migration 009 schema finalization — `hevy_routine_id` fields are safe to create now as nullable columns.

---

*This document is the output of Architecture Session 1. It maps the decision space. It does not make architecture decisions. Subsequent sessions produce ADRs, schemas, and designs within the boundaries established here.*
