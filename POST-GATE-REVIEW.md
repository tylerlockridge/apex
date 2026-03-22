# Post-Gate Review: Pre-Architecture Planning Package

**Date:** 2026-03-15
**Scope:** All 5 planning documents
**Purpose:** Final adversarial review before architecture begins. Determine whether planning is mature enough.

---

## Section 1: False Locks / Overcommitted Decisions

### D-10 scope is broader than the evidence supports

D-10 locks both the Hevy API abstraction layer AND a specific fallback: "if Hevy API disappears, Apex displays generated workouts in-app for manual Hevy logging." The abstraction interface is well-justified (asymmetric payoff). The fallback display UI is a scope commitment embedded in what looks like a defensive coding decision. Building an in-app workout display UI is real work. The decision to build the interface boundary is locked. The scope of the fallback (full in-app display vs. simple text export vs. nothing) should be a preference resolved during architecture.

**Recommendation:** Split D-10 into: (a) abstraction interface — locked, and (b) fallback display scope — preference, decided during architecture.

### D-06 over-specifies the TDEE data flow

D-06 says "HC → Room → client-side TDEE algorithm (EMA smoothing, weekly recalculation) → server sync of outputs." The locked decision is: TDEE is client-first, ships static first, adaptive upgrades after 4+ weeks. The specific data flow, EMA smoothing method, and recalculation schedule are architecture, not product decisions. They're reasonable guesses but shouldn't be locked pre-architecture.

**Recommendation:** Lock the principle (client-first TDEE, static then adaptive). Move the data flow details to architecture.

### P-06 is stronger than a preference

P-06 (Hevy as source of truth for actual workout data) is classified as a Preference but placed in the "Safe to Lock" list with the note "locked as principle." This is a genuine decision — using actual performance data instead of prescribed data for progression is not optional or adjustable. It should be reclassified as a Decision (D-17) for register clarity.

**Recommendation:** Promote P-06 to D-17.

### No other false locks identified

The remaining 15 locked decisions are either explicit user choices (D-01/02/03), backed by overwhelming evidence (D-04, D-07, D-09), low-cost defaults (D-13, D-15), logical consequences of other locked decisions (D-14, D-16), or principles where the direction is clearly right even if the specific implementation is TBD (D-05, D-08, D-11, D-12).

---

## Section 2: Weak Hypotheses / Missing Validation Criteria

### H-01 has no success/failure definition

H-01 (readiness-to-Hevy loop) says "validate during MVP" with an "end-to-end test." But what constitutes success? If the loop works technically but the readiness-adjusted workout doesn't feel better than a non-adjusted one, is that success? If two of three links work but one doesn't, is the hypothesis confirmed or falsified?

**Fix:** Define success criteria explicitly:
- **Technical success:** HC data arrives fresh enough for same-day readiness (< 12h old) in > 80% of training days. Hevy API push succeeds on first attempt > 90% of the time.
- **Value success:** Tyler subjectively feels that readiness-adjusted workouts match his energy/recovery state better than non-adjusted workouts, measured over 4 weeks of use.
- **Failure criteria:** If HC data is stale > 50% of mornings, OR if Hevy push fails > 20% of the time, OR if readiness adjustments feel wrong more often than right after 4 weeks — downgrade from "primary differentiator" to "optional enhancement."

### H-02 has no correlation threshold

H-02 (HRV predicts strength readiness) says "4-6 weeks of tracking HRV alongside subjective session quality (RPE) and correlating." What correlation constitutes validation?

**Fix:** Define thresholds:
- **Success:** Negative correlation between morning HRV deviation (from personal baseline) and session RPE exists at r > 0.25 (weak-to-moderate) over 20+ data points. OR: days with HRV > 1 SD below baseline correspond to higher RPE > 60% of the time.
- **Partial success:** Correlation is present but weak (r = 0.1-0.25). HRV is kept as one of several inputs with low weight.
- **Failure:** No correlation or positive correlation. HRV weight in readiness algorithm is set to zero; readiness relies on sleep + subjective only.

### H-03 has no accuracy target

H-03 (readiness algorithm quality) says "score matches felt experience." How closely?

**Fix:** Define tolerance:
- **Success:** Readiness score direction (above/below personal midpoint) matches Tyler's subjective assessment ("good day" / "bad day") > 70% of the time over 4 weeks.
- **Failure:** Agreement < 50% (worse than random). Algorithm needs fundamental redesign or readiness feature deprioritized.

### H-06 has no workability threshold

H-06 (Hevy rate limits) says "ramp until 429s." But what limit is "workable"?

