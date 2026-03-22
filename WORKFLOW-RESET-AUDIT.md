# WORKFLOW RESET AUDIT

**Date:** 2026-03-18
**Scope:** Full orchestration system critique — COORDINATION-PROTOCOL.md, SESSION-LOG.md, SESSION-HANDOFF.md, research queue, audit gates, decision tracking, and all process artifacts.

---

## 1. Executive Assessment

**The current orchestration is partially effective but significantly process-heavy.** It has become overfit to its own workflow machinery.

The protocol successfully preserved continuity across session loss and prevented decision drift on 9 locked architecture items. That's real value. But the system now maintains ~2,900 lines of process documentation to coordinate 5 research tasks and 3 manual validations for a single-user project. Orchestration artifacts outweigh research content by a 1.19:1 ratio. The workflow has 8 sequential gates between "research complete" and "implementation begins," and it is currently stalled at gate 2 with no escalation mechanism.

The protocol was designed defensively for a worst case (3 async agents, no shared memory, frequent session loss). That worst case was real when the protocol was created. It is no longer the operating reality. The defensive overhead is now the dominant cost.

---

## 2. Evidence of Healthy Workflow

- **Locked decisions are stable and respected.** 9 architecture decisions have not drifted since being locked. Each has evidence, and the anti-drift rules have prevented reopening without contradiction evidence. This is working.
- **Research outputs are high quality.** R-1 (RP volume landmarks) and R-5 (v1 client data flows) are well-structured, evidence-cited, and scoped correctly. The output contract format produced useful, auditable deliverables.
- **Session recovery is possible.** Even after session loss, a new session can reconstruct state from 2-3 files in under 10 minutes. This was the core design goal and it works.
- **Evidence standard prevented hand-waving.** Both R-1 and R-5 cite specific sources, confidence levels, and contradictions. R-5 caught documentation drift that would have misled architecture planning.
- **Scope control held.** Neither research task drifted into implementation, algorithm design, or schema proposals. The scope boundaries defined in the protocol were effective.

---

## 3. Evidence of Workflow Contamination or Overfit

### 3.1 Information Triplication

Five operational items are maintained in 3-4 places simultaneously:

| Information | Locations | Drift risk |
|-------------|-----------|------------|
| Locked decisions | COORDINATION-PROTOCOL, SESSION-LOG, DECISION-REGISTER, scattered in ADRs | 3 confirmed drift instances already |
| Research queue state | COORDINATION-PROTOCOL, SESSION-LOG, SESSION-HANDOFF | Must update 3 files per status change |
| Active handoff | COORDINATION-PROTOCOL, SESSION-LOG, SESSION-HANDOFF | All 3 say the same thing differently |
| Current phase | COORDINATION-PROTOCOL, SESSION-LOG, SESSION-HANDOFF, PROJECT.md | 4 places; PROJECT.md already slightly different wording |
| Open questions | COORDINATION-PROTOCOL, SESSION-LOG, SESSION-HANDOFF | Q5/OA-2 answered by R-1 but still `queued` in protocol |

Every status change requires updating 2-4 files manually. Every missed update creates silent drift. Three drift instances already exist.

### 3.2 Process Documentation Outweighs Content

| Category | Lines | Files |
|----------|-------|-------|
| Orchestration/process docs | ~2,900 | COORDINATION-PROTOCOL, SESSION-LOG, SESSION-HANDOFF, GATE-REVIEW, POST-GATE-REVIEW, PRE-ARCHITECTURE-PLANNING-MEMO |
| Research/architecture content | ~2,400 | ADRs, research briefs, research outputs, schema inventory, exercise attribution scope |

The process infrastructure is larger than the work product it coordinates.

### 3.3 Self-Referential Process Rules

COORDINATION-PROTOCOL.md dedicates ~220 lines (44%) to meta-process:
- 50-line output contract template for Claude
- 46-line output contract template for Codex
- 39-line audit checklist (which restates the evidence standard as checkboxes)
- 26-line anti-drift rules
- Two restart checklists (one in COORDINATION-PROTOCOL, one in SESSION-LOG — nearly identical)
- Two session closeout templates (same duplication)

