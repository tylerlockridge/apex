# Apex Full Product Gap Matrix

*Created: 2026-03-28 | Scope: product-reset audit against the intended full Apex product, not the shipped workout-first MVP*

---

## Audit Frame

This audit treats the workout-first MVP as a historical sequencing decision, not the governing product scope.

Evidence base used for this reset:
- `README.md`
- `IMPLEMENTATION-ROADMAP.md`
- `documentation/01-architecture-overview.md`
- `documentation/11-unimplemented-features.md`
- `documentation/12-business-rules-and-edge-cases.md`
- `RESEARCH-BRIEF-nutrition.md`
- `RESEARCH-BRIEF-food-photo.md`
- `RESEARCH-BRIEF-workout-builder.md`
- `RESEARCH-BRIEF-ai-coach.md`
- `RESEARCH-BRIEF-supplements.md`
- `ADR-001-hevy-abstraction-and-sync-strategy.md`
- `ADR-004-workout-generation-reconciliation-model.md`
- `ADR-005-health-data-source-abstraction.md`
- `ARCHITECTURE-ASSUMPTIONS.md`
- `DECISION-REGISTER.md`
- `PRE-ARCHITECTURE-PLANNING-MEMO.md`

## Core Reset Finding

The shipped product diverged because sequencing decisions were allowed to harden into scope decisions. The repo successfully shipped the workout-first MVP on 2026-03-23, but that MVP then became the de facto definition of "Apex complete enough for daily use." The broader product intent remained present in research, architecture, and schema-planning docs, yet those pillars were never converted into active app/server delivery tracks.

## Where Scope Narrowed

| Narrowing point | Broader intent | What actually happened | Product consequence |
|---|---|---|---|
| `D-02` workout-first sequencing | Ship a multi-pillar product in phases, with nutrition, coaching, photo, and supplements still treated as real future pillars | The implementation roadmap and ship criteria centered only the workout/readiness/Hevy loop | The shipped product looks "complete" if judged by the MVP, but incomplete if judged by the intended full app |
| `P-05` workout MVP boundary | MVP boundary was supposed to contain only the first workout slice | The boundary became the effective long-term product definition | Mesocycles, broader goals, coaching actions, and nutrition-informed progression never moved onto the active plan |
| `D-15` design-now-build-later | Nutrition, supplement, and coaching schemas were intentionally placeholders for later activation | Empty schemas and research briefs substituted for delivered capability | Full-product pillars have architecture shape but no usable feature path |
| Post-ship daily-use posture | Daily use was meant to validate the MVP loop and reveal next build priorities | Docs shifted into passive monitoring mode after MVP ship | Freshness, provider breadth, nutrition, and coaching gaps were normalized instead of turned into a reset roadmap |

---

## Full Product Matrix

