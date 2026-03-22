# GSD Package 0B

**Package:** Client Health Provider Interface
**Status:** Ready for execution
**Source:** IMPLEMENTATION-ROADMAP.md, Phase 0B; ADR-005
**Estimated effort:** 1 session

---

## 1. Objective and Scope

**Objective:** Decouple `SyncWorker` from `HealthConnectReader` by introducing a `HealthDataProvider` interface, wrapping the existing reader as `HealthConnectProvider`, and defining project-level health domain types. This is a strictly behavior-preserving refactor — all existing sync behavior must be unchanged after completion.

**In scope:**
- `HealthDataProvider` interface covering all data-type reads, change token operations, availability, and permission queries
- Project-level domain types: move existing `BloodPressureData`, `SleepData`, `BodyMeasurementData`, `HrvData` out of `HealthConnectReader.kt` into their own file(s)
- `HealthConnectProvider` adapter wrapping `HealthConnectReader` behind the new interface
- `SyncWorker` refactored to call `HealthDataProvider` instead of `HealthConnectReader` directly
- Unit tests for `HealthConnectProvider` adapter
- Update existing `SyncWorkerTest` to mock the provider interface instead of `mockkConstructor(HealthConnectReader::class)`

**Out of scope:**
- All server work (Package 0A / Phase 1)
- Alternative providers (WHOOP, Oura, Garmin)
- Readiness engine (Phase 2)
- Any new data flows, new data types, or feature work
- Queue format changes (`SyncQueueEntity` unchanged)
- Changes to `HealthConnectReader` internal logic
- `SettingsViewModel` permission checks (future D-08 integration, not Package 0B)

---

## 2. Why This Package Comes Next

1. **Phase 2 depends on it.** The readiness engine needs stable domain types and the provider interface to read health data through a testable abstraction.
2. **Zero external dependencies.** No server work, no validation results, no API access needed.
3. **Improves testability immediately.** `SyncWorkerTest` currently uses `mockkConstructor(HealthConnectReader::class)` which is fragile. Interface-based mocking is cleaner.
4. **Parallel with Phase 1.** Package 0B is client-only. Phase 1 is server-only. No coupling.

---

## 3. Code-Level Findings from Codebase Inspection

### Existing domain types already exist

`HealthConnectReader.kt` lines 357-389 define four data classes at the bottom of the file:
- `BloodPressureData` (systolic, diastolic, measuredAt, pulse?, context?, deviceName?)
- `SleepData` (sleepStart, sleepEnd, durationMinutes, deepSleepMinutes?, remSleepMinutes?, lightSleepMinutes?, sleepScore?, deviceName?)
- `BodyMeasurementData` (measuredAt, weightKg?, bodyFatPercent?, muscleMassKg?, deviceName?)
- `HrvData` (measuredAt, hrvMs, deviceName?)

**These are already HC-decoupled.** They contain no HC SDK imports. The "define project-level domain types" task from ADR-005 is partially done — the types exist but live in the wrong file. Package 0B moves them to a dedicated location.

### HealthConnectReader public API surface

The interface must cover all public methods called by `SyncWorker`:

| Method | Used by SyncWorker | Change-token involved? |
|--------|-------------------|----------------------|
| `readBloodPressure(since: Instant)` | Line 88 (fallback) | No |
| `readBloodPressureChanges(token: String)` | Line 85 | Yes — returns `Pair<List<BloodPressureData>, String>` |
| `getBpChangesToken()` | Line 88 (with fallback) | Returns token |
| `readSleep(since: Instant)` | Line 114 (fallback) | No |
| `readSleepChanges(token: String)` | Line 111 | Yes |
| `getSleepChangesToken()` | Line 114 | Returns token |
| `readWeight(since: Instant)` | Line 133 | No (no change tokens for body) |
| `readHeartRateVariability(since: Instant)` | Line 150 (fallback) | No |
| `readHrvChanges(token: String)` | Line 147 | Yes |
| `getHrvChangesToken()` | Line 150 | Returns token |

