# Validation Runbook

**Purpose:** Central runbook for the three remaining manual validations that affect Apex v2 tuning and optional paths.

**Current policy:** H-06, A-01, and VD-1 do **not** block implementation from starting. They run in parallel with execution and become binding only at their latest responsible moment from `IMPLEMENTATION-ROADMAP.md`.

---

## 1. H-06 — Hevy API Rate Limit Validation

**Owner:** Tyler
**Affects:** Phase 1 sync interval/backoff config
**Latest responsible moment:** End of Phase 1, before Phase 3 exits

### Goal

Measure the practical Hevy API request threshold so the adapter sync tier is tuned with evidence instead of assumption.

### Preconditions

- Active Hevy Pro subscription and working API key
- A disposable or low-risk environment for repeated API calls
- Logging of response status, latency, and any `Retry-After` headers

### Procedure

1. Start at `1 request / 10 seconds`.
2. Call the same stable read endpoint repeatedly. Prefer:
   - `GET /v1/workouts`
   - optionally repeat with `GET /v1/exercise_templates`
3. Increase rate in steps:
   - `1 / 10s`
   - `1 / 5s`
   - `1 / 2s`
   - `1 / 1s`
4. Hold each rate long enough to observe at least 20-30 requests.
5. Record:
   - first 429 occurrence
   - any `Retry-After` value
   - whether 5xx/timeouts appear before 429
   - whether rate limit recovers cleanly after waiting
6. Repeat once on a separate day/time window if the result looks borderline.

### Classification Thresholds

| Result | Interpretation | Operational decision |
|--------|----------------|---------------------|
| `>= 30 req/min` | Fully workable | Tier 1 possible; on-demand reads feasible |
| `5-30 req/min` | Workable with caching | Stay with Tier 2 defaults |
| `< 5 req/min` | Severely constrained | Batch-oriented sync only |
| `< 1 req/min` | Blocking for real-time use | Reassess Hevy dependency / Phase 3 UX |

### What changes operationally

- `>= 30 req/min`: Phase 1 can relax toward Tier 1 polling/manual behavior.
- `5-30 req/min`: current Tier 2 plan remains correct.
- `< 5 req/min`: keep Phase 1 architecture but ship conservative/batch defaults.
- `< 1 req/min`: Phase 3 generation UX and freshness promises need rework before ship.

### What this does **not** block

- Package 0A
- Package 0B
- Phase 1 start
- Phase 2 start

---

## 2. A-01 — Wearable Health Connect Data Validation

**Owner:** Tyler
**Affects:** Phase 2 HRV weight default
**Latest responsible moment:** End of Phase 2, before readiness ships with non-zero HRV weighting

### Goal

Confirm whether Tyler's actual wearable/device setup writes usable HRV data to Health Connect.

### Preconditions

- Specific wearable identified
- Health Connect app installed
- Apex already granted Health Connect permissions
- At least one overnight sync window completed with the wearable

### Procedure

1. Record the wearable model and companion app.
2. In Health Connect, inspect whether these data types exist and are current:
   - sleep stages / sleep session
   - HRV
   - resting heart rate if available
3. In Apex, confirm that recent sleep/HRV values appear in the dashboard snapshot path after sync.
4. Repeat on at least two different mornings to rule out one-off missing data.

### Pass / Fail Threshold

| Result | Interpretation | Operational decision |
|--------|----------------|---------------------|
| HRV present and current within 24h | Pass | Enable non-zero HRV weight in Phase 2 config |
| Sleep present but HRV absent/inconsistent | Partial | Keep readiness engine; HRV weight stays `0` |
| Sleep and HRV both unreliable in HC | Fail for HRV path | Keep Phase 2 readiness limited to BP + sleep; monitor H-04 for provider fallback need |

`24h` is the meaningful threshold because ADR-003 excludes inputs older than 24 hours from readiness.

### What changes operationally

- Pass: Phase 2 default HRV weight can move to `0.25`.
- Partial/fail: HRV stays disabled; no architecture change required.

### What this does **not** block

- Package 0B
- Phase 2 start
- Phase 1 execution
- overall v2 forward progress

---

## 3. VD-1 — Hevy Routine Creation Validation

**Owner:** Tyler
**Affects:** Phase 3 push-to-Hevy path and prescribed-to-actual linkage
**Latest responsible moment:** During Phase 3

### Goal

Confirm whether Apex can create a routine in Hevy and see that exact routine ID round-trip into completed workout history.

### Preconditions

- Working Hevy API key
- A test routine body ready to send
- Ability to complete one real or disposable test workout in Hevy

### Procedure

