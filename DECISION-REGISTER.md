# Apex v2 Decision Register

**Date:** 2026-03-15 | **Last updated:** 2026-03-15 (post-gate normalization applied)
**Input:** Pre-Architecture Planning Memo + Gate Review + Blocker Resolutions + Newly Surfaced Architectural Assumptions + Post-Gate Review
**Purpose:** Classify every major item as Decision, Hypothesis, Assumption, or Preference with full metadata. Produce safe-to-lock, validate-before-locking, and defer-until-after-MVP lists.

---

## Full Register

### Decisions (D): Firm commitments backed by evidence + explicit user choice

---

#### D-01: Precision-capable nutrition model

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Nutrition architecture supports accurate manual logging, macro tracking, adaptive TDEE, body-composition workflows. Photo estimation is secondary convenience, not primary truth. Underlying model distinguishes estimated vs. verified entries. |
| **Evidence basis** | Tyler's explicit blocker resolution. Supported by: MacroFactor's adaptive TDEE as key differentiator (r/MacroFactor migration threads), Cronometer's precision as brand anchor (J. Human Nutrition, Oct 2025). |
| **Confidence** | High — explicit user decision, aligned with market evidence for precision-oriented users |
| **Dependencies** | USDA + OFF database integration, food entry provenance field (D-13), client-side TDEE algorithm (D-06) |
| **Risks** | Precision logging is higher friction than awareness logging. If Tyler's actual eating patterns make precision impractical, the model supports falling back to quick-add (calories + protein only) without breaking the schema. |
| **Validation still needed** | 1-week personal logging test to confirm friction tolerance for precision tracking |
| **Impact if wrong** | Over-engineered nutrition model that Tyler uses in quick-add mode anyway. Low architectural cost — precision-capable schema is a superset of awareness-only schema. |
| **When decided** | Before architecture (resolved) |

---

#### D-02: Workout-first execution sequence

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Workout programming/logging intelligence ships before nutrition. Nutrition and supplement schemas are designed in parallel but implemented second (D-15). AI coaching ships in workout-context-only mode first, gaining nutrition context when that pillar ships. |
| **Evidence basis** | Tyler's explicit blocker resolution. Supported by: gate review finding that workout generation is smaller-scope + higher-differentiation + builds on existing Hevy integration. Nutrition is a larger build (~equivalent to a second app). |
| **Confidence** | High — explicit user decision, supported by scope/differentiation analysis |
| **Dependencies** | Hevy API stability (H-01), readiness scoring algorithm (H-03), server workout schema, exercise-to-muscle-group mapping (A-02) |
| **Risks** | If the Hevy API breaks or HRV-strength correlation doesn't hold, the first shipped feature loses its differentiator. Mitigated by: abstraction layer (D-10a), readiness scoring works independently of workout generation. |
| **Validation still needed** | Hevy API rate limit testing (H-06), HRV-strength correlation (H-02) |
| **Impact if wrong** | Workout feature ships first but doesn't deliver expected value → nutrition becomes urgent, but schema is already designed (low pivot cost). |
| **When decided** | Before architecture (resolved) |

---

#### D-03: Hybrid compute split

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Client-first for deterministic, personal, low-cost calculations (TDEE, readiness scoring). Server-backed for sync, history aggregation, AI orchestration, workout generation (needs full Hevy history). Photo estimation client-direct-to-AI-API. |
| **Evidence basis** | Tyler's explicit blocker resolution. Principle: core trust features should not depend on constant server execution. |
| **Confidence** | High — explicit user decision with clear rationale |
| **Dependencies** | Room DB must cache enough local data for client-side TDEE and readiness. Server must expose Hevy history for workout generation. |
| **Risks** | Client-side TDEE requires local weight history in Room (new data flow). Readiness scoring client-side means the score may differ from what server would compute if data is stale. |
| **Validation still needed** | HC data freshness on Tyler's device (H-04) |
| **Impact if wrong** | Some calculations may need to migrate server-side if client data is too stale or incomplete. Architecture should use clean interfaces that allow migration. |
| **When decided** | Before architecture (resolved) |

---

#### D-04: Food database quality protection

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | USDA FoodData Central as primary source. Open Food Facts for barcode lookup, flagged as community-sourced. No unverified user submissions overwrite validated entries. Custom foods apply to user's own account only. |
| **Evidence basis** | MFP database decay (decade of r/Myfitnesspal complaints), MacroFactor crowdsourcing decay (r/MacroFactor, Feb 2026, 37-upvote complaint), J. Human Nutrition study (Oct 2025). |
| **Confidence** | Very High — overwhelming multi-source evidence |
| **Dependencies** | USDA API integration, OFF API integration, food cache in Room |
| **Risks** | USDA is US-focused; regional food gaps for non-US products. OFF fills some gaps but quality varies. |
| **Validation still needed** | None — evidence is conclusive |
| **Impact if wrong** | N/A — the alternative (crowdsourcing without verification) has been demonstrated to fail. |
| **When decided** | Before architecture (locked) |

---

#### D-05: Workout generation shows reasoning

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Every generated workout displays rationale: why each exercise, why that weight/rep range, mesocycle position, next deload date. User can override any element with one tap. Semi-autonomous, not fully autonomous. This applies to any algorithmic training recommendation surfaced to the user, not only full generated routines — including MVP-scope outputs such as weight suggestions (2-for-2 rule), MRV flags, and volume-vs-landmark comparisons (P-05). |
| **Evidence basis** | Fitbod algorithm distrust (6+ threads, Exercise Science-credentialed review at 56 upvotes, Fitbod rep acknowledgment). RP Hypertrophy: users discover "simple double progression" doesn't justify $300/yr when the logic is opaque. |
| **Confidence** | High — the complaint (opacity) is very well-evidenced; the solution (reasoning display) is the logical inverse but untested in any product |
| **Dependencies** | Workout generation algorithm (H-03), exercise-to-muscle-group mapping (A-02), Hevy exercise template data |
| **Risks** | Displaying reasoning is only valuable if the reasoning is correct. If the algorithm makes bad choices, transparency exposes the error. |
| **Validation still needed** | Whether the reasoning display actually builds trust (can only test with working feature) |
| **Impact if wrong** | Users see the reasoning but still disagree → still better than opacity (they can override with understanding), but the feature feels less magical. Acceptable. |
| **When decided** | Before architecture (locked) |

