# Implementation Brief: Phase 3

**Phase:** Workout Generation MVP
**Target repos:** Health-Platform-Desktop and Apex
**Server stack:** Node.js / Express / PostgreSQL / raw SQL migrations already in place through 009
**Client stack:** Kotlin / Jetpack Compose / Retrofit / ViewModel
**Test frameworks:** Vitest (server), JUnit/Robolectric (client)

---

## 1. Objective

Implement the first usable workout-generation loop:
- Apex sends readiness context and session intent
- the server reads real Hevy history and exercise-attribution data
- the server returns a generated routine with reasoning
- Apex presents a review flow and records accept/reject
- Path B ships by default; Path A is conditional on VD-1

This phase must preserve the existing architecture:
- progression uses actual workouts only
- generation is server-side
- user review remains mandatory

---

## 2. Scope

**In scope:**
- progression summary endpoint grounded in `workout_sessions`, `workout_sets`, and `exercise_muscle_overrides`
- generated-routine create/read/decision endpoints
- Apex API models and review UI
- training-load input activation via the progression summary
- Path B manual-execution fallback
- conditional Path A push-to-Hevy slice if VD-1 is confirmed positive

**Out of scope:**
- full mesocycle planning
- client-side workout logging
- nutrition/supplement/coaching work
- alternative Hevy fallbacks beyond Path B display/manual execution
- general repository cleanup unrelated to the generation flow

---

## 3. Required Deliverables

### 3.1 Server Progression Summary

**Likely files to add/modify:**
- New: `src/services/progressionEngine.js`
- Modified: `src/routes/workouts.js`
- Modified: `src/index.js`
- Modified: `src/__tests__/workouts.test.js`

**Endpoint:**
`GET /api/workouts/progression/summary?days=7`

**Expected response shape:**

```json
{
  "snapshotDate": "2026-03-19",
  "periodDays": 7,
  "trainingLoadScore": 62,
  "historyFresh": true,
  "volumeByMuscle": {
    "chest": 10,
    "front_delts": 4,
    "triceps": 9
  },
  "landmarkStatus": {
    "chest": {
      "sets": 10,
      "mevLow": 4,
      "mavLow": 6,
      "mavHigh": 16,
      "mrvHigh": 24,
      "status": "in_range",
      "approachingMrv": false
    }
  },
  "exerciseSignals": [
    {
      "exerciseTemplateId": "79D0BB3A",
      "exerciseName": "Bench Press",
      "lastWeightKg": 80.0,
      "consecutiveTargetHits": 2,
      "suggestion": "increase_weight"
    }
  ]
}
```

**Behavioral requirements:**
- `trainingLoadScore` is derived from recent volume/MRV proximity, not from subjective readiness
- `historyFresh` reflects whether the underlying Hevy cache is reasonably current under Phase 1/H-06 assumptions
- if no workout history exists, return a clear non-500 application error
- progression summary may persist into `progression_snapshots`, but correctness must not depend on snapshot persistence

**No-history error recommendation:**

```json
{
  "error": "No cached Hevy workout history available",
  "code": "NO_WORKOUT_HISTORY"
}
```

### 3.2 Server Generation Endpoints

**Likely files to add/modify:**
- New: `src/routes/generatedRoutines.js`
- New: `src/schemas/generatedRoutines.js`
- New: `src/services/workoutGenerator.js`
- Modified: `src/index.js`
- New: `src/__tests__/generatedRoutines.test.js`

**Create endpoint:**
`POST /api/generated-routines`

**Recommended request body:**

```json
{
  "sessionType": "push",
  "targetMuscleGroups": ["chest", "front_delts", "triceps"],
  "durationMinutes": 60,
  "readiness": {
    "aggregateScore": 74,
    "label": "Take it easy",
    "computedAt": "2026-03-19T08:15:00Z",
    "inputs": [
      {
        "id": "sleep",
        "status": "fresh",
        "score": 70,
        "effectiveWeight": 0.30,
        "lastUpdatedAt": "2026-03-19T06:40:00Z",
        "reason": "Under 7h sleep"
      }
    ]
  }
}
```

**Response shape:**

