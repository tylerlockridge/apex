# GSD Phase 3

**Phase:** Workout Generation MVP
**Status:** Complete — 2026-03-22. All audit gaps resolved: generation flow wired, progression card added, readiness/training-load contract unified via ReadinessPayloadBuilder.
**Source:** IMPLEMENTATION-ROADMAP.md Phase 3; ADR-001, ADR-003, ADR-004; Decision Register D-05, D-10a, D-10b, D-11, D-14, D-17, P-05
**Target repos:** Health-Platform-Desktop and Apex
**Estimated effort:** 3-5 sessions
**Hard prerequisites:** Phase 1 complete and live enough to provide cached Hevy workout history + seeded override data; Phase 2 readiness engine complete and exposing the locked readiness payload shape

---

## 1. Objective and Scope

**Objective:** Deliver the core Apex v2 MVP feature: generate a personalized workout on the server from real Hevy history plus client readiness context, then let the user review and accept or reject it in the Android app.

**In scope:**
- Server-side progression summary from actual Hevy workout history
- Server-side workout generation endpoint and persistence in migration-009 tables
- 2-for-2 weight suggestion logic, weekly volume tracking, and MRV proximity flagging
- Apex-side generation entry point, review screen, and accept/reject flow
- Training-load input wired into the readiness engine using the server-side progression summary
- Path B fallback UX by default: generated workout displayed in Apex with manual Hevy execution
- Path A push-to-Hevy branch only if VD-1 is confirmed positive during execution

**Out of scope:**
- Full mesocycle planning or periodization
- Adaptive deload logic
- Nutrition, supplements, coaching, or empty-table activation
- New health-provider work beyond the Package 0B seam
- A full in-app workout logging alternative to Hevy
- Re-opening the progression source-of-truth decision

---

## 2. Why Phase 3 Is the MVP Core

This phase is where the project stops being "Apex plus synced data" and becomes "Apex as a training companion." The upstream work only matters if it produces a usable daily loop:

1. Health data sync and readiness context from Apex
2. Actual workout history and exercise attribution from the server
3. A generated routine with understandable reasoning
4. A user-controlled review step before execution

Phase 3 is therefore the main value-delivery layer, but it only works if it stays disciplined:
- progression uses actual Hevy data only
- generation never calls Hevy live when cached data should exist
- review stays user-in-the-loop
- Path B remains shippable even if VD-1 is still unresolved

---

## 3. Current State (Code-Grounded)

**Server state in Health-Platform-Desktop:**
- `src/routes/workouts.js` already serves workout sessions, workout stats, and exercise history from `workout_sessions` and `workout_sets`
- `src/routes/sync.js` already syncs Hevy workouts into the database
- `src/services/hevyClient.js` already performs cached exercise-template reads and preserves `hevy_routine_id` from Hevy workout responses
- Migration 009 already created the schema surfaces Phase 3 needs: `generated_routines`, `generated_routine_exercises`, `progression_snapshots`, `exercise_muscle_overrides`, `workout_sessions.hevy_routine_id`

**Client state in Apex:**
- `ActivityScreen.kt` is already the workout-focused tab: summary card, recent workouts, pull-to-refresh, and manual Hevy sync trigger
- `ActivityViewModel.kt` already talks to `ServerApiClient` for workouts and workout stats
- `ServerApiClient.kt` currently has no generation or progression-summary methods
- `MainActivity.kt` has only the four main destinations plus QR scan; no generation review route exists yet

**Architectural constraints already locked:**
- D-14: generation stays server-side
- D-17: actual Hevy data is the only progression source of truth
- ADR-004: generated routines are historical suggestions, not progression state
- D-11: user review stays mandatory
- VD-1 only changes whether push-to-Hevy exists; it does not block the base MVP flow

---

## 4. Task Decomposition

### Task 1: Progression Summary Surface

**Purpose:** Turn actual Hevy history into a reusable summary that both the generator and the client can consume.

**Files/components likely touched:**
- Modified: `src/routes/workouts.js`
- New: `src/services/progressionEngine.js`
- Modified: `src/index.js`
- Modified: `src/__tests__/workouts.test.js`

