# ADR Acceptance Record

**Date:** 2026-03-19
**Purpose:** Capture the current Codex acceptance recommendation for the ADRs that remained in "acceptance-ready" state after planning stabilized.
**Source inputs:** `IMPLEMENTATION-ROADMAP.md`, `WORKFLOW-RESET-AUDIT.md`, `PROJECT.md`, Package 0B implementation state, and the Phase 1-3 planning artifacts.

---

## Acceptance Status

| ADR | Status | Accepted by | Date | Note |
|-----|--------|-------------|------|------|
| ADR-001 | **Accepted** | Tyler (human controller-of-record) | 2026-03-23 | Accepted for MVP as implemented. VD-1 push path deferred to post-MVP. H-06 validated >= 30 req/min. |
| ADR-003 | **Accepted** | Tyler (human controller-of-record) | 2026-03-23 | Accepted. A-01 inconclusive; HRV weight ships at 0.0, config-ready for activation. |
| ADR-005 | **Accepted** | Tyler (human controller-of-record) | 2026-03-23 | Accepted. Provider interface proven in Package 0B. Alternative providers post-MVP. |

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

## Governance Complete

All three ADRs were accepted by Tyler on 2026-03-23 as part of Phase 4 Task 8 (ADR Governance Close-Out). The ADR files themselves have been updated from "Proposed for acceptance" to "Accepted" with the acceptance date and controller-of-record attribution. No further governance action is required for MVP ship.
