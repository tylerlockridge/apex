# Project Coordination Protocol

## Purpose

Single canonical control artifact for the Apex v2 planning and research phase. Shared between three participants — Human Operator, Codex (GPT-5.4), and Claude Code (Opus) — via manual file-based handoff. All planning decisions, research tasks, audits, and prompt transfers flow through this document.

This document governs **planning and research only**. No implementation code is produced under this protocol. Implementation begins only after this protocol's research queue is empty and all architecture decisions are locked.

**File location:** `Apex/COORDINATION-PROTOCOL.md`
**Controller-of-record:** Human Operator. This file is updated only by the human after each tool session. Tools may propose edits but changes take effect only when the human writes them here.

---

## Current Phase

| Field | Value |
|-------|-------|
| Phase | v2 Architecture — Planning & Research |
| Status | Active |
| Last updated | 2026-03-17 |
| Last tool session | Claude Code / Opus — R-1 + R-5 research completed |
| Next action | See [Active Handoff](#active-handoff) |

---

## Tool Responsibilities

### Human Operator — Controller-of-Record

| Responsibility | Description |
|---------------|-------------|
| Authority | All status changes, decision locks, and protocol updates require human action |
| Transfer | Copies prompts between tools. No automatic relay exists. |
| Validation execution | Performs manual validation tasks (API tests, device checks) that require hardware or credentials |
| Acceptance | Moves research from `complete` to `accepted` or `rework` after Codex audit |
| File control | Only participant who writes to `COORDINATION-PROTOCOL.md` and `SESSION-LOG.md` |

### Codex / GPT-5.4 — Planner, Auditor, Continuity Keeper

| Responsibility | Description |
|---------------|-------------|
| Architecture continuity | Detects contradictions between new outputs and locked decisions |
| Audit | Reviews research outputs against the evidence standard and audit checklist |
| Prompt generation | Writes the next research prompt based on audit results |
| Scope enforcement | Catches scope creep, overreach, and premature implementation framing |
| Decision management | Recommends locking, updating, or deferring decisions based on evidence |
| Output style | Concise, operational. No essays. Tables and bullet lists preferred. |

**Codex does NOT:** perform primary research, synthesize external sources, write implementation code, or unilaterally lock decisions.

### Claude Code / Opus — Researcher, Synthesizer, Drafter

| Responsibility | Description |
|---------------|-------------|
| Research | Source-backed exploration via web search, codebase analysis, and available tools |
| Synthesis | Structures findings into the mandatory output contract format |
| Drafting | Creates and updates architecture artifacts (ADRs, research briefs, schema docs) |
| Evidence gathering | Provides sources, confidence levels, and contradiction flags |
| Output style | Structured per the output contract. Findings formatted for Codex audit. |

**Claude does NOT:** re-litigate settled decisions unless presenting contradicting evidence. Does not make architecture decisions unilaterally. Does not produce implementation code during this phase. Does not audit its own outputs — all research goes to Codex for audit.

---

## Global Operating Rules

1. **No implementation code.** This protocol governs planning and research. Code artifacts are out of scope.
2. **Decisions require evidence.** No decision is locked without supporting evidence or explicit human judgment call.
3. **Settled means settled.** Locked decisions are not reopened without contradiction evidence that passes the audit checklist.
4. **Manual transfer only.** There is no automatic prompt passing between Codex and Claude. The human operator copies prompts and pastes outputs. All transfers are file-mediated through this document and the project's markdown artifacts.
5. **One canonical doc.** This file is the single source of truth for coordination state. If this file and a tool's memory disagree, this file wins.
6. **Tools propose, human disposes.** Both tools may propose changes to locked decisions, research queue items, or protocol rules. Changes take effect only when the human updates this document.
7. **Phase gates are explicit.** Moving from planning to implementation requires: research queue empty, all blocking validations complete, all ADRs in `locked` status, human sign-off.
8. **No self-audit.** Claude does not audit its own research. All research outputs go to Codex for audit via human transfer. Codex does not audit its own planning outputs — the human reviews directly.
9. **Human owns all manual validation.** API tests requiring credentials, device inspections, and other hands-on checks are human tasks, not tool tasks. These are tracked separately in the research queue.

---

## Locked Decisions

Decisions with status `locked` are not reopened without contradiction evidence. Status definitions:

| Status | Meaning |
|--------|---------|
| `locked` | Settled. Do not reopen without contradiction evidence. |
| `in_review` | Under active audit or research. May change. |
| `needs_validation` | Depends on an unresolved validation item. |
| `deferred` | Intentionally postponed. Not forgotten — has a trigger condition. |
| `queued` | Identified but not yet worked. |

### Decision Register

| ID | Decision | Status | Evidence | Last Reviewed |
|----|----------|--------|----------|---------------|
| D-17 | Hevy is source of truth for actual workout data | `locked` | Decision Register, ADR-004 | 2026-03-16 |
| ADR-002 | Sequential idempotent SQL migrations, pillar-grouped, schema/data separation | `locked` | ADR-002 accepted | 2026-03-16 |
| ADR-004 | Actuals-first progression; prescribed-to-actual linkage for D-05 explainability only | `locked` | ADR-004 accepted | 2026-03-16 |
| ATTR | System-scoped `exercise_muscle_overrides` table (~55 rows), no `user_id` | `locked` | EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md | 2026-03-16 |
| PROG | Progression source of truth is `workout_sessions` + `workout_sets`, not `generated_routines` | `locked` | ADR-004 §5 | 2026-03-16 |
| SNAP | `progression_snapshots` is derived/optional persistence, not foundational | `locked` | ADR-004 convergence pass | 2026-03-16 |
| PUSH | Push to Hevy is conditional on VD-1; manual/in-app execution is a valid MVP path | `locked` | ADR-004, ARCHITECTURE-ASSUMPTIONS D-11 | 2026-03-16 |
| VD1-GATE | VD-1 does NOT block migration 009 schema finalization | `locked` | Convergence pass 2026-03-16 | 2026-03-16 |
| FLOW | Product flow is generate -> review -> execute (not generate -> review -> push) | `locked` | ARCHITECTURE-ASSUMPTIONS D-11 | 2026-03-16 |

> **To add a decision:** Append a row. Set status to `in_review`. Only the human moves status to `locked` after Codex audit confirms.

---

## Assumptions To Validate

| ID | Assumption | Status | Method | Owner | Blocked By | Blocks |
|----|-----------|--------|--------|-------|------------|--------|
| VD-1 | Hevy API supports `POST /v1/routines` | `queued` | API test | Human | None | Whether push path is operationally usable |
| H-06 | Hevy rate limits are workable (>= 5 req/min) | `queued` | Ramp test | Human | None | ADR-001 sync strategy |
| A-01 | Tyler's wearable writes HRV to Health Connect | `queued` | Device inspection | Human | None | ADR-003 readiness inputs, ADR-005 HC abstraction |
| OA-2 | RP volume landmarks available in usable format | `queued` | Web research | Claude | None | Workout gen MVP (P-05) |
| OA-3 | Hevy workout completion sync lag < 1 hour | `queued` | Opportunistic during H-06 | Human | H-06 | Progression data freshness |

> **To update:** Change status. Add results in a new column or link to a findings doc.

---

## Open Questions

| # | Question | Owner | Status | Answer / Link |
|---|----------|-------|--------|---------------|
| Q1 | What specific tables does migration 009 contain? | Codex | `in_review` | Partially answered: override table scoped. Others depend on ADR-003. |
| Q2 | Nutrition/supplement schema detail level for "designed now" tables? | Codex | `queued` | ADR-002 Q2 |
| Q3 | Migration runner: shell or Node.js? | Codex | `deferred` | Implementation decision |
| Q4 | Zod schemas for empty tables: now or at activation? | Codex | `deferred` | ADR-002 Q4 |
| Q5 | RP volume landmark reference values source | Claude | `queued` | OA-2 |

---

## Research Queue

Two task types: **research tasks** (assigned to Claude) and **manual validation tasks** (performed by the human operator with hardware/credentials). Both types flow through Codex audit before acceptance.

### Research Tasks (Claude)

| # | Task | Question to Answer | Status | Output Destination |
|---|------|--------------------|--------|-------------------|
| R-1 | RP volume landmark data | Are RP MEV/MAV/MRV values publicly available in machine-readable format? What are the 16 muscle group values? Licensing? | `complete` | `research/rp-volume-landmarks.md` |
| R-5 | Map Apex v1 client data flows (C-2) | Room entities, SharedPrefs keys, WorkManager jobs, data freshness model | `complete` | `research/v1-client-data-flows.md` |

### Manual Validation Tasks (Human)

| # | Task | Question to Answer | Status | Output Destination |
|---|------|--------------------|--------|-------------------|
| V-1 | Hevy rate limit test (H-06) | What is the 429 threshold? Per-endpoint or global? Backoff behavior? | `queued` | `research/hevy-rate-limits.md` |
| V-2 | Hevy routine creation test (VD-1) | Does `POST /v1/routines` exist? Round-trip test. | `queued` | `research/hevy-routine-creation.md` |
| V-3 | Wearable HC data types (A-01) | Device model? HRV availability? Sleep stages? | `queued` | `research/wearable-hc-data.md` |

### Research task status flow

```
queued → in_progress → complete → audited (by Codex) → accepted or rework
```

- `complete` means Claude has written output to the destination file
- `audited` means Codex has reviewed and returned findings
- `accepted` means the human confirmed the audit and output is usable (terminal state)
- `rework` means Codex or human identified gaps; Claude re-executes with revised prompt

### Manual validation status flow

```
queued → in_progress → complete → audited (by Codex) → accepted
```

> **To add:** Append row with `queued` status to the appropriate table. **To complete:** Change status, link output file. **Human moves to `accepted` after Codex audit.**

---

## Evidence Standard

All research outputs and architecture claims must meet these criteria:

| Criterion | Requirement |
|-----------|------------|
| Source attribution | Every factual claim links to a source (URL, API response, file path, or commit hash) |
| Recency | Sources must be current. If a source is >12 months old, flag it. |
| Confidence rating | Every finding carries a confidence level: `high` (direct evidence), `medium` (strong inference), `low` (educated guess) |
| Contradiction check | Every output explicitly states whether findings contradict any locked decision |
| Scope boundary | Every output states what it does NOT cover |
| Reproducibility | API tests include the exact request/response. Web research includes URLs visited. |

---

## Prompt Transfer Protocol

### How transfer works

```
                        ┌───────────────┐
                        │    Human      │
                        │   Operator    │
                        │ (controller)  │
                        └──┬─────────┬──┘
              updates file │         │ updates file
          copies prompt ↓  │         │  ↓ copies prompt
┌──────────────┐    file write    ┌──────────────┐
│  Claude Code │ ──────────────→  │ COORDINATION │
│  (Opus)      │                  │ PROTOCOL.md  │
│  Researcher  │ ←──────────────  │ SESSION-LOG  │
│  Synthesizer │   human updates  │ research/*   │
│  Drafter     │                  │ audits/*     │
└──────────────┘                  └──────┬───────┘
                                         │
                              human copies ↓ prompt
                                  ┌──────────────┐
                                  │  Codex App   │
                                  │  (GPT-5.4)   │
                                  │  Planner     │
                                  │  Auditor     │
                                  └──────────────┘
```

1. **Human assigns work** → updates Active Handoff block with the target tool and prompt
2. **Claude finishes research** → writes findings to `research/<topic>.md` using the output contract → proposes a Codex audit prompt in its output
3. **Human reviews** → updates this document (status fields, research queue) → copies the audit prompt into Codex
4. **Codex audits** → returns audit using the Codex output contract → proposes a next prompt for Claude or flags decisions for human action
5. **Human reviews** → updates this document (statuses, decisions, Active Handoff) → continues to next step
6. **Repeat**

### File locations

| Artifact | Path | Written By | Updated By |
|----------|------|-----------|-----------|
| This protocol | `COORDINATION-PROTOCOL.md` | Human | Human only |
| Session log | `SESSION-LOG.md` | Human | Human only |
| Research outputs | `research/<topic>.md` | Claude | Claude (create), Human (status) |
| Audit outputs | `audits/<date>-<topic>.md` | Human (transcribes Codex output) | Human |
| Architecture decisions | `ADR-*.md` | Claude (drafts) | Human (accepts) |
| Locked decision updates | This file, "Locked Decisions" table | Human | Human only |

### Status flow for decisions

```
queued → in_review → locked
                   → needs_validation → (validation result) → locked or deferred
                   → deferred (with trigger condition)
```

---

## Output Contract for Claude

Every research or synthesis output from Claude Code must use this structure. No exceptions.

```markdown
# [Topic]: [Specific Question Answered]

## Objective
What this research was asked to determine. Copy from the Research Queue task.

## Sources Reviewed
| # | Source | Type | Date | Relevance |
|---|--------|------|------|-----------|

## Findings
[Structured findings. Use tables, bullet lists. No essays.]

## Confidence
| Finding | Confidence | Basis |
|---------|-----------|-------|
| [finding 1] | high/medium/low | [why] |

## Contradictions to Current Assumptions
- [ ] No contradictions found
- OR: [specific contradiction with reference to locked decision ID]

## Impact on Locked Decisions
- [ ] No impact
- OR: [specific decision ID + proposed change + evidence]

## Out of Scope
What this research intentionally did NOT cover.

## Recommended Next Step
[One concrete action. Not a menu of options.]

## Proposed Codex Audit Prompt
[Exact prompt the human should paste into Codex for audit. Include context.]
```

**Example "Proposed Codex Audit Prompt":**
```
Review the attached RP volume landmark research (research/rp-volume-landmarks.md).
Audit against the evidence standard in COORDINATION-PROTOCOL.md.
Specifically confirm: (1) sources are primary, (2) the 16 muscle groups match
EXERCISE-ATTRIBUTION-REFINEMENT-SCOPE.md Section 1, (3) no licensing issues
block inclusion as seed data. Return your audit using the Codex output contract.
```

---

## Output Contract for Codex

Every audit or review output from Codex must use this structure.

```markdown
# Audit: [Topic]

## Document Reviewed
[filename + date]

## Accepted Findings
| # | Finding | Confidence Confirmed? | Notes |
|---|---------|----------------------|-------|

## Rejected or Weakly Supported Findings
| # | Finding | Issue | Required Action |
|---|---------|-------|----------------|

## Effects on Architecture Continuity
- [ ] No effects
- OR: [specific locked decision ID + effect]

## Decisions to Lock / Update / Defer
| Decision ID | Action | New Status | Rationale |
|-------------|--------|-----------|-----------|

## Research Gaps
| # | Gap | Priority | Suggested Research Task |
|---|-----|----------|------------------------|

## Proposed Claude Prompt
[Exact prompt the human should paste into Claude Code. Include context and constraints.]
```

**Example "Proposed Claude Prompt":**
```
Research task R-5: Map Apex v1 client data flows.
Read the existing codebase under app/src/main/java/com/healthplatform/.
Document: (1) all Room entities and their fields, (2) all SharedPreferences keys
and what stores them, (3) all WorkManager jobs and their schedules, (4) data
freshness model (how stale can cached data be before re-sync). Output per the
Claude output contract to research/v1-client-data-flows.md. Do not propose
architecture changes — document current state only.
```

---

## Audit Checklist

Run this checklist against every research output before accepting findings or locking decisions. Codex runs this checklist; the human confirms.

### Scope & Drift

- [ ] Output answers the specific question asked (not a broader question)
- [ ] No new architecture decisions were made without being flagged
- [ ] No implementation code or implementation-level detail was produced
- [ ] No new tables, columns, or schemas were proposed outside the research scope
- [ ] Output does not expand the scope of migration 009 beyond what is settled

### Evidence Quality

- [ ] Every factual claim has a source
- [ ] Sources are primary (not summaries of summaries)
- [ ] Confidence levels are present and justified
- [ ] API test results include exact request/response (if applicable)
- [ ] No "it is likely that..." without a confidence tag

### Decision Integrity

- [ ] No locked decision was reopened without contradiction evidence
- [ ] No conditional dependency was treated as a settled fact
- [ ] No "generate -> review -> push" language (must be "generate -> review -> execute")
- [ ] Progression is framed as actuals-first (`workout_sessions` + `workout_sets`)
- [ ] `progression_snapshots` is framed as derived/optional, not foundational
- [ ] VD-1 is not framed as blocking migration 009
- [ ] System override table has no user-scoped semantics
- [ ] Push to Hevy is framed as conditional on VD-1

### Operational Quality

- [ ] Output is structured per the output contract
- [ ] "Proposed Codex Audit Prompt" or "Proposed Claude Prompt" section is present and actionable
- [ ] Out-of-scope section is present
- [ ] Output is concise (no philosophical framing, no motivation, no "could also consider")

---

## Anti-Drift / Do-Not-Reopen Rules

### Do Not Reopen Without Contradiction Evidence

| Rule | What It Prevents |
|------|-----------------|
| Progression is actuals-first | Do not propose progression tables as foundational stores. `workout_sessions` + `workout_sets` are the source of truth. |
| Push is conditional | Do not describe push as the default or only execution path. Manual/in-app execution is valid at MVP. |
| VD-1 does not gate migration 009 | Do not add "before VD-1 validates" to any migration finalization language. |
| System override table has no `user_id` | Do not add user-scoping to `exercise_muscle_overrides`. User overrides go in a separate future table. |
| `progression_snapshots` is derived | Do not treat it as a foundational progression history table. It is a materialized cache. |
| Generate -> review -> execute | Do not use "generate -> review -> push". Push is one conditional execution path. |

### Drift Patterns to Catch

| Pattern | What It Looks Like | Correct Response |
|---------|-------------------|-----------------|
| Broad re-analysis | Tool produces a 2000-word exploration when a 3-line answer was needed | Reject. Re-prompt with tighter scope. |
| Reopening settled decisions | "We should reconsider whether..." without new evidence | Reject. Cite the locked decision. |
| Implementation creep | Research output includes function signatures, class designs, or build steps | Reject. Strip implementation detail. Return to research scope. |
| Conditional-as-settled | "When Apex pushes the routine to Hevy..." (assumes VD-1 positive) | Correct to "If VD-1 validates, Apex may push..." |
| Philosophical framing | "The fundamental tension between..." | Reject. Ask for operational output. |
| Scope inflation | Research on RP landmarks expands into coaching algorithm design | Reject. Scope to the original question only. |
| Tool capability assumption | Output assumes a specific tool or API is available without checking | Remove the assumption. State the research method actually used. |

---

## Session Closeout Template

At the end of every tool session, the human fills out this template and appends it to `SESSION-LOG.md`.

```markdown
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

**Active Handoff updated:** [yes/no — if yes, which tool is next]
```

---

## Restart Instructions for a New Session

### Starting a Claude Code session

1. Open Claude Code in the Apex project directory
2. Paste the following as the first message:

```
Read COORDINATION-PROTOCOL.md. You are operating as the Researcher/Synthesizer/Drafter
defined in that protocol. Check the "Active Handoff" section for your current task.
Follow the output contract exactly. Do not re-litigate locked decisions. If you find
contradictions to locked decisions during research, flag them in the "Contradictions"
section of your output — do not resolve them unilaterally. Do not audit your own
output — end with a proposed Codex audit prompt.
```

3. If Claude needs additional context, point it to the specific architecture docs referenced in the prompt

### Starting a Codex session

1. Open the Codex app
2. Attach or paste the contents of `COORDINATION-PROTOCOL.md`
3. Attach or paste the research output being audited (from `research/<topic>.md`)
4. Paste the following:

```
You are operating as the Planner/Auditor/Continuity Keeper defined in
COORDINATION-PROTOCOL.md. Review the attached research output against the audit
checklist and evidence standard. Use the Codex output contract for your response.
Check all findings against the locked decisions table. Flag any drift, scope creep,
or unsupported claims. Write the proposed next prompt for Claude if follow-up
research is needed.
```

### Resuming after a break

1. Read the "Current Phase" table in this doc
2. Read the last entry in `SESSION-LOG.md`
3. Read the "Active Handoff" section for the current pending action
4. Continue from there

---

## Active Handoff

> This section replaces separate "Next Prompt" sections. It contains exactly one pending action at a time. The human updates this after every session.

| Field | Value |
|-------|-------|
| **Target tool** | Codex / GPT-5.4 |
| **Task ID** | R-1 + R-5 audit |
| **Status** | Both research tasks complete. Awaiting Codex audit. |
| **Prompt** | _(see Pending audit below)_ |

| **Pending audit** | R-1 (`research/rp-volume-landmarks.md`) and R-5 (`research/v1-client-data-flows.md`) |
|-------|-------|
| **Audit prompt** | Paste the Proposed Codex Audit Prompt from the end of each research file into Codex. Audit R-1 and R-5 sequentially or together. |
