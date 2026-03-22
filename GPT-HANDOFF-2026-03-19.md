# GPT Context Recovery Handoff

**Date:** 2026-03-19
**From:** Claude Code (Opus) — has full project context
**To:** GPT (Monica.im) — lost context, needs catch-up
**Purpose:** Get GPT back up to speed on the Apex v2 planning/research workflow and recommend moving audit/prompt work to Codex to prevent future context loss.

---

## What Is This Project?

**Apex** is a Kotlin/Compose Android app that syncs health data (BP, sleep, HRV, body comp, workouts) from Android Health Connect to a self-hosted Node.js/Express server. v1 is shipped and stable.

**v2** adds: workout generation, nutrition tracking, food photo estimation, AI coaching, supplements. We are in the **planning and research phase** — no v2 implementation code has been written yet (except Package 0A migration infrastructure, which just completed).

---

## Where We Are Right Now

### Phase: Transitioning from planning to implementation

The planning phase is essentially complete. Here's the status:

| Item | Status |
|------|--------|
| 17 product decisions (D-01 to D-17) | Locked |
| 8 hypotheses with validation criteria | Registered |
| 5 research briefs | Complete |
| Architecture session 1 | Complete |
| ADR-002 (migration strategy) | **Accepted** |
| ADR-004 (workout reconciliation model) | **Accepted** |
| ADR-001 (Hevy sync strategy) | **Drafted** — proposed for acceptance. Sync tier conditional on H-06 rate limit test. |
| ADR-003 (readiness scoring inputs) | **Drafted** — proposed for acceptance. HRV weight conditional on A-01 wearable test. |
| ADR-005 (health data source abstraction) | **Drafted** — proposed for acceptance. Unconditional — provider interface ready. |
| R-1 (RP volume landmarks research) | **Complete** — never audited by Codex |
| R-5 (v1 client data flows) | **Complete** — never audited by Codex |
| Implementation roadmap | **Finalized** (`IMPLEMENTATION-ROADMAP.md`) |
| Package 0A (migration infrastructure) | **Implemented and verified** — ready for live adoption |
| Package 0B (client health provider interface) | Not started |

### What got stalled

The original coordination protocol required GPT/Codex to **audit** Claude's research outputs (R-1 and R-5) before they could be accepted. That audit never happened because GPT lost context. Claude eventually did a workflow reset audit (`WORKFLOW-RESET-AUDIT.md`) which concluded the audit gate was blocking progress unnecessarily, and proceeded to draft all remaining ADRs and the implementation roadmap treating the research as planning-sufficient.

### Manual validations still pending

These are human tasks that inform config values, not architecture:

| ID | What | Affects | Status |
|----|------|---------|--------|
| H-06 | Hevy API rate limit test | ADR-001 sync tier config | Queued — human must do |
| A-01 | Check wearable HC data types (HRV?) | ADR-003 HRV weight, ADR-005 provider needs | Queued — human must do |
| VD-1 | Test `POST /v1/routines` on Hevy | ADR-004 push path (display-only fallback exists) | Queued — human must do |

None of these block starting implementation. They determine config parameters.

---

## Implementation Roadmap (Summary)

```
Package 0A (migration infra) ✅ DONE
  → Package 0B (client health provider interface) — next
  → Phase 1 (server Hevy adapter + workout schema)
  → Phase 2 (empty future-pillar schemas + readiness engine) — parallel with Phase 1
  → Phase 3 (workout generation MVP)
  → Phase 4 (polish + ship)
```

Estimated: 10-15 implementation sessions for MVP.

---

## What GPT's Role Has Been

In prior sessions, GPT (via Monica.im) served as **Planner / Auditor / Continuity Keeper**:
- Audited the coordination protocol design
- Tightened R-1 execution prompt
- Designed the R-5 research package
- Provided scope enforcement and drift detection

The formal coordination protocol (`COORDINATION-PROTOCOL.md`) defines this role and includes output contract templates for Codex audit responses.

---

## The Problem: Context Loss in Monica.im

GPT sessions in Monica.im are ephemeral. When context is lost, the audit/planning continuity breaks. This has happened at least twice and caused the R-1/R-5 audit to never complete.

---

## Recommendation: Move Audit and Prompt Work to Codex

**Codex** (OpenAI's code-connected agent) can:
- Persist context across sessions via its repository connection
- Read project files directly (all planning artifacts are markdown in the repo)
- Run structured audit prompts against research outputs
- Generate and refine prompts for Claude without losing state

### Proposed role split going forward

| Role | Tool | What it does |
|------|------|-------------|
| **Research / Synthesis / Implementation** | Claude Code (Opus) | Writes research, drafts ADRs, builds code, executes GSD packages |
| **Audit / Prompt Generation / Progress Tracking** | Codex | Reviews Claude outputs, generates next-task prompts, maintains continuity, catches drift |
| **Controller-of-Record** | Human (Tyler) | Approves decisions, performs manual validations, gates live deployments |
| **Ad-hoc planning discussion** | GPT (Monica.im) | Quick questions, brainstorming — but NOT the audit bottleneck |

### What Codex should do first

1. **Accept R-1 and R-5 as planning-sufficient.** The workflow reset audit concluded these don't need formal audit gates — the ADRs were drafted from them successfully.
2. **Review the 3 draft ADRs** (ADR-001, ADR-003, ADR-005) if a quality check is desired. These have already been through a Claude-side quality audit and tightening pass.
3. **Track implementation progress** as Package 0B and Phase 1 begin via GSD/Ralph loops.

---

## Key Files GPT/Codex Should Read

For full context recovery, read these in order:

| Priority | File | What it tells you |
|----------|------|------------------|
| 1 | `PROJECT.md` (Quick Resume section) | Current state in 5 lines |
| 2 | `IMPLEMENTATION-ROADMAP.md` | Full phased build plan |
| 3 | `WORKFLOW-RESET-AUDIT.md` | Why the old audit gates were removed |
| 4 | `DECISION-REGISTER.md` | All 17 locked decisions + 8 hypotheses |
| 5 | `ADR-001-hevy-abstraction-and-sync-strategy.md` | Hevy sync architecture |
| 6 | `ADR-003-readiness-scoring-input-architecture.md` | Readiness engine design |
| 7 | `ADR-005-health-data-source-abstraction.md` | HC abstraction interface |
| 8 | `GSD-PACKAGE-0A.md` + `IMPL-BRIEF-PACKAGE-0A.md` | Migration infra (done) |
| 9 | `research/rp-volume-landmarks.md` | RP volume data availability |
| 10 | `research/v1-client-data-flows.md` | Current client architecture baseline |

---

## What Happens Next

1. **Human:** Run H-06 (Hevy rate limit test) and A-01 (wearable check) when convenient
2. **Claude:** Execute Package 0B (client health provider interface) then begin Phase 1
3. **Codex:** Pick up audit/tracking role — review ADRs if desired, track implementation progress, generate next-task prompts for Claude
4. **GPT (Monica.im):** Available for ad-hoc discussion but no longer the audit bottleneck