**What it produces:**
- `GET /api/workouts/progression/summary`
- Trailing-7-day volume by muscle group
- Per-muscle landmark status versus RP ranges
- Per-exercise 2-for-2 progression signals
- A single training-load score/input the client can use in the readiness engine
- Optional persistence/update of `progression_snapshots`

**Completion criteria:**
- Summary is computed from `workout_sessions` + `workout_sets` + `exercise_muscle_overrides` only
- No query depends on `generated_routines`
- Empty-history case is explicit and machine-handleable

### Task 2: Generation Endpoint and Persistence

**Purpose:** Generate a routine from readiness context plus actual training history and persist it as a historical suggestion.

**Files/components likely touched:**
- New: `src/routes/generatedRoutines.js`
- New: `src/schemas/generatedRoutines.js`
- New: `src/services/workoutGenerator.js`
- Modified: `src/index.js`
- New: `src/__tests__/generatedRoutines.test.js`

**What it produces:**
- `POST /api/generated-routines`
- Reads from cached workout history and exercise template/override data
- Persists `generated_routines` and `generated_routine_exercises`
- Returns exercise-level reasoning, suggested targets, and warning flags
- Fails clearly if no cached Hevy history exists

**Completion criteria:**
- Generation does not call Hevy live for personalization
- Response includes reasoning per exercise and a summary rationale
- Persisted generated routine can be re-fetched for review

### Task 3: Lifecycle and Conditional Push Path

**Purpose:** Close the review loop without letting VD-1 stall the main feature.

**Files/components likely touched:**
- Modified: `src/routes/generatedRoutines.js`
- Modified: `src/schemas/generatedRoutines.js`
- Conditional: `src/services/hevyClient.js`
- Conditional: `src/__tests__/hevyClient.test.js`

**What it produces:**
- `POST /api/generated-routines/:id/decision` for `accepted` / `rejected`
- Path B base behavior:
  - accepted routine stays in Apex review history
  - user executes manually in Hevy
- Path A conditional behavior if VD-1 is positive:
  - push endpoint/button
  - store resulting `hevy_routine_id`
  - allow `pushed` lifecycle state

**Completion criteria:**
- Path B is fully usable with no VD-1 dependency
- Path A is additive only if validated during execution

### Task 4: Apex Generation Entry and Review Flow

**Purpose:** Add the client-side loop for requesting, viewing, and deciding on generated workouts.

**Files/components likely touched:**
- Modified: `app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt`
- Modified: `app/src/main/java/com/healthplatform/sync/ui/ActivityScreen.kt`
- Modified: `app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt`
- New: `app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineScreen.kt`
- New: `app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineViewModel.kt`
- Modified: `app/src/test/java/com/healthplatform/sync/ui/ActivityViewModelTest.kt`
- New: `app/src/test/java/com/healthplatform/sync/ui/GeneratedRoutineViewModelTest.kt`

**What it produces:**
- "Generate workout" entry point from the Activity tab
- Review screen showing generated routine, per-exercise reasoning, and warnings
- Accept / reject actions
- Path B copy/instructions for manual execution in Hevy
- Conditional Path A button only if VD-1 is positive and wired

**Completion criteria:**
- Generation request/response is fully modeled in `ServerApiClient`
- Review screen handles loading, error, empty-history, and success states cleanly
- Activity tab remains functional if generation fails

### Task 5: Training-Load Readiness Integration and Volume Display

**Purpose:** Make the readiness engine aware of recent training load and expose the progression summary to the user.

**Files/components likely touched:**
- Modified: Phase-2 readiness files in Apex
- Modified: `DashboardViewModel.kt`
- Modified: `DashboardScreen.kt`
- Modified: `ActivityScreen.kt`
- Modified: `ActivityViewModel.kt`

**What it produces:**
- Training-load input activated in the readiness engine
- Dashboard readiness breakdown includes training load
- Activity tab shows a volume/progression card or section grounded in the same summary returned by the server

**Completion criteria:**
- Training-load input is fed by the progression summary, not a local guess
- Dashboard and Activity agree on the same upstream progression data

### Task 6: Tests and End-to-End Verification

**Purpose:** Keep Phase 3 from becoming the place where multiple upstream assumptions silently break.

