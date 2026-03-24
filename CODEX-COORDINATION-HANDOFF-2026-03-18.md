# CODEX COORDINATION HANDOFF

**Date:** 2026-03-18  
**Author:** Codex (GPT-5.4)  
**Purpose:** Give the new GPT coordination-planning chat a clean Codex-side summary of what work Codex already performed for Apex, what was never persisted beyond chat, and which existing workflow artifacts are now stale relative to the live project state.

---

## 1. What Codex Actually Did

### A. Protocol audit and tightening
Codex reviewed the original cross-tool coordination design and pushed for a more operational model:
- Human Operator as controller-of-record
- Claude as researcher/synthesizer/drafter, not executor
- no self-audit path
- split research tasks from manual validation tasks
- single `Active Handoff` block instead of duplicated next-prompt sections
- normalized statuses (`queued -> in_progress -> complete -> audited -> accepted/rework`)
- manual/file-mediated prompt transfer only
- no fake assumption of native hooks or automation

This guidance appears to have influenced the current shape of [COORDINATION-PROTOCOL.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/COORDINATION-PROTOCOL.md), but Codex did **not** persist a standalone markdown for that earlier review.

### B. R-1 prompt and handoff tightening
Codex tightened the R-1 execution framing around:
- RP data availability vs. licensing/usage as separate questions
- primary-source priority
- no schema/design drift
- no legal-advice framing
- no fallback into broad alternatives research

That prompt guidance was delivered in chat, not saved as its own file.

### C. Next-work-package design
Codex then chose the next highest-value Claude task as **R-5** and wrote the generation package that asked Claude to map the current Apex v1 client data flows from code:
- Room inventory
- SharedPreferences / encrypted prefs inventory
- WorkManager / scheduler inventory
- end-to-end client data flows
- freshness/invalidation rules
- architecture implications grounded in code

That package was also delivered in chat, not persisted as a standalone markdown.

### D. Research audits performed by Codex in chat
Codex later audited both research outputs in chat:
- [research/rp-volume-landmarks.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/research/rp-volume-landmarks.md)
- [research/v1-client-data-flows.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/research/v1-client-data-flows.md)

Key Codex conclusions from that chat audit:
- R-1 was materially sound, with only minor wording/metadata issues left.
- R-5 was materially sound and architecture-relevant.
- Neither research artifact required reopening locked architecture decisions.

Important limitation: **those Codex audits were not saved anywhere in the Apex workspace.** There is no persisted Codex audit output file for R-1 or R-5.

---

## 2. What Is Actually Persisted In Files

### File-backed research outputs
- [research/rp-volume-landmarks.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/research/rp-volume-landmarks.md)
- [research/v1-client-data-flows.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/research/v1-client-data-flows.md)

These are real, durable research artifacts and remain valid reference material.

### File-backed coordination artifacts
- [COORDINATION-PROTOCOL.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/COORDINATION-PROTOCOL.md)
- [SESSION-LOG.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/SESSION-LOG.md)
- [SESSION-HANDOFF.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/SESSION-HANDOFF.md)

These preserve the older coordination system state, including the assumption that the next required action was a Codex audit of R-1 and R-5.

### File-backed workflow critique authored today by Claude
- [WORKFLOW-RESET-AUDIT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/WORKFLOW-RESET-AUDIT.md)

This is **not** a Codex artifact. It is Claude’s orchestration critique and simplification proposal.

### File-backed current project state
- [PROJECT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/PROJECT.md)

As of 2026-03-18, this is the strongest single-file indicator of live project state.

---

## 3. What Was Chat-Only and Never Persisted

These Codex outputs existed in chat but do **not** currently exist as Apex markdown artifacts:

1. The original Codex audit of the coordination protocol
2. The Codex tightening pass for the `Active Handoff` block and R-1 execution prompt
3. The Codex-generated R-5 work package
4. The Codex audit outputs for R-1 and R-5

For the new GPT planning chat, this matters because the workspace by itself will misleadingly suggest the Codex audit never happened at all.

That is partly true and partly false:
- **False** at the conversation level: Codex did perform those audits in chat.
- **True** at the repository level: no durable Codex audit file was written, so the workspace has no persisted evidence of those reviews.

---

## 4. Current File-Backed State vs. Older Workflow State

### A. The older workflow artifacts still show a pending audit gate
The following files still frame the work as being stalled on the R-1/R-5 Codex audit step:
- [COORDINATION-PROTOCOL.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/COORDINATION-PROTOCOL.md)
- [SESSION-LOG.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/SESSION-LOG.md)
- [SESSION-HANDOFF.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/SESSION-HANDOFF.md)