Not called by SyncWorker but part of reader's public API:
- `checkPermissions()` — used by `SettingsViewModel`
- `hasAllPermissions()` — used by `SettingsViewModel`
- `requiredPermissions` — used by `MainActivity`, `SettingsViewModel`
- `isAvailable(context)` — companion object, used by `MainActivity`

**Decision:** The `HealthDataProvider` interface covers the SyncWorker-facing methods only (data reads + change tokens). Permission and availability queries go on the interface as well per ADR-005, but the existing permission call sites (`SettingsViewModel`, `MainActivity`) are NOT refactored in Package 0B — they continue calling `HealthConnectReader` directly. That migration is future scope.

### SyncWorker test injection

`SyncWorker` is a WorkManager `CoroutineWorker` with a fixed `(Context, WorkerParameters)` constructor — it cannot accept additional constructor parameters without a custom `WorkerFactory`, which is overkill for this project.

**Chosen mechanism:** A `@VisibleForTesting` companion object field `providerOverride: HealthDataProvider?` on `SyncWorker`. Production calls use the default (`HealthConnectProvider(applicationContext)`). Tests set `providerOverride` in `@Before` and clear it in `@After`.

**Rejected alternatives:**
- *Constructor parameter:* Not possible — WorkManager controls SyncWorker construction via the fixed `(Context, WorkerParameters)` signature.
- *Custom WorkerFactory DI:* Correct but overkill for a single-user app with one provider at MVP. Introduces Hilt/manual DI scope for no user-facing benefit.
- *`inputData` extras:* WorkManager `Data` only supports primitives, not object injection.

### SyncWorkerTest mocking approach

Current: `mockkConstructor(HealthConnectReader::class)` + `coEvery { anyConstructed<HealthConnectReader>().readBloodPressure(any()) }` etc.

After refactor: `val mockProvider = mockk<HealthDataProvider>()`, set `SyncWorker.providerOverride = mockProvider` in `@Before`, clear in `@After`. Interface-based mocking is cleaner and eliminates constructor-mock fragility.

### Result metadata deferral

ADR-005 §1 and IMPLEMENTATION-ROADMAP.md specify the interface should support "result metadata: new change token, staleness flag, source identifier." Package 0B **defers the metadata wrapper** to Phase 2 for these reasons:

1. **Behavior-preserving constraint.** The current `SyncWorker` does not consume staleness flags or source identifiers. Adding a `HealthReadResult<T>` wrapper now would change the interface beyond what `SyncWorker` currently needs, forcing non-mechanical changes to Phase 1 sync logic.
2. **Change tokens are already handled.** The incremental-read methods already return `Pair<List<T>, String>` where the second element is the new token. This satisfies the "change token pass-through" requirement without a wrapper.
3. **Phase 2 is the correct integration point.** The readiness engine (ADR-003) is the first consumer that needs staleness metadata. When Phase 2 begins, the interface can be extended with a `HealthReadResult<T>` wrapper that adds `fetchedAt: Instant`, `source: String`, and `isStale: Boolean`. This is an additive, non-breaking change to the interface.

**Follow-up:** Phase 2 planning must include a task to add `HealthReadResult<T>` to `HealthDataProvider` and update `HealthConnectProvider` to populate it. This is documented here and in the IMPL-BRIEF so it is not silently dropped.

---

## 4. Task Decomposition

### Task 1: Extract domain types to dedicated file

**Purpose:** Move the 4 data classes from `HealthConnectReader.kt` to their own file so they are importable independently of the reader.

**Files affected:**
- Modified: `data/HealthConnectReader.kt` — remove data class definitions (lines 357-389)
- New: `data/health/HealthDomainTypes.kt` — the 4 data classes, same package or new `data.health` package

**Completion criteria:**
- Data classes compile in new location
- All existing imports still resolve (`SyncWorker`, `SyncWorkerTest`, `HealthConnectReaderTest` all import from new location)
- Zero behavior change