**Required coverage:**
- no-history error path
- successful generation with persisted routine + exercises
- accepted vs rejected lifecycle updates
- Path B fallback instructions
- Path A push flow if VD-1 is positive
- progression summary correctness for 2-for-2 and MRV warning cases
- Apex viewmodel coverage for generate/review/decision flows

---

## 5. Recommended Execution Order

```
Server foundation:
  Task 1 (progression summary)
    -> Task 2 (generation endpoint + persistence)
      -> Task 3 (decision lifecycle; Path A only if VD-1 positive)

Client track:
  Task 4 (entry + review flow)
    -> Task 5 (training-load readiness integration + volume display)
      -> Task 6 (tests and final verification)
```

Task 4 can begin once the request/response contract from Task 2 is stable, even before Path A is known.

---

## 6. Validation Interaction

### VD-1 (Hevy Routine Creation)

| If VD-1 is positive during execution | If VD-1 is negative or still unresolved |
|-------------------------------------|-----------------------------------------|
| Add push endpoint/button and store `hevy_routine_id` | Ship Path B only |
| Allow lifecycle state `pushed` | Accept/reject only |
| Enable prescribed-to-actual linkage | No linkage at MVP; acceptable per ADR-004 |

### H-06 (Hevy Rate Limits)

| If H-06 is already resolved | If H-06 is still operating on Tier-2 defaults |
|-----------------------------|-----------------------------------------------|
| Tune freshness copy/thresholds to measured behavior | Treat slightly stale cache as acceptable and show warnings conservatively |
| Refine staleness messaging in Activity/review flow | Avoid blocking generation unless history is missing entirely |

**Neither validation blocks Phase 3 start.** VD-1 changes only the push branch. H-06 affects freshness UX and confidence thresholds.

---

## 7. Definition of Done

Phase 3 is complete when ALL of the following are true:

- [x] Apex can request a generated workout from the server
- [x] Generation reads actual Hevy history from cache-backed server data, not live API dependency
- [x] Generated routine persists to `generated_routines` and `generated_routine_exercises`
- [x] Response includes per-exercise reasoning and progression-based target suggestions
- [x] Activity flow presents a review screen with accept/reject
- [x] Empty-history case returns a meaningful error and guidance
- [x] Volume/progression summary is visible to the user
- [x] Training-load input is active in the readiness engine
- [x] MRV proximity warnings and 2-for-2 suggestions surface in the returned/output model
- [x] Path B is fully usable with no VD-1 dependency
- [ ] Path A is added only if VD-1 is confirmed positive — **VD-1 unresolved, Path B shipped**
- [x] Server and client tests cover the main flow
- [x] CI/checks are green in both repos

---

## 8. Explicit Non-Goals

- No full programming engine
- No mesocycle auto-builder
- No Hevy replacement UI
- No nutrition/supplement/coaching activation
- No client-side recomputation of progression from raw workouts
- No reopening of D-17 or ADR-004

---

## 9. Main Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Hevy workout cache is empty or stale when generation is requested | High | Make `NO_WORKOUT_HISTORY` an explicit error path; verify real history as a pre-exit check for Phase 1/Phase 3 |
| Override seed accuracy is insufficient for muscle attribution | High | Treat override quality as a hard verification point before trusting MRV/volume outputs |
| Phase 2 readiness payload and Phase 3 generation input drift apart | Medium | Reuse the locked Phase-2 payload shape directly; do not redesign it inside Phase 3 |
| Client review scope expands into full workout editing | Medium | Keep MVP to review + accept/reject + Path B manual execution |
| VD-1 uncertainty causes last-minute push-path churn | Medium | Build Path B first and treat Path A as a contained optional slice |

---

## 10. Handoff to Phase 4

Phase 4 should not invent new feature scope. It should close the loop on what Phase 3 proved:
- apply any H-06 freshness tuning
- activate the Path A push slice if VD-1 is positive but not yet landed
- tune readiness/training-load weighting from real use
- run end-to-end daily-use verification

Phase 3 succeeds if it makes Apex usable as a daily "generate → review → train → sync back" companion, even before any post-MVP polish.
