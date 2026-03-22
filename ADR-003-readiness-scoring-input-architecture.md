# ADR-003: Readiness Scoring Input Architecture

## Status

**Proposed for acceptance** — 2026-03-18. The architecture decision (weighted pluggable inputs, configurable weights, graceful degradation, client-side computation) is complete. A-01 determines initial HRV weight (0.25 or 0). H-02/H-03 tune weights during MVP. Neither blocks this ADR.

## Context

Readiness scoring is computed client-side (D-03), uses multiple health inputs with configurable weights, must function without any single input, must handle stale data gracefully (D-08), and must be treated as an unsolved research problem that will iterate (Post-Gate §3.1).

The current v1 app already computes a simple readiness score in `DashboardViewModel.computeReadiness()` using cached BP, sleep, and HRV values from SharedPreferences. R-5 confirmed this is a local computation with no server involvement.

### What is known

| Fact | Status | Source |
|------|--------|--------|
| Client computes readiness from cached prefs (BP, sleep, HRV) | Confirmed | R-5, DashboardViewModel.kt |
| Readiness feeds into workout generation context | Confirmed | ADR-004 |
| HRV data availability depends on Tyler's wearable | **Unvalidated** (A-01) | ARCHITECTURE-ASSUMPTIONS.md |
| HRV predicts strength readiness (r > 0.25) | **Unvalidated** (H-02) | MVP validation |
| Simple readiness matches subjective feel (> 70%) | **Unvalidated** (H-03) | MVP validation |
| HC data may have staleness issues | **Unvalidated** (H-04) | MVP monitoring |
| Client has no repository layer; VMs read prefs directly | Confirmed | R-5 |
| Dashboard readiness currently uses hardcoded thresholds | Confirmed | R-5, DashboardViewModel |

### Planning traceability

| ID | Constraint | Role |
|----|-----------|------|
| D-03 | Readiness computed client-side | Compute placement |
| D-05 | All recommendations show reasoning | Readiness must explain itself |
| D-08 | HC with staleness display | Stale inputs must be surfaced |
| H-01 | Readiness-to-Hevy loop is differentiator | Readiness must be useful enough to drive workout adjustments |
| H-02 | HRV predicts strength readiness | Determines whether HRV is a meaningful input |
| H-03 | Simple algorithm matches subjective feel | Validates the approach |
| H-04 | HC delivers reliable data | Data quality assumption |
| A-01 | Wearable writes HRV to HC | Input availability |

---

## Decision

### 1. Input model — pluggable, weighted, gracefully degrading

Readiness is computed from a set of **health signal inputs**, each with:
- A current value (from HC sync → server → client cache, or from local prefs)
- A staleness timestamp (when the value was last updated)
- A weight (0.0 to 1.0, configurable)
- A scoring function (maps raw value to a 0-100 contribution)

**Planned inputs for MVP:**

| Input | Source | Weight (initial) | Degradation if missing |
|-------|--------|-------------------|----------------------|
| Sleep duration + quality | HC sleep records via sync cache | 0.30 | Score uses remaining inputs, re-weighted |
| Blood pressure | HC BP records via sync cache | 0.20 | Score uses remaining inputs, re-weighted |
| HRV (rMSSD) | HC HRV records via sync cache | 0.25 (if A-01 confirms availability) | **Dropped entirely if A-01 negative.** Score re-weights across sleep + BP + subjective. |
| Subjective feel | User input (optional daily prompt) | 0.15 | Score uses remaining inputs; never blocks |
| Training load (recent volume vs MRV proximity) | Computed from cached workout history | 0.10 | Excluded until workout generation is active |

**If A-01 is negative (no HRV available):**

| Input | Adjusted weight |
|-------|----------------|
| Sleep | 0.40 |
| Blood pressure | 0.25 |
| Subjective feel | 0.25 |
| Training load | 0.10 |

The algorithm works without HRV. HRV improves precision if available, but is not a prerequisite.

### 2. Weight configuration — stored, not hardcoded

