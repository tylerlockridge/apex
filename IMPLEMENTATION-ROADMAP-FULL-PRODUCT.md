# Apex Full Product Implementation Roadmap

**Created:** 2026-03-28  
**Status:** Active replacement roadmap for current product planning  
**Scope:** Intended full Apex product across Android + Health-Platform-Desktop  
**Supersedes for current planning:** `IMPLEMENTATION-ROADMAP.md` (workout-first MVP roadmap)

---

## 1. Purpose

This document replaces the narrow workout-first MVP sequencing with a full-product execution plan that matches the intended Apex product:

- health sync and freshness users can trust
- daily nutrition and hydration logging
- barcode-backed food lookup
- richer goal-driven training progression
- food photo estimation as a nutrition accelerator
- AI coaching grounded in real cross-pillar context
- provider expansion beyond Health Connect where the current source path is insufficient

This roadmap is not a feature wishlist. It is the planning handoff for real implementation across:

- **Apex repo** — Android app, local caches, offline queues, UX, camera flows, on-device freshness handling
- **Health-Platform-Desktop repo** — schema, APIs, food/provider integrations, read models, orchestration, coaching, and deeper workout generation logic

## 2. Product Planning Reset

### Shipped baseline to preserve

The workout-first MVP shipped on 2026-03-23 remains a valid baseline:

- HC sync pipeline works
- readiness engine exists
- Hevy-backed workout generation exists
- secure onboarding, queueing, widget, and dashboard flows exist

### What changes now

That shipped baseline is **not** the product target. It is the starting layer beneath the full product. Current planning must optimize for:

1. **Daily value** — nutrition and freshness matter every day, not only on training days
2. **Dependency truth** — many high-value Android features depend on server schemas, APIs, and read-model work first
3. **Cross-pillar context** — nutrition, training, recovery, and coaching must converge instead of staying in isolated MVP slices
4. **Trustworthiness** — stale data, missing provider breadth, and placeholder schemas are acceptable in an MVP, not in the intended full application

## 3. Research Readiness

| Workstream | Research status | What is already good enough to build | Targeted new research still needed |
|---|---|---|---|
| Nutrition + hydration foundation | **Nutrition: sufficient**. **Hydration: partial** | Nutrition data model, logging UX, daily totals, targets, adaptive TDEE direction, offline cache pattern | Hydration target defaults, reminder strategy, and whether hydration should affect readiness numerically or remain informational first |
| Barcode + food database | **Sufficient** | USDA + Open Food Facts stack, barcode strategy, provenance expectations, cache direction | None before execution. Verify API quotas and payload quirks during implementation |
| Durable inbound cache + freshness read models | **Sufficient for execution** | Need for local/server read models, staleness surfacing, and trust gap are already clear | Exact freshness contract and invalidation policy need execution-time design, not new product research |
| Provider expansion beyond Health Connect | **Partial** | HC seam exists; fallback need is documented | Which provider should be first if HC stays stale: Oura direct, WHOOP direct, Garmin, or mixed-source. This must be grounded in Tyler's actual devices and missing metrics |
| Richer workout builder / goal-driven progression | **Sufficient for hypertrophy expansion; partial for non-hypertrophy modes** | Mesocycle, deload, split support, MRV logic, richer progression are already researched enough | Which goal mode should follow hypertrophy first: PRT prep vs. race training |
| Food photo estimation | **Sufficient** | Vision model choice, UX, validation rules, privacy model, before/after optionality | None before execution. If scale integration is desired later, that is separate research |
| AI coach | **Sufficient for foundation** | Context model, endpoints, safety rails, chat UX, model routing concept | Execution-time verification of exact provider/tooling choices for memory and streaming |

### Research rule

No new research is required to start the first implementation phase. The targeted research items above should run in parallel where noted and only block the specific branches they affect.

## 4. Repo Ownership Map