---

#### D-06: TDEE calculation client-first with server sync

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | TDEE is computed client-side. Ships with Mifflin-St Jeor static estimate first; adaptive algorithm activates after 4+ weeks of data. TDEE outputs and metadata sync to the server for history and backup. The server does not compute TDEE directly. |
| **Evidence basis** | Hybrid compute decision (D-03). Gate review: adaptive TDEE downgraded from table-stakes to enhancement. MacroFactor calibration confusion (r/MacroFactor) shows 4-8 week cold-start is real. |
| **Confidence** | High for the compute split; Medium for whether adaptive TDEE adds value at n=1 |
| **Dependencies** | Room weight history table, HC weight data reliability, sufficient food logging for intake data |
| **Risks** | Client-side TDEE with gaps in weight or food logging data produces noisy estimates. Single-user adaptive estimates may be more volatile than population-based algorithms. |
| **Validation still needed** | Whether adaptive TDEE is more useful than static Mifflin-St Jeor for Tyler specifically (test after 4+ weeks of logging) |
| **Impact if wrong** | Adaptive TDEE is noisy/unhelpful → fall back to static estimate permanently. Low cost since static is already the v1 path. |
| **When decided** | Before architecture (locked for compute split; adaptive upgrade is hypothesis H-05) |
| **Implementation note** | *Expected approach: EMA smoothing on weight trend, weekly recalculation of expenditure estimate. Subject to architecture review — these are starting assumptions, not constraints.* |

---

#### D-07: AI safety rails are non-negotiable

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | AI coach never diagnoses conditions. Always recommends doctor for BP >= 140/90. Never fabricates data. Always cites which data point drove recommendation. Prefixes supplement mentions with "based on current evidence." Never checks supplement interactions (link to drugs.com/Examine.com instead). Acknowledges uncertainty explicitly. |
| **Evidence basis** | Oura Advisor hallucination (1K-upvote thread, Mar 2026), ASHP study (ChatGPT drug advice 75% wrong), r/science AI nutrition for teens (208 upvotes), Peloton/iFit constraint-ignoring failures. |
| **Confidence** | Very High — the failure modes are documented, recent, and severe |
| **Dependencies** | System prompt engineering, structured constraint injection, output validation |
| **Risks** | Over-constraining the AI makes it too cautious to be useful ("I can't advise on that"). Balance required. |
| **Validation still needed** | None for the principle; prompt engineering iteration needed for the right constraint-vs-usefulness balance |
| **Impact if wrong** | If safety rails are too loose: incorrect health advice to a real person. If too tight: useless coach. Both are correctable with prompt iteration. |
| **When decided** | Before architecture (locked) |

---

#### D-08: Health Connect sync with active monitoring

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Background permission watchdog checks HC permissions every WorkManager sync cycle. Alerts user before data goes stale. Last-sync timestamp visible in UI. Manual "Refresh now" button. Graceful degradation: show last-known data with age, not empty screens. |
| **Evidence basis** | HC permission drops (r/Coros, Sep 2025), 24h sync delay (r/ConquerorChallenge), Samsung blocking 3P writes (r/ouraring, Nov 2025), WHOOP HC broken 16+ months. |
| **Confidence** | Very High — device-specific, well-documented, affects core product trust |
| **Dependencies** | Existing WorkManager infrastructure, HC permission API |
| **Risks** | Watchdog adds complexity to sync cycle. Over-alerting on permission issues could be annoying. |
| **Validation still needed** | Whether Tyler's specific device drops permissions (H-04) — determines urgency, not necessity |
| **Impact if wrong** | If permissions never drop on Tyler's device, the watchdog is unused safety code. Low cost. |
| **When decided** | Before architecture (locked) |

---

#### D-09: No streak counters or aggressive gamification

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | No streak counters as engagement mechanic. Use trend visualization (weekly/monthly progress curves) instead. |
| **Evidence basis** | iNews "296-week streak ruining my life" (Jan 2026), r/duolingo "fear of losing a number," r/AppleWatchFitness data-fatigue, behavioral research on extrinsic motivation crowding out intrinsic. |
| **Confidence** | Very High |
| **Dependencies** | None |
| **Risks** | None — trend visualization is strictly better for a motivated single user |
| **Validation still needed** | None |
| **Impact if wrong** | Tyler misses having a streak counter → trivial to add later. |
| **When decided** | Before architecture (locked) |

---

#### D-10a: Hevy API abstraction interface

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | All Hevy API calls go through the server via an abstraction interface. Aggressive caching of workout history. Workout generation algorithm lives on Apex's server, not Hevy. The abstraction boundary ensures Hevy API changes are contained to the adapter layer and do not propagate into business logic. |
| **Evidence basis** | Hevy "may change or abandon" disclaimer, 429 errors (r/Hevy, Apr 2025), 10M+ users indicating active development/change. |
| **Confidence** | High — risk is explicit and acknowledged by Hevy; asymmetric payoff favors building it |
| **Dependencies** | Server-side workout generation (D-14), Hevy API rate limit testing (H-06) |
| **Risks** | Over-engineering the abstraction for an API that may remain stable for years. Acceptable cost. |
| **Validation still needed** | Actual rate limit threshold (H-06) — determines caching and backoff strategy |
| **Impact if wrong** | If Hevy API is perfectly stable: abstraction adds minor code complexity. If API breaks: abstraction saves the feature. |
| **When decided** | Before architecture (locked) |
| **Implementation note** | *Expected approach: exponential backoff for rate limits. Subject to architecture review after H-06 validation.* |

---

