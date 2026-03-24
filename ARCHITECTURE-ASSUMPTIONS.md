# Architecture Assumptions: Planning-to-Architecture Handoff

**Date:** 2026-03-15
**Input:** Decision Register (post-gate normalization), Post-Gate Review, Gate Review
**Purpose:** Define what architecture may assume, what remains uncertain, and what must be validated early. This is NOT architecture — no schemas, APIs, or component designs.

---

## 1. Purpose and Scope

This document bridges the completed planning phase and the beginning of architecture. It establishes:

- Which planning decisions are firm constraints on architecture (Section 2)
- Which hypotheses architecture must design around without assuming their truth (Section 3)
- Which deferred capabilities require present-day design consideration (Section 4)
- Which external dependencies carry fragility risk (Section 5)
- Where computation runs for each feature area (Section 6)
- What data model principles are locked (Section 7)
- What is explicitly out of scope for MVP (Section 8)
- What must be validated in the first week of architecture (Section 9)

Architecture may freely choose implementation patterns, algorithms, schemas, and component structures. It may NOT contradict the locked decisions or silently convert hypotheses into assumptions.

---

## 2. Locked Planning Decisions That Constrain Architecture

These 17 decisions are firm. Architecture must satisfy them.

| ID | Decision | Why Locked | Architectural Implication | Source |
|----|----------|-----------|--------------------------|--------|
| D-01 | Precision-capable nutrition model | Explicit user choice + market evidence (MacroFactor, Cronometer) | Schema must distinguish estimated vs. verified entries; quick-add must not break precision model | Decision Register |
| D-02 | Workout-first execution sequence | Explicit user choice + scope analysis | Workout generation, Hevy integration, and readiness are the first features to architect; nutrition schema designed but not populated | Decision Register |
| D-03 | Hybrid compute split (client + server) | Explicit user choice — core trust features should not depend on constant server execution | Client handles TDEE and readiness; server handles sync, history, AI, workout gen; photo goes client-direct-to-AI-API | Decision Register |
| D-04 | Food database quality protection (USDA + OFF) | Overwhelming evidence (MFP decay, MacroFactor complaints, published research) | Must integrate two external food APIs; no unverified user submissions overwrite validated data | Decision Register |
| D-05 | Workout generation shows reasoning | Strong evidence (Fitbod distrust, RP opacity complaints) | Workout generation output must include per-exercise rationale; user override on every element | Decision Register |
| D-06 | TDEE client-first, static then adaptive | Hybrid compute principle + gate review downgrade of adaptive to enhancement | Client-side TDEE computation; Room must cache weight history; server receives outputs only | Decision Register |
| D-07 | AI safety rails are non-negotiable | Documented failure modes (Oura hallucination, ASHP drug advice study, Peloton/iFit constraint ignoring) | System prompt constraints, structured constraint injection, output validation pipeline required | Decision Register |
| D-08 | HC sync with active monitoring | Device-specific evidence of permission drops, sync delays, 3P write blocking | Permission watchdog in sync cycle; staleness display in UI; graceful degradation to last-known data | Decision Register |
| D-09 | No streak counters or aggressive gamification | Behavioral research + user harm evidence (Duolingo, Apple Watch) | Trend visualization instead; no daily-obligation mechanics | Decision Register |
| D-10a | Hevy API abstraction interface | Hevy's explicit "may change or abandon" disclaimer; asymmetric payoff | All Hevy calls through server abstraction layer; business logic must not reference Hevy API directly | Decision Register |
| D-11 | No fully autonomous workout generation | Fitbod distrust evidence; autonomous requires being right every time | Semi-autonomous: generate → review → execute. Push to Hevy is one conditional execution path (requires VD-1); manual/in-app execution is a valid MVP path | Decision Register |
| D-12 | No social features | Single-user app; no critical mass; health data privacy | No leaderboards, feeds, sharing, or multi-user data paths | Decision Register |
| D-13 | Food entry provenance field from day one | Required by precision nutrition (D-01); enables future TDEE weighting by data quality | Every food entry carries a source enum (verified/estimated/corrected/custom/quick_add) | Decision Register |
| D-14 | Server-side workout generation for v1 | Hevy data already server-side; simpler than moving history client-side for an infrequent action | Workout generation runs on server; client provides readiness context, server returns routine with reasoning | Decision Register |
| D-15 | Nutrition + supplement schema designed now, built later | Prevents migration conflicts when later pillars ship | Schema for nutrition AND supplement tables defined during architecture; tables created empty; populated when respective pillar ships | Decision Register + Post-Gate §3 |
| D-16 | AI coaching limited context in workout phase | Direct consequence of workout-first (D-02) | Context assembly pipeline must be modular — nutrition data source plugs in later without restructuring | Decision Register |
| D-17 | Hevy is source of truth for actual workout data | Progression must track reality, not prescriptions | Progression algorithm uses completed Hevy data, not Apex-generated prescriptions; user-initiated Hevy workouts enter model normally | Decision Register (promoted from P-06) |