Weights are stored in a local configuration object (SharedPreferences or a dedicated Room table). They are:
- Initialized to defaults on first install
- Modifiable by the system as H-02/H-03 validation produces data
- Not user-editable in MVP (users don't tune readiness weights manually)
- Synced to server as part of user profile backup (future)

This allows the algorithm to evolve without code changes. A weight update is a data change, not an app release.

### 3. Staleness handling

Each input carries a `lastUpdatedAt` timestamp from the sync cache.

| Staleness | Behavior |
|-----------|----------|
| < 12 hours | Normal contribution |
| 12-24 hours | Weight reduced by 50%; readiness card shows "data from yesterday" |
| > 24 hours | Input excluded from calculation; readiness card shows which inputs are stale |
| All inputs stale > 24h | Readiness score not displayed; card shows "sync to update readiness" |

Staleness thresholds are configurable (same config store as weights). D-08 is satisfied: stale data is surfaced, not silently used.

### 4. Scoring functions — isolated, testable, replaceable

Each input has a dedicated scoring function that maps a raw value to a 0-100 contribution. The specific function shapes (linear, threshold-based, deviation-from-baseline) are implementation decisions tuned during MVP per H-03 validation.

The architecture decision is structural: scoring functions are isolated per-input, testable in isolation, and replaceable without modifying the aggregation logic. Swapping a scoring function is a single-unit change, not a system change.

### 5. Aggregation and output

```
readiness_score = sum(input_i.score * input_i.effective_weight) / sum(input_i.effective_weight)
```

Where `effective_weight` is 0 for missing/excluded inputs and `weight * staleness_factor` for available inputs.

Output is a 0-100 integer with three semantic bands:
- 80-100: "Good to go" — full-intensity training appropriate
- 50-79: "Moderate" — reduce volume or intensity
- 0-49: "Recovery day" — deload or rest

These bands and their labels are configurable. D-05 is satisfied: the readiness card shows which inputs contributed and their individual scores, not just the aggregate.

### 6. Integration with workout generation

The client sends readiness context to the server as part of the workout generation request (ADR-004). The context includes:
- Aggregate readiness score
- Per-input scores and staleness flags
- Which inputs were excluded and why

The server uses this to adjust workout parameters (volume, intensity, exercise selection). The server does not recompute readiness — it trusts the client's computation. This is consistent with D-03 (client owns readiness).

### 7. Relationship to existing v1 readiness

The v1 app already computes readiness client-side from cached prefs (R-5). The v2 architecture preserves this pattern (local computation, prefs-based inputs) but makes weights configurable and scoring functions replaceable. The specific refactoring of `DashboardViewModel.computeReadiness()` into a testable computation unit is an implementation task, not an architecture decision.

---

## Alternatives Considered

| Alternative | Why rejected |
|------------|-------------|
| **Server-side readiness computation** | Violates D-03 (client-first for readiness). Readiness must work offline. Adding a server dependency for a core trust feature is unacceptable. |
| **Fixed algorithm with hardcoded weights** | Violates Post-Gate §3.1 (readiness is an unsolved research problem). Weights must be tunable as H-02/H-03 produce data. Hardcoded weights require app releases to adjust. |
| **HRV as a required input** | A-01 unvalidated. If wearable doesn't write HRV to HC, readiness breaks entirely. Graceful degradation is required by the uncertainty. |
| **User-configurable weights at MVP** | Premature. Users cannot meaningfully tune readiness weights without understanding the algorithm. System tuning based on H-02/H-03 data is the right first step. User configurability is a post-MVP consideration. |
| **Rules-based system (if-then thresholds instead of weighted scoring)** | Less composable. Adding a new input requires writing new rules and testing interactions. Weighted scoring is additive — new inputs slot in with a weight. Rules-based systems become brittle as input count grows. |

---

## Consequences

- The scoring algorithm is data-driven, not code-driven. Tuning requires no app release.
- HRV is architecturally optional. A-01 determines initial weight; H-02 determines whether it stays.
- Every readiness display shows its inputs (D-05 satisfied).
- Stale data is visible, not hidden (D-08 satisfied).
- The server trusts client readiness and does not recompute it.

## Validation Required

| Item | What it determines | Timing |
|------|-------------------|--------|
| A-01 | Whether HRV is an available input | Before enabling non-zero HRV weight in Phase 2 defaults |
| H-02 | Whether HRV weight should be > 0 long-term | During MVP — 4-week observation |
| H-03 | Whether the overall algorithm is useful | During MVP — 4-week subjective match |
| H-04 | Whether HC data is reliable enough | During MVP — 2-week monitoring |

## Risks

- **A-01 negative (no HRV):** Algorithm works but loses the highest-signal input for physical readiness. Mitigation: sleep and subjective feel partially compensate. H-01 (readiness-to-Hevy differentiator) becomes harder to validate.
- **H-03 negative (algorithm doesn't match feel):** Weights need retuning. Worst case: readiness feature is demoted to informational display rather than workout-driving input. Architecture supports this gracefully — just reduce readiness weight in the generation request.
- **All inputs stale simultaneously:** User who doesn't wear their device and doesn't sync sees no readiness score. This is correct behavior, not a failure. Workout generation proceeds without readiness context.