#### D-10b: Hevy API fallback display scope

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | If Hevy API becomes unavailable, Apex should be able to display generated workouts in-app so the user can log them manually in Hevy. The scope of this fallback display (full in-app workout UI vs. simple text/list export vs. copy-to-clipboard) is to be determined during architecture. |
| **Evidence basis** | Follows from D-10a — the abstraction layer needs a defined degradation path. The specific fallback scope depends on implementation cost vs. probability of Hevy API disappearing. |
| **Confidence** | Medium — the need for a fallback is clear; the investment level is not |
| **Dependencies** | D-10a, workout generation output format |
| **Risks** | Over-investing in fallback UI for an API that may remain stable. Under-investing and having no usable output if the API breaks. |
| **Validation still needed** | Implementation cost estimation during architecture |
| **Impact if wrong** | If fallback is too thin and Hevy API breaks → scramble to build display UI. If fallback is over-built → wasted effort. |
| **When decided** | During architecture |

---

#### D-11: No fully autonomous workout generation

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Semi-autonomous mode: Apex generates workout proposal with reasoning, user reviews/adjusts, then pushes to Hevy. User always stays in the loop. No "just start the workout the app decided for you" flow. |
| **Evidence basis** | Fitbod distrust (6+ threads), Exercise Science user review, RP algorithm transparency. The fundamental problem: autonomous generation requires being right every time. |
| **Confidence** | Very High |
| **Dependencies** | Workout reasoning display (D-05), Hevy routine push |
| **Risks** | Adds one extra review step vs. fully autonomous. For a single user who knows their training, this step is a feature, not friction. |
| **Validation still needed** | None |
| **Impact if wrong** | Tyler wishes he could skip the review step → add a "trust mode" toggle later. Trivial. |
| **When decided** | Before architecture (locked) |

---

#### D-12: No social features

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | No leaderboards, social feeds, or public sharing features. Single-user app. |
| **Evidence basis** | No critical mass possible. Health data privacy anxiety. Fitbod Clubs failed. |
| **Confidence** | Very High |
| **Dependencies** | None |
| **Risks** | None for single-user context |
| **Validation still needed** | None |
| **Impact if wrong** | N/A |
| **When decided** | Before architecture (locked) |

---

#### D-13: Food entry provenance field from day one

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Every food entry carries a `source` enum: `verified` (USDA/barcode-confirmed), `estimated` (photo AI accepted), `corrected` (photo AI user-adjusted), `custom` (user-created), `quick_add` (calories/protein only). |
| **Evidence basis** | Precision nutrition decision (D-01) requires distinguishing data quality. Photo-as-secondary means the system must know which entries are high-confidence vs. estimates. TDEE algorithm may later weight entries differently by provenance. |
| **Confidence** | High — schema field is low-cost and enables future flexibility |
| **Dependencies** | Nutrition schema design (D-15) |
| **Risks** | None — adding a field is cheap; removing one is hard |
| **Validation still needed** | None |
| **Impact if wrong** | Unused enum values. Zero cost. |
| **When decided** | Before architecture (locked) |

---

#### D-14: Server-side workout generation for v1

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Workout generation runs on the server for v1, because Hevy workout history already lives there. The client provides readiness context; the server returns a generated routine with reasoning for client-side review and approval before pushing to Hevy. |
| **Evidence basis** | Hybrid compute decision (D-03): server for "history aggregation, computationally heavier" tasks. Hevy data currently syncs to server. Moving it client-side adds complexity without clear benefit for an infrequent on-demand action. |
| **Confidence** | High |
| **Dependencies** | Server Hevy data, workout generation algorithm, Hevy API abstraction (D-10a) |
| **Risks** | Server dependency for workout generation means feature is unavailable if server is down. Acceptable for an infrequent action (1-2x per day max). |
| **Validation still needed** | None for v1 |
| **Impact if wrong** | If offline generation becomes important later, algorithm can migrate client-side (deterministic logic, portable). |
| **When decided** | Before architecture (locked) |
| **Implementation note** | *Expected interaction pattern: client sends readiness score as parameter, server returns generated routine with per-exercise reasoning. Subject to architecture review.* |

---

#### D-15: Nutrition and supplement schema designed now, built later

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Nutrition tables (foods, food_entries, nutrition_targets) AND supplement tables (supplements, supplement_entries, supplement_stack, supplement_interactions) are designed in the same architecture phase as workout tables. Tables created empty on server. Populated when each respective pillar ships. Prevents future migration conflicts. |
| **Evidence basis** | Gate review Section 3.3 + 4.4: each new pillar risks breaking existing tables if schema isn't pre-planned. Workout-first sequencing makes this explicit. Post-gate review: supplement schema has the same migration-conflict risk as nutrition and should be included. |
| **Confidence** | High — standard software practice for known future scope |
| **Dependencies** | Server schema migration plan |
| **Risks** | Pre-designed schema may need changes when features are actually implemented. Acceptable — empty tables are cheap to alter. |
| **Validation still needed** | None |
| **Impact if wrong** | Schema revision during implementation. Minor. |
| **When decided** | Before architecture (locked) |
| **Scope clarification** | This is a *design-now, implement-later* decision. The schema is defined and tables are created (empty) during the workout-first architecture phase. Data population and CRUD endpoints ship when each pillar is implemented. This is distinct from fully deferred items which have no present-day schema work. |

---

#### D-16: AI coaching has limited context in workout-first phase

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | AI coach ships with health + training context only (readiness, Hevy workouts, sleep, HRV, BP). No nutrition context until that pillar ships. Context assembly pipeline is modular — nutrition data source plugs in later without restructuring. |
| **Evidence basis** | Workout-first decision (D-02). Gate review: AI coaching needs enough data to be non-obvious; health + training data is sufficient for readiness briefings, workout recommendations, and training trend analysis. |
| **Confidence** | High |
| **Dependencies** | Modular context assembly in coaching architecture |
| **Risks** | Coach can't give nutrition-aware advice (e.g., "you're underfueled for today's volume") until nutrition ships. Acceptable limitation for v1. |
| **Validation still needed** | Whether health + training context alone produces non-generic coaching (test with real data) |
| **Impact if wrong** | Coach feels thin without nutrition context → motivates faster nutrition implementation. Self-correcting. |
| **When decided** | Before architecture (locked) |

---

#### D-17: Hevy is the source of truth for actual workout data