**Normalization note:** D-06, D-14, P-03, and D-10a contain "Implementation note" fields that describe expected implementation approaches (EMA smoothing, request/response pattern, batch job timing, exponential backoff). These are starting hypotheses, not constraints. Architecture may choose different patterns if better alternatives emerge. The locked decisions are the principles above each implementation note.

---

## 3. Active Hypotheses Architecture Must Not Assume Away

Architecture must design for testability of these hypotheses. It must not assume they are true, and must not require them to be true for the system to function.

| ID | Hypothesis | Working Assumption | Validation Method | Threshold | Timing | If False, What Changes |
|----|-----------|-------------------|------------------|-----------|--------|----------------------|
| H-01 | Readiness-to-Hevy loop is the primary differentiator | Highest-potential differentiator, contingent on 3 links | End-to-end test of HC → readiness → workout → Hevy push | Technical: HC < 12h old on > 80% training mornings; Hevy push succeeds > 90%. Value: readiness-adjusted workouts match energy state > 70% over 4 weeks | During MVP | Downgrade from primary differentiator to optional enhancement. Each component (readiness display, workout gen, Hevy sync) retains standalone value |
| H-02 | HRV predicts strength training readiness | HRV is a useful input; weight is configurable | 4–6 weeks HRV + RPE tracking, correlation analysis | Success: r > 0.25 negative correlation (HRV deviation vs. RPE) over 20+ points, OR low-HRV days → higher RPE > 60%. Partial: r = 0.1–0.25 → low weight. Failure: no/positive correlation | During MVP | Set HRV weight to zero in readiness algorithm; rely on sleep + subjective only. Architecture must make readiness weights configurable, not hardcoded |
| H-03 | Readiness algorithm produces meaningful composite scores | Simple heuristic (equal-weight normalized average + subjective) is a starting point | 4–6 weeks of score-vs-actual-performance tracking | Success: score direction matches subjective "good/bad day" > 70% over 4 weeks. Failure: < 50% agreement (worse than random) | During MVP | Algorithm needs fundamental redesign or readiness feature deprioritized. Architecture must treat readiness as an unsolved research problem, not a display problem |
| H-04 | Health Connect delivers reliable data on Tyler's device | HC is functional but fragility is device-dependent | 2-week HC sync monitoring with logging | Permission persistence, data freshness < 24h, completeness of HRV/sleep/HR data | Early MVP | Watchdog (D-08) becomes critical infrastructure. May need direct wearable API integration (WHOOP/Oura OAuth) as HC bypass. Architecture should not rule out direct integrations |
| H-05 | Adaptive TDEE adds value over static estimates at n=1 | Static Mifflin-St Jeor ships first; adaptive is Phase 2 | Run static 4 weeks, then activate adaptive, compare | Adaptive estimate closer to actual experience than static | Post-MVP (after 4+ weeks logging) | Fall back to static permanently. Zero wasted architecture since static is already v1 default |
| H-06 | Hevy API rate limits are workable | Limits exist; threshold unknown | Ramp requests from 1/10s upward until 429s | Fully workable: ≥ 30 req/min. Caching needed: 5–30 req/min. Severely constrained: < 5 req/min. Blocking: < 1 req/min | Before sync design finalizes | Determines caching strategy and sync architecture. < 5 req/min → batch-only sync (1–2x daily). < 1 req/min → reassess Hevy dependency entirely |
| H-07 | Proactive AI coaching sustains engagement beyond 3 months | Directionally promising; no product has proven sustained engagement beyond 12 months | Monthly engagement tracking: interactions initiated by coach, read rate, recommendations acted on | Assess at 3-month mark; no hard threshold pre-defined | Post-MVP | Coaching becomes an occasional feature, not daily. Infrastructure (Claude API, context assembly) is shared with other features — sunk cost is low |
| H-08 | Supplement-outcome correlation is compelling | Demand inferred from gap, not measured; n=1 stats unreliable | Build simplest version (overlay graph with "correlation ≠ causation" label); measure usage | Tyler uses it more than once | Post-MVP (5th in sequence) | An unused graph in the supplement section. Minimal wasted effort if built simply |

