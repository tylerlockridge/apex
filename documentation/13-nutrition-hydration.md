# Feature: Nutrition & Hydration

*Created: 2026-03-28 | Updated: 2026-03-28 | Project: Apex*

---

## Feature Overview

**What it does:**
Manual food and water logging with offline-first persistence, daily macro tracking against targets, and dashboard integration. Built against the server nutrition/hydration contract (migration 014, merged via Health-Platform-Desktop PR #34).

**What it does NOT do:**
- Barcode scanning for packaged food lookup (future phase)
- USDA / Open Food Facts database integration (future phase)
- Food photo estimation (future phase)
- Adaptive TDEE calculation (future phase)
- Quick-add calories without a food record (future phase)
- Hydration reminders or notifications

---

## Data Model

### Room DB Tables (v2 migration)

| Table | Purpose | PK |
|-------|---------|------|
| `food_cache` | Locally cached foods (server-synced + custom) | `id` (server UUID or local UUID) |
| `food_entry_cache` | Today's food log entries | `id` |
| `water_entry_cache` | Today's water log entries | `id` |
| `nutrition_target_cache` | Date-effective calorie/macro targets | `effectiveDate` |
| `hydration_target_cache` | Date-effective hydration targets | `effectiveDate` |
| `nutrition_write_queue` | Pending offline writes (deduplicated) | `id` (auto-increment) |

### Sync State

All cache entities carry a `syncState` field:

| State | Meaning |
|-------|---------|
| `synced` | Server has this record |
| `pending_create` | Created locally, not yet on server |
| `pending_update` | Modified locally, server has old version |
| `pending_delete` | Marked for deletion, server still has it |
| `pending_upsert` | Target upserted locally, not yet on server |

### Pending Write Queue

The `nutrition_write_queue` table uses a dedupe key (unique index) to prevent duplicate writes. Action types:

- `create_food`, `create_food_entry`, `update_food_entry`, `delete_food_entry`
- `create_water_entry`, `delete_water_entry`
- `upsert_nutrition_target`, `upsert_hydration_target`

---

## Architecture

### Write Path (Offline-First)

```text
User action (log food / add water / set target)
  -> NutritionRepository: optimistic local insert (syncState = pending_*)
  -> NutritionRepository: enqueue PendingNutritionWriteEntity
  -> NutritionSyncWorker.runOnce(): trigger immediate drain attempt
  -> NutritionSyncWorker: for each pending write:
       -> ServerApiClient: POST/PATCH/DELETE/PUT to server
       -> On success: delete from queue, update cache syncState to synced
       -> On failure: leave in queue for retry
```

### Read Path (Cache-First)

```text
Screen loads
  -> ViewModel: read from local Room cache (instant)
  -> ViewModel: background refresh from server API
  -> ViewModel: update Room cache with server response
  -> UI: recompose with fresh data
```

### Key Classes

| Class | Role |
|-------|------|
| `NutritionCacheEntities.kt` | Room entities + sync state constants |
| `NutritionCacheDao.kt` | Room DAO with search, CRUD, target lookup, pending queue |
| `NutritionRepository.kt` | Offline-first repository mediating API, cache, and queue |
| `NutritionSyncWorker.kt` | WorkManager worker draining pending writes |
| `NutritionViewModel.kt` | Nutrition screen state management |
| `HydrationViewModel.kt` | Hydration screen state management |
| `NutritionScreen.kt` | Food search, entry list, macro summary, add/edit/delete |
| `HydrationScreen.kt` | Quick-add water, daily total, entry list |
| `NutritionTargetsScreen.kt` | Calorie + macro target editor |
| `HydrationTargetsScreen.kt` | Daily ml target editor |

---

## Server Contract

All endpoints are behind Bearer + HMAC auth (same as existing sync endpoints).

### Foods

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/foods` | Create custom food |
| `GET` | `/api/foods/search?q=&barcode=&limit=` | Search saved foods |

### Food Entries

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/food-entries` | Create entry from `food_id` |
| `GET` | `/api/food-entries?date=` | List entries for a date |
| `PATCH` | `/api/food-entries/:id` | Update servings/meal_type/notes |
| `DELETE` | `/api/food-entries/:id` | Delete entry |

**PATCH semantics:** Omitted fields are preserved; explicit `null` clears. The client uses `PatchField<T>` (tri-state: Unchanged/SetNull/SetValue) and builds a `JsonObject` body to handle this correctly. The pending-write queue uses a sentinel string (`__null__`) to encode "clear to null" in JSON payloads.

### Nutrition Daily & Targets

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/nutrition/daily?date=` | Daily totals + target + remaining |
| `GET` | `/api/nutrition/targets?date=` | Active target for date |
| `PUT` | `/api/nutrition/targets` | Upsert target by effective_date |

### Water Entries & Hydration Targets

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/water-entries` | Create water entry |
| `GET` | `/api/water-entries?date=` | List entries + daily totals + summary |
| `DELETE` | `/api/water-entries/:id` | Delete water entry |
| `GET` | `/api/hydration/targets?date=` | Active target |
| `PUT` | `/api/hydration/targets` | Upsert target |

---

## UI Screens

### Nutrition Tab (second in nav: Home / Nutrition / Trends / Training / Settings)

- **Macro summary card**: Calorie headline with target progress bar, protein/carbs/fat progress bars
- **Overshoot display**: When calories exceed target, shows "X over" in red instead of clamping to zero
- **Food entries by meal**: Grouped by breakfast/lunch/dinner/snack/other, each with kcal subtotal
- **Entry rows**: Tappable to edit (servings, meal type); swipe/X to delete; cloud icon for pending sync
- **FAB**: Opens add-food bottom sheet
- **Header actions**: Water drop icon → Hydration screen, target icon → Nutrition targets

### Add Food Bottom Sheet

- Search field (queries local cache + server in background)
- Create custom food button → dialog with name, brand, calories, protein, carbs, fat
- Food search results with name, brand, serving size, calories
- Food selection → confirm entry with servings slider + meal type chips

### Edit Entry Dialog

- Shows food name, current servings, meal type chips
- Tri-state meal type: tap selected chip to clear (sends null), tap different to change, leave as-is for unchanged
- Save updates local cache + enqueues pending write

### Hydration Screen (secondary route from Nutrition)

- Progress card: total ml vs target with progress bar, "X remaining" or "Goal reached!"
- Quick-add buttons: 250ml, 500ml, 750ml
- Custom amount field with Add button (validated: 1–10000 ml)
- Today's entry list with time, amount, delete

### Target Editors

- **Nutrition targets**: Calories, protein (g), carbs (g), fat (g) — date-effective upsert
- **Hydration target**: Daily ml with preset buttons (2000/2500/3000/3500)

### Dashboard Integration

- Nutrition tile: today's calories vs target with progress bar
- Hydration tile: today's ml vs target with progress bar
- Both tiles appear in a new "Nutrition & Hydration" section below health metrics (only shown when data exists)

---

## Validation Rules

| Rule | Where enforced |
|------|---------------|
| Water amount: 1–10000 ml | Repository (`require`), HydrationScreen (button disable), HydrationViewModel (guard) |
| Food name: 1–255 chars | Server schema validation |
| Food calories: >= 0 | Server schema validation |
| Servings: > 0 | Server schema validation, edit dialog button disable |
| Nutrition target: positive ints | Server schema validation |
| Hydration target: 1–20000 ml | Server schema validation |

---

## Background Sync

`NutritionSyncWorker` follows the same pattern as `SyncWorker`:

- **Scheduling**: 15-minute periodic with network constraint + exponential backoff
- **One-shot**: Triggered after every local write for immediate sync attempt
- **Queue drain**: Processes all pending writes in FIFO order
- **Error handling**: Transient failures → `Result.retry()`, unknown actions → drop
- **ID reconciliation**: When a custom food is created, the worker replaces the local UUID with the server UUID in both the food cache and any pending food-entry writes that reference it

---

## Design System

| Element | Token |
|---------|-------|
| Nutrition accent | `ApexNutritionAccent` = `#FB923C` (orange) |
| Hydration accent | `ApexHydrationAccent` = `#38BDF8` (sky blue) |
| Macro protein | `#60A5FA` (blue) |
| Macro carbs | `#A78BFA` (purple) |
| Macro fat | `#FBBF24` (amber) |

Cards follow the standard Apex pattern: gradient accent bar + accent border (alpha 0.18), no elevation.

---

## What's Deferred

| Feature | Why deferred | Dependency |
|---------|-------------|------------|
| Barcode scanning | Needs Open Food Facts server adapter | Server Phase 3 |
| USDA food database | Needs server USDA adapter | Server Phase 3 |
| Quick-add (calories only) | UX polish, not blocking | None |
| Adaptive TDEE | Needs 7+ days of data + weight trend | Nutrition logging active |
| Food photo estimation | Needs reliable manual logging first | This phase complete |
| Hydration reminders | Low priority, informational | None |
| Readiness integration | Nutrition context for readiness engine | Decision pending |

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| Room v2 migration | PASS | 6 tables + write queue |
| Offline write queue | PASS | Deduplicated, 8 action types |
| NutritionSyncWorker | PASS | Periodic + one-shot, backoff |
| Food search (local) | PASS | Name/brand/barcode LIKE search |
| Custom food creation | PASS | Optimistic local + queue |
| Food entry add/edit/delete | PASS | Full CRUD with tri-state PATCH |
| Water entry add/delete | PASS | Quick-add + custom amount |
| Hydration validation | PASS | 1–10000 ml enforced |
| Nutrition targets | PASS | Date-effective upsert |
| Hydration targets | PASS | Date-effective upsert with presets |
| Dashboard tiles | PASS | Calorie + hydration with progress |
| Overshoot display | PASS | Shows "X over" instead of clamping |
| PATCH null-vs-omit | PASS | PatchField tri-state + JsonObject body |
| Server contract alignment | PASS | Matches merged PR #34 / migration 014 |
| Production deployment | BLOCKED | Server at migration 013; 014 not yet deployed |