```json
{
  "id": "generated-routine-uuid",
  "title": "Push Day",
  "status": "presented",
  "reasoningSummary": "Chest volume is below MAV while triceps are near target, so the session emphasizes pressing with moderate accessory work.",
  "readiness": {
    "aggregateScore": 74,
    "label": "Take it easy"
  },
  "progression": {
    "trainingLoadScore": 62,
    "historyFresh": true
  },
  "warnings": [],
  "exercises": [
    {
      "id": "generated-routine-exercise-uuid",
      "ordering": 1,
      "exerciseTemplateId": "79D0BB3A",
      "exerciseName": "Bench Press",
      "targetSets": 3,
      "targetReps": 8,
      "targetWeightKg": 82.5,
      "targetRpe": 8.0,
      "resolvedPrimary": "chest",
      "resolvedSecondaries": ["front_delts", "triceps"],
      "reasoning": "Last two completed bench sets hit target reps at 80kg, so increase weight modestly while chest remains below MAV.",
      "flags": ["two_for_two_increase"]
    }
  ]
}
```

**Decision endpoint:**
`POST /api/generated-routines/:id/decision`

**Decision body:**

```json
{
  "decision": "accepted"
}
```

Allowed values: `accepted`, `rejected`

**Read endpoint:**
`GET /api/generated-routines/:id`

Must return routine + exercise rows for a persisted review screen reload.

### 3.3 Conditional Path A Push Endpoint

Only implement if VD-1 is positive during execution.

**Likely files to modify:**
- Modified: `src/routes/generatedRoutines.js`
- Modified: `src/services/hevyClient.js`
- Modified: `src/schemas/generatedRoutines.js`
- Modified: `src/__tests__/hevyClient.test.js`
- Modified/New: `src/__tests__/generatedRoutines.test.js`

**Endpoint:**
`POST /api/generated-routines/:id/push`

**Expected success response:**

```json
{
  "status": "pushed",
  "hevyRoutineId": "3f179f7a-0fbb-495a-a859-cad1770fcf98"
}
```

**If VD-1 is negative or unresolved:** do not stub a fake push call. Omit the endpoint/button and ship Path B cleanly.

### 3.4 Apex Client API Surface

**Likely files to modify:**
- Modified: `app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt`

Add:
- response models for progression summary and generated routines
- request models for generation and decision calls
- methods:
  - `getProgressionSummary(days: Int = 7)`
  - `generateRoutine(request: GenerateRoutineRequest)`
  - `getGeneratedRoutine(id: String)`
  - `decideGeneratedRoutine(id: String, decision: String)`
  - conditional `pushGeneratedRoutine(id: String)` if Path A lands

**Recommended Kotlin models:**

```kotlin
data class ProgressionSummaryResponse(
    val snapshotDate: String,
    val periodDays: Int,
    val trainingLoadScore: Int,
    val historyFresh: Boolean,
    val volumeByMuscle: Map<String, Int>,
    val landmarkStatus: Map<String, LandmarkStatusResponse>,
    val exerciseSignals: List<ExerciseSignalResponse>
)

data class GenerateRoutineRequest(
    val sessionType: String,
    val targetMuscleGroups: List<String>,
    val durationMinutes: Int,
    val readiness: ReadinessPayloadRequest
)

data class GeneratedRoutineResponse(
    val id: String,
    val title: String,
    val status: String,
    val reasoningSummary: String?,
    val readiness: GeneratedRoutineReadinessResponse,
    val progression: GeneratedRoutineProgressionResponse,
    val warnings: List<String>,
    val exercises: List<GeneratedRoutineExerciseResponse>
)
```

### 3.5 Apex Review Flow

**Likely files to add/modify:**
- Modified: `app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt`
- Modified: `app/src/main/java/com/healthplatform/sync/ui/ActivityScreen.kt`
- Modified: `app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt`
- New: `app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineScreen.kt`
- New: `app/src/main/java/com/healthplatform/sync/ui/GeneratedRoutineViewModel.kt`

**Recommended flow:**
1. User taps `Generate workout` from Activity
2. App fetches/uses the latest readiness payload and progression summary
3. App sends generation request
4. New screen displays loading, then the generated routine
5. User chooses:
   - `Accept`
   - `Reject`
   - conditional `Push to Hevy` if Path A is active

**Navigation recommendation:**
- Add a dedicated nav route in `MainActivity.kt` rather than overloading the Activity tab state
- Route can be simple and stable:
  - `generatedRoutine`
  - or `generatedRoutine/{id}` if the screen reloads by ID

**Path B UX requirement:**
After acceptance, show a short manual execution instruction:
- routine accepted in Apex
- start the workout manually in Hevy
- Apex will continue using actual logged performance from Hevy as the truth source

### 3.6 Training-Load Input Activation

Phase 3 must activate the dormant training-load slot planned in Phase 2.

**Likely files to modify once Phase 2 lands:**
- `app/src/main/java/com/healthplatform/sync/readiness/ReadinessModels.kt`
- `app/src/main/java/com/healthplatform/sync/readiness/ReadinessEngine.kt`
- `app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt`
- `app/src/main/java/com/healthplatform/sync/ui/DashboardScreen.kt`
- `app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt`
- `app/src/main/java/com/healthplatform/sync/ui/ActivityScreen.kt`