| Workstream | Apex repo | Health-Platform-Desktop repo | Primary owner |
|---|---|---|---|
| Nutrition + hydration foundation | Screens, local cache, offline queue, dashboard cards, settings toggles | Tables, CRUD APIs, daily totals, targets, server summaries | Server first, then Apex |
| Barcode + food database | Camera flow, barcode UX, local recent-food cache, correction UI | USDA/OFF adapters, normalized food search, provenance, cache refresh | Both |
| Freshness + durable read models | Local persisted inbound cache, stale banners, cache invalidation on sync | Freshness metadata, summary/read-model endpoints, source timestamps | Both |
| Provider expansion | Provider selection UI, auth surfaces, client sync adapter work | Source provenance contract, server acceptance, source-specific summaries | Apex first for data-source plumbing, both for contracts |
| Workout builder expansion | Review UX, goal selection, plan display, new cards/screens | Progression engine, mesocycles, deloads, goal-mode generation, Hevy linkage | Server primary |
| Food photo estimation | Capture, validation UX, edit/confirm flow, local API client | Optional persistence of estimation result only, nutrition entry acceptance | Apex primary |
| AI coach | Chat UI, notifications, local recent-history cache | Context assembly, orchestration, message persistence, plan generation, safety checks | Server primary |

## 5. Dependency Principles

1. **Server primitives before dependent Android UX.** Nutrition, hydration, freshness, and coaching all need server truth first.
2. **Manual nutrition before photo nutrition.** Photo estimation is an accelerator, not the base path.
3. **Freshness/read-model work before AI coach.** Coaching on stale or missing context is worse than no coaching.
4. **Workout depth expansion after nutrition/freshness are active.** The existing generator already covers a thin MVP; the full product now needs broader daily utility first.
5. **Provider expansion is conditional implementation, not speculative implementation.** Start with telemetry and source attribution, then build the first direct provider only if the actual missing-data profile justifies it.

---

## 6. Full Product Phases

### Phase 1: Server Foundation for Nutrition, Hydration, and Freshness

**Start immediately after this roadmap is written.**  
**Primary repo:** `Health-Platform-Desktop`  
**Apex repo role:** contract consumer only  
**Why this goes first:** It unlocks the first real user-facing nutrition/hydration slice and the freshness work required for later coaching. It is the highest-value dependency cluster and cannot be bypassed by Android-first work.

#### In scope

1. **Activate nutrition APIs from the existing schema work**
   - food search
   - food CRUD where needed for custom foods
   - food entry create/edit/delete
   - daily totals and daily target endpoints
2. **Add hydration model and API**
   - `water_entries` / `hydration_entries`
   - daily hydration totals
   - hydration targets
3. **Add freshness/read-model contract**
   - source timestamps for BP, sleep, HRV, body, workouts
   - explicit freshness classification in server responses
   - summary endpoint shape stable enough for Apex dashboard/read-model caching
4. **Define source provenance contract**
   - HC source
   - future direct-provider source values
   - nutrition provenance values exposed consistently
5. **Deployable server migration/application plan**
   - verify production schema state
   - apply or reconcile future-pillar migrations already referenced by the MVP planning docs
   - add hydration migration(s) if absent

#### Acceptance criteria

- Server exposes working endpoints for food search, food entries, nutrition daily totals, nutrition targets, hydration entries, hydration totals, and hydration targets
- Server summaries include explicit freshness metadata for HC-derived metrics and workouts
- Nutrition and hydration contracts are documented well enough for Apex implementation without guesswork
- Existing workout-generation and sync flows remain green
- At least one contract test exists for each new endpoint family

#### Key risks

- Production schema may not fully match the historical MVP planning docs
- Hydration is under-researched compared with nutrition, so target defaults may need to ship simple first
- Freshness contract design can sprawl if it tries to solve every future provider edge case up front

#### Targeted new research

- Hydration target/reminder defaults: needed, but not blocking
- None required for nutrition core or freshness metadata shape

---

### Phase 2: Apex Nutrition and Hydration Core

**Primary repo:** `Apex`  
**Depends on:** Phase 1

#### In scope

1. **Nutrition screen and flows**
   - manual food search
   - recent/favorite foods
   - quick add
   - entry editing
   - daily totals vs target
2. **Hydration logging**
   - quick-log water actions
   - manual quantity entry
   - daily total vs target
3. **Offline support**
   - food-entry queue
   - hydration-entry queue
   - local recent-food cache
