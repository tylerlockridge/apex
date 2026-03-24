# SESSION HANDOFF

**Generated:** 2026-03-18
**Source:** Workspace scan of `C:\Users\tyler\Documents\Claude Projects\Apex`

---

## 1. Project Overview

Apex is a Kotlin/Compose Android app that syncs health data (blood pressure, sleep, body composition, HRV, workouts) from Android Health Connect to a self-hosted Node.js/Express server at `tyler-health.duckdns.org`. The v1 app is shipped, stable, and in daily use (last commit `728b228`, CI passing).

The project is now in a **v2 architecture planning and research phase** — designing workout generation, nutrition tracking, food photo estimation, AI coaching, and supplement tracking features. No v2 implementation code has been written. The planning phase uses a formal multi-tool coordination protocol (Claude for research/synthesis, Codex/GPT-5.4 for audit, human as controller-of-record).

**Lifecycle phase:** v2 Architecture — Planning & Research (active since 2026-03-15).

---

## 2. Current Objective

Complete the Codex audit of R-1 (RP volume landmarks) and R-5 (v1 client data flows), then advance the remaining three manual validations (VD-1, H-06, A-01) that block the remaining ADR drafts (ADR-001, ADR-003, ADR-005).

---

## 3. Current State Summary

- Two research deliverables (R-1 and R-5) are `complete` and awaiting Codex audit. Both contain proposed audit prompts.
- Nine architecture decisions are locked. Two ADRs are accepted (ADR-002: migration strategy, ADR-004: reconciliation model). Three ADRs cannot be drafted until manual validations complete (ADR-001 needs H-06, ADR-003 needs A-01, ADR-005 needs A-01).
- Three manual validation tasks (VD-1, H-06, A-01) are `queued` and require the human operator (hardware/credentials). These are the primary blockers.
- The coordination protocol is functioning correctly. COORDINATION-PROTOCOL.md, SESSION-LOG.md, and PROJECT.md are mutually consistent with one minor drift noted below.
- No implementation work is in scope until the research queue is empty and all ADRs are locked.

---

## 4. Completed / Established

- **v1 app shipped and stable.** 85 tests pass, CI builds debug+release APKs, release signing configured, API key baked in.
- **v2 planning phase completed.** DECISION-REGISTER.md contains 17 locked planning decisions (D-01 through D-17), 8 hypotheses with explicit validation thresholds, and 5 registered assumptions.
- **Architecture session 1 completed** (2026-03-15). Identified 5 top drivers, 8 constraints, 5 ADR candidates, and 5 validation-dependent areas.
- **ADR-002 accepted:** Sequential idempotent SQL migrations, pillar-grouped, schema/data separation.
- **ADR-004 accepted:** Actuals-first progression; prescribed-to-actual linkage for explainability only.
- **A-02 validated** (2026-03-15): Hevy exercise templates include muscle groups, but granularity is coarse (20 Hevy groups vs. 16 RP groups). ~55 system-scoped overrides needed.
- **R-1 complete:** 13/16 RP muscle groups have public volume landmark tables. 3 (Lats, Upper Back, Lower Back) combined under "Back." RP ToS grants Creative Commons license with attribution request. No locked decision affected.
- **R-5 complete:** Full client data flow map — Room queue, SharedPrefs, encrypted prefs, WorkManager jobs, 6 end-to-end flows, freshness rules. Key finding: durable outbound sync exists but inbound reads are non-durable/server-live.
- **COORDINATION-PROTOCOL.md finalized** per Codex audit (2026-03-16): human controller-of-record, no self-audit, single Active Handoff, normalized statuses.
- **SESSION-LOG.md created** as durable session ledger.
- **Server schema inventoried:** PostgreSQL 16+, 7 raw SQL migrations, 11 tables.
- **Exercise attribution scope defined:** `exercise_muscle_overrides` table, system-scoped, no `user_id`, ~55 rows.
- **5 research briefs completed** (pre-architecture): workout-builder, nutrition, food-photo, ai-coach, supplements.
- **3 LLM audits completed** on v1 codebase (2026-02-28, 2026-03-03, 2026-03-14) with all findings fixed.

---

## 5. In Progress

- **R-1 + R-5 Codex audit:** Active Handoff targets Codex. Combined audit prompt prepared. Human must paste into Codex.
- **V-1/V-2/V-3 manual validations:** Queued but not started. Can run in parallel with audit.
  - V-1: Hevy rate limit test (H-06) — ramp test until 429
  - V-2: Hevy routine creation (VD-1) — `POST /v1/routines` round-trip
  - V-3: Wearable HC data types (A-01) — device model, HRV availability, sleep stages