| Field | Value |
|-------|-------|
| **Type** | Decision |
| **Description** | Hevy is where the user logs actual performance. Apex generates and suggests; Hevy records. The progression algorithm uses ACTUAL completed workout data from Hevy (what was done), not PRESCRIBED data from Apex (what was suggested). If the user modifies the generated routine in Hevy or logs a workout directly in Hevy without Apex generating it, that data enters the progression model normally. |
| **Evidence basis** | Gate review Section 4.2: data reconciliation between prescribed vs. actual must be explicit. Research brief: "Algorithm adapts to what was actually done, not prescribed." Using actuals is clearly correct for progression — prescribing based on what was prescribed (not done) disconnects the algorithm from reality. |
| **Confidence** | High — using actuals is the only defensible approach for progression |
| **Dependencies** | Hevy API read after workout completion, sync timing |
| **Risks** | Lag between Hevy log and Apex re-sync means Apex may briefly show stale progression data. |
| **Validation still needed** | Sync timing after Hevy workout completion (how quickly does new workout data appear via API?) |
| **Impact if wrong** | Using prescribed data instead of actuals would make progression suggestions disconnected from reality. Would need to be corrected immediately. |
| **When decided** | Before architecture (locked) |

*Supersedes P-06, which was promoted to this decision during post-gate normalization.*

---

### Hypotheses (H): Directionally supported but require validation before full commitment

---

#### H-01: Readiness-to-Hevy loop is the primary differentiator

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | HC readiness data → Apex → adaptive workout → Hevy push → user logs → re-sync → repeat. No competitor does this. Highest-potential differentiator. |
| **Evidence basis** | Pass 1: no competitor does end-to-end loop. Hevy write API confirmed. Pass 2: Fitbod users want readiness-aware workouts. Gate review: 3 unvalidated links in the chain. |
| **Confidence** | Medium-High — the gap is real, but the chain has 3 uncertain dependencies |
| **Dependencies** | HC data reliability (H-04), HRV-strength correlation (H-02), Hevy API stability (H-06) |
| **Risks** | Any link failing degrades the loop. HC stale → readiness unreliable → workout suggestion irrelevant. Hevy API change → can't push routines. |
| **Validation still needed** | End-to-end test of the full chain. **Success criteria:** (1) *Technical:* HC data is < 12h old on > 80% of training mornings; Hevy routine push succeeds on first attempt > 90% of the time. (2) *Value:* Tyler subjectively rates readiness-adjusted workouts as matching his energy/recovery state better than non-adjusted workouts > 70% of the time, measured over 4 weeks. **Failure criteria:** HC data stale > 50% of mornings, OR Hevy push fails > 20% of the time, OR readiness adjustments feel wrong > 50% of the time after 4 weeks → downgrade from "primary differentiator" to "optional enhancement." |
| **Impact if wrong** | The loop doesn't deliver expected value. Fallback: workout generation still works (without readiness adjustment), readiness still displays (without workout generation), Hevy sync still works (without routine push). Each component has standalone value. |
| **When decided** | Validate during MVP. Architecture should support the loop but not depend on all three links working perfectly from day one. |

---

#### H-02: HRV predicts strength training readiness

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | HRV (from HC / wearable) is a useful input for adjusting strength training volume and intensity. |
| **Evidence basis** | HRV-readiness validated for endurance athletes. r/whoop: "HRV has not been demonstrated as a reliable metric of readiness for strength training." HRV4Training founder's research is primarily endurance-focused. |
| **Confidence** | Medium — established for endurance, unvalidated for strength |
| **Dependencies** | HC delivers HRV data reliably (H-04), sufficient personal data to correlate (20+ training sessions) |
| **Risks** | If HRV doesn't predict strength performance, the readiness score's most prominent input is unreliable. |
| **Validation still needed** | 4-6 weeks of tracking HRV alongside subjective session quality (RPE). **Success criteria:** Negative correlation (r > 0.25) between morning HRV deviation from personal baseline and session RPE over 20+ data points. OR: low-HRV days (> 1 SD below baseline) correspond to higher-than-usual RPE > 60% of the time. **Partial success:** r = 0.1–0.25 → keep HRV as an input with low weight. **Failure:** No correlation or positive correlation → set HRV weight to zero in readiness algorithm; rely on sleep + subjective input. |
| **Impact if wrong** | Readiness algorithm uses HRV as one of several inputs (sleep, subjective, BP). If HRV is noise, reduce its weight and increase subjective + sleep. The algorithm is tunable. |
| **When decided** | Validate during MVP. Architecture weights must be configurable, not hardcoded. |

---

#### H-03: Readiness algorithm produces meaningful composite scores

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | Weighting HRV, sleep, BP, and subjective input into a single readiness score yields training-relevant guidance. |
| **Evidence basis** | WHOOP and Oura do this (imperfectly). Gate review: "the readiness algorithm is an unsolved research problem, not just a display problem." No product gets it right consistently. |
| **Confidence** | Medium — the concept is sound, the calibration is hard |
| **Dependencies** | HC data quality, HRV-strength correlation (H-02), personal baseline establishment (2-4 weeks) |
| **Risks** | Poorly calibrated weights produce scores that don't match felt experience → exact same complaint as WHOOP/Oura. Transparency (D-05) mitigates but doesn't eliminate. |
| **Validation still needed** | Start with simple heuristic (equal-weight normalized average + subjective). Iterate based on 4-6 weeks of score-vs-actual-performance tracking. **Success criteria:** Readiness score direction (above/below personal midpoint) matches Tyler's subjective "good day / bad day" assessment > 70% of the time over 4 weeks. **Failure criteria:** < 50% directional agreement (worse than random) → algorithm needs fundamental redesign or readiness feature is deprioritized. |
| **Impact if wrong** | Readiness score is directionally useful but not precise → still better than no score (provides a starting point for discussion with the AI coach). Acceptable. |
| **When decided** | Validate during MVP. Ship simple heuristic first; refine post-MVP. |

---