**Requirements:**
- training-load weight becomes active when progression summary is available
- dashboard readiness display shows training load as another input row
- Activity tab shows a concise progression/volume summary card grounded in the same server response
- if progression summary is unavailable because of empty history, training load is excluded cleanly rather than faked

---

## 4. Server Algorithm Guardrails

### 4.1 Progression source of truth

Never query `generated_routines` to make progression decisions.

Allowed upstreams:
- `workout_sessions`
- `workout_sets`
- `exercise_muscle_overrides`
- `hevy_exercise_cache`

### 4.2 Generation dependencies

Generation may consume:
- readiness payload from the client
- progression summary derived from actuals
- cached exercise template/override data

Generation must not:
- call Hevy live for personalization on the hot path
- rely on a prescribed-to-actual link existing
- refuse to work just because VD-1 is unresolved

### 4.3 Exercise selection and reasoning

The first MVP pass does not need a giant AI planner. It needs deterministic, inspectable rules:
- choose exercises from cached Hevy templates that map cleanly through overrides
- use actual recent exercise history for 2-for-2 suggestions
- use weekly muscle-group set totals for MRV proximity flags
- generate reasoning text from these factors, not from opaque prose generation

### 4.4 Failure handling

Return explicit application errors for:
- no Hevy history
- missing required readiness payload
- unsupported session type
- missing generated routine on decision/read

Do not convert these into generic 500s.

---

## 5. Client Safety Constraints

1. Activity screen must remain usable even if generation endpoints fail.
2. Review UI must never imply that a generated routine is the source of truth.
3. Path B must be complete enough that the user can proceed without waiting on VD-1.
4. Training-load input must degrade cleanly if the server has no progression summary yet.
5. No client-side logic may reinterpret actual workout data in a way that contradicts ADR-004.

---

## 6. Acceptance Criteria

- [ ] `GET /api/workouts/progression/summary` returns volume, landmarks, exercise signals, and a training-load score
- [ ] `POST /api/generated-routines` creates and persists a routine with exercise rows and reasoning
- [ ] `GET /api/generated-routines/:id` rehydrates the review screen
- [ ] `POST /api/generated-routines/:id/decision` updates lifecycle state correctly
- [ ] no-history case returns a clear application error with code
- [ ] Apex Activity flow can request and display a generated routine
- [ ] review screen supports accept/reject
- [ ] Path B manual-execution flow is complete
- [ ] dashboard readiness can include training load when available
- [ ] progression-based volume/flag data is visible in the UI
- [ ] server tests cover progression summary + generation lifecycle
- [ ] Apex tests cover generation success/failure and decision flow
- [ ] conditional Path A works only if VD-1 is positive

---

## 7. Verification Plan

**Server verification:**
- request progression summary with seeded workout history
- request generation with a valid readiness payload
- verify inserts into `generated_routines` and `generated_routine_exercises`
- accept and reject the same routine in separate tests
- verify `NO_WORKOUT_HISTORY` on empty DB/user
- if Path A lands, verify `hevy_routine_id` persistence and lifecycle update

**Apex verification:**
- Activity screen shows generate CTA without regressing manual Hevy sync
- generation success navigates to review state/screen
- generation failure shows clear error without breaking workouts list
- accept/reject updates UI state correctly
- dashboard readiness excludes/includes training load appropriately

---

## 8. Main Risks

1. **Contract drift:** if the Phase 2 readiness payload differs from the request body assumed here, Phase 3 will waste time on adapter code. Reuse the Phase 2 contract directly.
2. **Progression scope creep:** adding mesocycle intelligence or generalized AI planning will slow the only thing that matters for MVP.
3. **Override-data fragility:** bad muscle attribution poisons both MRV warnings and training-load scoring.
4. **Client review bloat:** a fully editable routine builder is not needed to satisfy the MVP review requirement.

---

## 9. Execution Notes

- Implement Path B first. Treat Path A as a small conditional branch, not as the default design center.
- Put new generation routes in a dedicated file rather than cramming them into `workouts.js`; keep `workouts.js` focused on actual logged sessions plus the progression summary.
- Add the new progression summary route in `workouts.js` before `/:id` if using that file, to avoid route shadowing.
- Keep reasoning deterministic and data-backed so D-05 helps trust instead of exposing arbitrary logic.
- Update `PROJECT.md` after landing this phase so future sessions do not rediscover the same contract decisions.
