# Pre-Architecture Planning Memo: Apex v2

**Date:** 2026-03-15
**Input:** Pass 1 (Competitive Landscape Report, 18 products) + Pass 2 (Product Intelligence Report, 9 complaint clusters, 8 anti-patterns)
**Purpose:** Distill all research into explicit product decisions, ranked by evidence strength, before any architecture or code begins

---

## Top 10 Product Decisions This Research Affects

### 1. Nutrition logging speed is the single most important UX constraint

The 30-second-per-entry threshold is the line between retention and abandonment. 77% of nutrition app users are gone by day 30 (Adjust/Stanford). Homemade meals — the #1 friction point — require 4-6 steps per meal in every existing app. No competitor has solved this.

**Decision:** Every nutrition screen interaction must be benchmarked against 30 seconds. If a logging flow exceeds this, it ships with a faster fallback (quick-add: just calories + protein). Recent/favorite foods for one-tap re-logging must be the primary entry point, not search.

**Evidence:** r/loseit homemade meals thread, r/WeightLossAdvice "exhausting" (50 upvotes), MacroFactor speed research, Adjust Day-30 retention data. Strength: Very Strong.

---

### 2. Food database quality must be protected from crowdsourcing decay

MacroFactor built its market position on a verified database. In Feb 2026, users are threatening to leave because crowdsourced entries are overwriting verified data — the exact failure MFP has suffered for years. Database accuracy is a silent brand attribute: users never praise it, but they leave when it breaks.

**Decision:** Use USDA FoodData Central (validated, 380K+ foods) as the primary source. Use Open Food Facts for barcode lookup only, flagged as "community-sourced." Never allow unverified user submissions to overwrite validated entries. If custom foods are added, they apply only to the user's own account.

**Evidence:** MFP "pizza at 50 calories" (r/Myfitnesspal), MacroFactor database decay (r/MacroFactor, Feb 2026, 37 upvotes on key complaint), J. Human Nutrition study (Oct 2025, Cronometer > MFP). Strength: Very Strong.

---

### 3. Workout generation must show its reasoning — or users won't trust it

Fitbod is the market leader in AI workout generation, and its subreddit is dominated by distrust: the algorithm places isolation before compound exercises, produces identical workouts when goals change, suggests dangerous weight swings, and never explains why. Users with 1,000+ logged workouts are leaving. The failure is not the algorithm per se — it's opacity.

**Decision:** Every generated workout must display a brief rationale: why each exercise was selected, why that weight/rep range, where the user is in their mesocycle, and when the next deload is scheduled. The user must be able to override any element with one tap. This is the primary differentiator vs. Fitbod.

**Evidence:** r/fitbod one-year review (56 upvotes, Exercise Science credential), "App is effectively broken" thread, "same workouts all the time" (6+ threads), Fitbod rep acknowledgment of algo issues. Strength: Very Strong.

---

### 4. The AI coach must be proactive and interpretive — never prescriptive on health

AI coaching fails at workout programming (Fitbod, iFit) but succeeds at accountability (weight loss outcomes despite bad AI plans). It fails when it gives generic advice (Strava, Peloton, Garmin), ignores user constraints (iFit, Peloton), or hallucinates health data (Oura Advisor, Mar 2026). It succeeds when it remembers context (Oura Advisor pre-hallucination) and surfaces non-obvious correlations in the user's own data.

**Decision:** The coach anchors on three functions: (a) proactive morning briefing with ONE concrete action item based on real data, (b) post-workout analysis citing specific numbers, (c) trend correlation alerts ("your HRV dropped 18% on days after training legs past 7pm"). It never diagnoses, never fabricates data, never gives supplement interaction advice, and always cites which data point drove the recommendation. Persistent memory of user constraints (injuries, equipment, preferences) is non-negotiable — every constraint stated once must be enforced in every future interaction.

**Evidence:** Oura hallucination (1K-upvote thread, Mar 2026), Peloton "absolute garbage" (150+ upvotes), iFit "just broke up" (detailed thread, ignored constraints 5+ times), Strava "ashtray on a roller coaster" (144-upvote top comment), MobiDev 40% decay / 24% improvement with nudges. Strength: Very Strong.