#### H-04: Health Connect delivers reliable data on Tyler's device

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | HC permissions persist, data arrives within acceptable freshness (<24h), and wearable data flows without manual intervention on Tyler's specific Android device. |
| **Evidence basis** | HC issues are device-dependent. Some devices are stable, others drop permissions. Tyler's device behavior is unknown. |
| **Confidence** | Medium — category-level evidence of fragility, personal device untested |
| **Dependencies** | Tyler's wearable (A-01) + Android device combination |
| **Risks** | If HC is unreliable on Tyler's device, readiness scoring, workout adjustment, and the entire health data pipeline are degraded. |
| **Validation still needed** | 2-week HC sync monitoring with logging. Check permission persistence, data freshness, completeness. |
| **Impact if wrong** | HC is unreliable → permission watchdog (D-08) becomes critical infrastructure instead of safety net. Readiness staleness policy activates frequently. May need direct wearable API integration (WHOOP OAuth, Oura OAuth) as HC bypass. |
| **When decided** | Validate during MVP (early — this affects the foundation). |

---

#### H-05: Adaptive TDEE adds value over static estimates at n=1

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | MacroFactor-style adaptive TDEE (back-calculated from weight trend + intake) is more useful than Mifflin-St Jeor static estimate for a single user. |
| **Evidence basis** | MacroFactor users strongly prefer adaptive (migration threads). Gate review: "significant algorithmic investment" and "most fitness apps don't have it." Single-user EMA may be noisy. 4-8 week calibration required. |
| **Confidence** | Medium — works at scale, untested at n=1 with personal data noise |
| **Dependencies** | Consistent food logging (4+ weeks), consistent weight data (3+ per week), client-side algorithm (D-06) |
| **Risks** | Adaptive TDEE with inconsistent logging produces nonsensical estimates (documented in MacroFactor threads). Single-user EMA more volatile than population-based. |
| **Validation still needed** | Run static estimate for 4 weeks alongside manual weight tracking. Then activate adaptive. Compare which estimate matches actual experience better. |
| **Impact if wrong** | Adaptive TDEE is noisy → fall back to static. Static is already the v1 default. Zero wasted architecture. |
| **When decided** | Post-MVP. Ship static first; adaptive is a Phase 2 enhancement activated after sufficient data accumulation. |

---

#### H-06: Hevy API rate limits are workable

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | Hevy's undocumented rate limits permit the sync frequency needed for workout generation + history retrieval. |
| **Evidence basis** | 429 errors confirmed (r/Hevy, Apr 2025). Exact limits unknown. API disclaimer warns of instability. |
| **Confidence** | Medium — limits exist but threshold is unknown |
| **Dependencies** | None |
| **Risks** | If limits are very aggressive, real-time workout generation is impractical. Must batch + cache instead. |
| **Validation still needed** | Empirical testing: ramp requests from 1 req/10s upward until 429s appear. **Workability thresholds:** (1) *Fully workable:* >= 30 req/min → on-demand reads during workout generation are feasible. (2) *Workable with caching:* 5–30 req/min → cache full workout history locally, refresh on schedule; adds ~10 min of stale data. (3) *Severely constrained:* < 5 req/min → batch-only sync (1-2x daily); workout generation uses data up to 24h old. (4) *Blocking:* < 1 req/min → Hevy API is unsuitable for programmatic use; reassess Hevy dependency entirely. |
| **Impact if wrong** | Very aggressive limits → architecture shifts to heavy caching + batch sync instead of on-demand reads. Changes sync design but not feature set. |
| **When decided** | Validate before architecture finalizes sync design. This is the most time-sensitive validation. |

---

#### H-07: Proactive AI coaching sustains engagement beyond 3 months

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | Morning briefings, trend alerts, and post-workout analysis sustain daily AI coaching engagement past the 3-month novelty cliff. |
| **Evidence basis** | MobiDev: 40% engagement drop after onboarding; proactive nudges improve retention 24%. No product has demonstrated sustained AI coaching engagement beyond 12 months. Oura Advisor praised for memory → but then hallucination crisis hit. |
| **Confidence** | Low-Medium — directionally promising, unproven at duration |
| **Dependencies** | AI coaching architecture, sufficient data history, proactive notification infrastructure |
| **Risks** | Coach becomes noise after 3 months → investment not proportional to outcome. |
| **Validation still needed** | Build engagement tracking from day 1. Measure monthly: interactions initiated by coach, interactions read, recommendations acted on. Assess at 3-month mark. |
| **Impact if wrong** | AI coaching becomes a background feature used occasionally, not daily. Acceptable — the infrastructure (Claude API, context assembly) is shared with other features. |
| **When decided** | Post-MVP. Ship coaching; measure; adjust. |

---

#### H-08: Supplement-outcome correlation is compelling

| Field | Value |
|-------|-------|
| **Type** | Hypothesis |
| **Description** | Graphing supplement intake against biometric trends (sleep, HRV, BP) over 14-30 day windows is useful and used. |
| **Evidence basis** | NutraIngredients/Lumina: 93% of health app users buy supplements from app suggestions. r/QuantifiedSelf demand. Staqc attempting this market. Gate review: n=1 correlation is statistically unreliable, confounders make causal inference impossible. |
| **Confidence** | Low-Medium — demand is inferred from gap, not measured; statistical validity is weak |
| **Dependencies** | Supplement tracking pillar, biometric history, UI for overlay graphs |
| **Risks** | Feature conveys false confidence about causation. "Magnesium improved your sleep" may actually be seasonal change, training volume shift, or placebo. |
| **Validation still needed** | Build simplest version (overlay graph with explicit "correlation ≠ causation" label). Ship. Measure whether Tyler uses it more than once. |
| **Impact if wrong** | An unused graph in the supplement section. Minimal wasted effort if built simply. |
| **When decided** | Post-MVP. Supplement tracking is 5th in sequence. |

---

### Assumptions (A): Operational or environmental dependencies that are believed true but not yet verified

---

#### A-01: Tyler has a wearable that writes HRV, sleep, and resting HR to Health Connect