1. Send `POST /v1/routines` with a minimal valid routine:
   - title
   - one exercise
   - one set
2. Record the returned routine ID if successful.
3. Open Hevy, start the created routine, and complete the workout.
4. Call `GET /v1/workouts?page=1&pageSize=1`.
5. Confirm the completed workout's `routine_id` matches the created routine ID.
6. Optional follow-up:
   - test `PUT /v1/routines/{id}` if create succeeds

### Pass / Fail Threshold

| Result | Interpretation | Operational decision |
|--------|----------------|---------------------|
| POST succeeds and completed workout echoes the same `routine_id` | Pass | Phase 3 can ship push-to-Hevy path |
| Read API works but create/update fails or no round-trip ID | Fail | Phase 3 ships display-only/manual-start path |

### What changes operationally

- Pass:
  - `generated_routines.status = 'pushed'` becomes active
  - push button can ship in Phase 3
  - prescribed-to-actual linkage is available
- Fail:
  - `hevy_routine_id` remains nullable and mostly unused
  - user starts the routine in Hevy manually
  - progression remains correct because it uses actuals only

### What this does **not** block

- Migration 009 schema finalization
- Phase 1 completion
- Phase 3 start

---

## 4. Prompt Pack

### 4.1 Claude Prompt — Phase 1 Implementation

Use this in the **Health-Platform-Desktop** repo:

```text
Read these files first:

1. IMPLEMENTATION-ROADMAP.md
2. ADR-001-hevy-abstraction-and-sync-strategy.md
3. ADR-002-server-schema-migration-strategy.md
4. ADR-004-workout-generation-reconciliation-model.md
5. GSD-PHASE-1.md
6. IMPL-BRIEF-PHASE-1.md
7. VALIDATION-RUNBOOK.md

Task:
Execute Phase 1 end-to-end in the Health-Platform-Desktop repo.

Do this in order:
1. Reconcile any remaining ambiguity in the Phase 1 docs before coding, but do not broaden scope.
2. Implement migration 009, the Hevy adapter/cache work, the route refactor, and tests.
3. Keep the implementation behavior-preserving for the Apex client.
4. Run the relevant server test/lint/build checks.
5. Update continuity docs with:
   - what landed
   - what remains tied to H-06 and VD-1
6. Return:
   - files changed
   - checks run and results
   - any residual risks or manual follow-ups

Constraints:
- Do not expand into Phase 2 or Phase 3 work.
- Do not reopen settled architecture without direct contradiction evidence.
- Treat H-06 and VD-1 as tuning/optional-path validations, not phase-start blockers.
```

### 4.2 Claude Prompt — Phase 2 Implementation

Use this in the **Apex** repo for the client work, and in **Health-Platform-Desktop** for the empty migrations if you split execution by repo.

```text
Read these files first:

1. IMPLEMENTATION-ROADMAP.md
2. ADR-002-server-schema-migration-strategy.md
3. ADR-003-readiness-scoring-input-architecture.md
4. ADR-005-health-data-source-abstraction.md
5. GSD-PHASE-2.md
6. IMPL-BRIEF-PHASE-2.md
7. VALIDATION-RUNBOOK.md
8. app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt
9. app/src/main/java/com/healthplatform/sync/ui/DashboardScreen.kt
10. app/src/test/java/com/healthplatform/sync/ui/DashboardViewModelTest.kt

Task:
Execute Phase 2 end-to-end.

Do this in order:
1. Implement server migrations 010-012 as schema-only, idempotent migrations.
2. Implement the Apex readiness engine and config store.
3. Refactor the dashboard to use the engine and show per-input readiness details.
4. Add/update tests.
5. Run the relevant client/server checks.
6. Update continuity docs with:
   - landed migrations
   - readiness output shape
   - whether A-01 has changed the default HRV weight
7. Return:
   - files changed
   - checks run and results
   - residual risks

Constraints:
- No feature activation for nutrition, supplements, or coaching.
- No subjective-feel capture UI.
- No training-load readiness activation.
- Keep the dashboard prefs-backed in this phase; do not turn this into a broader repository refactor.
```

### 4.3 Codex Prompt Template — Post-Implementation Audit

Use this after Claude finishes either phase:

```text
Read the roadmap, the governing ADRs for the phase, the new phase docs, and the actual repo diff.

Task:
Audit the implementation against package/phase scope.

Return:
1. findings first, ordered by severity
2. scope drift, if any
3. behavioral-regression risks
4. missing tests
5. whether the phase is acceptable as-is or needs one revision pass

Constraints:
- only real contradictions or risks
- no re-litigating accepted architecture
- optimize for forward progress
```
