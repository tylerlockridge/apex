# Feature: Data Sync Protocol

*Created: 2026-03-02 | Updated: 2026-03-22 | Project: Apex*

---

## Feature Overview

**What it does:**
Defines how Apex moves health data to and from the Health Platform Desktop server, and how the workout generation request/response cycle works.

- **Outbound:** Health Connect reads are staged into a Room queue, then flushed to the server through `ApiService`.
- **Inbound:** Viewmodels read server history, stats, and progression summaries through `ServerApiClient`.
- **Generation:** `GeneratedRoutineViewModel` sends a readiness-enriched request to the server, receives a generated routine, and presents it for review.

**What it does NOT do:**
- Does not persist server-fed trends or workout read models locally
- Does not make the client a direct Hevy consumer
- Does not provide full transactional semantics across all synced data types

---

## Outbound Sync (Apex -> Server)

### High-level flow

```text
Health Connect read
  -> normalize to project domain type
  -> insert into Room sync_queue
  -> upload queued records in batches
  -> delete only after server acknowledgement
```

### Data types currently uploaded

| `data_type` | Source path | Notes |
|-------------|-------------|-------|
| `blood_pressure` | Health Connect -> queue -> `ApiService.syncBloodPressure()` | change-token incremental |
| `sleep` | Health Connect -> queue -> `ApiService.syncSleep()` | change-token incremental |
| `body_measurements` | Health Connect -> queue -> `ApiService.syncBodyMeasurements()` | full 30-day read |
| `hrv` | Health Connect -> queue -> `ApiService.syncHrv()` | change-token incremental |

### Request wire format

```json
{
  "data_type": "blood_pressure | sleep | body_measurements | hrv",
  "records": [ ... ]
}
```

`device_secret` is no longer transmitted in the request body. Possession of the shared secret is proven through the HMAC signature headers.

### Authentication and transport protections

All outbound requests include:
- `Authorization: Bearer <api_key>`
- `X-Timestamp: <unix-seconds>`
- `X-Signature: sha256=<hmac>`
- certificate pinning to the configured host

### Queue semantics

| Behavior | Current implementation |
|----------|------------------------|
| Local queue | `Room` table `sync_queue` |
| Deduplication | unique `recordHash` + `OnConflictStrategy.IGNORE` |
| Batch size | 200 records per POST |
| Queue cap | 5,000 rows; oldest rows evicted above cap |
| Delete policy | delete only after successful upload |
| Malformed payloads | dropped locally during flush |
| Permanent server failures | stop retries with `Result.failure()` |
| Transient failures | keep queued records and `Result.retry()` |

---

## Incremental Sync Behavior

### Implemented today

| Data type | Incremental? | Mechanism |
|-----------|--------------|-----------|
| Blood pressure | Yes | change token in `health_sync` prefs |
| Sleep | Yes | change token in `health_sync` prefs |
| HRV | Yes | change token in `health_sync` prefs |
| Body measurements | No | full 30-day read |

If a stored change token is missing or invalid, the worker clears it and falls back to a full 30-day read in the same run.

---

## Inbound Reads (Server -> Apex UI)

### Endpoints currently used

| Method | Path | Consumer |
|--------|------|----------|
| `GET` | `/api/bp` | Trends |
| `GET` | `/api/sleep` | Trends |
| `GET` | `/api/body` | Trends |
| `GET` | `/api/hrv/recent` | Trends |
| `GET` | `/api/workouts` | Activity, Dashboard recent workout |
| `GET` | `/api/workouts/stats/summary` | Activity |
| `GET` | `/api/workouts/progression/summary` | Activity (progression card), DashboardViewModel (training-load readiness input), GeneratedRoutineViewModel (training-load for readiness payload) |
| `POST` | `/api/sync/hevy/workouts` | Activity manual Hevy sync |
| `POST` | `/api/generated-routines` | GeneratedRoutineViewModel — generate a new routine |
| `GET` | `/api/generated-routines/:id` | GeneratedRoutineViewModel — re-fetch a persisted routine |
| `POST` | `/api/generated-routines/:id/decision` | GeneratedRoutineViewModel — accept or reject |
| `GET` | `/api/version` | Settings/version compatibility check |