---

## 4. Deferred Capabilities with Present-Day Implications

### Design-now, implement-later (active schema work during architecture)

| Capability | Why Deferred | Present-Day Implication | Safe to Ignore? | Source |
|-----------|-------------|------------------------|-----------------|--------|
| Nutrition CRUD + logging | Workout-first sequencing (D-02) | Schema for foods, food_entries, nutrition_targets must be designed during architecture to prevent migration conflicts | No — schema design is required now | D-15 |
| Supplement tracking | 5th in sequence; weak demand signal | Schema for supplements, supplement_entries, supplement_stack, supplement_interactions must be designed alongside nutrition schema | No — same migration-conflict risk as nutrition | D-15 + Post-Gate §3 |
| AI coaching conversation storage | Coaching ships during workout phase but conversation persistence was unaddressed until post-gate review | Conversation table schema must be designed alongside workout/coaching tables | No — server-side storage is the natural location (P-07) | P-07 + Post-Gate §3 |

### Fully deferred (no present-day schema work)

| Capability | Why Deferred | Present-Day Implication | Safe to Ignore? | Source |
|-----------|-------------|------------------------|-----------------|--------|
| Adaptive TDEE algorithm | Needs 4+ weeks of data; static ships first | D-06 client-side TDEE architecture should accommodate a future algorithm swap (static → adaptive) without restructuring | Mostly — just use a clean interface for the TDEE calculation | H-05 |
| Photo food estimation | 4th in sequence; requires manual logging UX first | None during workout-first phase | Yes — fully additive later | P-04 |
| Meal planning / recipe generation | No evidence of demand; let it emerge from coaching usage | None | Yes | Deferred list |
| WHOOP direct OAuth | Only needed if HC is unreliable for WHOOP data | None until H-04 validates HC reliability | Yes — contingent on H-04 | Deferred list |
| Garmin Health API | Enterprise-gated; basic metrics via HC | None | Yes | Deferred list |
| Watch companion app (Wear OS) | Apex is sync + intelligence, not a logging app; Hevy has Wear OS app | None | Yes | Deferred list |
| AI coaching long-term engagement optimization | Can only measure with real usage over 3+ months | Build engagement tracking from day 1 (H-07) | No — tracking infrastructure must exist at coaching launch | H-07 |
| Supplement-outcome correlation graphs | Weak demand; n=1 stats unreliable | None during workout-first phase | Yes | H-08 |

---

## 5. External Dependency Assumptions