---

### 5. Readiness scores must be transparent, not opaque

Cross-device readiness scores disagree wildly (Oura 84 vs. WHOOP 43 on the same night). Users experiencing illness get 85+ readiness. New users get misleading scores during 2-6 week calibration. The meta-response: "these scores are made up by the platforms." The failure is presenting a single number without the inputs that produced it.

**Decision:** Apex's readiness display must always show the top 2-3 contributing factors and their weights (e.g., "Readiness 62% — HRV dropped 18% [biggest factor], sleep 5.2h [below 7.1h avg], BP normal"). During the calibration period, show "Still calibrating (day 5 of ~14) — scores will improve with more data" instead of a confident-looking number. Use probabilistic language ("your recovery indicators suggest...") not definitive language ("you ARE recovered").

**Evidence:** r/whoop score distrust (hundreds of threads, 2021-2026), Oura flu-while-green (Dec 2024), cross-device disagreement (Jun 2025), WHOOP algorithm update breaking baselines (Jun 2025), Oura 6-week calibration report. Strength: Very Strong.

---

### 6. Photo food estimation is an optional accelerator, not a core feature

Independent RD testing: 82% accuracy for simple foods, 50% for homemade/ethnic food. Hidden calories (oil, butter, sauces) are a physical information gap. Cal AI returns different results for identical inputs. The correction burden often equals or exceeds manual logging time. An ML engineer: "anyone selling this as a product is lying to you."

**Decision:** Photo estimation ships after manual logging UX is polished — it is feature #4 in the sequence, not feature #1. It is positioned as "quick baseline you can adjust" with per-item confidence scores. A one-tap "add cooking oil" button addresses the hidden-calories gap. LLM temperature is set to 0 for deterministic results. Manual search remains the primary path for homemade meals.

**Evidence:** Peony RD testing (100+ meals, 3 months), Cal AI stress test with food scale (r/ArtificialInteligence), r/nutrition consensus "manual is more accurate" (12 upvotes), r/PetiteFitness hidden calories (30 upvotes), ML engineer assessment (r/loseit). Strength: Very Strong.

---

### 7. Health Connect sync needs active monitoring, not passive trust

HC permissions drop silently on multiple devices. Data arrives 24h late. Apps must be opened to trigger sync. Samsung blocks third-party step writes. WHOOP's HC integration has been broken for 16+ months. Oura recalculates imported data, underreporting by 20-40%. Garmin's HC scope hasn't expanded since July 2025 and permanently excludes proprietary metrics.

**Decision:** Implement a background permission watchdog that checks HC permissions on every WorkManager sync cycle and alerts the user before data goes stale. Show last-sync timestamp prominently in the UI. Include a manual "Refresh now" button. For WHOOP data, use their OAuth API directly (HC is unreliable). Accept that Garmin Body Battery and Training Load will never be available via HC. Build graceful degradation: if HC data is missing, show "last synced 2h ago" with the most recent available data, not an empty screen.

**Evidence:** r/Coros permission drops (Sep 2025), r/ConquerorChallenge 24h delay, WHOOP step sync broken 16+ months (multiple r/whoop threads), Samsung blocking 3P writes (r/ouraring, Nov 2025), Oura calorie undercount 20-40% (r/ouraring, Dec 2024), Garmin HC scope unchanged (verified Mar 2026). Strength: Very Strong.

---

### 8. Hevy is the right workout execution platform, but the API dependency is a real risk

Hevy's write API (`POST /v1/routines`) enables the readiness-to-workout loop that no competitor offers. But the API has an explicit "may change or abandon" caveat, undocumented rate limits (429 errors confirmed, r/Hevy Apr 2025), and no SLA. Hevy's own feature roadmap may eventually add readiness/recovery features, reducing Apex's differentiator.