| Field | Value |
|-------|-------|
| **Type** | Assumption |
| **Description** | Tyler owns and regularly wears a device that writes HRV, sleep stages, and resting heart rate to Android Health Connect. The specific device model is TBD. |
| **Evidence basis** | The entire readiness pipeline (H-01, H-02, H-03, H-04) depends on wearable data flowing through HC. Apex v1 already reads from HC (BP, sleep, HRV, weight), implying a wearable is present. Specific device not confirmed in planning docs. |
| **Confidence** | High that a wearable exists (Apex v1 already syncs HC data); Medium that HRV data specifically is available (device-dependent — Galaxy Watch writes HRV during sleep only, WHOOP HC is unreliable, Oura writes to HC on Android). |
| **Dependencies** | None |
| **Risks** | If the wearable doesn't write HRV to HC, the readiness pipeline has no HRV input. Readiness would rely on sleep + BP + subjective only. |
| **Validation still needed** | Identify the specific wearable device. Confirm which HC data types it writes. Confirm HRV is among them. |
| **Impact if wrong** | Readiness algorithm operates without HRV (sleep + subjective + BP only). Less differentiated but still functional. |
| **When decided** | Confirm during first week of architecture |

---

#### A-02: Hevy exercise templates include muscle group data

| Field | Value |
|-------|-------|
| **Type** | Assumption — **VALIDATED 2026-03-15** |
| **Description** | Hevy's GET exercise_templates API endpoint returns primary and secondary muscle group metadata for each exercise. If not, Apex requires a curated exercise-to-muscle-group mapping table (~200-500 exercises). |
| **Evidence basis** | **Validated via live API call (2026-03-15).** `GET /v1/exercise_templates` returns `primary_muscle_group` (string) and `secondary_muscle_groups` (string array) on every exercise. ~440 exercises in the Hevy library across 44 pages. Compound exercises have populated secondary arrays (e.g., Decline Bench Press: primary=chest, secondary=[shoulders, triceps]). Equipment and exercise type fields also present. |
| **Confidence** | **High — confirmed.** Muscle group data exists and is structured. However, granularity is coarser than RP volume landmarks require (e.g., Hevy uses "shoulders" where RP distinguishes front/side/rear delts). `full_body` and `cardio` categories are catch-alls with no muscle-group attribution. No versioning — if Hevy changes attributions, Apex wouldn't know. |
| **Dependencies** | Hevy API access (requires Pro subscription, A-03) |
| **Risks** | **(Revised)** Data exists but is insufficient as a sole canonical source for RP volume landmark tracking. Hevy's muscle group taxonomy is too coarse for shoulder/back subdivision that RP landmarks require. Custom exercises inherit whatever muscle group the user sets in Hevy (Tyler currently has no custom exercises). Hevy could change attributions without notice. |
| **Validation still needed** | ~~Call GET /v1/exercise_templates and inspect the response schema.~~ **Done.** Remaining question: whether Apex needs a first-party override/refinement layer on top of Hevy's coarse attributions for RP landmark accuracy. See A-02 validation findings below. |
| **Impact if wrong** | N/A — assumption validated. The new question is about granularity sufficiency, not data existence. |
| **When decided** | **Validated 2026-03-15.** Granularity sufficiency decision needed during architecture. |
| **Validation findings** | Hevy API response fields: `id` (8-char hex), `title`, `type` (weight_reps/reps_only/duration/etc.), `primary_muscle_group`, `secondary_muscle_groups` (array), `equipment`, `is_custom`. ~440 exercises. Muscle group values observed: biceps, chest, triceps, quadriceps, lats, abdominals, forearms, hamstrings, glutes, shoulders, upper_back, full_body, cardio. Secondary arrays populated for compounds. Coarse granularity: no front/side/rear delt distinction, no trap subdivisions, no long/short head bicep distinction. |

---

#### A-03: Tyler maintains a Hevy Pro subscription

| Field | Value |
|-------|-------|
| **Type** | Assumption |
| **Description** | Tyler has an active Hevy Pro subscription (~$60/yr) which is required for API access. If the subscription lapses, the entire Hevy API-dependent feature set (workout sync, routine push, exercise templates) stops working. |
| **Evidence basis** | Hevy API docs: "API only available to Hevy Pro users." Research brief confirms API key is obtained from hevy.com/settings?developer (Pro-only section). Apex v1 already syncs Hevy data, implying Pro subscription is active. |
| **Confidence** | High — Apex v1 already uses the Hevy API, implying Pro is active |
| **Dependencies** | None |
| **Risks** | Subscription lapse → all Hevy-dependent features stop. This is an operational dependency, not a code problem. |
| **Validation still needed** | None — this is a known operational fact, not a technical hypothesis. Register it as a dependency so it isn't forgotten. |
| **Impact if wrong** | Core workout features unavailable until subscription is renewed. |
| **When decided** | Known (registered for completeness) |

---

#### A-04: Server remains Node.js/Express

| Field | Value |
|-------|-------|
| **Type** | Assumption |
| **Description** | The Apex server (Health Platform Desktop) is a Node.js/Express application. New server-side logic for workout generation, AI coaching orchestration, and nutrition CRUD must fit within this framework unless a migration is explicitly justified. |
| **Evidence basis** | Existing codebase: `src/index.js` registers Express routes, `src/routes/sync.js` handles health data sync. Server runs in Docker on DigitalOcean droplet at 165.227.125.102. |
| **Confidence** | High — this is the current state of the codebase |
| **Dependencies** | None |
| **Risks** | Node.js/Express may not be the ideal framework for streaming AI responses (SSE), complex workout generation algorithms, or long-running batch jobs. These are solvable within Node.js but may require additional libraries or patterns. |
| **Validation still needed** | None — this is a constraint, not a hypothesis. If architecture review determines Node.js is inadequate for a specific feature, a migration proposal should be explicit. |
| **Impact if wrong** | N/A — this is a statement of current state, not a prediction |
| **When decided** | Known (registered as constraint) |

---

#### A-05: Claude API remains stable and available

| Field | Value |
|-------|-------|
| **Type** | Assumption |
| **Description** | The Anthropic Claude API (used for AI coaching) remains available, stable, and at current or similar pricing through the v2 development and initial usage period. |
| **Evidence basis** | Anthropic is a well-funded company with commercial API SLAs. Claude API has been stable through Apex v1 development. No evidence of impending deprecation or major pricing changes. |
| **Confidence** | High — commercial API from a major AI company |
| **Dependencies** | None |
| **Risks** | API deprecation or major pricing increase could affect coaching feature viability. Mitigated by abstracting the AI provider behind an interface (same principle as D-10a for Hevy). |
| **Validation still needed** | None — monitor Anthropic announcements during development |
| **Impact if wrong** | Switch to alternative LLM provider (Gemini, GPT). System prompt and context assembly would need adaptation but core architecture is provider-agnostic if designed with interface boundary. |
| **When decided** | Known (registered for completeness) |