| Dependency | Capability | Evidence | Fragility | Fallback | Must Validate? |
|-----------|-----------|----------|-----------|----------|---------------|
| Tyler's wearable writes HRV, sleep, resting HR to HC (A-01) | Readiness pipeline (H-01, H-02, H-03, H-04) | Apex v1 syncs HC data (BP, sleep, HRV, weight), implying wearable exists. Specific device not confirmed. Galaxy Watch writes HRV during sleep only; WHOOP HC unreliable; Oura writes to HC on Android | Medium — device-dependent data availability | Readiness uses sleep + BP + subjective only (no HRV). Less differentiated but functional | Yes — first week of architecture |
| ~~Hevy exercise templates include muscle group data (A-02)~~ **VALIDATED 2026-03-15** | Workout generation (D-05, P-05), volume landmark tracking | **Confirmed.** API returns `primary_muscle_group` and `secondary_muscle_groups` (array) on all ~440 exercises. Compounds have populated secondaries. Granularity is coarse (no delt subdivisions, no trap/bicep head distinctions). `full_body`/`cardio` are useless catch-alls. | Low for data existence; **Medium for granularity sufficiency** — Hevy taxonomy is coarser than RP volume landmarks require | Apex needs a first-party refinement/override layer for exercises where Hevy's coarse groups don't map cleanly to RP landmarks. Not a full mapping table — a targeted override set (~30–50 exercises needing subdivision) | ~~Yes~~ **Done.** New follow-up: granularity refinement layer scoping |
| Tyler maintains Hevy Pro subscription (A-03) | All Hevy API features (sync, push, templates) | Apex v1 uses Hevy API, implying Pro is active. ~$60/yr | Low — operational, not technical | Subscription renewal. No code mitigation needed | No — known operational fact |
| Server remains Node.js/Express (A-04) | All server-side features (workout gen, AI coaching, nutrition CRUD) | Current codebase: Express routes in `src/index.js`, Docker on DigitalOcean | Low — established, working | If inadequate for specific feature (SSE, long jobs), migration proposal must be explicit | No — constraint, not hypothesis |
| Claude API remains stable and available (A-05) | AI coaching (D-07, D-16) | Well-funded company, commercial API SLAs, stable through v1 | Low — commercial API from major provider | Switch to alternative LLM (Gemini, GPT). Design coaching with provider-agnostic interface | No — monitor announcements |
| USDA FoodData Central API (via D-04) | Food database for nutrition logging | Federal government API, free, well-documented | Low — government infrastructure, stable | Open Food Facts as partial backup; custom foods | No — established infrastructure |
| Open Food Facts API (via D-04) | Barcode lookup, international foods | Community-maintained, open API | Medium — community data quality varies | USDA as primary; OFF as supplementary with community-source flag | No — secondary source |
| Hevy API rate limits (via H-06) | Sync frequency for workout gen + history retrieval | 429 errors confirmed; exact limits unknown | High — undocumented, could change without notice | Tiered response: caching, batching, or full reassessment depending on severity | Yes — before sync design finalizes |

---

## 6. Compute Placement Assumptions

| Feature | Assumption | Why | Offline Expectation | Uncertain? |
|---------|-----------|-----|-------------------|-----------|
| TDEE calculation | Client-side (D-06) | Core trust feature; should not depend on server availability | Fully offline after initial data sync | No — locked |
| Readiness scoring | Client-side (D-03) | Personal, deterministic, low-cost calculation | Fully offline with cached HC data (subject to staleness policy P-01) | No — locked as principle |
| Workout generation | Server-side (D-14) | Hevy history already server-side; infrequent action (1–2x/day) | Unavailable offline. Acceptable for an infrequent on-demand action | No — locked for v1 |
| AI coaching (real-time chat) | Server-side (D-03, D-16) | Server has all health data; Claude API call required | Unavailable offline. Client caches recent messages for display (P-07) | No — locked |
| Morning briefing | Pre-generated preference (P-03) | Reduces synchronous dependency at app-open time | Available offline if pre-generated during last sync | Yes — P-03 is a preference; architecture may choose on-demand if freshness tradeoff is unacceptable |
| Photo food estimation | Client-direct-to-AI-API (D-03) | Direct to Gemini API; server not involved | Unavailable offline (requires AI API call) | No — locked |
| Supplement correlation | Unspecified | Deferred to architecture; depends on data volume and complexity | TBD during architecture | Yes — not yet decided |

---

## 7. Data Model and Provenance Assumptions

Architecture may freely define schemas, but must satisfy these planning-level principles:

1. **Provenance field on food entries (D-13):** Every food entry carries a `source` enum distinguishing verified, estimated, corrected, custom, and quick_add origins. This is a day-one schema requirement, not a future enhancement.