**Decision:** Build an abstraction layer around the Hevy API. Implement exponential backoff for rate limits. Cache workout data aggressively. Have a fallback plan: if Hevy API disappears, Apex must be able to display generated workouts in-app (even if the user logs them manually in Hevy). Monitor r/Hevy and API changelog monthly. The workout generation algorithm lives entirely on Apex's server — Hevy is the delivery mechanism, not the brain.

**Evidence:** Hevy API OAS 3.0 docs (confirmed write endpoints), "may change or abandon" disclaimer, 429 errors (r/Hevy, Apr 2025), 10M+ Hevy users (2025 recap). Strength: Strong.

---

### 9. Supplement tracking should be a lightweight audit tool, not a daily obligation

The sustainable use pattern is 2-4 week bursts ("did magnesium help my sleep?"), not permanent daily logging. Retroactive logging is the main friction point (WHOOP journal: "trying to remember everything the next day" — 236 upvotes). Alert fatigue from reminders kicks in fast. AI supplement interaction checking is formally unsafe (75% wrong per ASHP pharmacist study).

**Decision:** Supplement tracking ships as a daily checklist with one-tap "taken" buttons, not individual dose logging. Default expectation is short-burst auditing, not permanent tracking. The killer feature is outcome correlation (supplement intake graphed against biometric trends over 14-30 day windows). Never use LLMs for interaction checking — link to Examine.com and drugs.com instead. Reminders are opt-in, not default.

**Evidence:** WHOOP journal (r/whoop, 236 upvotes), MacroFactor supplement workaround (r/MacroFactor, May 2024), ASHP ChatGPT drug study (r/JordanHarbinger), NutraIngredients/Lumina 93% stat (Feb 2025). Strength: Strong.

---

### 10. Feature sequencing should follow the daily-value gradient

Not all five pillars deliver equal daily value. Nutrition logging and workout generation are daily activities. AI coaching and supplement tracking are enhancement layers. Photo estimation is an accelerator for nutrition, not a standalone feature.

**Decision:** Build in this order:
1. **Nutrition logging** (USDA + OFF, barcode, recent foods, adaptive TDEE) — daily use, highest friction to solve, highest abandonment risk
2. **Workout generation** (readiness → algorithm → push to Hevy) — daily use on training days, strongest differentiator, no competitor does this
3. **AI coaching** (proactive briefings, trend correlations, persistent memory) — needs enough data history to be non-obvious, builds on top of pillars 1 + 2
4. **Photo food estimation** (Gemini Flash, optional accelerator) — needs manual logging UX to be solid first, so correction paths are smooth
5. **Supplement tracking** (checklist, outcome correlation) — lowest daily-use frequency, highest value in short bursts

**Evidence:** Synthesis of all research — logging friction is the #1 abandonment driver; workout generation is the #1 unclaimed differentiator; AI coaching needs data depth to avoid generic-advice failure; photo AI works best as a supplement to manual logging; supplement tracking has lowest daily engagement.

---

## Top 10 Mistakes to Avoid

| # | Mistake | What Happens If You Make It | Evidence Source |
|---|---------|---------------------------|----------------|
| 1 | **Ship AI coaching that gives generic advice** | Users ignore it within 3 months; "ashtray on a roller coaster" | Strava (144 upvotes), Peloton (150+ upvotes), iFit, WHOOP, Garmin Connect+ (417 comments) |
| 2 | **Let AI fabricate or hallucinate health data** | Liability risk; users demand feature removal | Oura Advisor (1K-upvote thread, Mar 2026) |
| 3 | **Allow crowdsourced food data to overwrite verified entries** | Database trust decay — the exact failure killing both MFP and now MacroFactor | MFP (decade of complaints), MacroFactor (Feb 2026) |
| 4 | **Present readiness as an opaque single number** | Users say "these scores are made up" and stop trusting | WHOOP/Oura cross-device disagreement, flu-while-green, calibration confusion |
| 5 | **Generate workouts without showing reasoning** | Users call it "random exercise generator" and override everything manually | Fitbod (6+ complaint threads, Exercise Science-credentialed review) |
| 6 | **Market photo estimation as accurate for all foods** | 50% accuracy on homemade meals; users feel lied to | RD testing (Peony, 100+ meals), Cal AI stress tests, ML engineer assessment |
| 7 | **Trust Health Connect to sync reliably without monitoring** | Silent permission drops → stale data → user blames Apex | r/Coros, r/ConquerorChallenge, Samsung blocking, WHOOP 16-month bug |
| 8 | **Implement streak counters as primary engagement** | Anxiety, guilt, and eventual abandonment when streak breaks | iNews (296-week streak essay), r/duolingo, r/AppleWatchFitness, behavior research |
| 9 | **Use LLMs for supplement interaction checking** | 75% wrong/incomplete per pharmacist study | ASHP study via r/JordanHarbinger |
| 10 | **Hard-couple to Hevy API without abstraction** | "May change or abandon" caveat + undocumented rate limits = fragile core feature | Hevy API disclaimer, 429 errors (r/Hevy, Apr 2025) |

