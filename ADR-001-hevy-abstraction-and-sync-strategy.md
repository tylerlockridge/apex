# ADR-001: Hevy Abstraction Layer Design and Sync Strategy

## Status

**Proposed for acceptance** — 2026-03-18. The architecture decisions (server-side adapter boundary, cache-through model, tiered sync, fallback behavior) are complete. H-06 selects the sync tier at implementation time but does not change the architecture. VD-1 determines whether routine cache is needed.

## Context

Hevy is the source of truth for actual workout data (D-17), the conditional target for routine pushes (VD-1), and the data source for server-side workout generation (D-14). All Hevy interactions go through a server-side abstraction layer (D-10a).

The Hevy API is undocumented, explicitly disclaimed, rate-limited to an unknown degree (H-06), and could change without notice. This ADR defines the abstraction boundary, caching model, sync cadence, and fallback behavior.

### What is known

| Fact | Status | Source |
|------|--------|--------|
| `GET /v1/workouts` returns completed workouts with `routine_id`, exercises, sets, reps, weights | Confirmed | A-02 validation, existing `HevyClient.transformWorkout()` |
| `GET /v1/exercise_templates` returns 431 exercises with `primary_muscle_group` + `secondary_muscle_groups` | Confirmed | A-02 validation |
| `GET /v1/routines` returns user's saved routines | Confirmed | Existing client code |
| `POST /v1/routines` for creating routines | **Unvalidated** (VD-1) | ADR-004 Path A/B handles both outcomes |
| Rate limit threshold | **Unvalidated** (H-06) | Sync tier depends on this |
| Workout completion → API availability lag | **Unvalidated** (OA-3) | Affects data freshness assumptions |

### What the existing client already does

From R-5: The Apex client already has `ServerApiClient` with methods for `getWorkouts()`, `getWorkoutStats()`, and `triggerHevySync()`. The server exposes `POST /api/sync/hevy/workouts` which the client triggers manually from the Activity screen. Workout data is fetched live from the server (no client-side cache for workouts).

### Planning traceability

| ID | Constraint | Role |
|----|-----------|------|
| D-10a | All Hevy API calls through server abstraction | Primary driver |
| D-10b | Hevy fallback if API breaks | Degradation path |
| D-14 | Server-side workout generation (needs Hevy history) | Caching requirement |
| D-17 | Hevy is source of truth for actuals | Data flow direction |
| H-06 | Rate limits determine sync strategy | **Conditional branch** |
| VD-1 | Routine creation support | Push path availability |
| OA-3 | Workout completion sync lag | Freshness assumption |

---

## Decision

### 1. Server-side Hevy adapter boundary

All Hevy API calls are made by a single server-side module (`HevyAdapter` or equivalent). No other server module or client code calls Hevy directly. The adapter owns:

- Authentication (API key management)
- Rate limit tracking and backoff
- Response normalization (Hevy response → internal domain types)
- Caching layer (see below)
- Error classification (transient vs permanent vs rate-limited)

The adapter exposes an internal interface to the rest of the server. Downstream consumers (workout generation, sync routes, exercise template resolution) never see Hevy response shapes.

### 2. Caching model

The server maintains a local cache of Hevy data in PostgreSQL. This serves two purposes:
- Reduces API calls (respects rate limits)
- Provides data for workout generation without requiring live Hevy access at generation time

**Cached entities:**

| Entity | Refresh trigger | Staleness tolerance | Notes |
|--------|----------------|---------------------|-------|
| Completed workouts | Periodic sync + manual trigger | Minutes to hours (tier-dependent) | Existing `hevy_workouts` table extended or replaced |
| Exercise templates | Daily or on-demand | 24 hours (templates rarely change) | New cache table |
| User routines | On routine creation/sync | Hours | Only if VD-1 confirms write support |

**Cache-through pattern:** Reads go to cache first. If cache is stale beyond tolerance, adapter attempts a live fetch. If live fetch fails (rate limit, timeout, API down), stale cache is returned with a staleness flag. Consumers decide whether to proceed with stale data or surface a warning. Every cached record carries a `fetched_at` timestamp.

### 3. Sync strategy — tiered by H-06 result

The sync cadence is conditional on rate limit validation. Three tiers:

