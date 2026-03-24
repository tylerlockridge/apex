# Feature: Architecture Overview

*Created: 2026-03-02 | Updated: 2026-03-22 | Project: Apex*

---

## Feature Overview

**What it does:**
Apex is a native Android app that reads health data from Android Health Connect, stages outbound sync in a local Room queue, uploads normalized records to the Health Platform Desktop server, and displays summaries, trends, and workouts through a mix of local cached state and live server reads.

**What it does NOT do:**
- Apex does not have a repository layer that mediates all data flows
- Apex does not keep a durable local read-model cache for server-fed trends or workouts
- Apex does not run workout generation or AI coaching on-device
- Apex does not implement alternative health providers yet; Health Connect is still the only provider

---

## Current Architecture Shape

### Local state layers

| Layer | Current role |
|-------|--------------|
| `Room` (`apex.db`) | Durable outbound queue for pending health records (`sync_queue`) |
| Plain `SharedPreferences` (`health_sync`) | Lightweight summary/read model for dashboard, widget, weekly summary, sync metadata, and change tokens |
| Encrypted `SharedPreferences` (`apex_secure`) | API key, biometric toggle, device secret |
| In-memory `StateFlow` / saved instance state | Per-screen UI state and lock/auth state |

### Architectural character

- MVVM-style screen architecture with `ViewModel` + `StateFlow` + Compose UI
- No repository/service layer between most viewmodels and their data sources
- `SyncWorker` is still the most coupled component: it coordinates Health Connect reads, Room queue writes, server uploads, summary-pref updates, notifications, and widget refresh
- Dashboard/widget/weekly summary are prefs-backed; Trends and Activity remain live server-read surfaces

---

## Package Structure

| Package / File | Purpose |
|----------------|---------|
| `HealthSyncApp.kt` | Application entry point, notification channel setup, weekly summary scheduling, WorkManager configuration |
| `Config.kt` | Runtime config, server URL handling, cert pins, minimum server version |
| `data/health/` | Health domain types, `HealthDataProvider`, `HealthConnectProvider` |
| `data/db/` | Room database, queue entity/DAO, dedupe hash logic |
| `data/HealthConnectReader.kt` | Health Connect-specific read logic |
| `security/` | `SecurePrefs`, `BiometricLockManager` |
| `readiness/` | `ReadinessEngine`, `ReadinessConfigStore`, `ReadinessModels`, `ReadinessPayloadBuilder` — client-side readiness scoring (ADR-003) |
| `service/` | `ApiService`, `ServerApiClient`, `SyncWorker`, `WeeklySummaryWorker` |
| `ui/` | Screens (Dashboard, Trends, Activity, Settings, GeneratedRoutine), onboarding, navigation, viewmodels |
| `widget/` | Glance widget and receiver |

**Current source footprint:** ~42 Kotlin source files under `app/src/main/java/com/healthplatform/sync`.

---

## Primary Data Flows

### Outbound health sync

```text
Health Connect
  -> HealthConnectReader
  -> HealthConnectProvider / HealthDataProvider
  -> SyncWorker
  -> Room sync_queue
  -> ApiService (HMAC + Bearer + cert pinning)
  -> Health Platform Desktop
```

### Cached summary path

```text
SyncWorker success
  -> health_sync SharedPreferences
  -> DashboardViewModel / HealthGlanceWidget / WeeklySummaryWorker
```

### Live server-read path

```text
Health Platform Desktop
  -> ServerApiClient
  -> TrendsViewModel / ActivityViewModel / SettingsViewModel
  -> Compose UI
```

### Readiness computation path

```text
health_sync SharedPreferences (sleep, BP, HRV timestamps)
  + ServerApiClient.getProgressionSummary() → trainingLoadScore
  -> ReadinessPayloadBuilder.build()
  -> ReadinessEngine.compute()
  -> DashboardViewModel (display) / GeneratedRoutineViewModel (generation request)
```

`ReadinessPayloadBuilder` is the single source of truth for the readiness payload used in generation requests. Both `DashboardViewModel` and `GeneratedRoutineViewModel` use the same builder so that the readiness context shown on the dashboard matches what is sent to the server for generation.