---

## Top 10 Table-Stakes Capabilities

These are minimum expectations. Shipping without them means not being taken seriously, regardless of what differentiators exist.

| # | Capability | Why It's Table-Stakes | Evidence |
|---|-----------|----------------------|----------|
| 1 | **Barcode scanning (free, never paywalled)** | MFP's paywall caused the largest user exodus in fitness app history | r/loseit (3,593 upvotes); every major competitor has it |
| 2 | **Recent/favorite foods for one-tap re-logging** | Power users across all nutrition apps cite this as the #1 speed hack | Consistent across r/loseit, r/MacroFactor, r/CICO |
| 3 | **Previous session visible during workout logging** | Universal expectation — users need to know "what did I lift last time?" | r/Hevy, r/fitbod, r/strongapp feature requests |
| 4 | **Offline support for core logging** | Gyms have poor signal; logging must work without connectivity | 8/18 competitors have full offline; Apex already has Room queue |
| 5 | **Health Connect read integration** | 11/18 competitors support it; Android ecosystem standard | Pass 1 integration matrix |
| 6 | **Data export (CSV minimum)** | MFP paywalling data export drives resentment; users expect portability | r/Myfitnesspal (Nov 2025, 14 upvotes); Cronometer offers free export |
| 7 | **Honest calibration/cold-start messaging** | WHOOP/Oura users furious about misleading timelines | Oura 6-week calibration (Facebook group); WHOOP first-week confusion |
| 8 | **Adaptive calorie targets (not static formulas)** | Static goals are the #1 reason users leave MFP for MacroFactor | r/MacroFactor migration threads; "the lesson: adaptive beats database size" |
| 9 | **Basic food search with reasonable database** | Without search, the app is a blank slate users can't populate | Every nutrition app has this; USDA + OFF covers it for free |
| 10 | **Manual refresh / last-sync timestamp for Health Connect** | Users need to know their data is current or stale | HC sync failures documented across multiple devices and apps |

---

## Top 5 Differentiators Worth Serious Consideration

These are rare or unique capabilities where evidence supports real user value and Apex has a structural advantage.

### 1. Readiness-Aware Workout Generation → Hevy (The Closed Loop)

**What it is:** Health Connect data (HRV, sleep, BP) → Apex server → readiness-adjusted workout → `POST /v1/routines` → Hevy → user logs → Apex re-syncs → repeat.

**Why it differentiates:** No competitor does this. HevyGPT generates static plans with no health data. Fitbod has no readiness input. WHOOP's strain coach doesn't know your training split. AthleteData.health advises conversationally but doesn't push structured routines.

**Risk:** Hevy API instability. Mitigated by abstraction layer + in-app fallback display.

**Evidence strength:** Very Strong — confirmed via Hevy API docs (write endpoints verified), Pass 1 integration matrix, Pass 2 workout complaints showing the gap.

---

### 2. Transparent Readiness Scoring (Show the "Why")

**What it is:** Readiness score that always displays contributing factors with weights, not just a number. "62% — HRV dropped 18% (biggest factor), sleep 5.2h (below your 7.1h avg)."