### Inbound read characteristics

| Surface | Source of truth |
|---------|-----------------|
| Dashboard metrics | `health_sync` SharedPreferences |
| Dashboard readiness | `ReadinessEngine` computed from SharedPreferences timestamps + server progression summary |
| Widget | `health_sync` SharedPreferences |
| Weekly summary | `health_sync` SharedPreferences |
| Trends | live server reads via `ServerApiClient` |
| Activity (workouts + stats) | live server reads via `ServerApiClient` |
| Activity (progression card) | live server read: `GET /api/workouts/progression/summary` |
| Generated routine review | live server reads: `POST /api/generated-routines` + decision |
| Settings server status/version | live server read |

The app therefore has a **durable outbound sync path** but **non-durable inbound read paths** for trends and workouts.

---

## Error Handling

### Outbound

| Condition | Current behavior |
|-----------|------------------|
| Missing API key | `Result.failure()`; sync stops |
| HTTP 400/401/403/404/422 | permanent failure; queue kept; no retry loop |
| HTTP 429/5xx | transient failure; queue kept; retry with WorkManager backoff |
| Network/timeout exception | transient failure; queue kept; retry |
| Server `success=false` in body | treated as failure; queue kept |

### Inbound

`ServerApiClient` returns `Result.failure(e)` on request exceptions and leaves error presentation to the consuming viewmodel/UI.

---

## Post-Sync Local Effects

After successful per-type uploads, `SyncWorker` updates the lightweight summary cache in `health_sync`:
- last BP values/time
- last sleep values/time
- last weight/time
- last HRV/time

`last_sync` is updated only when the overall run has no failures. `sync_history` is updated on every run with success/failure status.

If at least one widget is pinned, the worker refreshes the Glance widget after the run.

---

## Progression Summary Endpoint

`GET /api/workouts/progression/summary?days=7`

Returns a trailing-window progression summary computed from actual Hevy workout data (D-17). Used by:
- `ActivityViewModel.loadProgressionSummary()` — drives the Activity progression card
- `DashboardViewModel.loadFromPrefs()` — extracts `trainingLoadScore` for readiness computation
- `ReadinessPayloadBuilder.fetchTrainingLoadScore()` — same call as DashboardViewModel, used before generation

Response shape:

```json
{
  "snapshotDate": "2026-03-22",
  "periodDays": 7,
  "trainingLoadScore": 62,
  "historyFresh": true,
  "volumeByMuscle": { "chest": 12, "triceps": 8 },
  "landmarkStatus": {
    "chest": { "sets": 12, "mevLow": 4, "mavLow": 6, "mavHigh": 16, "mrvHigh": 24, "status": "in_range", "approachingMrv": false }
  },
  "exerciseSignals": [
    { "exerciseTemplateId": "abc123", "exerciseName": "Bench Press", "lastWeightKg": 80.0, "consecutiveTargetHits": 2, "suggestion": "increase_weight" }
  ]
}
```

Returns 404 with `code: "NO_WORKOUT_HISTORY"` when no cached Hevy workout data exists.

---

## Workout Generation Protocol

### Request: `POST /api/generated-routines`

The client sends a readiness-enriched generation request. The readiness payload is assembled by `ReadinessPayloadBuilder` — the single source of truth shared between the dashboard display and the generation request.

```json
{
  "sessionType": "push",
  "targetMuscleGroups": ["chest", "front_delts", "triceps"],
  "durationMinutes": 60,
  "readiness": {
    "aggregateScore": 78,
    "label": "Take it easy",
    "computedAt": "2026-03-22T14:30:00Z",
    "inputs": [
      { "id": "SLEEP", "status": "FRESH", "score": 70, "effectiveWeight": 0.30, "lastUpdatedAt": "2026-03-22T07:00:00Z", "reason": "Under 7h sleep" },
      { "id": "BLOOD_PRESSURE", "status": "FRESH", "score": 100, "effectiveWeight": 0.20, "lastUpdatedAt": "2026-03-22T08:00:00Z", "reason": "BP normal" },
      { "id": "HRV", "status": "EXCLUDED", "score": null, "effectiveWeight": 0.0, "lastUpdatedAt": null, "reason": "Disabled" },
      { "id": "SUBJECTIVE", "status": "MISSING", "score": null, "effectiveWeight": 0.0, "lastUpdatedAt": null, "reason": "No data" },
      { "id": "TRAINING_LOAD", "status": "FRESH", "score": 62, "effectiveWeight": 0.10, "lastUpdatedAt": "2026-03-22T14:30:00Z", "reason": "Load 62%" }
    ]
  }
}
```

