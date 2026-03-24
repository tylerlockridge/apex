# Session Log

Durable session ledger for the Apex v2 planning and research phase. Human-operated. Optimized for continuity across session loss or tool interruption.

**Governed by:** `COORDINATION-PROTOCOL.md`
**Controller-of-record:** Human Operator
**Scope:** Planning and research only. No implementation code.

---

## Current Objective

Complete the v2 architecture research queue (R-1, R-5, V-1, V-2, V-3), audit all outputs via Codex, and lock remaining ADRs before entering implementation.

---

## Current Phase

| Field | Value |
|-------|-------|
| Phase | v2 Architecture — Planning & Research |
| Status | Active |
| Last session | 2026-03-16 — Claude Code / Opus |
| Session work | Architecture session 1 convergence; protocol finalization |

---

## Files in Play

| File | Purpose | Last Modified |
|------|---------|---------------|
| `COORDINATION-PROTOCOL.md` | Canonical coordination artifact | 2026-03-17 |
| `SESSION-LOG.md` | This file — session continuity ledger | 2026-03-17 |
| `research/rp-volume-landmarks.md` | R-1 output — RP volume landmark availability | 2026-03-17 |
| `research/v1-client-data-flows.md` | R-5 output — v1 client data flow map | 2026-03-17 |
| `ADR-002-server-schema-migration-strategy.md` | Migration strategy (accepted) | 2026-03-16 |
| `ADR-004-workout-generation-reconciliation-model.md` | Reconciliation model (accepted) | 2026-03-16 |
| `ARCHITECTURE-SESSION-01-OUTPUT.md` | Session 1 drivers, constraints, follow-ups | 2026-03-16 |
| `ARCHITECTURE-ASSUMPTIONS.md` | Planning-to-architecture handoff | 2026-03-16 |
| `SERVER-SCHEMA-INVENTORY.md` | PostgreSQL schema baseline (11 tables) | 2026-03-16 |
| `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md` | System-scoped override table (~55 rows) | 2026-03-16 |
| `DECISION-REGISTER.md` | Full decision history | 2026-03-16 |

---

## Locked Decisions

Mirrors `COORDINATION-PROTOCOL.md` "Locked Decisions" table. Updated here only when a session changes a decision status.

| ID | Decision | Status |
|----|----------|--------|
| D-17 | Hevy is source of truth for actual workout data | `locked` |
| ADR-002 | Sequential idempotent SQL migrations, pillar-grouped | `locked` |
| ADR-004 | Actuals-first progression; prescribed-to-actual linkage for explainability | `locked` |
| ATTR | System-scoped `exercise_muscle_overrides`, no `user_id` | `locked` |
| PROG | Progression source of truth is `workout_sessions` + `workout_sets` | `locked` |
| SNAP | `progression_snapshots` is derived/optional, not foundational | `locked` |
| PUSH | Push to Hevy conditional on VD-1; manual execution valid for MVP | `locked` |
| VD1-GATE | VD-1 does NOT block migration 009 schema finalization | `locked` |
| FLOW | Product flow: generate -> review -> execute (not push) | `locked` |

---

## Open Questions

| # | Question | Owner | Status |
|---|----------|-------|--------|
| Q1 | Migration 009 table contents? | Codex | `in_review` |
| Q2 | Nutrition/supplement schema detail level? | Codex | `queued` |
| Q3 | Migration runner: shell or Node.js? | Codex | `deferred` |
| Q4 | Zod schemas for empty tables: now or at activation? | Codex | `deferred` |
| Q5 | RP volume landmark reference values source | Claude | `queued` |

---

## Validation Items

Human-performed. Require hardware, credentials, or manual testing.

| ID | Item | Status | Blocks |
|----|------|--------|--------|
| VD-1 | Hevy `POST /v1/routines` exists and round-trips | `queued` | Push path usability |
| H-06 | Hevy rate limits >= 5 req/min | `queued` | ADR-001 sync strategy |
| A-01 | Wearable writes HRV + sleep stages to Health Connect | `queued` | ADR-003, ADR-005 |

---

## Research Queue Snapshot

### Research Tasks (Claude)

| # | Task | Status | Output |
|---|------|--------|--------|
| R-1 | RP volume landmark data (MEV/MAV/MRV, 16 groups, licensing) | `complete` | `research/rp-volume-landmarks.md` |
| R-5 | Map Apex v1 client data flows (Room, SharedPrefs, WorkManager) | `complete` | `research/v1-client-data-flows.md` |

### Manual Validation Tasks (Human)

| # | Task | Status | Output |
|---|------|--------|--------|
| V-1 | Hevy rate limit test (H-06) | `queued` | `research/hevy-rate-limits.md` |
| V-2 | Hevy routine creation test (VD-1) | `queued` | `research/hevy-routine-creation.md` |
| V-3 | Wearable HC data types (A-01) | `queued` | `research/wearable-hc-data.md` |

### Status flow

```
queued → in_progress → complete → audited (by Codex) → accepted or rework
```

---

## Last Completed Action

| Field | Value |
|-------|-------|
| Date | 2026-03-17 |
| Tool | Claude Code / Opus |
| Action | Executed R-1 (RP volume landmarks) and R-5 (v1 client data flows). Both research outputs written per output contract. R-1 revised per human correction on RP ToS licensing. |
| Result | Both research tasks `complete`. Proposed Codex audit prompts included in each output file. |