2. **Dual food database integration (D-04):** USDA as primary (validated), Open Food Facts as secondary (community-sourced, flagged). Architecture must distinguish data quality at the source level.

3. **Schema pre-design for future pillars (D-15):** Nutrition tables, supplement tables, and conversation history table are designed during the workout-first architecture phase. Tables created empty on the server. Populated when each pillar ships. This prevents migration conflicts.

4. **Actuals vs. prescribed workout data (D-17, ADR-004):** Progression uses `workout_sessions` + `workout_sets` exclusively and never queries `generated_routines`. Unmatched workouts (ad-hoc, pre-Apex, or from non-Apex routines) are valid progression inputs with identical treatment to matched workouts. The prescribed-to-actual link exists for D-05 explainability only, not correctness, and is conditional on Hevy routine creation support (ADR-004 VD-1). Execution modifications are expected behavior, not errors.

5. **Modular context assembly (D-16):** AI coaching context pipeline must be designed so that nutrition data, supplement data, and future data sources can plug in without restructuring. The workout-first phase ships with health + training context only.

---

## 8. Non-Goals and Explicit MVP Exclusions

| Item | Status | Rationale | Revisitable? |
|------|--------|-----------|-------------|
| Streak counters / gamification | Permanently excluded (D-09) | Behavioral research shows harm; trend visualization is strictly better for motivated single user | Only if Tyler specifically requests it; trivial to add |
| Fully autonomous workout generation | Permanently excluded (D-11) | Requires being right every time; Fitbod distrust evidence; semi-autonomous is strictly better | "Trust mode" toggle could be added later if semi-autonomous reviews feel redundant |
| Social features | Permanently excluded (D-12) | Single-user app; no critical mass; health data privacy | No realistic path to revisiting |
| Full mesocycle auto-generation | MVP exclusion (P-05) | Non-trivial algorithm; MVP proves concept with volume tracking + weight suggestion + MRV flagging | v2 after MVP workout gen validates the approach |
| Meal planning / recipe generation | Deferred | No evidence of demand | Only if coaching usage data shows demand |
| Watch companion app (Wear OS) | Deferred | Apex is sync + intelligence, not logging; Hevy has Wear OS | Only if in-app workout logging replaces Hevy |
| WHOOP/Garmin direct API | Deferred | HC is the integration layer; direct APIs only if HC fails | After H-04 validates HC reliability |
| LLM interaction checking (AI validating AI) | Permanently excluded | Over-engineering; safety rails via system prompt constraints | No |
| Photo estimation as primary logging path | Excluded by D-01 | Precision nutrition decision makes manual logging primary; photo is secondary convenience | If Tyler discovers precision logging is impractical, D-01 allows quick-add fallback without schema changes |

---

## 9. First-Week Validation Checklist

These items must be validated during the first week of architecture work. They shape specific design decisions and should run in parallel with schema design.

| Item | Owner | Method | Threshold | Timing | Impact if Failed |
|------|-------|--------|-----------|--------|-----------------|
| Hevy API rate limits (H-06) | Tyler | Ramp requests from 1/10s upward; document 429 threshold | ≥ 30 req/min = on-demand OK. 5–30 = caching needed. < 5 = batch only. < 1 = reassess Hevy dependency | Before sync design finalizes (first week) | Determines entire sync architecture: on-demand vs. cached vs. batch |
| Wearable device + HC data types (A-01) | Tyler | Identify specific wearable; check which HC data types it writes (especially HRV) | HRV data is present and current in HC | First week | If no HRV: readiness uses sleep + BP + subjective only; HRV-related hypotheses (H-02) become moot |
| ~~Hevy exercise template muscle groups (A-02)~~ | ~~Tyler~~ | ~~Call `GET /v1/exercise_templates`; inspect response~~ | ~~`primary_muscle_group` exists~~ | ~~First week~~ | **VALIDATED 2026-03-15.** Data exists. New question: Hevy's coarse taxonomy (e.g., "shoulders" not "side delts") is insufficient for RP landmark granularity. Apex needs a refinement/override layer for ~30–50 exercises. |
| Hevy routine creation support (ADR-004 VD-1) | Tyler | `POST /v1/routines` test → start routine in Hevy → complete workout → confirm `routine_id` round-trip | Created routine ID appears on completed workout | Does not gate migration 009. Can run in parallel with H-06 | If unavailable: push path disabled; prescribed-to-actual linkage unavailable at MVP; `hevy_routine_id` on `generated_routines` permanently NULL; manual/in-app execution remains valid; D-10b fallback scope activated |
| Register hidden assumptions (Post-Gate §8.1) | Tyler | Add to decision register: wearable device, muscle group data, Hevy Pro, server framework, Claude API stability | All 5 items registered with metadata | Before first architecture session (< 30 min) | Already done — A-01 through A-05 are in the register |
| Conversation storage preference (Post-Gate §8.8) | Tyler | Confirm server-side history + client cache for coaching conversations | Confirmed or adjusted | First week | Already addressed — P-07 added to register |