These are rules about how to use the rules. They served their purpose in the first 2 sessions. They are now ceremony.

### 3.4 Stalled at Gate 2 of 8

The documented path from "research complete" to "implementation begins" has 8 gates:

1. Research execution (Claude) -- done
2. Codex audit (human transfers prompt) -- **stalled here**
3. Human acceptance of audit
4. Manual validation (V-1, V-2, V-3) -- not started
5. ADR drafting (depends on validations)
6. Codex ADR audit
7. Human ADR acceptance
8. Final phase gate (human sign-off)

R-1 and R-5 have been `complete` since 2026-03-17. No Codex audit has been executed (no audit output file exists anywhere in the workspace). V-1/V-2/V-3 have been `queued` since 2026-03-16 with zero progress. The protocol has no escalation mechanism for stalled gates.

### 3.5 SESSION-LOG Is a Mirror, Not a Ledger

SESSION-LOG.md is described as a "durable session ledger" but ~90% of its content mirrors COORDINATION-PROTOCOL.md. It duplicates: locked decisions, research queue, active handoff, open questions, and current phase. It adds: one session history entry and a restart checklist that also exists in COORDINATION-PROTOCOL.md. The only unique value is the session history appendix at the bottom, which has one entry.

---

## 4. Current Critical Path: Real vs Documented

### Documented Critical Path
1. Codex audits R-1 and R-5
2. Human accepts or requests rework
3. Human performs V-1 (Hevy rate limits), V-2 (Hevy routine creation), V-3 (wearable HC data)
4. Claude drafts ADR-001, ADR-003, ADR-005
5. Codex audits ADRs
6. Human accepts all ADRs
7. Phase gate: enter implementation

### Real Critical Path
1. **Human performs V-1, V-2, V-3.** These are the actual blockers. They require hardware, credentials, and manual testing. They have been queued for 2 days. No orchestration artifact can advance them.
2. **Claude drafts remaining ADRs** using validation results + existing R-1/R-5 findings.
3. **Review and lock ADRs.**
4. **Begin implementation.**

### Mismatch
The documented path puts the Codex audit of R-1/R-5 first. But R-1 and R-5 are reference material — they don't block ADR drafting on their own. What blocks ADR drafting is the missing validation data (H-06 rate limits, A-01 wearable data, VD-1 routine creation). The Codex audit is a quality gate on completed research, not a prerequisite for the next phase of work.

**The real bottleneck is the three human-owned manual validations, not the Codex audit.** The protocol's sequential ordering obscures this because it processes the research queue linearly before acknowledging that V-1/V-2/V-3 are the actual critical path.

---

## 5. Audit of Current "Next Task"

**Documented next task:** Codex audit of R-1 and R-5.

**Assessment:** This is a quality gate, not a critical-path advancement. R-1 findings (RP volume data is publicly available under CC license, 13/16 groups covered) and R-5 findings (client has durable outbound queue, no durable inbound cache) are useful reference data. But neither finding gates any specific next decision. The locked decisions they might affect are already locked. The ADRs they inform (ADR-001, ADR-003, ADR-005) are gated by validations, not by audit acceptance of R-1/R-5.

**Highest-leverage action right now:** Start V-1 (Hevy rate limits) and V-3 (wearable HC data). These directly unblock ADR-001 (sync strategy) and ADR-003/ADR-005 (readiness inputs, HC abstraction). V-2 (routine creation) is lower priority because VD-1 is already confirmed as non-blocking for migration 009.

**The Codex audit can run in parallel with manual validations.** There is no dependency between them. The protocol's sequential framing is artificial.

---

## 6. Process Overhead Inventory

