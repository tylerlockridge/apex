# RESEARCH BRIEF: Calorie & Macro Tracking

**Feature:** Daily calorie and macronutrient tracking with food diary, barcode scanning, and adaptive TDEE
**Date:** 2026-03-15
**Status:** COMPLETE — ready for architecture review

---

## 1. Competitive Landscape

### Tier 1 — Best in Class
| App | Strengths | Weaknesses | Price |
|-----|-----------|------------|-------|
| **MacroFactor** | Adaptive TDEE algorithm, 54 tracked nutrients, fastest logging (AI describe), verified database, weekly auto-adjusting targets | Smaller database (verified > quantity), $72/yr | $6/mo |
| **Cronometer** | Most accurate micronutrient data, clinical-grade NCCDB + USDA, no user-submitted entries | Dated UI, no AI features, weaker barcode coverage | $50/yr |

### Tier 2 — Mass Market
| App | Strengths | Weaknesses | Price |
|-----|-----------|------------|-------|
| **MyFitnessPal** | Largest database (14M+ foods), social features, brand recognition | Inaccurate user-submitted entries, 15 actions to log vs MacroFactor's 10, only 14 tracked nutrients, aggressive ads | Free + $80/yr premium |
| **LoseIt** | Simple UX, good free tier, photo scanning | Smaller database, US-focused | Free + $40/yr |

### Tier 3 — AI-First (New Wave)
| App | Strengths | Weaknesses | Price |
|-----|-----------|------------|-------|
| **Cal AI** | Photo-first logging, very fast | Accuracy varies wildly, no verified database behind it | $70/yr |
| **Fitia** | RD-reviewed, clean UI | Small user base, limited food database | Free + premium |

### Key Takeaways
1. **Database accuracy > database size.** MFP has 14M foods but half are wrong. MacroFactor's verified DB is smaller but trustworthy.
2. **Speed of logging is the #1 retention driver.** 70% of users abandon within 2 weeks if logging is too slow.
3. **Adaptive TDEE is the killer feature.** MacroFactor's back-calculated expenditure from weight trend + intake is 40% more accurate than static equations after 3-4 weeks.
4. **30% drop-off after month 1** across all nutrition apps — simplicity is existential.

---

## 2. Food Database API Evaluation

### Option A: USDA FoodData Central (Recommended — Primary)
- **Cost:** Free, no usage limits beyond 1,000 req/hr per IP
- **Coverage:** 380K+ foods, 150+ nutrients per item, FDA-validated
- **Auth:** API key (free signup)
- **Pros:** Zero cost, scientifically validated, updated quarterly, no attribution required
- **Cons:** US-focused, no barcode/UPC data, no NLP, quarterly updates
- **Verdict:** Use as the **primary database** for generic/whole foods

### Option B: Open Food Facts (Recommended — Barcode Layer)
- **Cost:** Free (open source, CC-BY-SA)
- **Coverage:** 2.5M+ products globally, barcode/UPC indexed
- **Auth:** None required (custom User-Agent header recommended)
- **Pros:** Free, global, barcode-first, crowd-sourced and growing
- **Cons:** Crowd-sourced = variable quality, some entries incomplete
- **Verdict:** Use as the **barcode scanning layer** — look up UPC, fall back to USDA for nutrients

### Option C: FatSecret Platform API (Strong Alternative)
- **Cost:** Free tier = 5,000 calls/day; Premier free for startups
- **Coverage:** 1.9M+ foods, branded + restaurant + generic
- **Auth:** OAuth 2.0
- **Pros:** Large database, barcode support built-in, NLP search, free startup tier
- **Cons:** Attribution required on free tier, rate limited
- **Verdict:** Strong single-API alternative if we want one source instead of USDA+OFF combo

### Option D: Edamam (Premium Alternative)
- **Cost:** Free tier limited; $19/mo for 200 searches/min; Vision API: 10K free calls
- **Coverage:** 900K+ foods, 615K UPC codes, NLP parsing
- **Pros:** Best NLP ("1 cup rice" → structured nutrition), image recognition API, diet/allergen tagging
- **Cons:** Paid beyond free tier, vendor lock-in
- **Verdict:** Consider for NLP food parsing if we build text-based quick-add

### Option E: Nutritionix (Enterprise Only)
- **Cost:** $1,850/mo minimum
- **Cons:** Way too expensive for single-user app
- **Verdict:** Skip

### Recommended Stack
**USDA FoodData Central** (generic foods, micronutrients) + **Open Food Facts** (barcode scanning, packaged foods) + **local cache** (Room DB for frequently logged foods). Total API cost: **$0/mo**.

---

## 3. Barcode Scanning — Android Libraries

Already have ML Kit barcode-scanning 17.2.0 + CameraX 1.3.4 in the project (used for QR onboarding). ML Kit supports UPC-A, UPC-E, EAN-8, EAN-13 — all standard food barcodes.

