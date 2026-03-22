# ADR-005: Health Data Source Abstraction

## Status

**Proposed for acceptance** — 2026-03-18. The decision (abstract behind a provider interface now, implement HC-only at MVP) is complete and unconditional. A-01/H-04 determine whether alternative providers are ever needed, not whether the interface should exist.

## Context

Health Connect (HC) is the current integration layer for health data (BP, sleep, body composition, HRV). The v1 app reads from HC via `HealthConnectReader`, stages records into a Room queue, and uploads to the server via `SyncWorker` (confirmed by R-5).

HC is a Google-maintained aggregation layer, not a direct data source. Tyler's wearable writes data to HC, and Apex reads it. This creates two risks:
1. **Device-specific reliability (H-04):** HC permissions may drop, data may go stale, or HRV data may be incomplete on Tyler's specific device.
2. **Wearable dependency (A-01):** Tyler's wearable model is unconfirmed. Whether it writes HRV to HC at all is unknown.

If HC proves unreliable or if the wearable doesn't write certain data types, direct wearable APIs (WHOOP OAuth, Oura Cloud API, Garmin Connect) may be needed. The question: should the architecture abstract health data behind a provider interface now, or build directly against HC and retrofit later?

### What is known

| Fact | Status | Source |
|------|--------|--------|
| v1 client reads HC via `HealthConnectReader` with data-type-specific methods | Confirmed | R-5 |
| HC reads use change tokens for incremental sync (BP, sleep, HRV) | Confirmed | R-5, SyncWorker.kt |
| Body measurements do full 30-day reads (no change tokens) | Confirmed | R-5 |
| `HealthConnectReader` has Oura-preference filters for sleep | Confirmed | R-5 |
| HC data flows: HC → HealthConnectReader → SyncQueueEntity → server | Confirmed | R-5 |
| No repository layer exists between HC reader and SyncWorker | Confirmed | R-5 |
| Tyler's wearable model is unidentified | **Unvalidated** (A-01) | |
| HC reliability on Tyler's device | **Unvalidated** (H-04) | |

### Planning traceability

| ID | Constraint | Role |
|----|-----------|------|
| D-08 | HC with permission watchdog and staleness display | Reliability monitoring |
| H-04 | HC delivers reliable data on Tyler's device | Viability of HC-only path |
| A-01 | Wearable writes HRV to HC | Data availability |
| D-03 | Client-first for readiness (needs local health data) | Data must be available offline |

---

## Decision

### 1. Introduce a HealthDataProvider interface — low-cost, high-option-value

Define a provider interface that `SyncWorker` calls instead of calling `HealthConnectReader` directly. The interface must support:

- Per-data-type read methods (BP, sleep, body, HRV)
- Change token pass-through for incremental sync (already used by HC)
- Availability and permission status queries
- Result metadata: new change token, staleness flag, source identifier

The exact interface signature is an implementation decision. The architecture decision is that the abstraction exists and that SyncWorker depends on the interface, not on `HealthConnectReader` directly.

### 2. HealthConnectProvider as the default (and likely only MVP) implementation

`HealthConnectProvider` wraps the existing `HealthConnectReader` behind the interface. This is a thin adapter — the existing reader logic does not change. The adapter:
- Translates HC-specific types to domain types (`BpRecord`, `SleepRecord`, etc.)
- Passes change tokens through
- Reports permission status via HC's permission APIs
- Reports availability via `HealthConnectClient.isAvailable()`

### 3. SyncWorker calls the provider, not the reader

`SyncWorker` is refactored to depend on `HealthDataProvider` instead of `HealthConnectReader` directly. This is the only structural change required. The rest of the sync pipeline (Room queue, server upload, prefs update) is unchanged.

From R-5: `SyncWorker` currently calls `HealthConnectReader` methods directly in its `doWork()` body. The refactor replaces those calls with provider interface calls. The sync queue entity format (`SyncQueueEntity`) does not change — it already stores normalized payloads.

### 4. No alternative providers at MVP

This ADR does **not** implement WHOOP, Oura, or Garmin providers. It creates the seam. Alternative providers are implemented only if:
- A-01 reveals the wearable doesn't write to HC, **or**
- H-04 monitoring reveals HC is unreliable on Tyler's device

The cost of the interface now is one Kotlin interface file and one adapter class. The cost of retrofitting later (without the interface) would be refactoring `SyncWorker`, `HealthConnectReader`, and potentially the queue entity format.

### 5. Provider selection

At MVP, provider selection is hardcoded to `HealthConnectProvider`. If an alternative provider is needed post-A-01/H-04:
- Provider selection moves to a config setting (in `health_sync` SharedPreferences or a dedicated config)
- The app could support multiple simultaneous providers (e.g., HC for sleep, WHOOP API for HRV) but this is not designed until needed

### 6. Permission watchdog integration (D-08)

The existing v1 app has HC permission checks in `SettingsViewModel`. The provider interface's `getPermissionStatus()` method standardizes this. The watchdog (D-08) calls the provider for permission status rather than HC directly. If an alternative provider is added later, it reports its own permission/auth state through the same interface.

---

## Alternatives Considered

| Alternative | Why rejected |
|------------|-------------|
| **Build directly against HC, retrofit interface later if needed** | Retrofit cost is moderate (refactor SyncWorker + reader + potentially queue format). Interface cost now is minimal (one interface + one adapter). The option value of the interface exceeds its cost. |
| **Full multi-provider framework at MVP** | Over-architecture. No alternative provider is needed unless A-01 or H-04 fail. Building OAuth flows and API clients for WHOOP/Oura/Garmin now is wasted effort if HC works. |
| **Abstract at the server level instead of client** | Health data reads happen on the client (D-03, R-5). The server receives normalized data from the sync queue. The abstraction seam belongs where the data source coupling exists — the client. |
| **Use HC types as project domain types** | Couples the entire codebase to HC's SDK types. If an alternative provider is ever needed, every consumer must be rewritten. Project domain types cost a few data classes now and provide permanent decoupling. |

---

## Consequences

- One new Kotlin interface + one adapter class. Minimal implementation cost.
- `SyncWorker` depends on an interface, not a concrete class. Testability improves (mock provider in tests).
- Domain types (`BpRecord`, `SleepRecord`, `HrvRecord`, `BodyRecord`) must be defined as project types, not HC types. This is a small up-front cost that pays off in decoupling.
- No alternative providers are implemented unless triggered by validation results. The interface exists as an option, not a commitment.

## Validation Required

| Item | What it determines | Timing |
|------|-------------------|--------|
| A-01 | Whether the wearable writes HRV/sleep to HC at all | Determines whether an alternative provider is ever needed; does not block the HC interface seam |
| H-04 | Whether HC is reliable over 2 weeks of daily use | During MVP — determines whether alternative providers are needed |

## Risks

- **Over-abstraction concern:** If HC works perfectly and no alternative is ever needed, the interface adds one layer of indirection for no benefit. Mitigation: the layer is thin (adapter pattern, not framework). The testability benefit alone justifies it.
- **A-01 reveals no HC path at all:** If the wearable doesn't write to HC and no other HC-compatible device is available, a specific alternative provider must be implemented. The interface is ready for this, but the implementation effort (OAuth flow, API client, data normalization) is non-trivial. This is a product-level decision, not an architecture failure.
- **Multiple simultaneous providers:** If Tyler uses HC for some data and a direct API for other data, the provider interface supports this (one provider per data type). But the aggregation logic adds complexity. Defer until actually needed.