**Why it differentiates:** WHOOP, Oura, and Garmin all present opaque scores. Users' top complaint across all three is "I don't know why the score says what it says." Cross-device disagreement makes opaque scores look arbitrary.

**Risk:** The underlying data (HRV, sleep from Health Connect) may not be more accurate than WHOOP/Oura. Transparency helps because users can see which input they disagree with and learn the system's logic.

**Evidence strength:** Very Strong — hundreds of threads spanning 2021-2026 across r/whoop, r/ouraring, r/Garmin.

---

### 3. Proactive AI Coaching With Persistent Memory

**What it is:** AI that initiates conversations based on data anomalies ("your HRV has dropped 3 days in a row — consider reducing volume"), remembers all user constraints (injuries, equipment, goals, preferences), and never gives generic advice.

**Why it differentiates:** iFit Tailor can't remember a user's name after being told 5 times. Peloton's AI suggests chest workouts to someone with a shoulder fracture. Strava's AI can be fooled by renaming an activity. Oura Advisor is limited to 1-2 weeks of historical context. Every existing AI coach fails on either memory, specificity, or proactivity.

**Risk:** Engagement still decays ~40% after onboarding (industry-wide). Mitigated by proactive nudges (+24% retention per MobiDev), re-engagement mechanisms (goal resets, milestone acknowledgment), and honest "I notice you haven't asked me anything in a while" check-ins.

**Evidence strength:** Strong — Oura Advisor praised for memory (Nov 2024, 82-upvote thread), iFit Tailor destroyed by forgetting (detailed Apr 2025 thread), MobiDev retention data.

---

### 4. Supplement-Outcome Correlation

**What it is:** Graph supplement intake against biometric trends over 14-30 day windows. "You started magnesium 14 days ago; your average sleep score improved from 72 to 81."

**Why it differentiates:** CareClinic tracks supplements. WHOOP tracks biometrics. Welltory tracks HRV. No app in the research set correlates supplement intake with health outcomes in the user's own data. Staqc is attempting this but is pre-traction.

**Risk:** Demand is Medium-High, not Very Strong. The 93% NutraIngredients stat (health app users buying supplements from app suggestions) signals downstream value, but direct user requests for this specific feature are qualitative (r/QuantifiedSelf, r/PCOS). May be a feature users love once they see it but wouldn't have asked for.

**Evidence strength:** Moderate-Strong — NutraIngredients/Lumina (Feb 2025), r/QuantifiedSelf threads, Staqc's market entry. No quantified download or engagement data for the feature itself.

---

### 5. Self-Hosted Privacy as Explicit Product Positioning

**What it is:** Market Apex's self-hosted, single-user architecture as a feature, not just an implementation detail. "Your health data never leaves your server."

**Why it differentiates:** Cal AI breached 3.2M users (Mar 2026). Noom sells data to Meta, Taboola, Outbrain, Liftoff, Moloco. MFP faces active litigation over tracking without consent. Apex's architecture is structurally immune to all of these. r/privacy evidence shows health-data-aware users actively seek self-hosted alternatives.

**Risk:** Low. This is a positioning decision, not a technical one. The architecture already exists.

**Evidence strength:** Very Strong for competitor failures; Moderate for user selection behavior (r/privacy is a self-selected audience).

---

## Top 5 Ideas That Look Attractive But Should Likely Be Deferred or Avoided

### 1. Fully Autonomous AI Workout Generation (AVOID as primary mode)

**Why it's tempting:** Fitbod charges $80/year for it. RP charges $250/year. It sounds like the ultimate convenience feature.

**Why evidence says otherwise:** Fitbod's subreddit is the evidence. Users with 1,000+ logged workouts describe the algorithm as "random," "broken," "effectively a nonsense AI." Exercise science-credentialed users document compound-before-isolation errors, dangerous weight suggestions, and conservative overload that stunts progress. The fundamental problem: fully autonomous generation requires the algorithm to be right every time, and users can't tell when it's wrong unless they already know enough to program themselves.