---

## 6. Open Questions

- **Q1:** What specific tables does migration 009 contain? Partially answered (override table scoped); rest depends on ADR-003.
- **Q2:** Nutrition/supplement schema detail level for "designed now" tables?
- **Q5/OA-2:** RP volume landmark reference values source — **answered by R-1** (status not yet updated in protocol; see drift below).
- Is the dashboard/settings manual sync orchestration divergence intentional or a UI inconsistency? (surfaced by R-5)
- Should `health_sync` prefs be treated as an explicit client read model or a convenience cache? (surfaced by R-5)
- Is body-composition summary intentionally weight-only in prefs? (surfaced by R-5)
- Should planning treat current code or stale docs as authoritative? (surfaced by R-5; answer: code-first)

---

## 7. Blockers / Risks

- **BLOCKER: H-06 (Hevy rate limits).** Blocks ADR-001 (sync strategy). Human must perform ramp test. Threshold: >= 30 req/min = on-demand OK; 5-30 = caching needed; < 5 = batch-only; < 1 = reassess dependency entirely.
- **BLOCKER: A-01 (wearable HC data types).** Blocks ADR-003 (readiness inputs) and ADR-005 (HC abstraction). Human must inspect device and verify HRV/sleep stage availability.
- **BLOCKER: Codex audit of R-1 + R-5.** Required before research tasks can move to `accepted`. Human must copy prompt into Codex.
- **RISK: Documentation drift.** R-5 found that existing client docs (01-architecture-overview, 03-data-sync-protocol, 07-background-sync-and-workers) still describe "no Room / no offline queue" — contradicted by code. Planning must treat code as authoritative.
- **RISK: OA-2/Q5 status drift.** R-1 answered Q5/OA-2 (RP volume landmarks are available) but COORDINATION-PROTOCOL.md still shows both as `queued`. Minor — should be updated after Codex audit accepts R-1.

---

## 8. Authoritative Files / Artifacts

| Path | Purpose | Why It Matters |
|------|---------|----------------|
| `COORDINATION-PROTOCOL.md` | Canonical coordination artifact | Single source of truth for workflow state, locked decisions, research queue, active handoff |
| `SESSION-LOG.md` | Session continuity ledger | Quick-read surface for restart, mirrors protocol state |
| `DECISION-REGISTER.md` | Full decision/hypothesis/assumption registry | 50+ items with evidence, confidence, validation criteria |
| `ADR-002-server-schema-migration-strategy.md` | Accepted migration strategy | Governs how all v2 schema changes are structured |
| `ADR-004-workout-generation-reconciliation-model.md` | Accepted reconciliation model | Defines actuals-first progression and prescribed-to-actual linkage |
| `ARCHITECTURE-SESSION-01-OUTPUT.md` | Architecture session 1 output | Maps decision space, identifies ADR candidates and validation deps |
| `ARCHITECTURE-ASSUMPTIONS.md` | Planning-to-architecture handoff | What architecture may assume, what must be validated |
| `SERVER-SCHEMA-INVENTORY.md` | PostgreSQL schema baseline | 11 tables, 7 migrations — baseline for migration 009+ |
| `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md` | Override table scope | ~55 rows, system-scoped, maps Hevy 20-group to RP 16-group |
| `research/rp-volume-landmarks.md` | R-1 output | RP volume data availability, 13/16 completeness, CC license |
| `research/v1-client-data-flows.md` | R-5 output | Client persistence, workers, flows, freshness rules — architecture baseline |
| `PROJECT.md` | Project metadata and quick resume | Entry point for any session |
| `POST-GATE-REVIEW.md` | Planning gate review | Promoted hypotheses to explicit validation thresholds |
| `RESEARCH-BRIEF-workout-builder.md` | Workout builder research | Competitive landscape, RP methodology, generation architecture |
| `documentation/` (12 files) | v1 feature docs | **Partially stale** — several contradict current code (see Gaps) |

---

## 9. Prior Decisions and Rationale

- **D-17 / Hevy as source of truth:** Explicit user choice. Hevy is the gym companion app; Apex does not replace it for workout execution.
- **ADR-002 / Sequential SQL migrations:** Pillar-grouped, idempotent, schema/data separation. Rationale: server has no ORM; raw SQL is the existing pattern; pillar grouping prevents cross-feature migration conflicts.
- **ADR-004 / Actuals-first progression:** `workout_sessions` + `workout_sets` are the source of truth, not `generated_routines`. Prescribed-to-actual linkage exists only for D-05 explainability. Rationale: the app cannot control what the user actually does in the gym.
- **ATTR / System-scoped overrides:** `exercise_muscle_overrides` has no `user_id`. User overrides go in a separate future table. Rationale: MVP simplicity; system data is universal.
- **FLOW / Generate -> review -> execute:** Not generate -> review -> push. Push is one conditional execution path (conditional on VD-1). Manual/in-app execution is valid for MVP.
- **VD1-GATE / VD-1 does not block migration 009:** Migration schema can be finalized regardless of whether Hevy supports routine creation.
- **D-11 / Semi-autonomous workflow:** All workout recommendations require user review before execution. No auto-start.