| Capability area | Intended product | Current shipped state | Researched but unbuilt | Fully missing | Scope narrowing / divergence | Owner |
|---|---|---|---|---|---|---|
| Provider breadth and data ingestion | Health Connect is the starting point, not the forever-only source. `ADR-005` and `ARCHITECTURE-ASSUMPTIONS.md` expect fallback paths if HC proves stale or incomplete. | `HealthDataProvider` seam exists, but only `HealthConnectProvider` is implemented. Body still full-reads. Provider selection is hardcoded. | WHOOP/Oura/Garmin fallback is acknowledged in `ADR-005`, `ARCHITECTURE-ASSUMPTIONS.md`, and `DECISION-REGISTER.md`. | No direct provider implementations, no auth flows, no multi-provider merge rules, no per-source trust/reconciliation model. | The seam shipped, but the actual breadth never did. H-04 was treated as post-MVP monitoring instead of a near-term delivery risk for the full product. | Both |
| Sleep / HRV / BP freshness and durable inbound caching | Fresh readiness, fresh coaching context, durable local/server read models, and visible staleness remediation. | Outbound sync is durable via Room, but inbound read state is not. Dashboard/widget summaries live in SharedPreferences. Trends and workouts are live-read. HRV remains stale in shared state. | Readiness staleness handling exists. HC monitoring and provider fallback were documented. | No durable inbound history cache for trends/workouts, no freshness watchdog UX, no server-side "fresh enough for coaching" contract, no automatic stale-data recovery path. | MVP accepted stale data as non-blocking because sync plumbing worked; the full product cannot do that once coaching, nutrition, and adaptive logic depend on freshness. | Both |
| Training history sources beyond Hevy | Full training context for hypertrophy, PRT, race prep, and future coaching modes, not just Hevy strength logs. | Hevy is the only workout-history source. Apex reads server-fetched Hevy workouts and progression signals only. | `RESEARCH-BRIEF-ai-coach.md` sketches PRT and race modes conceptually, but not their ingestion model. | No non-Hevy source integrations, no manual training log, no cardio/running history ingestion, no ruck/conditioning schema, no source-normalized training timeline. | The MVP closed the loop around Hevy because it was the quickest differentiator, but the broader training product never expanded past that single-source assumption. | Both |
| Workout generation depth, goal support, and progression intelligence | Progressive overload with clear reasoning, split support, mesocycles, deloads, broader goal modes, and stronger prescribed-to-actual intelligence. | The shipped generator covers the MVP slice: readiness input, volume/MRV signals, 2-for-2 progression, review/accept flow, Path B manual execution. | `RESEARCH-BRIEF-workout-builder.md` defines split types, mesocycle structure, deload logic, goal modes, and deeper progression rules. `ADR-004` leaves room for richer reconciliation. | No push-to-Hevy path, no mesocycle planner, no deload automation, no goal-mode switching, no adherence analysis, no exercise substitution analysis, no race/PRT planning UX. | `P-05` was treated as "enough product" instead of "first workout slice." The differentiator shipped, but only its thinnest form. | Both |
| Recovery context completeness for training decisions | Readiness and coaching should eventually combine sleep, BP, HRV, training load, subjective readiness, nutrition, and hydration context. | Readiness is health + training only. HRV is still weighted at `0.0`. No subjective readiness UI. No nutrition or hydration inputs. | `ADR-003`, `RESEARCH-BRIEF-nutrition.md`, and `RESEARCH-BRIEF-ai-coach.md` all assume richer context later. | No subjective readiness capture, no under-fueling detection, no hydration input, no cross-pillar recovery model. | The readiness engine shipped as a workout-MVP dependency, not as the broader recovery intelligence layer originally implied by the full product. | Both |
| Nutrition logging | Precision-capable food logging with manual search first, recent/favorite foods, quick add, macros, body-composition workflows, and adaptive TDEE. This is a locked product decision in `D-01`. | No user-facing nutrition logging ships in the app. No nutrition screen, no food search, no entry flow, no daily totals, no targets, no TDEE UX. | `RESEARCH-BRIEF-nutrition.md`, `DECISION-REGISTER.md`, and `ARCHITECTURE-ASSUMPTIONS.md` define the model, endpoints, caching, and adaptive TDEE direction. | No usable nutrition feature surface at all. Even if empty nutrition tables exist in server code, they are not a shipped capability. | A table-stakes pillar was reduced to schema placeholders because the MVP roadmap became the active scope. | Both |
| Water / hydration logging | A full daily fitness/nutrition app needs either first-class hydration tracking or an explicit product decision to exclude it. | No hydration feature exists in the app or server. | No dedicated hydration research brief or schema work was found in the current corpus. | No intake model, no UI, no quick-log flow, no dashboard card, no coaching input, no target/reminder logic. | Hydration never even entered the active planning set. This is not "deferred implementation"; it is an unscoped product hole. | Both |
| Barcode scanning and food database quality | USDA FoodData Central + Open Food Facts, barcode-first packaged-food lookup, source provenance, and cached recent foods. | QR scanning infrastructure exists for onboarding, but there is no food barcode flow. No food database integration ships. | `RESEARCH-BRIEF-nutrition.md`, `ARCHITECTURE-ASSUMPTIONS.md`, and `DECISION-REGISTER.md` define USDA + OFF as the intended stack. | No barcode lookup UI, no OFF/USDA server integration, no food cache, no provenance surfacing, no correction flow. | The codebase has the camera foundation, but the entire food-data pillar stopped before any user-facing work started. | Both |
| Food photo estimation | Optional accelerator for nutrition logging after manual logging works well. Client-direct vision call, editable estimates, before/after flow possible. | No food-photo feature ships. Only QR scanning exists. | `RESEARCH-BRIEF-food-photo.md` is detailed: model choice, validation, privacy, UX, and before/after flow. | No capture flow, no AI clients, no validation rules, no correction UI, no estimate-to-entry pipeline. | Photo was intentionally secondary to manual nutrition, but because manual nutrition never shipped, the accelerator never had a base to attach to. | Both |
| AI coach / conversational guidance | Proactive briefings, trend correlation, persistent memory, chat, clear safety rails, goal-aware coaching, and action suggestions grounded in real data. | No coach tab, no chat UI, no coach endpoints, no conversation persistence, no proactive coaching notifications. | `RESEARCH-BRIEF-ai-coach.md`, `D-16`, and `P-07` lay out context assembly, endpoints, memory, and safety expectations. | No orchestration layer, no message storage in production, no client cache for messages, no structured action execution, no coach-quality evaluation harness. | Coaching was discussed as a real pillar, but only its future table and context assumptions were carried forward. No actual feature foundation shipped. | Both |
| Data model and API dependencies | A coherent app/server contract spanning health sync, food entries, nutrition targets, TDEE, coaching, and future source/provider provenance. | Health sync and workout-generation APIs exist. The product-wide contract stops there. | Nutrition, coaching, supplement, and photo flows all have documented schemas/endpoints in briefs and ADR-adjacent docs. | No deployed nutrition/coach API surface, no hydration model, no executable cross-pillar contract, no unified repository layer on app side. | "Schema exists" was treated as progress, but full-product delivery needs deployed and exercised APIs, not placeholder migrations. | Both |
| Validation and testing | End-to-end quality gates for sync, nutrition logging, freshness, photo estimation, coaching safety, and multi-provider failover. | Current tests are centered on the shipped sync/UI/workout loop. | Nutrition/photo/coach briefs include risk notes and phased plans; the old `README.md` also sketches broader test categories. | No real acceptance plan for nutrition, hydration, photo estimation, coaching safety, or alternate-provider reliability. No full-product ship criteria. | The project has MVP ship criteria, but not full-product ship criteria. That allowed the roadmap to close while most pillars never entered validation. | Both |
| Supplement tracking and outcome correlation | Lightweight checklist logging plus later correlation against biometrics, feeding coach context without unsafe interaction advice. | No supplement UI or workflow ships. | `RESEARCH-BRIEF-supplements.md`, `D-15`, `P-02`, and the planning memo define the concept clearly. | No checklist UX, no stack definition flow, no outcome-correlation graph, no reminders, no coach integration path. | Supplements were treated as a distant pillar, but the full product intent still includes them. They remain entirely outside active delivery. | Both |

