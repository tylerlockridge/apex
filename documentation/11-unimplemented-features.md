# Feature: Unimplemented Features & Known Gaps

*Created: 2026-03-02 | Updated: 2026-03-28 | Project: Apex*

---

## Feature Overview

**What it does:**
Tracks the gap between the intended full Apex product and the currently shipped
workout-first baseline across both the Android app and the Health-Platform-Desktop server.

**What it does NOT do:**
- Does not duplicate detailed testing strategy work (see `10-testing-strategy.md`)
- Does not duplicate security implementation detail (see `04-security-implementation.md`)
- Does not replace the authoritative cross-repo build sequence; see
  `IMPLEMENTATION-ROADMAP-FULL-PRODUCT.md`

---

## Historical Note

This document previously read like an MVP close-out checklist and implied that the
remaining gaps were mostly optional polish. That framing is no longer accurate.

The shipped workout-first slice is now treated as a useful baseline, not the product
definition. The intended product includes nutrition, hydration, food intelligence,
freshness-aware health data, richer training intelligence, and AI coaching. Anything
required to deliver that broader product remains a real gap even if the MVP shipped
successfully.

---

## Current Shipped Baseline

| Area | What is already shipped |
|------|-------------------------|
| Health sync client | Health Connect sync for BP, sleep, HRV, and body data with background worker, HMAC signing, and offline queue |
| Workout history | Hevy sync, activity display, workout history ingestion, and manual sync trigger |
| Workout guidance | Server-side workout generation MVP with review-before-execute flow |
| Readiness | Client-side readiness card with sleep/BP/training-load inputs and HRV-ready wiring |
| Security / onboarding | QR onboarding, certificate pinning, API signing, lock flow, sync history detail |

This baseline matters because the full-product plan should build on it rather than
pretend the repo is starting from zero.

---

## Full-Product Gaps

| Area | Intended full-product target | Current shipped state | Repo owner | Gap status |
|------|------------------------------|-----------------------|------------|------------|
| Nutrition logging | Manual meal logging, food entry UX, macro/micro totals, daily targets, meal history | Nutrition tables exist on server but no active routes, sync contracts, or Android UX | Both | Researched foundation exists, feature remains unbuilt |
| Hydration logging | Water intake capture, daily goal tracking, hydration contribution to readiness/coaching | No dedicated hydration schema, routes, local store, or UI | Both | Fully missing |
| Barcode + food database | Barcode scan to validated food item, fallback search, source provenance, serving normalization | No barcode flow, no USDA/Open Food Facts integration, no canonical food search API | Both | Research partially ready, implementation missing |
| Food photo estimation | Photo capture/import, estimate request, confidence handling, correction flow, nutrition-entry handoff | No API contract, no model pipeline, no Android capture flow for nutrition estimation | Both | Researched but unbuilt |
| Durable inbound cache + freshness | Server-side durable inbound cache, freshness metadata, source provenance, stable read models for Apex | MVP relies heavily on live server reads and limited freshness semantics; no nutrition-grade read model layer | Both | Partial foundation only |
| Sleep / HRV / BP freshness | Reliable freshness scoring, stale-data handling, historical durability, explicit source timestamps | Sync exists, but stale upstream data and weak freshness read models limit decision quality | Both | Partial |
| Provider breadth beyond Health Connect | Clear strategy for when HC is enough vs when direct wearable/provider integrations are needed | Client seam exists via `HealthDataProvider`; no direct provider integrations or operational policy | Both | Targeted new research + implementation missing |
| Training history beyond Hevy | Broader training history ingestion or normalization when Hevy is incomplete or not the only source | Hevy is the only real training history source in the shipped product | Both | Mostly missing |
| Workout builder depth | Goal-driven blocks, mesocycle logic, deloads, broader exercise selection logic, progression intelligence beyond single-session generation | Workout generation MVP exists, but scope is narrow and focused on single-session generation from Hevy history | Both | Researched baseline exists, major expansion unbuilt |
| Goal support | Support for hypertrophy, strength, fat loss / recomposition, recovery-aware modifications, and user goals that influence plans | No full goal model or end-to-end goal-aware progression system | Both | Mostly missing |
| AI coach | Conversational guidance, context assembly, explanation layer, safety boundaries, memory/events, coaching actions grounded in user data | Coaching tables exist but no active endpoints, prompt pipeline, chat UI, or safety/validation loop | Both | Researched but unbuilt |
| Data model + API contracts | Stable nutrition/hydration/photo/coach contracts across server and Android, with provenance and cache strategy | MVP contracts mainly cover sync + workout slice; broader product APIs are not yet built | Both | Partial / server-first gap |
| Validation + testing | Cross-repo test coverage for nutrition, barcode, freshness, photo estimates, and coaching behaviors | Existing tests focus on MVP sync/workout flows; broader product validation harnesses do not exist | Both | Mostly missing |

---

## Scope Narrowing That Caused Drift

| Narrowed decision | Why it helped the MVP | Where it diverged from full-product intent |
|-------------------|-----------------------|-------------------------------------------|
| Workout generation shipped before other pillars | Delivered a concrete training loop quickly | The sequencing quietly normalized nutrition, hydration, and coaching as optional instead of simply later in sequence |
| Nutrition/coaching schemas were created empty | Preserved future extensibility without blocking MVP | Schema existence was mistaken for product readiness even though no routes, UIs, or validated data flows were built |
| Health Connect + Hevy were treated as sufficient sources | Simplified integration risk and let the app ship | Full product needs source freshness, fallback strategy, and training/health ingestion beyond a single happy-path provider mix |
| Readiness focused on currently synced inputs | Kept the client shippable | Full readiness and coaching need hydration, nutrition quality, durable freshness, and better upstream data confidence |

---

## Priority View

If Apex is being planned against the intended product, the highest-signal missing areas are:

1. Nutrition + hydration foundation
2. Barcode + canonical food database integration
3. Durable inbound cache and freshness/read-model work
4. Goal-driven workout builder expansion
5. AI coach foundation
6. Food photo estimation
7. Provider expansion strategy where Health Connect is insufficient

For execution order and acceptance criteria, use
`IMPLEMENTATION-ROADMAP-FULL-PRODUCT.md` as the governing planning document.