---

## 10. Known Orchestration Failure Modes

- **Session loss without handoff:** Prior sessions lost context when compacted. SESSION-LOG.md and SESSION-HANDOFF.md mitigate this. Evidence: multiple session recovery messages in chat history.
- **Status drift between protocol files:** Q5/OA-2 answered by R-1 but not updated in COORDINATION-PROTOCOL.md. Risk: future sessions re-research answered questions.
- **Documentation-code divergence:** R-5 confirmed 3 docs describe "no Room / no offline queue" when Room+queue has been in code since 2026-03-02. Risk: architecture planning based on stale docs makes incorrect assumptions about client state.
- **Audit bottleneck on human transfer:** Protocol requires human to copy prompts between tools. No automation exists. Risk: workflow stalls if human is unavailable between tool sessions.
- **Parallel validation not started:** V-1/V-2/V-3 have been `queued` since 2026-03-16 with no progress. These are the critical-path blockers for 3 remaining ADRs. Risk: planning phase extends indefinitely if manual validations are not prioritized.

---

## 11. Gaps Between Docs and Reality

| Document | Gap | Impact |
|----------|-----|--------|
| `documentation/01-architecture-overview.md` | States no Room/offline queue | Contradicted by code; Room queue exists since 2026-03-02 |
| `documentation/03-data-sync-protocol.md` | States no durable outbound queue | Same — queue is durable across restarts, capped at 5000 rows |
| `documentation/07-background-sync-and-workers.md` | Omits Room queue phase of sync | Sync is now two-phase: HC→Room, then Room→server |
| `COORDINATION-PROTOCOL.md` Assumptions table | OA-2 still `queued` | R-1 answered this (RP data is available under CC license) |
| `COORDINATION-PROTOCOL.md` Open Questions | Q5 still `queued` | Same as OA-2 — answered by R-1 |
| `ARCHITECTURE-ASSUMPTIONS.md` Section 9 | A-02 still listed as "first week validation" | A-02 was validated 2026-03-15; revealed ~55 override scope |

---

## 12. Recommended Next Task for External Orchestrator

**Execute the Codex audit of R-1 and R-5.** This is the single blocking action on the critical path. Both research outputs are written, both contain proposed audit prompts, and the Active Handoff in both protocol files already targets Codex. After audit acceptance, the research queue will be clear and the workflow can advance to manual validations (V-1/V-2/V-3) which unblock the remaining three ADRs.

---

## 13. Recommended Prompt Inputs for External Orchestrator

For the Codex audit session, attach or paste:
1. `COORDINATION-PROTOCOL.md` — for locked decisions, evidence standard, audit checklist
2. `research/rp-volume-landmarks.md` — R-1 deliverable to audit
3. `research/v1-client-data-flows.md` — R-5 deliverable to audit
4. The combined Codex audit prompt (already prepared in prior session, reproduced in Active Handoff)

For post-audit continuation, the orchestrator should also have:
5. `SESSION-HANDOFF.md` (this file)
6. `DECISION-REGISTER.md` — for hypothesis validation criteria
7. `ARCHITECTURE-SESSION-01-OUTPUT.md` — for remaining ADR candidates

---

## 14. Confidence / Uncertainty Notes

**High confidence (determined from files):**
- Research queue state (R-1 complete, R-5 complete, both unaudited)
- Locked decision inventory (9 architecture + 17 planning, all with evidence)
- Active Handoff target (Codex audit)
- Manual validation blockers (VD-1, H-06, A-01 all `queued`, human-owned)
- Documentation drift (3 client docs contradict code, confirmed by R-5 code citations)
- v1 app state (stable, last commit 728b228, CI passing)

**Medium confidence (inferred from patterns):**
- V-1/V-2/V-3 have not been attempted (no output files exist; no status changes since creation)
- The combined Codex audit approach (auditing R-1 and R-5 together) has not been tested in this workflow

**Uncertain:**
- Whether the human intends to run V-1/V-2/V-3 before or after the Codex audit
- Whether any Codex audit has actually been run and results not yet captured (no evidence of this, but cannot rule out an unrecorded session)
- Timeline pressure or external deadlines affecting prioritization