---

### Preferences (P): Design direction choices that are adjustable during implementation

---

#### P-01: Readiness staleness policy — 24h threshold + subjective input

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | Compute readiness with HC data up to 24h old, always show timestamp. Beyond 24h, show "readiness unavailable." Include optional subjective input (1-5 scale) as always-fresh signal. |
| **Evidence basis** | HC 24h sync delay documented. WHOOP/Oura users want actionable guidance, not just numbers. Subjective input adds a data point that's never stale. |
| **Confidence** | Medium — reasonable default, adjustable based on experience |
| **Dependencies** | HC sync monitoring (D-08), readiness algorithm (H-03) |
| **Risks** | 24h threshold may be too generous or too strict depending on Tyler's sync patterns |
| **Validation still needed** | Observe actual data freshness during first weeks of use |
| **Impact if wrong** | Adjust threshold up or down. Trivial parameter change. |
| **When decided** | During MVP (adjustable) |

---

#### P-02: Supplement tracking as short-burst audit tool

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | Daily checklist with one-tap "taken" buttons. Designed for 2-4 week burst usage, not permanent daily obligation. Reminders opt-in, not default. |
| **Evidence basis** | WHOOP journal retroactive logging complaint (236 upvotes), MacroFactor supplement workaround, alert fatigue research. |
| **Confidence** | Medium-High — evidence supports short-burst as more sustainable, but Tyler's pattern may differ |
| **Dependencies** | Supplement schema (D-15), UI |
| **Risks** | Tyler may want permanent daily tracking. The checklist UX supports both patterns. |
| **Validation still needed** | Observe usage pattern after shipping |
| **Impact if wrong** | Checklist works for both burst and permanent use. No architectural change needed. |
| **When decided** | During MVP (adjustable) |

---

#### P-03: Morning briefing pre-generated during nightly sync

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | Morning readiness briefing is pre-computed to reduce synchronous dependency on the Claude API at app-open time. Real-time chat can still be server-side; only the proactive daily briefing is pre-generated. |
| **Evidence basis** | Gate review: server-side coaching adds synchronous dependency. Pre-generation removes real-time latency and server availability as failure modes for the highest-value daily touchpoint. |
| **Confidence** | Medium — logical but untested |
| **Dependencies** | Server sync job, Claude API, local storage for briefing |
| **Risks** | Pre-generated briefing may feel stale if health data changes between generation and viewing. |
| **Validation still needed** | Whether the freshness tradeoff matters in practice |
| **Impact if wrong** | Switch to on-demand generation. Requires server to be available when user opens app. Manageable. |
| **When decided** | During MVP |
| **Implementation note** | *Expected approach: batch job during nightly sync cycle. Subject to architecture review — could also be triggered by WorkManager on a schedule.* |

---

#### P-04: Photo estimation 4th in sequence

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | Photo estimation ships after workout generation, AI coaching, and manual nutrition logging. Needs manual logging UX to be solid first so correction paths are smooth. |
| **Evidence basis** | Precision nutrition decision (D-01) makes manual logging primary. Photo accuracy limitations (50-82%). Gate review noted this could be earlier if Tyler's goal was awareness — but precision was chosen. |
| **Confidence** | Medium-High — logical given precision decision, but could move earlier if manual logging is shipped and stable faster than expected |
| **Dependencies** | Manual nutrition logging UX, Gemini Flash API integration |
| **Risks** | Deferring photo too long misses a convenience improvement. Low risk since it's a layered addition. |
| **Validation still needed** | Personal accuracy test (20 meals with food scale) to confirm utility before building |
| **Impact if wrong** | Photo ships later than ideal. Can be reprioritized at any time without architectural impact. |
| **When decided** | During MVP (sequence adjustable) |

---

#### P-05: Workout generation MVP scope

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | v1 MVP: show weekly volume per muscle group against RP landmarks, suggest next workout weight using 2-for-2 rule, flag approaching MRV, display mesocycle position. Full mesocycle auto-generation is v2. |
| **Evidence basis** | Gate review: RP landmarks + mesocycle auto-generation is non-trivial (exercise mapping, volume tracking, substitution handling, readiness adjustment). MVP should prove the concept before scaling complexity. |
| **Confidence** | Medium — reasonable scoping, but the boundary between MVP and v2 may shift during implementation |
| **Dependencies** | Hevy exercise template data, muscle group mapping (A-02), RP volume landmark reference data |
| **Risks** | MVP too thin → doesn't feel like a differentiator. MVP too thick → delays shipping. |
| **Validation still needed** | Implementation complexity estimation during architecture |
| **Impact if wrong** | Scope adjusts during implementation. Normal. |
| **When decided** | During architecture (scope boundary) |

---

#### P-06: *Superseded — promoted to D-17*

*This item was originally "Hevy as source of truth for actual workout data," classified as a Preference. Post-gate review identified it as a firm decision, not an adjustable preference. It has been promoted to D-17 with full Decision metadata. See D-17 for the authoritative version.*

---

#### P-07: AI coaching conversation history stored server-side

| Field | Value |
|-------|-------|
| **Type** | Preference |
| **Description** | AI coaching conversation history is stored on the server (consistent with server-side AI orchestration per D-14 and D-16). The client caches recent N messages for offline display and fast loading. Full history is accessible via the server. |
| **Evidence basis** | AI coaching is server-orchestrated (D-14, D-16). Conversation history must persist across sessions for the "persistent memory" aspect of coaching (D-07, Pass 2 evidence on iFit forgetting constraints). Server storage is the natural location since the server already calls the Claude API. |
| **Confidence** | Medium-High — logical given server-side coaching; client caching scope (how many messages? all metadata or text only?) is an implementation detail |
| **Dependencies** | Server database schema (conversation table needed alongside workout/nutrition tables), coaching architecture |
| **Risks** | Server storage means conversation history is unavailable if server is down. Client cache mitigates for recent messages. |
| **Validation still needed** | None for the principle; cache size and sync strategy are implementation details |
| **Impact if wrong** | If client-side storage turns out to be better (e.g., for privacy or offline), migration is a data-move task, not an architecture change. |
| **When decided** | During architecture |

