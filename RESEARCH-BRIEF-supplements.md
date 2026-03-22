# RESEARCH BRIEF: Supplement Tracking & Advice

**Feature:** Log daily supplements, get evidence-based dosing recommendations, timing optimization, interaction warnings
**Date:** 2026-03-15
**Status:** COMPLETE — ready for architecture review

---

## 1. Competitive Landscape

| App | Key Feature | Database Size | Price |
|-----|------------|---------------|-------|
| **SuppTrack** | Barcode scan 189K products, nutrient totals, goal-based stacks, reminders | 189K products | Free + premium |
| **SuppCo** | 250K supplements, stack analysis algorithm, brand trust ratings, age/sex recommendations | 250K products | Free + premium |
| **Supplements AI** | AI-powered gap detection, timing optimization based on interactions/synergy, smart reminders | Unknown | $5/mo |
| **CareClinic** | Medical-grade tracking (brand, batch, dosage), nootropics/peptides support | General | Free + $10/mo |
| **SuppConnect** | AI product scanner, nutrition analysis, vitamin/mineral/creatine/peptide tracking | Unknown | Free + premium |

### Key Insight
Supplement tracking apps are **nascent and fragmented**. No dominant player. Most focus on logging, few on evidence-based advice. The opportunity is to integrate supplement tracking with Apex's existing health data (HRV, sleep, BP, workouts, nutrition) to provide **contextual recommendations** — e.g., "Your HRV is low and sleep quality declined — consider magnesium glycinate before bed."

---

## 2. Evidence Database Options

### Option A: Curated Static Database (Recommended for v1)
Build a focused database of **20-30 common fitness supplements** with evidence-graded recommendations. This is what Apex needs for a single-user fitness app.

**Core supplements to include:**
| Category | Supplements |
|----------|-------------|
| **Muscle/Performance** | Creatine monohydrate, beta-alanine, citrulline malate, caffeine, BCAAs |
| **Recovery/Sleep** | Magnesium (glycinate/threonate), melatonin, tart cherry extract, ashwagandha |
| **General Health** | Vitamin D3, omega-3 (fish oil), zinc, vitamin K2, vitamin B12 |
| **Joint/Connective** | Collagen peptides, glucosamine, MSM |
| **Protein** | Whey protein, casein, plant protein |
| **Other** | Electrolytes, probiotics, multivitamin |

**Data per supplement:**
- Evidence grade (A-F, Examine.com methodology)
- Effective dosage range
- Optimal timing (pre-workout, post-workout, with meals, before bed)
- Known interactions (with other supplements and common medications)
- Mechanism of action (1 sentence)
- Key studies summary

**Why static for v1:** Examine.com has no public API. NatMed API requires institutional subscription ($$$). For 20-30 supplements, a hand-curated JSON file is faster, cheaper, and more accurate than any API.

### Option B: Examine.com (Reference, not API)
- Largest evidence-based supplement database (800+ supplements)
- Grading system: A (strong evidence) → F (unsafe)
- Algorithm factors: consistency of evidence + magnitude of effect
- **No public API** — would need to manually extract data or link to their pages
- Examine Pro ($33/mo) for health professionals has downloadable study summaries

### Option C: NatMed / Natural Medicines Database (Enterprise)
- RESTful JSON API with interaction checker endpoint
- 1,000+ drug-supplement interactions with severity + evidence ratings
- **Enterprise pricing** — likely $500+/mo, overkill for single-user app
- Best for: healthcare apps with many users

### Option D: IMgateway (University of Sydney)
- 1,000+ drug-food-supplement interactions
- Evidence-based, developed with academic rigor
- API available for integration
- Pricing unknown, likely institutional

---

## 3. Supplement Timing Framework

Based on peer-reviewed research (PMC5545206):