### Necessary
| Item | Why |
|------|-----|
| COORDINATION-PROTOCOL.md locked decisions table | Prevents re-litigation of settled items |
| COORDINATION-PROTOCOL.md research queue | Tracks what's done, what's blocked, and why |
| COORDINATION-PROTOCOL.md evidence standard | Keeps research outputs auditable |
| PROJECT.md quick resume | Session entry point |
| Active Handoff (single location) | Shows what to do next |

### Optional (useful but not required)
| Item | Why optional |
|------|-------------|
| Codex audit of R-1/R-5 | Quality gate, not blocking gate. Findings are reference data, not decision inputs. Could be deferred to post-ADR review. |
| Output contract templates | Useful for first use; unnecessary once internalized. Could be a reference appendix, not inline protocol. |
| SESSION-HANDOFF.md | One-time snapshot for external orchestrator handoff. Not needed for ongoing sessions. |

### Redundant
| Item | Why redundant |
|------|---------------|
| SESSION-LOG.md (except session history) | 90% mirrors COORDINATION-PROTOCOL.md. Only the session history appendix adds value. |
| Restart checklist in SESSION-LOG.md | Identical to COORDINATION-PROTOCOL.md restart instructions |
| Session closeout template in SESSION-LOG.md | Identical to COORDINATION-PROTOCOL.md closeout template |
| Locked decisions table in SESSION-LOG.md | Mirror of COORDINATION-PROTOCOL.md with no additional data |
| Research queue snapshot in SESSION-LOG.md | Mirror of COORDINATION-PROTOCOL.md |
| Active handoff in SESSION-LOG.md | Mirror of COORDINATION-PROTOCOL.md |
| Current phase in SESSION-LOG.md | Mirror of COORDINATION-PROTOCOL.md |

### Actively Harmful
| Item | Why harmful |
|------|-----------|
| 4-file status synchronization requirement | Every update requires touching COORDINATION-PROTOCOL, SESSION-LOG, SESSION-HANDOFF, and PROJECT.md. Creates drift. Already drifted 3 times. |
| Sequential gate ordering (audit before validation) | Obscures the real critical path. V-1/V-2/V-3 are the bottleneck, not the Codex audit. |
| "Human updates this file only" rule on COORDINATION-PROTOCOL | Creates a bottleneck where Claude can identify drift but cannot fix it. Status corrections require human ceremony. |

---

## 7. Where the Workflow Is Losing Time

1. **V-1/V-2/V-3 stall (2+ days).** The three manual validations that block 3 remaining ADRs have not been started. The protocol has no mechanism to surface this as urgent. It just says `queued`.

2. **Codex audit never executed.** R-1 and R-5 have been `complete` since 2026-03-17. The combined audit prompt was prepared. It was never pasted into Codex. No audit output exists. The workflow is silently stalled at gate 2.

3. **Status synchronization tax.** Every state change requires 2-4 manual file edits. This was done correctly for the R-1/R-5 completion update (all 3 files updated). But OA-2/Q5 was missed (answered by R-1, still `queued` in protocol). The tax accumulates.

4. **Session recovery overhead.** Each new session reads COORDINATION-PROTOCOL (~500 lines), SESSION-LOG (~240 lines), and PROJECT.md (~80 lines) to reconstruct state. That's ~820 lines of context loading before any work begins. Much of this is template/boilerplate that doesn't change between sessions.

5. **Protocol revision cycles.** The COORDINATION-PROTOCOL itself was revised twice (initial creation, then Codex audit revision). SESSION-LOG was created, then restructured. These were necessary initially but consumed session time that could have gone to V-1/V-2/V-3.

---

## 8. Recommended Orchestration Simplification

### Merge to 3 Control Documents

| Document | Contents | Maintained by |
|----------|----------|---------------|
| **COORDINATION-PROTOCOL.md** (streamlined) | Locked decisions, research queue, assumptions to validate, active handoff, evidence standard. Remove: output contract templates (move to appendix or delete), restart checklists (redundant with quick resume), session closeout template (just append freeform notes), anti-drift rules (internalized — the rules worked, keep the decisions table). Target: ~250 lines. | Human updates status; Claude proposes changes inline. |
| **PROJECT.md** (as-is) | Quick resume, session summaries, project metadata. | Claude updates quick resume after each session. |
| **Session history** (append to COORDINATION-PROTOCOL or keep as slim SESSION-LOG) | One-line-per-session log. Date, tool, what happened, what changed. No mirrors, no snapshots, no templates. | Append-only after each session. |

