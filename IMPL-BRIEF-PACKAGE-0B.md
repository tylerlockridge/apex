# Implementation Brief: Package 0B

**Package:** Client Health Provider Interface
**Target repo:** Apex (Android)
**Language:** Kotlin
**Build system:** Gradle KTS
**Test framework:** JUnit 4, Robolectric, MockK, Turbine

---

## 1. Objective

Introduce a `HealthDataProvider` interface between `SyncWorker` and `HealthConnectReader`. Move existing health domain types to a dedicated file. Wrap the reader as `HealthConnectProvider`. Refactor `SyncWorker` to depend on the interface. Update tests to use interface-based mocking. This is a strictly behavior-preserving refactor.

---

## 2. Scope

**In scope:**
- Move domain types to `data/health/HealthDomainTypes.kt`
- New file: `data/health/HealthDataProvider.kt` (interface)
- New file: `data/health/HealthConnectProvider.kt` (adapter)
- Modified: `service/SyncWorker.kt` (use provider instead of reader)
- Modified: `service/SyncWorkerTest.kt` (interface-based mocking)
- New file: `data/health/HealthConnectProviderTest.kt`
- Modified: `data/HealthConnectReader.kt` (remove data class definitions)
- Modified: any file importing the moved data classes (import path update)

**Out of scope:**
- Server-side work
- Alternative health providers (WHOOP, Oura, Garmin)
- Readiness engine
- New data flows or feature additions
- Changes to `SyncQueueEntity` or queue format
- Changes to `HealthConnectReader` internal logic (pagination, Oura filtering, body-fat matching)
- Refactoring `SettingsViewModel` or `MainActivity` permission checks (future scope)

---

## 3. Required Deliverables

### 3.1 `data/health/HealthDomainTypes.kt`

Move these 4 data classes from `HealthConnectReader.kt` (lines 357-389) to this new file:

- `BloodPressureData(systolic: Int, diastolic: Int, measuredAt: String, pulse: Int? = null, context: String? = null, deviceName: String? = null)`
- `SleepData(sleepStart: String, sleepEnd: String, durationMinutes: Int, deepSleepMinutes: Int? = null, remSleepMinutes: Int? = null, lightSleepMinutes: Int? = null, sleepScore: Int? = null, deviceName: String? = null)`
- `BodyMeasurementData(measuredAt: String, weightKg: Double? = null, bodyFatPercent: Double? = null, muscleMassKg: Double? = null, deviceName: String? = null)`
- `HrvData(measuredAt: String, hrvMs: Double, deviceName: String? = null)`

Package: `com.healthplatform.sync.data.health`

These data classes must be **identical** to the current definitions. No field additions, renames, or type changes.

### 3.2 `data/health/HealthDataProvider.kt`

Interface with all methods `SyncWorker` currently calls on `HealthConnectReader`, plus availability/permission methods per ADR-005.

```kotlin
package com.healthplatform.sync.data.health

import java.time.Instant

interface HealthDataProvider {
    // Full-window reads
    suspend fun readBloodPressure(since: Instant): List<BloodPressureData>
    suspend fun readSleep(since: Instant): List<SleepData>
    suspend fun readWeight(since: Instant): List<BodyMeasurementData>
    suspend fun readHeartRateVariability(since: Instant): List<HrvData>

    // Change-token incremental reads
    suspend fun readBloodPressureChanges(token: String): Pair<List<BloodPressureData>, String>
    suspend fun readSleepChanges(token: String): Pair<List<SleepData>, String>
    suspend fun readHrvChanges(token: String): Pair<List<HrvData>, String>

    // Change-token acquisition
    suspend fun getBpChangesToken(): String
    suspend fun getSleepChangesToken(): String
    suspend fun getHrvChangesToken(): String

    // Availability and permissions
    suspend fun isAvailable(): Boolean
    suspend fun checkPermissions(): Set<String>
    suspend fun hasAllPermissions(): Boolean
    val requiredPermissions: Set<String>
}
```

**Result metadata (ADR-005 §1) — explicitly deferred from Package 0B:**

ADR-005 specifies the interface should support "result metadata: new change token, staleness flag, source identifier." Package 0B does NOT add a `HealthReadResult<T>` metadata wrapper because:
1. `SyncWorker` does not consume staleness or source metadata — it uses raw data lists.
2. Change tokens are already returned via separate `Pair<List<T>, String>` and `get*ChangesToken()` methods.
3. Adding a wrapper now would force non-mechanical changes to SyncWorker, violating the behavior-preserving constraint.

**Phase 2 follow-up required:** When the readiness engine (ADR-003) needs staleness data, the interface must be extended with `HealthReadResult<T>` wrapping `data: List<T>`, `fetchedAt: Instant`, `source: String`, and `isStale: Boolean`. This is an additive, non-breaking change — existing methods remain, new overloads or replacement methods are added.

### 3.3 `data/health/HealthConnectProvider.kt`

Adapter implementing `HealthDataProvider` that delegates every call to `HealthConnectReader`.