Specific stale points still present:
- `OA-2` remains `queued` in the protocol even though R-1 answered it
- `Q5` remains `queued` in the protocol even though R-1 answered it
- `Active Handoff` still targets the R-1 + R-5 audit step

### B. The live project appears to have moved ahead of that workflow layer
[PROJECT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/PROJECT.md) now says:
- all 5 ADRs are drafted
- ADR-002 and ADR-004 are accepted
- ADR-001, ADR-003, ADR-005 are drafted with explicit assumptions pending validation
- the next real action is **H-06 rate-limit testing**, then **A-01 wearable inspection**

This means the old coordination layer and the current project summary are no longer in sync.

### C. Practical interpretation
The prior workflow system is no longer a reliable single source of truth.

For coordination planning **today**, the GPT chat should assume:
- R-1 and R-5 are completed reference research
- the historical audit gate around them is no longer the main driver of progress
- the live project state has advanced into ADR drafting
- the true blockers are the manual validations called out in [PROJECT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/PROJECT.md)

---

## 5. Codex View on the Research Outputs

### R-1
Codex’s prior assessment was:
- useful and materially sufficient for planning
- correctly scoped to availability/completeness/licensing
- did not require reopening any locked architecture decision
- had minor wording/source-metadata cleanup only

### R-5
Codex’s prior assessment was:
- high-value architecture baseline
- correctly identified durable outbound sync vs. non-durable inbound reads
- correctly surfaced documentation drift in the v1 client docs
- did not require rework before architecture planning could continue

### Important caveat
Because these Codex audits were not persisted, the new GPT planner should **not** treat them as formal accepted artifacts. It should treat them as prior expert judgment from Codex that can inform simplification of the workflow, but not as durable audited state unless it chooses to recreate that state in-file.

---

## 6. Codex View on the Coordination Workflow

The main Codex positions that still stand:

1. **Manual transfer realism was correct.** The protocol was right to avoid pretending Codex and Claude had native orchestration.
2. **Role separation was worth keeping.** Codex as controller/critic/prompt architect and Claude as heavy-lift researcher/drafter is a sound split.
3. **The workflow became too heavy once the initial uncertainty dropped.** The protocol design made sense during setup, but the mirror files and gate sequencing created overhead and drift.
4. **Reference research does not need the same ceremony as decision documents.** R-1 and R-5 were useful inputs, but the audit gate around them became less important than the real manual blockers.
5. **Current truth should now come from live project state, not frozen handoff machinery.** At this point, [PROJECT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/PROJECT.md) and the ADR set matter more than the older Active Handoff chain.

---

## 7. Recommended Source-of-Truth Order for the New GPT Planning Chat

If the new GPT coordination-planning chat needs to reconstruct Apex accurately, it should prioritize files in this order:

1. [PROJECT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/PROJECT.md)  
   Current highest-level project state

2. [ADR-001-hevy-abstraction-and-sync-strategy.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/ADR-001-hevy-abstraction-and-sync-strategy.md) through [ADR-005-health-data-source-abstraction.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/ADR-005-health-data-source-abstraction.md)  
   Current decision work product

3. [research/rp-volume-landmarks.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/research/rp-volume-landmarks.md) and [research/v1-client-data-flows.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/research/v1-client-data-flows.md)  
   Reference research that fed architecture work

4. [WORKFLOW-RESET-AUDIT.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/WORKFLOW-RESET-AUDIT.md)  
   Claude’s critique of the old orchestration system

5. [COORDINATION-PROTOCOL.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/COORDINATION-PROTOCOL.md), [SESSION-LOG.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/SESSION-LOG.md), and [SESSION-HANDOFF.md](C:/Users/tyler/Documents/Claude%20Projects/Apex/SESSION-HANDOFF.md)  
   Historical coordination context only; do not assume they still represent live truth

---

## 8. Bottom Line for the New Planning Chat

If the GPT coordination-planning chat asks, “What prior Codex work should I respect?”, the answer is:

- Respect the **role split** and the **manual-transfer realism** from the earlier Codex protocol guidance.
- Treat the R-1 and R-5 research outputs as **credible reference inputs**.
- Do **not** assume the older pending-audit workflow still reflects the live project.
- Start from the fact that the project has already moved into **ADR drafting**, and that the remaining blockers are manual validations, not orchestration mechanics.

If the GPT chat wants a durable replacement for the old coordination flow, it should rebuild from the **actual 2026-03-18 project state**, not from the older `Active Handoff` chain.