**Better alternative:** Semi-autonomous generation with reasoning. Apex generates a workout based on mesocycle position and readiness, explains why, and lets the user adjust before pushing to Hevy. The user stays in the loop.

**Evidence strength:** Very Strong.

---

### 2. Social Features / Leaderboards (DEFER indefinitely)

**Why it's tempting:** Strava's social graph is its strongest retention mechanism. Research shows accountability increases 30-day goal completion.

**Why evidence says otherwise:** Social features require critical mass that Apex (single-user app) doesn't have. Empty leaderboards are demotivating. For health data apps specifically (BP, HRV, weight), social sharing creates privacy anxiety. Fitbod Clubs failed. The research consistently shows that for health-data-aware users, optional 1:1 accountability > public social graphs.

**When to reconsider:** Only if Apex ever has multiple users AND they explicitly request it.

**Evidence strength:** Strong.

---

### 3. Photo Food Estimation as the Primary Logging Path (DEFER to 4th in sequence)

**Why it's tempting:** Cal AI has 30M+ downloads. It's the fastest-growing category in nutrition apps. It feels like the future.

**Why evidence says otherwise:** 50% accuracy on homemade/ethnic food. Hidden calories (oil, sauces, butter) are a physical limitation no vision model can solve. The correction burden often negates time savings. Cal AI suffered a catastrophic data breach. MacroFactor's photo feature is in beta. The best users treat photo AI as a rough baseline they correct — making it an accelerator for manual logging, not a replacement.

**When to build it:** After manual logging UX is polished and correction paths (one-tap weight adjust, food swap, "add cooking oil") are smooth.

**Evidence strength:** Very Strong.

---

### 4. Streak Counters / Aggressive Gamification (AVOID)

**Why it's tempting:** Duolingo's streak is one of the most famous engagement mechanics in consumer software. Apple Watch rings are iconic.

**Why evidence says otherwise:** "My 296-week fitness app streak was ruining my life" (iNews, Jan 2026). "My streak is the only thing keeping me here. Not curiosity. Not progress. Just fear of losing a number" (r/duolingo). Behavioral research confirms extrinsic motivation crowds out intrinsic motivation. When a streak breaks (travel, illness, life event), users abandon entirely rather than restart. For health/fitness specifically, data-fatigue and tracking anxiety are documented (r/AppleWatchFitness).

**Better alternative:** Trend visualization. Show weekly/monthly progress curves instead of consecutive-day counts. Progress is motivating; streaks are anxiety-inducing.

**Evidence strength:** Very Strong.

---

### 5. Meal Planning / Recipe Generation (AVOID)

**Why it's tempting:** Every nutrition app roundup article mentions it. Oura Advisor users praise recipe suggestions.

**Why evidence says otherwise:** Meal planning features are frequently promoted in reviews but never cited as a retention driver in actual user feedback. No user in any of the hundreds of threads analyzed cited "meal planning" as the reason they stay with an app. The single positive case (Oura Advisor recipe suggestion) was for a user with highly specific dietary constraints (recovered ED, AuDHD) who provided extensive personal context — an edge case, not a general use case.

**When to reconsider:** Only if AI coaching data shows users frequently asking for meal suggestions — let demand emerge from real usage.

**Evidence strength:** Moderate — absence of positive signal rather than presence of negative signal. The claim is "no evidence it matters" not "evidence it doesn't matter."

---

## Top Unresolved Questions Requiring Further Validation

### 1. Does the 30-second logging threshold hold for Tyler's actual eating patterns?

**Why it matters:** The threshold comes from community research across thousands of users. But Tyler's specific diet (home-cooked vs. restaurant vs. packaged) determines how often he'll hit the friction ceiling. If Tyler eats 15 rotating meals, logging is trivial regardless of UX. If Tyler eats varied homemade food daily, the threshold is the binding constraint.

**How to validate:** Log 1 week of meals in MacroFactor or Cronometer. Time each entry. Calculate percentage of meals that exceed 30 seconds. This determines how much to invest in logging speed optimizations.

---