---

## 10. Open Assumptions Log

Items that surfaced during planning but are not yet classified as decisions, hypotheses, or validated assumptions. Architecture should note these and resolve them as design progresses.

| # | Assumption | Status | Needed By |
|---|-----------|--------|-----------|
| OA-1 | The readiness algorithm can be meaningfully calibrated with simple heuristics before sports-science-grade modeling | Working assumption — start simple, iterate | Readiness feature design |
| OA-2 | RP volume landmarks (MEV/MAV/MRV) are implementable from published reference data without licensing | Believed true — RP publishes guidelines publicly | Workout generation design |
| OA-3 | Hevy API workout completion data is available promptly after the user finishes logging (sync lag < 1 hour) | Unknown — affects progression data freshness (D-17) | Sync architecture |
| OA-4 | Claude API cost at single-user coaching volume (estimated 5–20 coaching interactions/day) is sustainable | Believed true — single-user volume is negligible at current pricing | Coaching feature scoping |
| OA-5 | CameraX + ML Kit barcode scanning (already in Apex v1 for QR onboarding) can be reused for food barcode scanning | Believed true — same camera pipeline, different barcode format | Nutrition feature design |
| OA-6 | Hevy's coarse muscle group taxonomy can be refined with a small first-party override layer (~30–50 exercises) rather than a full canonical mapping table (~440 exercises) | Believed true — most exercises map 1:1; only shoulder/back/arm subdivisions need refinement for RP landmarks | Workout generation data model (migration 009) |

---

## 11. Change Control

This document reflects the planning state as of 2026-03-15, updated 2026-03-16 with architecture session results. During architecture:

- **Locked decisions (Section 2)** may only be changed by explicit user decision with documented rationale. Architecture may propose changes but not unilaterally adopt them.
- **Hypotheses (Section 3)** may be updated as validation data arrives. Update this document when a hypothesis is confirmed, partially confirmed, or falsified.
- **Assumptions (Section 5)** should be struck through and annotated when validated or invalidated.
- **Open assumptions (Section 10)** should be resolved and moved to the appropriate section as architecture progresses.
- **Implementation notes** in the decision register (D-06, D-14, P-03, D-10a) are starting hypotheses. Architecture may choose different patterns without changing this document — those are architecture decisions, not planning amendments.

---

## Normalization Notes

No unresolved inconsistencies remain between the Decision Register and the Post-Gate Review. All post-gate recommendations were applied during normalization:

- P-06 promoted to D-17 (actuals vs. prescribed is a decision, not a preference)
- D-10 split into D-10a (locked: abstraction interface) and D-10b (preference: fallback scope)
- D-15 extended to include supplement schema
- H-01, H-02, H-03, H-06 received explicit success/failure thresholds
- A-01 through A-05 registered as formal assumptions
- P-07 added for conversation storage
- Implementation notes on D-06, D-14, P-03, D-10a marked as "subject to architecture review"

---

*This document is the planning-to-architecture handoff. It defines the boundaries within which architecture operates. It does not contain architecture.*