### Server-side generation

The server (`workoutGenerator.js`) reads only from cached data — no live Hevy API calls during generation (ADR-001 §4):
1. Calls `progressionEngine.computeProgressionSummary()` for volume/MRV/2-for-2 signals
2. Queries `hevy_exercise_cache` + `exercise_muscle_overrides` for exercise selection
3. Adjusts volume targets based on the readiness aggregate score
4. Persists the routine to `generated_routines` + `generated_routine_exercises`
5. Returns the routine with per-exercise reasoning, MRV warnings, and progression flags

### Response: `201 Created`

```json
{
  "id": "uuid",
  "title": "Push Day — Volume Phase",
  "status": "presented",
  "reasoningSummary": "...",
  "readiness": { "aggregateScore": 78, "label": "Take it easy" },
  "progression": { "trainingLoadScore": 62, "historyFresh": true },
  "warnings": ["Approaching MRV for chest (20/24 sets)"],
  "exercises": [
    {
      "id": "uuid", "ordering": 1,
      "exerciseTemplateId": "abc123", "exerciseName": "Bench Press",
      "targetSets": 3, "targetReps": 8, "targetWeightKg": 82.5, "targetRpe": 8.0,
      "resolvedPrimary": "chest", "resolvedSecondaries": ["triceps", "front_delts"],
      "reasoning": "2-for-2 met — increasing from 80kg. 12 chest sets this week (in MAV range).",
      "flags": ["two_for_two_increase"]
    }
  ]
}
```

Error responses: 404 with `code` values `NO_WORKOUT_HISTORY`, `NO_EXERCISE_TEMPLATES`, or `NO_MATCHING_EXERCISES`.

### Review and decision: `POST /api/generated-routines/:id/decision`

```json
{ "decision": "accepted" }
```

Response: `{ "id": "uuid", "status": "accepted", "decidedAt": "2026-03-22T15:00:00Z" }`

Valid decisions: `accepted`, `rejected`. Only routines in `presented` or `draft` status can receive a decision. Attempting to re-decide returns 409.

### Current execution path (Path B)

After accepting a routine, the user is instructed to start the workout manually in Hevy. Apex does not push routines to Hevy. When the workout is completed in Hevy and synced back, subsequent generation requests will reflect the updated volume and progression history.

Push-to-Hevy (Path A) is conditional on VD-1 validation and is not yet built.

---

## Known Gaps

| Gap | Impact |
|-----|--------|
| Body measurements still use full-window reads | More data churn than BP/sleep/HRV |
| No durable local cache for trends/workouts | Server-read screens have no offline fallback |
| Summary prefs and `last_sync` can diverge | Per-type success can update summary values even if the full run ends with failure |
| No cross-type transaction boundary | One data type can succeed while another fails |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Outbound queue | ✅ PASS | Room-backed, deduplicated, retry-safe |
| HRV upload | ✅ PASS | Wired through `ApiService.syncHrv()` |
| Incremental sync | ✅ PARTIAL | BP/sleep/HRV yes; body no |
| HMAC + cert pinning | ✅ PASS | Outbound and server-read clients both hardened |
| Live Hevy sync trigger | ✅ PASS | Activity screen can trigger server-side sync |
| Progression summary | ✅ PASS | `GET /api/workouts/progression/summary` — volume, MRV, 2-for-2 |
| Generation request/review | ✅ PASS | `POST /api/generated-routines` + decision flow (Path B shipped) |
| Durable inbound read model | ❌ GAP | Trends/workouts still live-only |