**Implementation:** Reuse existing `QrScanScreen.kt` camera infrastructure. On barcode detect → query Open Food Facts API → display nutrition → allow user to log.

No new dependencies needed.

---

## 4. Proposed Data Model

### Server-Side (PostgreSQL — Health Platform Desktop)

```sql
-- Foods cached from APIs (USDA, OFF) + user-created custom foods
CREATE TABLE foods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    brand           TEXT,               -- NULL for generic foods
    barcode         TEXT,               -- UPC/EAN from Open Food Facts
    source          TEXT NOT NULL,       -- 'usda', 'openfoodfacts', 'custom'
    source_id       TEXT,               -- FDC ID or OFF barcode
    serving_size_g  DOUBLE PRECISION,
    calories        DOUBLE PRECISION,   -- per serving
    protein_g       DOUBLE PRECISION,
    carbs_g         DOUBLE PRECISION,
    fat_g           DOUBLE PRECISION,
    fiber_g         DOUBLE PRECISION,
    sugar_g         DOUBLE PRECISION,
    sodium_mg       DOUBLE PRECISION,
    -- Extended micros (nullable, populated from USDA)
    vitamin_d_mcg   DOUBLE PRECISION,
    calcium_mg      DOUBLE PRECISION,
    iron_mg         DOUBLE PRECISION,
    potassium_mg    DOUBLE PRECISION,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_foods_barcode ON foods(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_foods_name ON foods USING gin(to_tsvector('english', name));

-- Daily food log entries
CREATE TABLE food_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    food_id         UUID REFERENCES foods(id),
    meal_type       TEXT NOT NULL,       -- 'breakfast', 'lunch', 'dinner', 'snack'
    servings        DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    logged_at       TIMESTAMPTZ NOT NULL,
    notes           TEXT,
    -- Photo estimation fields (for Feature 2)
    photo_url       TEXT,
    ai_estimated    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_food_entries_date ON food_entries(logged_at);

-- Daily nutrition targets (adaptive)
CREATE TABLE nutrition_targets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    effective_date  DATE NOT NULL UNIQUE,
    calories        INT NOT NULL,
    protein_g       INT NOT NULL,
    carbs_g         INT NOT NULL,
    fat_g           INT NOT NULL,
    method          TEXT DEFAULT 'manual', -- 'manual', 'adaptive'
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Daily weight log (already exists as weight from body_measurements)
-- Reuse existing weight data for TDEE calculation
```

### Client-Side (Room — Offline Cache)

```kotlin
// Cache frequently-logged foods for offline access + quick search
@Entity(tableName = "food_cache", indices = [Index("barcode", unique = true)])
data class FoodCacheEntity(
    @PrimaryKey val id: String,          // server UUID
    val name: String,
    val brand: String?,
    val barcode: String?,
    val servingSizeG: Double?,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val lastUsed: Long = System.currentTimeMillis()
)

// Pending food entries (offline queue, same pattern as sync queue)
@Entity(tableName = "food_entry_queue")
data class FoodEntryQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,                 // JSON of food entry
    val recordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 5. Adaptive TDEE Algorithm (MacroFactor-Inspired)

**Core idea:** Back-calculate daily energy expenditure from weight trend + calorie intake.

### Algorithm
1. **Weight trend:** Apply exponential moving average to daily weigh-ins (α = 0.1) to smooth water/glycogen noise
2. **Weekly delta:** `Δweight_kg = trend_this_week - trend_last_week`
3. **Energy balance:** `weekly_surplus_kcal = Δweight_kg × 7700` (1 kg fat ≈ 7700 kcal)
4. **Estimated TDEE:** `TDEE = avg_daily_intake - (weekly_surplus_kcal / 7)`
5. **Smoothing:** Apply EMA to TDEE estimate itself to avoid jumps
6. **Weekly adjustment:** On Sunday, recalculate targets: `target_calories = TDEE + daily_deficit_or_surplus`
7. **Macro split:** Protein fixed at goal (e.g., 1g/lb bodyweight), remaining calories split between carbs/fat per user preference

### Requirements
- Minimum 7 days of food logging + 3 weigh-ins before first TDEE estimate
- Weight data already flows from Health Connect → server (existing pipeline)
- Show confidence indicator: "Calibrating..." (< 2 weeks), "Estimated" (2-4 weeks), "Dialed in" (> 4 weeks)

---

## 6. UX Design Principles

### Logging Speed (Critical for Retention)
1. **Recent foods** — top of search, one-tap re-log
2. **Barcode scan** — camera → UPC → auto-fill → confirm → done (3 taps)
3. **Quick add** — just enter calories + protein (skip everything else)
4. **AI describe** — text input like "chicken breast 6oz with rice" → NLP parse (Phase 2, uses Edamam or custom)
5. **Photo log** — take picture → AI estimates (Feature 2, separate research)

### Daily View
- **Meal sections:** Breakfast / Lunch / Dinner / Snacks (collapsible)
- **Running totals:** Calories, Protein, Carbs, Fat — progress bars against targets
- **Remaining budget:** "X calories left" prominent

### Dashboard Integration
- New card in Dashboard LazyRow: **"Nutrition"** with today's calorie intake vs target
- Readiness algorithm updated to factor in nutrition (undereating = recovery concern)

---

## 7. Server API Endpoints Needed

```
POST   /api/foods              — Create custom food
GET    /api/foods/search       — Search by name (full-text) or barcode
GET    /api/foods/:id          — Get food details