### Delete or Archive
- **SESSION-LOG.md** — archive to `archive/SESSION-LOG-v1.md`. Its session history entries move to the slim log. Everything else was a mirror.
- **SESSION-HANDOFF.md** — archive after this session. It was a one-time snapshot for external orchestrator context. Do not maintain it.
- **WORKFLOW-RESET-AUDIT.md** — this file. Archive after acting on recommendations.

### Simplify Gates
- **Remove the Codex audit gate for reference research.** R-1 and R-5 are reference data. Accept them directly. If a finding later contradicts a locked decision, catch it during ADR drafting.
- **Keep the Codex audit gate for ADRs.** ADRs are decision documents. They warrant a second-tool review before locking.
- **Parallelize everything parallelizable.** V-1/V-2/V-3 can run now. Codex audit can run now. ADR drafting can start for any ADR whose validation data arrives.

### Reduce Status Locations to 1
- Research queue status lives in COORDINATION-PROTOCOL.md only.
- Active handoff lives in COORDINATION-PROTOCOL.md only.
- PROJECT.md quick resume is the human-readable summary. It does not duplicate queue state.

---

## 9. Minimum Control Surface

To run this project effectively without losing continuity, you need exactly:

| Artifact | Purpose | Lines (target) |
|----------|---------|----------------|
| `COORDINATION-PROTOCOL.md` | Locked decisions, research/validation queue, active handoff, evidence standard | ~250 |
| `PROJECT.md` | Quick resume, session summaries | ~80 |
| `DECISION-REGISTER.md` | Full decision/hypothesis registry (read-only reference) | as-is (~300) |
| `research/*.md` | Research outputs (R-1, R-5, future) | as-is |
| `ADR-*.md` | Architecture decision records | as-is |

Total operational overhead: ~330 lines of actively maintained state (down from ~2,900).

Everything else is reference material that can be read on demand but does not need to be kept in sync.

---

## 10. Recommended Immediate Next Action

**Start V-1 (Hevy rate limit test).**

This is the highest-leverage action because:
- It directly unblocks ADR-001 (Hevy sync strategy), the most architecturally consequential remaining decision.
- It is human-owned and has been queued for 2+ days.
- It requires ~30 minutes of manual API testing, not extensive research.
- H-06 has explicit success/failure thresholds already defined (>= 30 req/min = on-demand OK; 5-30 = caching; < 5 = batch-only; < 1 = reassess).
- No other task, audit, or protocol step needs to happen before this.

**In parallel:** Paste the existing Codex audit prompt for R-1/R-5 if you want the quality gate. But do not wait for it.

---

## 11. Confidence / Uncertainty

### Strongly Supported
- The orchestration system has more process than content (line counts are objective).
- Information is tripled across 3-4 files (direct comparison confirms).
- V-1/V-2/V-3 are the real critical path blockers (dependency analysis from ARCHITECTURE-SESSION-01-OUTPUT.md and COORDINATION-PROTOCOL.md).
- The Codex audit has not been executed (no output file exists).
- Status drift exists in 3 documented places (confirmed by cross-file comparison).
- SESSION-LOG.md is ~90% mirror content (direct comparison with COORDINATION-PROTOCOL.md).

### Uncertain
- Whether the human has specific reasons for not starting V-1/V-2/V-3 (e.g., waiting for hardware, travel, time constraints).
- Whether the Codex audit was attempted in an unrecorded session.
- Whether the process overhead is felt as burdensome by the human operator or is considered acceptable insurance.
- Whether reducing gates would introduce regressions in decision quality that the current system prevents.