---

## Sorted Lists

### Safe to Lock Now

These items have high confidence, strong evidence, explicit user decisions, or are low-cost defaults. Architecture can proceed treating these as firm.

| ID | Item | Rationale for locking |
|----|------|----------------------|
| D-01 | Precision nutrition model | Explicit user decision, aligned with evidence |
| D-02 | Workout-first execution | Explicit user decision, supported by scope analysis |
| D-03 | Hybrid compute split | Explicit user decision with clear principle |
| D-04 | Food database quality protection (USDA + OFF) | Overwhelming evidence, zero reasonable alternative |
| D-05 | Workout reasoning transparency | Strong evidence for the complaint; logical solution |
| D-06 | TDEE client-first, static first then adaptive | Follows hybrid compute + gate review downgrade |
| D-07 | AI safety rails | Non-negotiable; failure modes are documented and severe |
| D-08 | HC sync active monitoring | Well-documented fragility; low cost to implement |
| D-09 | No streak counters | Strong evidence, zero cost to avoid |
| D-10a | Hevy API abstraction interface | Explicit risk acknowledged by Hevy; asymmetric payoff |
| D-11 | No fully autonomous workout generation | Strong evidence from Fitbod; semi-autonomous is strictly better |
| D-12 | No social features | Single-user app; no critical mass possible |
| D-13 | Food entry provenance field | Low-cost schema field; enables future flexibility |
| D-14 | Server-side workout generation (v1) | Data already server-side; simpler path |
| D-15 | Nutrition + supplement schema designed now, built later | Standard practice; prevents migration conflicts |
| D-16 | AI coaching limited context in workout phase | Direct consequence of workout-first sequencing |
| D-17 | Hevy as source of truth for actual workout data | Clearly correct — progression must track reality |

---

### Validate Before Locking

These items are directionally correct but have dependencies or uncertainties that could change the implementation approach. Validate during early architecture or MVP development.

| ID | Item | What to validate | How | When |
|----|------|-----------------|-----|------|
| H-06 | Hevy API rate limits | Actual threshold (see tiered criteria in H-06) | Ramp requests from 1/10s upward until 429s | **Before architecture finalizes sync design** |
| A-02 | Hevy exercise muscle group data | Whether API returns muscle group fields | Call GET /v1/exercise_templates, inspect response | **First week of architecture** |
| A-01 | Wearable HC data types | Which data types Tyler's wearable writes to HC | Identify device; check HC data availability | **First week of architecture** |
| H-04 | HC reliability on Tyler's device | Permission persistence, data freshness | 2-week monitoring with logging | **Early MVP** |
| H-02 | HRV predicts strength readiness | Correlation with session quality (see criteria in H-02) | 4-6 weeks of HRV + RPE tracking | **During MVP** |
| H-01 | Readiness-to-Hevy loop | End-to-end chain works (see criteria in H-01) | Test each link, then full loop | **During MVP** |
| H-03 | Readiness algorithm quality | Score matches felt experience (see criteria in H-03) | Simple heuristic + 4-6 weeks tracking | **During MVP** |
| P-01 | Readiness staleness threshold | 24h is right cutoff | Observe actual data freshness | **During MVP** |
| P-05 | Workout generation MVP scope | Right boundary between v1 and v2 | Implementation complexity estimation | **During architecture** |
| D-10b | Hevy API fallback display scope | How much fallback UI to build | Cost estimation during architecture | **During architecture** |
| P-03 | Pre-generated vs. real-time briefing | Freshness tradeoff acceptable | Ship pre-generated, observe complaints | **During MVP** |

---

### Defer Until After MVP

These items are either low priority, have weak demand evidence, depend on data that doesn't exist yet, or can be added later without architectural changes.

| ID | Item | Why defer | Earliest reconsideration |
|----|------|----------|------------------------|
| H-05 | Adaptive TDEE | Needs 4+ weeks of logging data to even test; static estimate ships first | After 4 weeks of nutrition logging |
| H-07 | AI coaching long-term engagement | Can only measure with real usage over time | 3 months post-coaching-launch |
| H-08 | Supplement-outcome correlation | 5th in sequence; weak demand signal; n=1 stats unreliable | After supplement tracking ships |
| P-02 | Supplement tracking design (burst vs. permanent) | Usage pattern unknown until feature exists | After supplement tracking ships |
| P-04 | Photo estimation sequence position | Requires manual nutrition UX to be stable first | After nutrition pillar ships |
| — | Meal planning / recipe generation | No evidence of retention impact; let demand emerge from coaching usage | Only if coaching data shows demand |
| — | WHOOP direct OAuth integration | Only needed if HC is unreliable for WHOOP data; HC reliability test (H-04) comes first | After H-04 validation |
| — | Garmin Health API application | Enterprise-gated; basic metrics available via HC; advanced metrics permanently excluded | Only if HC Garmin data is insufficient |
| — | Watch companion app (Wear OS) | Apex is a sync + intelligence layer, not a logging app; Hevy has its own Wear OS app | Only if in-app workout logging replaces Hevy |

**Design-now, implement-later items** (distinct from fully deferred — these have active schema work during architecture):
- D-15: Nutrition tables + supplement tables — schema designed during architecture, tables created empty, populated when respective pillar ships.
- P-07: AI coaching conversation table — schema designed during architecture alongside coaching endpoint design.

---

*This register is the authoritative source for what is decided, what is hypothesized, what is assumed, and what is deferred. Architecture should proceed on the "Safe to Lock" items, design for testability on the "Validate Before Locking" items, register and confirm the Assumptions, and leave clean extension points for the "Defer" items.*

*Post-gate normalization applied: P-06 promoted to D-17, D-10 split into D-10a/D-10b, D-15 extended to include supplements, D-06 and D-14 narrowed to separate planning decisions from implementation notes, H-01/H-02/H-03/H-06 thresholds added, Assumptions section created (A-01–A-05), P-07 added for conversation storage.*