4. **Dashboard integration**
   - nutrition summary card
   - hydration summary card

#### Acceptance criteria

- User can log food and water without leaving the app
- Nutrition and hydration remain usable offline and replay on reconnect
- Dashboard surfaces today’s nutrition and hydration state
- Existing sync/security flows continue to work
- UI tests or equivalent smoke coverage exist for the core entry flows

#### Key risks

- Nutrition UX can become slow if search, recent foods, and quick-add are not treated as first-class
- Hydration can become low-value clutter if the flow is slower than a one-tap action

#### Targeted new research

- Decide whether hydration contributes to readiness numerically in this phase or remains separate until observed usage exists

---

### Phase 3: Barcode and Food Database Vertical Slice

**Primary repos:** `Health-Platform-Desktop` + `Apex`  
**Depends on:** Phase 2 for usable nutrition destination

#### In scope

1. **Server food adapters**
   - Open Food Facts barcode lookup
   - USDA enrichment / generic fallback
   - normalization into the `foods` domain
2. **Apex barcode flow**
   - reuse CameraX/ML Kit pipeline
   - scan packaged foods into review-confirm-log flow
3. **Quality and provenance UX**
   - show source and confidence/provenance
   - let user correct serving size before save

#### Acceptance criteria

- Barcode scan produces a food review screen and successful log flow
- Server distinguishes validated vs community-sourced results consistently
- Common packaged-food lookup works end-to-end without manual JSON cleanup
- Manual search remains available as fallback

#### Key risks

- OFF quality variance can erode trust if provenance is hidden
- Barcode flow will feel broken if the fallback path is unclear when a code is missing

#### Targeted new research

- None before build

---

### Phase 4: Durable Inbound Cache, Read Models, and Provider Readiness

**Primary repos:** `Health-Platform-Desktop` + `Apex`  
**Depends on:** Phase 1 server freshness contract

#### In scope

1. **Apex inbound persistence**
   - durable local cache for trends/activity/server summaries
   - explicit stale-state UX
   - refresh/invalidation rules
2. **Server read-model support**
   - stable summary endpoints for dashboard/trends/activity
   - freshness/source metadata carried through consistently
3. **Provider-readiness instrumentation**
   - identify stale or absent metrics by source
   - log which data types are actually missing from HC vs merely old
4. **Provider expansion decision gate**
   - pick the first direct provider only after the telemetry clarifies the actual gap
   - define whether the first direct source is Oura, WHOOP, Garmin, or none

#### Acceptance criteria

- Trends/activity/dashboard can render from durable local data when the server is temporarily unavailable
- Apex clearly distinguishes stale data from empty data
- Server read models and Apex caches agree on freshness semantics
- A documented provider decision is produced from telemetry, not guesswork

#### Key risks

- Cache duplication can create inconsistent truth if invalidation is sloppy
- Provider expansion can get prematurely triggered before telemetry is actually useful

#### Targeted new research

- **Required in this phase:** choose first direct provider based on Tyler’s actual device/data gaps

---

### Phase 5: Goal-Driven Workout Builder Expansion

**Primary repo:** `Health-Platform-Desktop`  
**Apex repo role:** goal selection, richer review UX, progression displays  
**Depends on:** Phases 1-4 enough to ensure freshness and nutrition can later influence training cleanly

#### In scope

1. **Hypertrophy depth beyond MVP**
   - mesocycles
   - deload logic
   - richer progression history
   - adherence / substitution analysis
2. **Goal-mode structure**
   - choose first non-hypertrophy mode after targeted decision: PRT prep or race training
   - keep the other mode explicitly queued, not vague
3. **Improved prescribed-to-actual flow**
   - push-to-Hevy path if supported and worth building
   - otherwise better explainability and reconciliation

#### Acceptance criteria

- Workout generation is no longer only a single-workout MVP slice
- At least one expanded goal mode beyond current hypertrophy MVP exists or is actively implemented after a documented decision
- Apex review UX supports the richer output without collapsing into a raw JSON-style screen

#### Key risks

- This work can sprawl into a second long MVP if goal-mode scope is not pinned
- PRT and race support need explicit prioritization instead of both being half-started