---

## Latest Codex Directive

| Field | Value |
|-------|-------|
| Date | 2026-03-16 |
| Audit target | COORDINATION-PROTOCOL.md (initial draft) |
| Directive | 8 workflow changes: add Human Operator as controller-of-record, rename Claude role, remove self-audit path, split research from validation, single Active Handoff, normalize statuses, add SESSION-LOG.md, remove hardcoded tool assumptions |
| Status | All 8 changes applied. Protocol finalized. |
| Next | No pending Codex directive. Human to assign first research task. |

---

## Latest Claude Deliverable

| Field | Value |
|-------|-------|
| Date | 2026-03-17 |
| Task | R-1 + R-5 research execution |
| Output files | `research/rp-volume-landmarks.md`, `research/v1-client-data-flows.md` |
| Audit status | Pending Codex audit |
| Acceptance | — |

---

## Active Handoff

> Mirrors `COORDINATION-PROTOCOL.md` Active Handoff. Updated here after every session.

| Field | Value |
|-------|-------|
| **Target tool** | Codex / GPT-5.4 |
| **Task ID** | R-1 + R-5 audit |
| **Status** | Both research tasks complete. Awaiting Codex audit. |
| **Prompt** | Paste Proposed Codex Audit Prompt from end of each research file. |
| **Pending audit** | R-1 (`research/rp-volume-landmarks.md`) + R-5 (`research/v1-client-data-flows.md`) |

**Remaining candidates:**
- V-1 (Hevy rate limits) → Human
- V-2 (Hevy routine creation) → Human
- V-3 (Wearable HC data) → Human

---

## Human Action Required

- [ ] Paste R-1 Proposed Codex Audit Prompt (from end of `research/rp-volume-landmarks.md`) into Codex with the R-1 file attached
- [ ] Paste R-5 Proposed Codex Audit Prompt (from end of `research/v1-client-data-flows.md`) into Codex with the R-5 file attached
- [ ] After Codex returns both audits: update R-1 and R-5 to `audited`, then `accepted` or `rework`
- [ ] Optionally: begin V-1/V-2/V-3 manual validation tasks in parallel

---

## Restart Checklist

Use this when resuming after a break, session loss, or tool interruption.

1. Read `COORDINATION-PROTOCOL.md` — Current Phase table
2. Read this file — Last Completed Action, Active Handoff, Human Action Required
3. Check Active Handoff for pending work
   - If a task is assigned: resume with the target tool
   - If empty: select next task from Research Queue Snapshot
4. Check Latest Codex Directive for any unaddressed findings
5. Check Latest Claude Deliverable for any unaudited output
6. Verify Locked Decisions table matches `COORDINATION-PROTOCOL.md`
7. Continue from Active Handoff

**Restart prompt for Claude Code:**
```
Read COORDINATION-PROTOCOL.md and SESSION-LOG.md. You are operating as the
Researcher/Synthesizer/Drafter. Check the Active Handoff section for your
current task. Follow the output contract exactly. Do not re-litigate locked
decisions. Do not audit your own output — end with a proposed Codex audit prompt.
```

**Restart prompt for Codex:**
```
You are operating as the Planner/Auditor/Continuity Keeper defined in
COORDINATION-PROTOCOL.md. Review the attached research output against the
audit checklist and evidence standard. Use the Codex output contract. Check
all findings against the locked decisions table. Flag drift, scope creep,
or unsupported claims. Write the proposed next prompt for Claude if needed.
```

---

## Session Closeout Entry Template

Append one entry per tool session. Human fills this out after each session.

```markdown
---

### Session: [YYYY-MM-DD] — [Tool Used]

**Duration:** [approx]

**Tasks worked:**
- [task ID]: [one-line summary] — [status after this session]

**Decisions proposed:**
- [decision ID]: [proposed status change] — [accepted by human / pending]

**Research queue changes:**
- [task ID]: [old status] → [new status]

**Documents created or modified:**
- [filename]: [what changed]

**Unresolved items carried forward:**
- [item]

**Active Handoff updated:** [yes/no — target tool if yes]
```

---

## Session History

### Session: 2026-03-16 — Claude Code / Opus

**Duration:** ~45 min

**Tasks worked:**
- Protocol creation: Created COORDINATION-PROTOCOL.md — `complete`
- Convergence pass: Fixed continuity violations across 5 architecture docs — `complete`

**Decisions proposed:**
- None new. Existing locked decisions preserved during convergence pass.

**Research queue changes:**
- R-1 through R-5 and V-1 through V-3: created as `queued`

**Documents created or modified:**
- `COORDINATION-PROTOCOL.md`: created, then revised per Codex audit
- `SESSION-LOG.md`: created
- `ADR-004-workout-generation-reconciliation-model.md`: convergence fixes (3 edits)
- `ADR-002-server-schema-migration-strategy.md`: convergence fixes (3 edits)
- `ARCHITECTURE-SESSION-01-OUTPUT.md`: convergence fixes (2 edits)
- `EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md`: convergence fixes (2 edits)
- `ARCHITECTURE-ASSUMPTIONS.md`: convergence fixes (2 edits)

**Unresolved items carried forward:**
- No research started. Queue populated but untouched.

**Active Handoff updated:** Yes — cleared, human to assign first task