```kotlin
package com.healthplatform.sync.data.health

import android.content.Context
import com.healthplatform.sync.data.HealthConnectReader

class HealthConnectProvider(context: Context) : HealthDataProvider {
    private val reader = HealthConnectReader(context)

    override suspend fun readBloodPressure(since: Instant) = reader.readBloodPressure(since)
    override suspend fun readSleep(since: Instant) = reader.readSleep(since)
    override suspend fun readWeight(since: Instant) = reader.readWeight(since)
    override suspend fun readHeartRateVariability(since: Instant) = reader.readHeartRateVariability(since)

    override suspend fun readBloodPressureChanges(token: String) = reader.readBloodPressureChanges(token)
    override suspend fun readSleepChanges(token: String) = reader.readSleepChanges(token)
    override suspend fun readHrvChanges(token: String) = reader.readHrvChanges(token)

    override suspend fun getBpChangesToken() = reader.getBpChangesToken()
    override suspend fun getSleepChangesToken() = reader.getSleepChangesToken()
    override suspend fun getHrvChangesToken() = reader.getHrvChangesToken()

    override suspend fun isAvailable() = HealthConnectReader.isAvailable(/* context needed */)
    override suspend fun checkPermissions() = reader.checkPermissions()
    override suspend fun hasAllPermissions() = reader.hasAllPermissions()
    override val requiredPermissions get() = reader.requiredPermissions
}
```

**Note:** `isAvailable()` is currently a companion object function on `HealthConnectReader` that takes `Context`. The adapter must hold the context reference to delegate this call. The implementation agent should check whether to pass context through or restructure slightly.

### 3.4 `SyncWorker.kt` refactor

Replace `HealthConnectReader` usage with `HealthDataProvider`:

1. Remove: `val reader = HealthConnectReader(applicationContext)` (line 50)
2. Replace with: `val provider: HealthDataProvider = providerOverride ?: HealthConnectProvider(applicationContext)`
3. Add to SyncWorker's existing `companion object`:
   ```kotlin
   @VisibleForTesting
   internal var providerOverride: HealthDataProvider? = null
   ```
   This is the **only** injection mechanism. SyncWorker is a WorkManager worker with a fixed `(Context, WorkerParameters)` constructor — constructor-based injection is not possible without a custom `WorkerFactory` (rejected as overkill).
4. Replace all `reader.` calls with `provider.` calls — same methods, same arguments, same return types
5. Remove `import com.healthplatform.sync.data.HealthConnectReader`
6. Add `import com.healthplatform.sync.data.health.HealthDataProvider`
7. Add `import com.healthplatform.sync.data.health.HealthConnectProvider`
8. Update data class imports from `com.healthplatform.sync.data.*` to `com.healthplatform.sync.data.health.*`

The `toQueueEntity()` extension functions (lines 302-326) are unchanged — they operate on the domain types which are the same classes, just in a new package.

### 3.5 Test updates

**`SyncWorkerTest.kt`:**
- Remove `mockkConstructor(HealthConnectReader::class)` and all `anyConstructed<HealthConnectReader>()` stubs (lines 84-91)
- Create `val mockProvider = mockk<HealthDataProvider>()`
- Set `SyncWorker.providerOverride = mockProvider` in `@Before`
- Clear `SyncWorker.providerOverride = null` in `@After`
- Replace all `coEvery { anyConstructed<HealthConnectReader>().readBloodPressure(any()) }` with `coEvery { mockProvider.readBloodPressure(any()) }`
- Same for all other mocked reader methods

**`HealthConnectProviderTest.kt` (new):**
- Mock `HealthConnectReader`
- Construct `HealthConnectProvider` with mocked reader (requires the adapter to accept an optional reader parameter, same pattern as `HealthConnectReader(context, client)`)
- Verify each interface method delegates to the correct reader method
- Minimum: one test per interface method group (reads, changes, tokens, permissions)

**`HealthConnectReaderTest.kt`:**
- Update imports for moved data classes
- No logic changes

**Other files importing data classes:**
- Grep for `import com.healthplatform.sync.data.BloodPressureData` (and the other 3) across the entire `app/src/` tree
- Update each import to `com.healthplatform.sync.data.health.BloodPressureData` etc.

---

## 4. Behavioral Requirements

### 4.1 Behavior-preserving guarantee

After Package 0B, the app must behave identically to before:
- Periodic health sync (BP, sleep, body, HRV) reads from Health Connect, queues to Room, uploads to server
- Change tokens used for incremental BP/sleep/HRV reads
- Body measurements use full 30-day reads
- Oura preference filtering in sleep and HRV unchanged
- Queue cap enforcement unchanged
- Widget refresh after sync unchanged
- Summary prefs update unchanged

### 4.2 No queue format changes

`SyncQueueEntity` fields (`dataType`, `measuredAt`, `payload`, `recordHash`, `createdAt`) are unchanged. The `toQueueEntity()` extension functions produce the same JSON payloads as before.

