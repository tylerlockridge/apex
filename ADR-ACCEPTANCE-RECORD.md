# ADR Acceptance Record

**Date:** 2026-03-19
**Purpose:** Capture the current Codex acceptance recommendation for the ADRs that remained in "acceptance-ready" state after planning stabilized.
**Source inputs:** `IMPLEMENTATION-ROADMAP.md`, `WORKFLOW-RESET-AUDIT.md`, `PROJECT.md`, Package 0B implementation state, and the Phase 1-3 planning artifacts.

---

## Current Recommendation

| ADR | Current repo status | Codex recommendation | Note |
|-----|---------------------|----------------------|------|
| ADR-001 | acceptance-ready | Accept with note | H-06 and VD-1 still affect sync-tier config and optional push behavior, but not the architecture |
| ADR-003 | acceptance-ready | Accept with note | A-01 still affects the initial HRV weight/config, not the readiness architecture |
| ADR-005 | acceptance-ready | Accept with note | Package 0B has already proven the seam in code; A-01 does not block the provider abstraction |

---

## Notes by ADR

### ADR-001: Hevy Abstraction and Sync Strategy

**Recommendation:** Accept with note

**Why:**
- Phase 1 planning and current server code both align with the abstraction/caching direction.
- H-06 changes sync-tier tuning, not the architectural boundary.
- VD-1 changes only the optional push path, not the Hevy abstraction itself.

**Remaining validation-linked note:**
- H-06 still determines the final sync interval/backoff constants.

### ADR-003: Readiness Scoring Input Architecture

**Recommendation:** Accept with note

**Why:**
- Phase 2 and Phase 3 planning now depend on a stable readiness payload contract.
- The architecture already treats missing inputs and configurable weighting as first-class concerns.
- A-01 only controls whether HRV starts at a non-zero weight.

**Remaining validation-linked note:**
- A-01 remains a config/input validation before enabling the intended initial HRV weighting.

### ADR-005: Health Data Source Abstraction

**Recommendation:** Accept with note

**Why:**
- Package 0B is implemented in the Apex codebase.
- `SyncWorker` now depends on the provider seam rather than directly on `HealthConnectReader`.
- The abstraction is now part of the live code shape, not just planning intent.

**Remaining validation-linked note:**
- Result-metadata expansion remains intentionally deferred to later work; that does not change the base abstraction acceptance decision.

---

## What This Does Not Do

- It does **not** mark these ADRs as human-approved.
- It does **not** convert H-06, A-01, or VD-1 into architecture blockers.
- It does **not** reopen any settled decisions absent contradictory repo evidence.

---

## Human Signoff Action

When ready, the human controller-of-record can mark:
- `ADR-001-hevy-abstraction-and-sync-strategy.md` as accepted
- `ADR-003-readiness-scoring-input-architecture.md` as accepted
- `ADR-005-health-data-source-abstraction.md` as accepted

That signoff should be treated as governance cleanup, not as a prerequisite for continuing implementation work already scoped by the roadmap.