**Risk:** Import path changes propagate to every file that references these types. Grep for all imports of `BloodPressureData`, `SleepData`, `BodyMeasurementData`, `HrvData` and update them.

---

### Task 2: Define HealthDataProvider interface

**Purpose:** Create the abstraction interface per ADR-005.

**Files affected:**
- New: `data/health/HealthDataProvider.kt`

**Interface methods** (derived from SyncWorker's actual usage):

```
// Full-window reads (since → list)
readBloodPressure(since: Instant): List<BloodPressureData>
readSleep(since: Instant): List<SleepData>
readWeight(since: Instant): List<BodyMeasurementData>
readHeartRateVariability(since: Instant): List<HrvData>

// Change-token incremental reads (token → data + newToken)
readBloodPressureChanges(token: String): Pair<List<BloodPressureData>, String>
readSleepChanges(token: String): Pair<List<SleepData>, String>
readHrvChanges(token: String): Pair<List<HrvData>, String>

// Change-token acquisition
getBpChangesToken(): String
getSleepChangesToken(): String
getHrvChangesToken(): String

// Availability and permissions (ADR-005 §1)
isAvailable(): Boolean
checkPermissions(): Set<String>
hasAllPermissions(): Boolean
```

All methods are `suspend` functions.

**Completion criteria:**
- Interface compiles
- No implementation yet (just the contract)

---

### Task 3: Implement HealthConnectProvider adapter

**Purpose:** Wrap `HealthConnectReader` behind the new interface. Pure delegation — no behavior changes.

**Files affected:**
- New: `data/health/HealthConnectProvider.kt`

**What it does:**
- Accepts `Context` in constructor
- Creates `HealthConnectReader` internally
- Every interface method delegates to the corresponding reader method
- `isAvailable()` delegates to `HealthConnectReader.isAvailable(context)` (static companion → instance method on provider)

**Completion criteria:**
- All interface methods implemented
- Each method is a one-liner delegation to the reader
- No logic beyond delegation

**Risk:** Minimal. This is mechanical wrapping.

---

### Task 4: Refactor SyncWorker to use HealthDataProvider

**Purpose:** Replace direct `HealthConnectReader` usage in SyncWorker with `HealthDataProvider` calls.

**Files affected:**
- Modified: `service/SyncWorker.kt`

**Changes:**
1. Replace `val reader = HealthConnectReader(applicationContext)` with `val provider = providerOverride ?: HealthConnectProvider(applicationContext)`
2. Add `@VisibleForTesting internal` companion object field `providerOverride: HealthDataProvider? = null`
3. Replace all `reader.readBloodPressure(...)`, `reader.readBloodPressureChanges(...)`, etc. with `provider.readBloodPressure(...)`, etc.
4. Remove `import com.healthplatform.sync.data.HealthConnectReader` from SyncWorker
5. The `toQueueEntity()` extension functions (lines 302-326) remain unchanged — they operate on the domain types, not on HC SDK types

**Completion criteria:**
- SyncWorker has zero direct references to `HealthConnectReader`
- SyncWorker imports `HealthDataProvider` (interface) and `HealthConnectProvider` (default impl)
- All Phase 1 / Phase 2 sync behavior unchanged
- `toQueueEntity()` extensions still work (they use the same data classes)

**Risk:** This is the highest-risk task. SyncWorker is ~490 lines and the most complex client file. The refactor must be strictly mechanical — replacing `reader.X()` with `provider.X()`. Any logic change is a bug.

---

### Task 5: Update tests

**Purpose:** Adapt existing tests and add new adapter tests.

**Files affected:**
- Modified: `service/SyncWorkerTest.kt` — replace `mockkConstructor(HealthConnectReader::class)` with interface mock
- New: `data/health/HealthConnectProviderTest.kt` — verify adapter delegates correctly
- Possibly modified: `data/HealthConnectReaderTest.kt` — update imports for moved data classes

**SyncWorkerTest changes:**
- Remove `mockkConstructor(HealthConnectReader::class)` setup (lines 84-91)
- Create `val mockProvider = mockk<HealthDataProvider>()`
- Mock each provider method (same stubs, cleaner syntax)
- Inject mock provider into worker (via the mechanism from Task 4)

**HealthConnectProviderTest (new):**
- Mock `HealthConnectReader`
- Verify each provider method delegates to the corresponding reader method
- Verify `isAvailable()` delegates to the companion object method

**Completion criteria:**
- All existing tests pass with updated imports
- `SyncWorkerTest` uses interface mocking, not constructor mocking
- New `HealthConnectProviderTest` covers delegation for all interface methods
- CI green

---

## 5. Recommended Execution Order

```
Task 1 (extract domain types)
  → Task 2 (define interface)
    → Task 3 (implement adapter)
      → Task 4 (refactor SyncWorker)
        → Task 5 (update tests)
```

**Sequential.** Task 2 needs domain types in new location. Task 3 needs the interface. Task 4 needs the adapter. Task 5 needs the refactored SyncWorker.

Tasks 1-3 are low-risk mechanical work (~40% effort). Task 4 is the most careful refactor (~30% effort). Task 5 is test adaptation (~30% effort).

---

## 6. Definition of Done

Package 0B is complete when ALL of the following are true:

- [ ] Domain types (`BloodPressureData`, `SleepData`, `BodyMeasurementData`, `HrvData`) live in a dedicated file, not inside `HealthConnectReader.kt`
- [ ] `HealthDataProvider` interface exists with all SyncWorker-facing methods
- [ ] `HealthConnectProvider` adapter exists, wraps `HealthConnectReader`, delegates all methods
- [ ] `SyncWorker.doWork()` contains zero direct references to `HealthConnectReader`
- [ ] `SyncWorker` imports and uses `HealthDataProvider` interface for all health data reads
- [ ] All existing health sync behavior unchanged (BP, sleep, body, HRV sync works end-to-end)
- [ ] All existing tests pass
- [ ] `SyncWorkerTest` uses interface-based mocking, not `mockkConstructor`
- [ ] New `HealthConnectProviderTest` covers delegation for all interface methods
- [ ] `./gradlew test` passes
- [ ] `./gradlew lint` passes (or no new warnings introduced)
- [ ] CI green

---

## 7. Open Questions

| Question | Impact | Resolution |
|----------|--------|-----------|
| Should domain types go in `data.health` subpackage or stay in `data`? | Import path cosmetics only | Prefer `data.health` for clean separation. Not blocking. |

Test injection is resolved (companion `providerOverride`). Result metadata is explicitly deferred to Phase 2 (see §3 "Result metadata deferral"). No open questions block starting.

---

## 8. Handoff to Next Package

### What Package 0B enables

- **Phase 2 client work (readiness engine):** The readiness engine reads health data through the provider interface. Without Package 0B, the engine would couple directly to `HealthConnectReader`.
- **Phase 3 (workout generation):** Client sends readiness context derived from provider-sourced health data.
- **Future alternative providers (post-A-01/H-04):** The provider interface is the seam. No additional architectural work is needed to add a WHOOP or Oura provider later.

### Deferred to Phase 2

- **Result metadata wrapper (`HealthReadResult<T>`):** ADR-005 §1 specifies the interface should return result metadata (staleness flag, source identifier). Package 0B defers this because `SyncWorker` does not consume it. Phase 2 must add `HealthReadResult<T>` to the interface when the readiness engine needs staleness data. This is an additive, non-breaking interface change.
- **Testability:** All health-data-dependent code can be tested with mocked providers instead of mocked HC SDK constructors.

### After Package 0B completes

Phase 1 (Hevy adapter + workout schema) and Phase 2 (readiness engine + empty schemas) can both begin. Phase 1 is server-side and already unblocked by Package 0A. Phase 2 client work is unblocked by Package 0B.