**Fix:** Define threshold:
- **Fully workable:** >= 30 req/min. On-demand reads during workout generation are feasible.
- **Workable with caching:** 5-30 req/min. Cache full workout history locally; refresh on schedule, not on-demand. Adds ~10 minutes of stale data.
- **Severely constrained:** < 5 req/min. Batch-only sync (1-2x daily). Workout generation uses cached data that may be up to 24h old. Acceptable but limits real-time feel.
- **Blocking:** < 1 req/min. Hevy API is unsuitable for programmatic use. Reassess Hevy dependency.

---

## Section 3: Deferred Items with Present-Day Planning Implications

### Supplement schema should be pre-designed like nutrition schema

D-15 says "Nutrition tables designed now, built later." The same logic applies to supplement tables (supplements, supplement_entries, supplement_stack, supplement_interactions). They're 5th in sequence, but if the server schema is designed in the architecture phase, supplement tables should be sketched at the same time to prevent migration conflicts later. The register is silent on this.

**Fix:** Extend D-15 to: "Nutrition AND supplement tables designed now, built later."

### AI coaching conversation storage is unaddressed

D-16 addresses AI coaching context scope. But where do conversation histories live? If coaching ships in the workout phase (before nutrition), conversations need a storage location. Server-side (consistent with coaching being server-orchestrated) or client-side (consistent with offline access)? The register doesn't mention conversation persistence at all.

**Fix:** Add a preference item: "AI coaching conversation history stored server-side (consistent with server-side AI orchestration). Client caches recent N messages for display. Full history accessible via server."

### Wearable device is an unregistered dependency

The readiness pipeline (H-01, H-02, H-03, H-04) assumes Tyler has a wearable that writes HRV and sleep data to Health Connect. The specific device matters: Samsung Galaxy Watch writes HRV during sleep only; WHOOP HC is unreliable; Oura writes to HC on Android. The register never names the device or registers it as a dependency.

**Fix:** Register as an assumption: "Tyler's wearable device writes HRV, sleep stages, and resting heart rate to Health Connect." Identify the specific device during architecture so HC data type availability can be confirmed.

### Exercise-to-muscle-group mapping needs a data source

D-05 and P-05 both depend on knowing which muscle groups each exercise targets. Hevy's exercise template API returns exercise names and types but may not include muscle group mappings. If not, Apex needs its own mapping table. This is a data dependency for the first-shipped feature and isn't registered anywhere.

**Fix:** Register as a validation item: "Confirm whether Hevy's exercise template API includes primary/secondary muscle group data. If not, Apex needs a curated exercise-to-muscle-group mapping table (~200-500 exercises)."

### Hevy Pro subscription is an unregistered operational dependency

The Hevy API requires a Hevy Pro subscription (~$60/yr). If the subscription lapses, the entire workout generation pipeline stops. This is noted in the research briefs but never registered as a dependency.