### Workout generation path

```text
GeneratedRoutineViewModel
  -> ReadinessPayloadBuilder.build() (client readiness payload)
  -> ServerApiClient.generateRoutine(POST /api/generated-routines)
  -> Server: workoutGenerator reads cached Hevy history + exercise_muscle_overrides
  -> Server: progressionEngine computes volume/MRV/2-for-2 signals
  -> Server: persists generated_routines + generated_routine_exercises
  -> Response: routine with exercises, per-exercise reasoning, MRV warnings
  -> GeneratedRoutineScreen (review)
  -> User accept/reject (POST /api/generated-routines/:id/decision)
  -> Path B: user starts workout manually in Hevy
```

Generation is server-side (D-14). The client sends a readiness payload and receives a complete routine. The server reads only from cached Hevy data — no live Hevy API calls during generation (ADR-001). Path B (manual Hevy execution) is the current shipped behavior. Path A (push-to-Hevy) is conditional on VD-1 validation and is not yet built.

---

## Build and Runtime Configuration

| Setting | Value |
|---------|-------|
| `compileSdk` | 34 |
| `targetSdk` | 34 |
| `minSdk` | 34 |
| JVM target | 17 |
| Compose compiler extension | 1.5.15 |
| Release minification | Enabled |
| Secrets injection | `BuildConfig.DEVICE_SECRET`, `BuildConfig.API_KEY` from `local.properties` |
| Background work | WorkManager |
| Local persistence | Room + SharedPreferences + EncryptedSharedPreferences |

Notable runtime dependencies:
- Health Connect `1.1.0-alpha06`
- Retrofit `2.9.0`
- OkHttp `4.12.0`
- WorkManager `2.9.0`
- Room `2.6.1`
- CameraX `1.3.4`
- ML Kit barcode scanning `17.2.0`

---

## Current v2 State

- **Package 0B:** `SyncWorker` depends on `HealthDataProvider`, not `HealthConnectReader` directly.
- **Phase 2 readiness engine:** `ReadinessEngine` (stateless, pure-function scorer) replaces the v1 inline heuristic. Configurable weights via `ReadinessConfigStore`. `ReadinessPayloadBuilder` assembles the payload for both the dashboard display and generation requests. Training-load input is active when the server progression summary is reachable.
- **Phase 3 workout generation:** Server-side `workoutGenerator` + `progressionEngine` produce personalized routines from cached Hevy history. Client sends readiness payload via `GeneratedRoutineViewModel`, receives a routine with per-exercise reasoning, and presents it in `GeneratedRoutineScreen` for accept/reject review. Path B (manual Hevy execution) is the shipped default. Push-to-Hevy (Path A) is not yet built.
- **Server-side Hevy adapter and schema work:** Hevy adapter caching, exercise-template cache, `exercise_muscle_overrides`, and future-pillar empty tables (migrations 008-012) are built and CI-green but not yet deployed to production.

---

## Known Architectural Constraints

| Constraint | Why it matters |
|-----------|----------------|
| No repository layer | Future refactors must avoid assuming a clean domain/data boundary already exists |
| Durable outbound queue only | Uploads are resilient; inbound trends/workouts are not equivalently cached |
| Summary prefs act as a read model | Dashboard/widget/weekly summary depend on `health_sync` being kept current |
| Health Connect is still the only provider | Alternative-provider work remains optional post-MVP architecture, not current implementation |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Outbound queue | ✅ PASS | Room-backed, deduplicated, delete-on-success |
| Health provider seam | ✅ PASS | Package 0B landed |
| Local summary cache | ✅ PASS | Dashboard/widget/weekly summary read from prefs |
| Live server reads | ✅ PASS | Trends/Activity/Settings use `ServerApiClient` |
| Readiness engine | ✅ PASS | `ReadinessEngine` + `ReadinessPayloadBuilder` — configurable, weighted, staleness-aware |
| Workout generation flow | ✅ PASS | Client request → server generation → review screen → accept/reject (Path B shipped) |
| Repository layer | ❌ GAP | Viewmodels and worker still read/write sources directly |
| Durable inbound cache | ❌ GAP | No persisted trends/workouts read model |