POST   /api/food-entries       — Log a food entry
GET    /api/food-entries       — Get entries for date range
DELETE /api/food-entries/:id   — Remove entry
PATCH  /api/food-entries/:id   — Edit serving size, meal type

GET    /api/nutrition/daily    — Daily totals (calories, macros) for date range
GET    /api/nutrition/targets  — Current targets
PUT    /api/nutrition/targets  — Set/update targets
GET    /api/nutrition/tdee     — Current TDEE estimate + confidence
```

---

## 8. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| User abandons after 2 weeks | HIGH | Speed of logging is #1 priority. Recent foods, barcode scan, quick-add. |
| Food database inaccuracy | MEDIUM | Use USDA (validated) as primary. Flag crowd-sourced OFF entries. Allow user corrections. |
| TDEE algorithm cold-start | LOW | Show "Calibrating" state, fall back to Mifflin-St Jeor estimate until enough data. |
| Offline logging | LOW | Room queue (same pattern as health sync). Entries sync when online. |
| API rate limits (USDA 1K/hr) | LOW | Cache aggressively in Room. Realistic usage: ~20-30 lookups/day max. |

---

## 9. Implementation Phases (Estimated)

| Phase | Scope | Dependencies |
|-------|-------|-------------|
| **Phase 1** | Server schema + CRUD endpoints for foods & food_entries | Server access |
| **Phase 2** | Android: Nutrition screen with manual food search + logging | Phase 1 |
| **Phase 3** | Barcode scanning → Open Food Facts lookup → log | Phase 2, existing CameraX |
| **Phase 4** | Dashboard nutrition card + daily summary | Phase 2 |
| **Phase 5** | USDA FoodData Central integration (richer nutrients) | Phase 2 |
| **Phase 6** | Adaptive TDEE algorithm + weekly target adjustment | Phase 2 + weight data |
| **Phase 7** | Quick-add + recent foods optimization | Phase 2 (polish) |

---

## 10. Open Questions for User

1. **Macro targets:** Do you want to set targets manually first, or jump straight to adaptive TDEE?
2. **Micronutrients:** Track beyond the big 4 (cal/protein/carbs/fat)? Fiber, sodium, vitamins?
3. **Meal timing:** Care about when you eat (meal windows), or just daily totals?
4. **Integration with AI Coach:** Should the coach be able to suggest meals, or just comment on intake?
5. **Custom foods:** Need ability to create custom foods (home recipes with ingredient breakdown)?

---

## Sources

- [USDA FoodData Central API Guide](https://fdc.nal.usda.gov/api-guide/)
- [Open Food Facts API Documentation](https://openfoodfacts.github.io/openfoodfacts-server/api/)
- [FatSecret Platform API Pricing](https://platform.fatsecret.com/api-editions)
- [Edamam Nutrition API](https://developer.edamam.com/edamam-nutrition-api)
- [MacroFactor vs MyFitnessPal 2025](https://macrofactor.com/macrofactor-vs-myfitnesspal-2025/)
- [MacroFactor Algorithm & Philosophy](https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/)
- [MacroFactor Algorithm Accuracy](https://macrofactorapp.com/algorithm-accuracy/)
- [MacroFactor Expenditure Calculator](https://macrofactorapp.com/energy-expenditure-calculator-app/)
- [Outlift MacroFactor Review 2026](https://outlift.com/macrofactor-review/)
- [Diet & Nutrition App Statistics 2026](https://media.market.us/diet-and-nutrition-apps-statistics/)
- [ML Kit Barcode Scanning Android](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [ML Kit vs ZXing Comparison](https://scanbot.io/blog/ml-kit-vs-zxing/)
- [Database Schema for Diet Services](https://chankapure.medium.com/designing-a-database-schema-for-diet-services-a-guide-347637b3662f)
- [Top Nutrition APIs 2026](https://www.spikeapi.com/blog/top-nutrition-apis-for-developers-2026)
- [Calorie Tracker App Features 2025](https://medium.com/predict/fitness-and-calorie-tracker-app-features-and-trends-to-look-for-2025-55c33d34a440)