**Fix:** Register as a known operational dependency with no architectural mitigation needed (it's a subscription, not a code problem).

---

## Section 4: Hidden Assumptions Still Not Registered

| Assumption | Where it's implied | Why it matters |
|-----------|-------------------|----------------|
| Tyler has a wearable that writes HRV + sleep to HC | H-01 through H-04, readiness pipeline | No wearable = no readiness data = no readiness-to-workout loop |
| Hevy exercise templates include muscle group data | D-05, P-05, workout generation | If absent, Apex must maintain its own mapping — initial data work before any workout generation can ship |
| Tyler has a Hevy Pro subscription and maintains it | D-10, H-01, H-06, entire workout pipeline | Core feature depends on a third-party subscription |
| Server is Node.js Express (existing architecture) | D-14, D-16, server-side workout generation | New server logic (workout gen, AI orchestration, nutrition CRUD) must fit within existing framework or justify migration |
| Claude API remains stable and available | D-07, D-16, AI coaching | API deprecation or pricing change could affect coaching feature |
| Tyler's Android device runs Health Connect (API level 34+) | D-08, all HC-dependent features | Already confirmed (minSdk = 34 in build config) — this one is registered implicitly |

---

## Section 5: Premature Architecture Leakage

The following items in the decision register contain implementation details that should be deferred to the architecture phase. They're reasonable guesses but shouldn't be treated as constraints.

| Item | What leaked | What should be locked instead |
|------|-----------|------------------------------|
| D-06 | "EMA smoothing, weekly recalculation" | "TDEE computed client-side; specific algorithm determined during architecture" |
| D-14 | "Client sends readiness score as parameter. Server returns generated routine with reasoning." | "Workout generation is server-side; request/response contract defined during architecture" |
| P-03 | "Batch job during nightly sync cycle" | "Morning briefing should minimize synchronous API dependency; implementation pattern determined during architecture" |
| D-10 | "Exponential backoff for rate limits" | "Rate limit handling strategy determined after H-06 validation" |

These are all reasonable implementation choices that will likely survive architecture review. The risk isn't that they're wrong — it's that they're being locked before the architecture phase has evaluated alternatives. During architecture, if a better pattern emerges, these shouldn't feel like constraints.

**Recommendation:** Reframe these as "expected implementation approach" rather than "locked decision." The product decision is locked; the implementation pattern is a starting hypothesis.

---

## Section 6: Remaining Blockers

**There are no remaining blockers for starting architecture.**

The three original blockers (precision vs. awareness, sequencing, compute split) are resolved. H-06 (Hevy rate limits) was flagged as "before architecture finalizes sync design" — this blocks a specific design decision within architecture, not the start of architecture itself. Architecture can begin on schema design, client structure, server endpoints, and readiness algorithm while rate limit testing runs in parallel.

The items identified in Sections 1-5 above are refinements, not blockers. They can be addressed in the first days of architecture work.

---

## Section 7: Final Recommendation

### GO — Architecture can begin.

The planning package is mature enough. Specifically:

**What is genuinely ready:**
- The competitive landscape is mapped with 18 products across 25 dimensions, audited and corrected
- User complaints and failure modes are documented with specific sources, upvote counts, and confidence labels
- 17 decisions are locked with evidence and explicit user choices
- 8 hypotheses are identified with validation methods and timing
- The "mistakes to avoid" and "avoid" lists are well-evidenced and actionable
- The three original blockers are resolved with clear rationale
- The newly surfaced architectural assumptions (7 items) are registered
- Deferred items have clean extension points identified

**What is imperfect but acceptable:**
- 4 hypotheses lack explicit success/failure thresholds (fixable in Section 8 conditions)
- 5 hidden assumptions were identified that need to be registered (fixable immediately)
- Some implementation details leaked into product decisions (acknowledged; treat as starting hypotheses, not constraints)
- The readiness algorithm is an unsolved problem being treated as a display problem — architecture must scope the algorithm work honestly

**What would NOT be acceptable:**
- Starting architecture without resolving the precision vs. awareness nutrition question — **resolved**
- Starting architecture without deciding workout-first vs. nutrition-first — **resolved**
- Starting architecture without a compute split principle — **resolved**
- Starting architecture without knowing the Hevy API can handle programmatic use — **H-06 can run in parallel; doesn't block start**

---

## Section 8: Exact Preconditions Before Architecture Begins

### Must do before the first architecture session (< 30 minutes of work):

1. **Register the 5 hidden assumptions from Section 4.** Add to the decision register:
   - Tyler's specific wearable device and its HC data types
   - Hevy exercise template muscle group data availability (check API docs)
   - Hevy Pro subscription as operational dependency
   - Server framework (Node.js Express) as a constraint
   - Claude API stability as a dependency

2. **Add success/failure criteria to H-01, H-02, H-03, and H-06** per the thresholds defined in Section 2 of this review.

3. **Extend D-15 to include supplement schema** in the "designed now, built later" scope.

4. **Split D-10** into (a) abstraction interface (locked) and (b) fallback display scope (preference).

5. **Promote P-06 to D-17** (it's a decision, not a preference).

### Must do during the first week of architecture (can run in parallel with schema design):

6. **Test Hevy API rate limits** (H-06). Start at 1 req/10s, ramp up. Document the threshold. This shapes sync architecture.

7. **Confirm Hevy exercise template API includes muscle group data.** If not, scope the curated mapping table as part of workout generation MVP.

8. **Add AI coaching conversation storage** as a preference item: server-side history, client-side recent cache.

### Not required before architecture starts:

- HC permission persistence testing (H-04) — runs during MVP, shapes urgency of watchdog, doesn't block architecture
- HRV-strength correlation (H-02) — runs during MVP, shapes readiness algorithm weights, doesn't block architecture
- Personal logging test — runs during nutrition pillar development, not workout-first phase
- Gemini Flash accuracy test — deferred until photo estimation is in scope

---

*This review finds the planning package mature enough to begin architecture, with 5 minor registration tasks and 3 parallel validation tasks as conditions. No structural blockers remain. The research quality is high, the decisions are well-classified, and the hypotheses have clear paths to validation. Proceed.*