---

## Highest-Value Missing Capabilities

Ranked by combined user impact and dependency weight for the full product reset.

| Rank | Capability | Why it ranks this high | Primary owner |
|---|---|---|---|
| 1 | Nutrition logging core: food search, entry flow, daily totals, targets | It is both a table-stakes daily-use pillar and a dependency for adaptive TDEE, under-fueling awareness, and meaningful coaching. | Both |
| 2 | Barcode + validated food database integration (USDA + OFF) | Without trustworthy and fast food lookup, nutrition logging becomes too slow to retain. This is the backbone of the nutrition pillar. | Both |
| 3 | Durable inbound freshness model for sleep / HRV / BP | Freshness is a prerequisite for trustworthy readiness, future coaching, and any cross-pillar correlation work. | Both |
| 4 | Full nutrition-derived recovery context: adaptive TDEE, under-fueling, body-composition loop | This turns Apex from a sync/dashboard app into an actual daily decision engine. | Both |
| 5 | AI coach foundation: endpoints, context assembly, chat UI, memory, safety harness | Coaching is one of the major product pillars and depends on already-shipped data sources plus the nutrition pillar. | Both |
| 6 | Workout generation expansion beyond MVP: goal modes, mesocycles, deloads, stronger reconciliation | The differentiator exists, but it is still the thinnest viable slice. Expanding depth increases long-term value. | Both |
| 7 | Provider breadth / HC fallback implementations | This is a risk-reduction capability with major downstream impact on freshness and trust if HC proves incomplete or stale. | Both |
| 8 | Food photo estimation attached to a solid manual logging flow | High convenience upside, but only after the manual nutrition path is dependable and editable. | Both |
| 9 | Training history beyond Hevy | Important for PRT/race/conditioning goals and future coaching accuracy, but it depends on first clarifying the target non-Hevy sources. | Both |
| 10 | Hydration logging | Lower dependency weight than nutrition, but it is still a glaring daily-living gap in a full health and performance product. | Both |

## Recommended Reset Direction

The next roadmap should stop asking "what is left after the MVP?" and instead ask "what must exist for Apex to count as the intended full product?" In practice, that means re-baselining around four active delivery tracks:

1. Nutrition core
2. Data freshness and source reliability
3. Coaching foundation
4. Workout depth beyond the MVP slice