**Tier 1: >= 30 req/min (on-demand feasible)**
- Workout sync: on-demand when user opens Activity screen or triggers manual sync, plus periodic every 15 minutes (aligned with existing health sync cadence)
- Exercise templates: daily refresh
- Generation reads: live-through-cache with short TTL (5 minutes)

**Tier 2: 5-30 req/min (caching required)**
- Workout sync: periodic every 30-60 minutes, manual trigger rate-limited to 1/5min
- Exercise templates: daily refresh
- Generation reads: cache-only with periodic background refresh
- Backoff: exponential with jitter on 429 responses

**Tier 3: < 5 req/min (batch-only)**
- Workout sync: batch every 2-4 hours, or on explicit user request (debounced)
- Exercise templates: weekly refresh
- Generation reads: cache-only; generation fails gracefully if cache is empty
- UI shows "last synced" prominently; manual sync shows rate limit context

**Tier 0: < 1 req/min or API unavailable**
- Reassess Hevy dependency entirely. Fallback to manual workout logging in Apex or CSV import. This scenario triggers D-10b.

**Assumption for drafting:** Tier 2 (5-30 req/min) is used as the design target because it is the most architecturally constrained tier that still supports the core use case. If H-06 reveals Tier 1, the design simplifies. If Tier 3, the design still works but UX degrades.

### 4. Generation reads from cache, never live

Server-side workout generation (D-14) reads cached Hevy data. It does not trigger live API calls or block on Hevy availability. If the cache is empty, generation fails explicitly rather than producing a workout from no history.

This is a direct consequence of the caching model: the adapter is the only Hevy caller, and downstream consumers — including generation — operate on cached state.

### 5. Fallback behavior (D-10b)

If the Hevy API becomes permanently unavailable:

| Capability | Fallback |
|-----------|---------|
| Workout history | Last cached data remains readable. No new data ingested. |
| Workout generation | Continues from cached history until it becomes too stale (configurable threshold). Then degrades to template-only generation without personalization. |
| Routine push (VD-1 path) | Disabled. User executes from in-app display only. |
| Exercise templates | Cached templates persist. Stale but functional. |

The abstraction layer means fallback is a configuration change in the adapter, not a code change in downstream consumers.

### 6. Client remains a server consumer, not a Hevy consumer

The client never calls Hevy directly (already true in v1 per R-5). The client reads workout data from server APIs. No client-side Hevy caching is introduced. This holds across all sync tiers.

---

## Alternatives Considered

| Alternative | Why rejected |
|------------|-------------|
| **Client-side Hevy caching (Room)** | Duplicates server cache. Adds sync complexity. Client already reads from server APIs (R-5). No benefit for a single-user app where the server is always the Hevy caller. |
| **No caching — live-through on every read** | Unworkable if H-06 reveals Tier 2 or lower. Generation would block on API calls. API downtime would break generation entirely. |
| **Separate Hevy microservice** | Over-architecture for a single-user Node.js server. Same adapter boundary can exist as a module within the existing Express app. |
| **Client-direct Hevy calls** | Violates D-10a. Puts API key on client. Makes rate limiting uncontrollable. |

---

## Consequences

- Server gains 1-2 new cache tables (`hevy_exercise_templates`, optionally `hevy_routines`).
- `HevyAdapter` module must track rate limit state (counter + window) and implement backoff.
- Workout generation depends on cached data quality. If cache is stale, generation quality degrades gracefully rather than failing.
- The specific sync cadence is not locked until H-06 validates. Implementation should parameterize the sync interval.

## Validation Required Before Lock

| Item | What it determines | Blocks |
|------|-------------------|--------|
| H-06 | Which sync tier to implement | Sync cadence, rate limit strategy |
| VD-1 | Whether `hevy_routines` cache is needed | Routine push path |
| OA-3 | Whether "on-demand" freshness claim holds | Staleness tolerance settings |

## Risks

- **H-06 reveals Tier 3 or Tier 0:** Architecture still works but UX is significantly degraded. Tier 0 requires a product-level decision about whether Hevy dependency is viable.
- **Hevy API changes without notice:** The adapter boundary contains the blast radius. Cached data provides a buffer. But if response shapes change, the adapter's normalization layer must be updated.
- **Cache invalidation bugs:** Stale cache served as fresh data would cause workout generation to use outdated history. Mitigation: every cached record carries a `fetched_at` timestamp; consumers can check freshness.