### 4.3 No permission flow changes

`SettingsViewModel` and `MainActivity` continue calling `HealthConnectReader` directly for permission checks in Package 0B. Migrating those call sites to the provider interface is future scope.

---

## 5. Safety Constraints

1. **The refactor must be strictly behavior-preserving.** No new data flows, no changed data formats, no altered sync timing.
2. **`HealthConnectReader` internal logic must not change.** Pagination, Oura filtering, body-fat timestamp matching, change token handling — all untouched. Only the data class definitions are removed (moved to new file).
3. **Domain type definitions must be identical.** Same field names, same types, same defaults. Only the package changes.
4. **`SyncQueueEntity` must not change.** The `toQueueEntity()` extensions produce the same output.
5. **No `HealthConnectReader` references remain in `SyncWorker.kt` after refactor.** This is the whole point — if any remain, the abstraction is incomplete.

---

## 6. Acceptance Criteria

- [ ] `data/health/HealthDomainTypes.kt` exists with all 4 data classes, identical to originals
- [ ] `data/health/HealthDataProvider.kt` exists with interface covering all SyncWorker-facing methods
- [ ] `data/health/HealthConnectProvider.kt` exists, delegates every method to `HealthConnectReader`
- [ ] `HealthConnectReader.kt` no longer contains data class definitions (they're in the new file)
- [ ] `SyncWorker.kt` has zero imports from `com.healthplatform.sync.data.HealthConnectReader`
- [ ] `SyncWorker.kt` uses `HealthDataProvider` for all health data reads
- [ ] `SyncWorkerTest.kt` uses interface-based mocking (no `mockkConstructor(HealthConnectReader::class)`)
- [ ] `HealthConnectProviderTest.kt` exists with delegation verification tests
- [ ] All data class imports across the codebase updated to new package path
- [ ] `./gradlew test` passes (all existing + new tests)
- [ ] `./gradlew lint` passes (no new warnings)
- [ ] Health sync works end-to-end on a real device or emulator (manual spot-check)

---

## 7. Implementation Notes

### Import propagation

The domain types are imported by many files. After moving them, run a project-wide search for:
- `import com.healthplatform.sync.data.BloodPressureData`
- `import com.healthplatform.sync.data.SleepData`
- `import com.healthplatform.sync.data.BodyMeasurementData`
- `import com.healthplatform.sync.data.HrvData`

And replace with `com.healthplatform.sync.data.health.*` equivalents. Files likely affected:
- `service/SyncWorker.kt`
- `service/SyncWorkerTest.kt`
- `service/ApiService.kt`
- `service/ApiServiceTest.kt`
- `data/HealthConnectReaderTest.kt`
- `ui/TrendsViewModel.kt` (if it references these types)
- `ui/DashboardViewModel.kt` (if it references these types)

### Provider injection for tests

`SyncWorker` is a WorkManager `CoroutineWorker` with a fixed `(Context, WorkerParameters)` constructor controlled by WorkManager. Constructor-based injection is not possible without a custom `WorkerFactory` (rejected as overkill).

The **only** injection mechanism is a `@VisibleForTesting internal var providerOverride: HealthDataProvider?` companion object field. Tests set it in `@Before` and clear it in `@After`. Production code never touches it — `doWork()` falls through to `HealthConnectProvider(applicationContext)` when the override is null.

This must be cleared in `@After` to prevent test pollution between test methods.

### HealthConnectProvider context handling

`HealthConnectReader.isAvailable(context)` is a static companion method. `HealthConnectProvider` must hold the `Context` reference to call it. The adapter constructor already takes `Context` for the reader — reuse it for `isAvailable()`.

### Adapter testability

`HealthConnectProvider` should accept an optional `HealthConnectReader` parameter (default: construct one from context) so that `HealthConnectProviderTest` can inject a mocked reader:

```kotlin
class HealthConnectProvider(
    private val context: Context,
    reader: HealthConnectReader? = null,
) : HealthDataProvider {
    private val hcReader = reader ?: HealthConnectReader(context)
    // ...
}
```

This matches the existing `HealthConnectReader(context, client)` pattern where the HC SDK client is optional and injectable. It is NOT the same mechanism as `SyncWorker.providerOverride` — the adapter uses constructor injection because `HealthConnectProvider` is a normal class, not a WorkManager worker.

### What to verify before considering complete

1. `./gradlew test` — all tests pass
2. `./gradlew lint` — no new warnings
3. Grep `SyncWorker.kt` for `HealthConnectReader` — zero matches
4. Grep all `.kt` files for old import paths of the 4 data classes — zero matches
5. Spot-check: install on device, trigger a manual sync, confirm BP/sleep/body/HRV data syncs to server

---

## 8. Items Requiring Human Confirmation

None required before implementation. Package 0B is a behavior-preserving refactor with no live environment implications.

Post-implementation, a manual sync spot-check on a real device confirms the refactor didn't break anything the automated tests can't cover (HC SDK interaction, real network sync).