#### Targeted new research

- **Required before non-hypertrophy build:** decide first goal mode to ship after hypertrophy depth: PRT or race

---

### Phase 6: Food Photo Estimation

**Primary repo:** `Apex`  
**Server repo role:** accept normalized estimated entries if needed, but keep photos off server by default  
**Depends on:** Phases 2-3

#### In scope

1. single-photo capture flow
2. AI estimate review/edit flow
3. nutrition entry save with provenance
4. optional before/after design reservation, but not necessarily in the first slice

#### Acceptance criteria

- User can capture, review, correct, and log a meal photo end-to-end
- Photo entries remain clearly marked as estimated/corrected
- Manual nutrition remains the primary path for ambiguous meals
- Original photos are not persisted by default

#### Key risks

- If the correction flow is clumsy, photo logging becomes slower than manual entry
- Over-selling accuracy will damage trust

#### Targeted new research

- None before execution
- Before/after can remain an implementation choice after the single-photo path works

---

### Phase 7: AI Coach Foundation

**Primary repo:** `Health-Platform-Desktop`  
**Apex repo role:** chat UI, notifications, local recent-history cache  
**Depends on:** Phases 2-6 enough to provide real cross-pillar context

#### In scope

1. **Server orchestration**
   - coach endpoints
   - context assembly
   - conversation persistence
   - safety validation
2. **Apex coach UI**
   - chat screen
   - recent-history rendering
   - notification entry points
3. **Proactive guidance**
   - morning briefing
   - trend-based prompts
   - post-workout and under-fueling style nudges only after data quality is trustworthy

#### Acceptance criteria

- Coach responses cite real Apex data and never fabricate missing metrics
- Conversation history persists across sessions
- Apex can render chat, actions, and at least one proactive entry point
- Safety rails are testable, not just prompt text

#### Key risks

- Coaching will feel generic if launched before nutrition/freshness context is real
- Notification fatigue can undermine retention if proactivity ships without restraint

#### Targeted new research

- Execution-time verification of exact memory/streaming/tooling stack

---

## 7. First Implementation Phase to Start Now

### Phase 1: Server Foundation for Nutrition, Hydration, and Freshness

This is the first phase that should begin immediately after this roadmap is accepted.

### Why it goes first

1. The highest-value missing feature cluster is nutrition, not another Android-only refinement of the shipped workout MVP.
2. The Android app cannot build real nutrition or hydration UX without stable server contracts.
3. Freshness and read-model work are preconditions for trustworthy coaching later.
4. This is the most honest cross-repo starting point: the first real implementation slice is in **Health-Platform-Desktop**, not Apex.

### Immediate handoff after this doc

- **Repo:** `C:\Users\tyler\Documents\health-rollout-worktree\Health-Platform-Desktop`
- **Phase:** Phase 1 from this roadmap
- **Focus:** nutrition/hydration API foundation plus freshness/read-model contract

## 8. Sequencing Risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| Android-first nutrition build without server contracts | Leads to rework, mocked flows, or speculative client models | Start in server repo with Phase 1 |
| Treating barcode as Phase 1 | Barcode is high-value, but still depends on nutrition destination flows | Ship manual nutrition first, then barcode |
| Launching AI coach before freshness/nutrition are real | Produces generic or wrong advice and burns trust early | Keep coach after the data pillars are live |
| Premature provider implementation | Wrong provider may be built if the actual missing-data source is misunderstood | Use Phase 4 telemetry and a decision gate |
| Letting the workout MVP expand before nutrition exists | Repeats the same product-narrowing mistake | Hold workout depth expansion until nutrition/freshness foundation is underway |

## 9. Definition of Success for This Reset

This roadmap succeeds if the next execution threads stop asking "what is next after the MVP?" and instead execute against this sequence:

1. Server nutrition/hydration/freshness foundation
2. Apex nutrition/hydration core
3. Barcode/data vertical slice
4. Durable read models and provider decision
5. Workout depth expansion
6. Food photo
7. AI coach

If execution drifts back to MVP-only framing, this roadmap has failed even if the docs remain accurate.