### 2. How accurate is Gemini Flash for Tyler's typical meals?

**Why it matters:** RD testing was on Western foods in controlled conditions. Tyler's actual meals may perform better or worse. The 50-82% range is wide enough that the feature could be either valuable or useless depending on dietary patterns.

**How to validate:** Test 20 meals with a food scale comparison. Photograph each meal, get Gemini Flash estimate, weigh and calculate actual. Compute personal accuracy rate and per-category breakdown.

---

### 3. Will Hevy's API remain stable through v2 development?

**Why it matters:** The readiness-to-workout loop is Apex's strongest differentiator, and it depends entirely on Hevy's API. The "may change or abandon" caveat is explicit. Hevy is actively developing (10M+ users, Product Hunt winner) which means both growth (good for ecosystem) and rapid change (bad for API stability).

**How to validate:** Monitor r/Hevy and API changelog monthly. Test API endpoints at the start of each development sprint. Build the abstraction layer from day 1 so the cost of a Hevy API change is contained.

---

### 4. Does Health Connect on Tyler's specific device drop permissions?

**Why it matters:** HC permission drops are device-dependent — some Samsung and Pixel users report drops every few days, while others never experience them. If Tyler's device is stable, the permission watchdog is a nice safety net. If Tyler's device drops permissions regularly, it's a critical feature.

**How to validate:** Run Apex's current HC sync for 2 weeks with logging. Check if permissions persist or require re-approval. Test on any other Android devices available.

---

### 5. Is HRV a useful readiness signal for strength training specifically?

**Why it matters:** A r/whoop user with exercise science background stated: "HRV has not been demonstrated as a reliable metric of readiness for strength training." Most HRV-readiness research is on endurance athletes. If HRV doesn't predict strength training readiness, the entire readiness-to-workout loop needs different inputs.

**How to validate:** Track HRV alongside subjective session quality (RPE, "felt good/bad") for 4-6 weeks. Correlate. If HRV predicts session quality for Tyler's training, it's useful. If it doesn't, supplement with sleep quality and subjective readiness as primary inputs, with HRV as one of several signals.

---

### 6. Will AI coaching engagement sustain beyond 3 months?

**Why it matters:** Every platform studied shows 40%+ engagement decay. The proactive-nudge approach improves retention by 24% (MobiDev), but even with that, long-term engagement is unproven beyond 12 months in any product. If the AI coach becomes noise after 3 months, the investment needs to be proportional.

**How to validate:** Build engagement tracking from day 1. Measure monthly: messages sent, messages read, recommendations acted on. If engagement drops below 1 interaction/day by month 3, the coach needs re-engagement mechanisms (goal resets, periodic challenge proposals, "I noticed you haven't asked me anything — here's what I've been tracking").

---

### 7. Is supplement-outcome correlation compelling enough to build?

**Why it matters:** This is classified as a differentiator, but demand evidence is Medium-Strong (qualitative, not quantified). The NutraIngredients stat (93% of health app users buy supplements from app suggestions) signals downstream value but doesn't directly validate the correlation feature. No competitor has built it, which could mean "unclaimed opportunity" or "nobody wants it enough to build it."

**How to validate:** Build the simplest possible version: a graph showing supplement intake dates overlaid with a selected biometric trend (sleep score, HRV, BP). Ship it. Measure whether Tyler actually uses it. If it proves compelling, invest in the AI coach surfacing correlations proactively. If not, keep it as a static graph and deprioritize.

---

### 8. What is the actual Hevy API rate limit?

**Why it matters:** Undocumented rate limits with confirmed 429 errors (r/Hevy, Apr 2025) mean the sync architecture must be conservative. But "conservative" could mean 1 req/minute (fine) or 10 req/hour (limiting for real-time sync).

**How to validate:** Empirical testing. Start at 1 request per 10 seconds and increase until 429s appear. Document the threshold. Build the sync schedule around 50% of the measured limit.

---

*This memo is the bridge between research and architecture. It does not propose data models, API endpoints, screen layouts, or code. Those decisions are next — informed by but not contained in this document.*