| Timing Window | Supplements | Evidence |
|--------------|-------------|----------|
| **Pre-workout (30-60 min)** | Caffeine (3-6mg/kg), citrulline (6-8g), beta-alanine (3-6g daily, timing doesn't matter for saturation) | Strong |
| **During workout** | Electrolytes, carbohydrates (if >60 min session) | Strong |
| **Post-workout (within 2h)** | Creatine (3-5g, slightly better post than pre), protein (20-40g), carbohydrates | Moderate-Strong |
| **With meals** | Vitamin D3 (with fat), omega-3 (with fat), zinc (with food to reduce nausea), vitamin K2 | Strong |
| **Before bed** | Magnesium glycinate (200-400mg), melatonin (0.5-3mg), casein protein | Moderate |
| **Any time (consistency > timing)** | Creatine (if not post-workout), beta-alanine, collagen, multivitamin | Research consensus |

**Key finding:** Consistency matters more than perfect timing for most supplements. The app should optimize timing but not stress about it.

---

## 4. Interaction Checking (Simplified)

For a single-user fitness app, a full drug interaction database is overkill. Instead, implement a **known-interactions matrix** for the 20-30 tracked supplements:

### Critical Interactions to Flag
| Supplement A | Interacts With | Effect | Severity |
|-------------|---------------|--------|----------|
| Calcium | Iron | Calcium inhibits iron absorption | Moderate — take 2+ hours apart |
| Zinc | Copper | High zinc depletes copper long-term | Low — supplement copper if zinc >40mg/day |
| Vitamin K2 | Blood thinners (warfarin) | K2 counteracts warfarin | HIGH — medical consultation needed |
| Magnesium | Certain antibiotics | Reduces antibiotic absorption | Moderate — take 2+ hours apart |
| Caffeine | Creatine | No negative interaction (common myth) | None — safe to combine |
| Fish Oil | Blood thinners | Additive anticoagulant effect | Moderate — monitor |
| Vitamin D | Magnesium | Synergistic — Mg needed for D activation | Positive — take together |
| Melatonin | Caffeine | Caffeine blocks melatonin | Moderate — don't take caffeine within 6h of melatonin |

This matrix is small enough to hardcode and maintain manually. Expand over time.

---

## 5. Data Model

### Server-Side (PostgreSQL)

```sql
-- Master supplement catalog (curated, ~30 entries)
CREATE TABLE supplements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,     -- "Creatine Monohydrate"
    category        TEXT NOT NULL,            -- 'muscle', 'recovery', 'health', 'joint', 'protein'
    evidence_grade  CHAR(1) NOT NULL,         -- 'A' through 'F'
    dose_min        DOUBLE PRECISION,         -- grams or mg
    dose_max        DOUBLE PRECISION,
    dose_unit       TEXT DEFAULT 'mg',        -- 'mg', 'g', 'mcg', 'IU'
    optimal_timing  TEXT,                     -- 'post_workout', 'with_meals', 'before_bed', 'any'
    mechanism       TEXT,                     -- 1-sentence explanation
    key_evidence    TEXT,                     -- Summary of supporting research
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Known interactions between supplements
CREATE TABLE supplement_interactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplement_a    UUID REFERENCES supplements(id),
    supplement_b    UUID REFERENCES supplements(id),
    interaction_type TEXT NOT NULL,           -- 'negative', 'positive', 'neutral'
    severity        TEXT NOT NULL,            -- 'high', 'moderate', 'low'
    description     TEXT NOT NULL,            -- "Calcium inhibits iron absorption"
    recommendation  TEXT NOT NULL             -- "Take 2+ hours apart"
);

-- Daily supplement log
CREATE TABLE supplement_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplement_id   UUID REFERENCES supplements(id),
    dose            DOUBLE PRECISION NOT NULL,
    dose_unit       TEXT NOT NULL,
    taken_at        TIMESTAMPTZ NOT NULL,
    timing_window   TEXT,                    -- 'pre_workout', 'post_workout', 'morning', 'evening'
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_supplement_entries_date ON supplement_entries(taken_at);

-- User's active supplement stack (what they take regularly)
CREATE TABLE supplement_stack (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplement_id   UUID REFERENCES supplements(id),
    daily_dose      DOUBLE PRECISION NOT NULL,
    dose_unit       TEXT NOT NULL,
    frequency       TEXT DEFAULT 'daily',    -- 'daily', 'workout_days', 'as_needed'
    preferred_time  TEXT,                    -- 'morning', 'pre_workout', 'post_workout', 'evening'
    active          BOOLEAN DEFAULT TRUE,
    start_date      DATE NOT NULL,
    end_date        DATE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### Client-Side (Room)
- Cache supplement catalog (static, rarely changes)
- Offline supplement entry queue (same pattern as food/health sync)

---

## 6. UX Design

### Supplement Screen (New Tab or Section in Settings)

```
[My Stack]
  ┌──────────────────────────────────────┐
  │ Creatine Monohydrate    5g    daily  │  ✓ Taken today
  │ Vitamin D3              5000 IU daily│  ✗ Not yet
  │ Omega-3 Fish Oil        2g    daily  │  ✗ Not yet
  │ Magnesium Glycinate     400mg evening│  ✗ Not yet
  │ Whey Protein            30g   post-WO│  ✓ Taken today
  └──────────────────────────────────────┘
  [+ Add Supplement]

[Quick Log] — tap any supplement → logs at current time
             → long-press → edit dose/time

[Today's Schedule]
  Morning:  Vitamin D3 (with breakfast fat)
  Pre-WO:   Caffeine 200mg (if training today)
  Post-WO:  Creatine 5g + Whey 30g
  Evening:  Magnesium 400mg, Omega-3 2g (with dinner)

[Interactions] — warning banner if conflicts detected
  ⚠️ Take Calcium and Iron 2+ hours apart

[Insights] — AI Coach integration
  "You haven't logged Vitamin D in 3 days. Your sleep quality
   has been declining — D3 supports sleep regulation."
```

### Dashboard Integration
- Small "Supplements" chip in Dashboard showing `X/Y taken today`
- Readiness algorithm: factor supplement compliance into recommendations

---

## 7. Integration with Other Apex Features

| Feature | Integration |
|---------|------------|
| **Nutrition** | Protein supplement intake counts toward daily protein macro target |
| **Workout Builder** | Pre-workout supplement reminders on training days |
| **AI Coach** | Context-aware suggestions ("HRV dropping — try magnesium", "Training volume high — ensure creatine loaded") |
| **Sleep/HRV** | Correlate supplement intake with sleep quality trends over time |
| **Notifications** | Scheduled reminders for each timing window |

---

## 8. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| User treats app as medical advice | HIGH | Prominent disclaimer: "Not medical advice. Consult your doctor." Don't recommend stopping medications. |
| Evidence changes over time | LOW | Curated database is small — update manually when major studies publish. Link to Examine.com for deep dives. |
| Interaction matrix incomplete | MEDIUM | Start with known critical interactions only. AI Coach can flag "I don't have interaction data for X+Y, consult a pharmacist." |
| Feature bloat (supplements feel tacked on) | MEDIUM | Keep it simple: stack definition + daily checklist + timing reminders. No complex analytics in v1. |
| Barcode scanning for supplement bottles | LOW | Defer. Manual entry for ~10-15 supplements in a personal stack is fine. Barcode scanning is overkill for v1. |

---

## 9. Implementation Phases

| Phase | Scope | Dependencies |
|-------|-------|-------------|
| **Phase 1** | Server: supplements catalog (seed 25 entries) + CRUD endpoints | Server access |
| **Phase 2** | Server: supplement_stack + supplement_entries + interactions table | Phase 1 |
| **Phase 3** | Android: "My Stack" screen with daily checklist + quick-log | Phase 2 |
| **Phase 4** | Timing schedule + notification reminders | Phase 3 |
| **Phase 5** | Interaction warnings (static matrix check) | Phase 2 |
| **Phase 6** | Dashboard integration (compliance chip) | Phase 3 |
| **Phase 7** | AI Coach integration (context-aware suggestions) | Phase 3 + AI Coach |

---

## 10. Open Questions for User

1. **Current stack:** What supplements do you currently take? (So I can seed the catalog with your actual stack)
2. **Medications:** Any prescription medications? (Needed for interaction checking scope)
3. **Reminder style:** Push notifications per supplement, or a single "morning supplements" / "evening supplements" reminder?
4. **Protein counting:** Should protein powder servings auto-add to the nutrition tracker's protein macro?
5. **Separate tab or subsection?** New bottom nav destination, or a section within Settings/Dashboard?

---

## Sources

- [Timing, Dose and Duration of Dietary Supplements in Sports (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC5545206/)
- [Examine.com: Evidence Grading System](https://examine.com/about/grades/)
- [Examine.com Database](https://examine.com/database/)
- [NatMed / Natural Medicines Database](https://trchealthcare.com/product/natmed-pro/)
- [Drug-Nutrient Interaction Validation Study (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC11698021/)
- [Drug Interaction Checker APIs Overview](https://www.altexsoft.com/blog/drug-interaction-checker-apis/)
- [SuppTrack App](https://supptrack.app/)
- [Supplements AI App](https://supplements-ai.com/)
- [SuppCo App](https://play.google.com/store/apps/details?id=co.supp.app)
- [Best Supplement Tracker Apps Guide](https://supplements-ai.com/blog/guides/best-supplement-tracker-apps)
- [Creatine Timing Research (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC8401986/)
- [Supplement Timing Guide 2025](https://gym-center.com/blog-supplement-timing-guide-2025/)
