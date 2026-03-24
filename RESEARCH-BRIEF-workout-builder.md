# RESEARCH BRIEF: Hevy Workout Builder

**Feature:** Auto-generate progressive overload workouts for hypertrophy based on current Hevy data, push new routines/workouts back to Hevy
**Date:** 2026-03-15
**Status:** COMPLETE — ready for architecture review

---

## 1. Competitive Landscape

### Workout Generation Apps
| App | Approach | Strengths | Weaknesses | Price |
|-----|----------|-----------|------------|-------|
| **Fitbod** | Algorithmic (2B+ logged sets) | Auto-generates full workouts, muscle recovery tracking, equipment-aware | Black box algorithm, no Hevy integration | $13/mo |
| **RP Hypertrophy App** | Dr. Mike Israetel's volume landmarks | Science-backed MEV/MAV/MRV per muscle, mesocycle auto-progression, deload scheduling | Own ecosystem (no Hevy sync), $15/mo | $15/mo |
| **Alpha Progression** | Periodized auto-regulation | Undulating periodization, deload auto-scheduling | Less research-backed than RP | $10/mo |
| **My Lifting Coach** | Apple Intelligence integration | Real-time form adjustment, auto-regulation | iOS only, new/unproven | $10/mo |
| **HevyGPT** | ChatGPT → Hevy routine import | Uses AI to generate plans, imports to Hevy format | One-shot generation (no adaptation), no progress tracking | Free (Hevy Pro) |

### Key Insight
**No app currently combines Hevy's workout logging with adaptive progressive overload.** HevyGPT generates static plans. Fitbod and RP have great algorithms but are siloed ecosystems. Apex can be the bridge: **read Hevy history → compute progression → push new workouts back to Hevy.**

---

## 2. Hevy API Capabilities

### Authentication
- **Method:** `api-key` header (not Bearer token)
- **Requirement:** Hevy PRO subscription (~$10/mo)
- **Key location:** Settings → Developer at https://hevy.com/settings?developer

### Available Operations

| Resource | Read | Create | Update | Delete |
|----------|------|--------|--------|--------|
| **Workouts** | GET list, GET by ID, GET count, GET events | POST create | PUT update | — |
| **Routines** | GET list, GET by ID | POST create | PUT update | — |
| **Exercise Templates** | GET list, GET by ID | — | — | — |
| **Routine Folders** | GET list, GET by ID | POST create | — | — |
| **Webhooks** | GET subscription | POST create | — | DELETE |

### Exercise Set Types
| Type | Fields | Example |
|------|--------|---------|
| Weight + Reps | `weight_kg`, `reps` | 3×10 @ 60kg |
| Bodyweight Reps | `reps` | 3×12 pull-ups |
| Time Hold | `duration_seconds` | 60s plank |
| Distance + Time | `distance_meters`, `duration_seconds` | 500m row |
| Distance + Weight | `distance_meters`, `weight_kg` | 50m farmer's walk |

### Superset Support
Sets can be grouped via `superset_id` (integer). Rest periods attach to the last exercise in the superset.

### Key Limitation
- No DELETE for workouts or routines (can only update)
- Rate limits not documented but likely exist
- Hevy PRO required (cost passed to user)

---

## 3. Progressive Overload Algorithm

### Core Approach: Volume-Driven Hypertrophy with RP Volume Landmarks

#### Volume Landmarks (Dr. Mike Israetel / RP Strength)

| Muscle Group | MEV (sets/wk) | MAV (sets/wk) | MRV (sets/wk) | Frequency |
|-------------|---------------|---------------|---------------|-----------|
| Chest | 10 | 12-20 | 22+ | 1.5-3x |
| Back | 10 | 14-22 | 25+ | 2-4x |
| Quads | 8 | 12-18 | 20+ | 1.5-3x |
| Hamstrings | 6 | 10-16 | 20+ | 2-3x |
| Glutes | — | 4-12 | 16+ | 2-3x |
| Biceps | 8 | 14-20 | 26+ | 2-6x |
| Triceps | 6 | 10-14 | 18+ | 2-4x |
| Side/Rear Delts | 8 | 16-22 | 26+ | 2-6x |
| Front Delts | — | 6-8 | 12+ | 1-2x |
| Calves | 8 | 12-16 | 20+ | 2-4x |
| Abs | 0 | 16-20 | 25+ | 3-5x |
| Traps | — | 12-20 | 26+ | 2-6x |

#### Progression Rules

```
FOR each exercise in the user's routine:
  1. Fetch last 4 weeks of performance from Hevy API
  2. Calculate volume_load = sets × reps × weight per session
  3. Calculate weekly_sets per muscle group

  IF user hit all prescribed reps on all sets:
    → Increase weight by minimum increment (2.5kg upper / 5kg lower)
    → Keep reps the same

  IF user hit prescribed reps on some sets but not all:
    → Keep weight the same
    → Target same reps (still adapting)

  IF user failed to hit minimum reps on 2+ sets:
    → Flag as potential MRV exceeded
    → Suggest deload or volume reduction

  DELOAD TRIGGER:
    IF performance declining for 2+ consecutive weeks across 3+ exercises:
      → Insert deload week (50% volume, same weight)
      → Reset progression after deload
```

#### Mesocycle Structure (4-6 weeks)
```
Week 1: MEV volume (introductory)
Week 2: MEV + 2 sets per muscle group
Week 3: Approaching MAV
Week 4: MAV (peak volume)
Week 5: MAV or slight overreach (optional)
Week 6: Deload (50% volume, maintain intensity)
→ Repeat with higher baseline weights
```

### The "2-for-2" Rule (Simple Alternative)
If user completes 2 extra reps beyond target on the last set, for 2 consecutive sessions → increase weight. Simple, proven, easy to implement as v1.

---

## 4. Data Model

### Server-Side (PostgreSQL)

```sql
-- Workout templates generated by Apex's algorithm
CREATE TABLE workout_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,           -- "Push Day A"
    split_type      TEXT NOT NULL,           -- 'push_pull_legs', 'upper_lower', 'full_body', 'bro_split'
    day_index       INT NOT NULL,            -- 0-6, position in the split
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Exercises within a template, with progression targets
CREATE TABLE template_exercises (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID REFERENCES workout_templates(id) ON DELETE CASCADE,
    hevy_exercise_id TEXT NOT NULL,          -- Hevy exercise template ID
    exercise_name   TEXT NOT NULL,
    muscle_group    TEXT NOT NULL,           -- 'chest', 'back', 'quads', etc.
    exercise_order  INT NOT NULL,
    sets            INT NOT NULL,            -- target sets
    rep_min         INT NOT NULL,            -- e.g., 8
    rep_max         INT NOT NULL,            -- e.g., 12
    current_weight_kg DOUBLE PRECISION,      -- last working weight
    rest_seconds    INT DEFAULT 120,
    superset_group  INT,                     -- NULL if not supersetted
    notes           TEXT
);

-- Progression log: tracks weight/rep changes over time
CREATE TABLE progression_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exercise_id     UUID REFERENCES template_exercises(id),
    hevy_workout_id TEXT,                    -- ID from Hevy API
    date            DATE NOT NULL,
    weight_kg       DOUBLE PRECISION NOT NULL,
    sets_completed  INT NOT NULL,
    reps_per_set    INT[] NOT NULL,          -- e.g., {10, 10, 9, 8}
    volume_load     DOUBLE PRECISION NOT NULL, -- sets × avg_reps × weight
    progression_action TEXT,                 -- 'increase_weight', 'hold', 'deload', 'reduce_volume'
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Mesocycle tracking
CREATE TABLE mesocycles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,            -- "Hypertrophy Block 3"
    start_date      DATE NOT NULL,
    end_date        DATE,
    status          TEXT DEFAULT 'active',    -- 'active', 'completed', 'deload'
    split_type      TEXT NOT NULL,
    weekly_volume   JSONB,                    -- { "chest": 16, "back": 18, ... }
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### Client-Side (Room — Minimal)
Workout generation happens server-side (needs full history). Client caches:
- Current week's workout templates for offline viewing
- Today's target workout for quick reference on Dashboard

---

## 5. Workout Generation Flow

### User Experience
```
[Activity Screen] — shows recent Hevy workouts (existing)
  └── "Generate Next Workout" button (NEW)
        ├── Apex fetches last 4 weeks from Hevy API
        ├── Algorithm computes progression for each exercise
        ├── Shows proposed workout:
        │     Exercise       Sets  Reps    Weight   Change
        │     Bench Press    4     8-12    70kg     +2.5kg ↑
        │     Incline DB     3     10-12   24kg     (hold)
        │     Cable Fly      3     12-15   14kg     +2kg ↑
        │     ...
        ├── User can adjust (tap any row to edit)
        └── "Push to Hevy" → creates routine via Hevy API
              └── User opens Hevy → starts the routine → logs as usual

[Dashboard] — Readiness card factors in training volume
  └── "Recovery day" if weekly volume approaching MRV
```

### Sync Loop
```
1. User logs workout in Hevy (existing behavior)
2. Apex server syncs Hevy data (existing: triggerHevySync())
3. Apex analyzes progression across mesocycle
4. Apex generates next workout with updated weights/volumes
5. User reviews and pushes to Hevy
6. Repeat
```

---

## 6. Split Types & Templates

### Supported Splits (v1)

| Split | Days/Week | Structure |
|-------|-----------|-----------|
| **Push/Pull/Legs** | 6 (2x each) | Push A, Pull A, Legs A, Push B, Pull B, Legs B |
| **Upper/Lower** | 4 | Upper A, Lower A, Upper B, Lower B |
| **Full Body** | 3 | Full A, Full B, Full C |

### Exercise Selection (per muscle group)
Each template includes compound movements first, isolation movements second (Fitbod-validated approach):

**Push Day Example:**
1. Bench Press (compound) — 4×8-12
2. Overhead Press (compound) — 3×8-12
3. Incline DB Press (compound) — 3×10-12
4. Lateral Raise (isolation) — 3×12-15
5. Tricep Pushdown (isolation) — 3×12-15

Exercise templates come from Hevy's exercise template API — we search by muscle group and match to the user's existing exercise history.

---

## 7. Integration with Existing Apex Features

| Feature | Integration Point |
|---------|-------------------|
| **Readiness Card** | Factor training volume + muscle group recovery into readiness score |
| **AI Coach** | Coach suggests split changes, deload timing, exercise swaps based on full context |
| **Nutrition** | Coach recommends calorie surplus/deficit aligned with mesocycle phase |
| **HRV/Sleep** | Low HRV or poor sleep → algorithm reduces volume for that day |
| **BP** | Elevated BP → suggest lighter cardiovascular-focused session |

---

## 8. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Hevy PRO required ($10/mo) | MEDIUM | User already has Hevy PRO (uses Hevy daily). Document requirement. |
| Hevy API rate limits unknown | MEDIUM | Cache aggressively. Batch reads. Only push when user explicitly confirms. |
| Algorithm prescribes too much volume → injury | HIGH | Start conservative (MEV, not MAV). Show volume warnings. AI Coach can intervene. MRV alerts. |
| User doesn't follow generated workout exactly | LOW | Re-sync from Hevy after each session. Algorithm adapts to what was actually done, not prescribed. |
| Hevy API breaking changes | LOW | Version pin API calls. Webhook for change notifications. Abstraction layer. |
| Complex UX (too many options) | MEDIUM | v1: single split selection + auto-generate. No manual periodization tuning. |

---

## 9. Implementation Phases

| Phase | Scope | Dependencies |
|-------|-------|-------------|
| **Phase 1** | Server: fetch full Hevy workout history, compute per-exercise progression | Hevy API key, server access |
| **Phase 2** | Server: workout generation algorithm (2-for-2 rule, simple) | Phase 1 |
| **Phase 3** | Android: "Generate Next Workout" screen with review/edit | Phase 2 |
| **Phase 4** | Push generated routine to Hevy via API | Phase 3 |
| **Phase 5** | Mesocycle tracking + deload auto-scheduling | Phase 2 |
| **Phase 6** | Volume landmark warnings (approaching MRV) | Phase 5 |
| **Phase 7** | HRV/sleep-aware volume adjustment | Phase 5 + existing health data |

---

## 10. Open Questions for User

1. **Split preference:** What split do you currently run? (PPL, Upper/Lower, Full Body?)
2. **Hevy PRO:** Do you already have Hevy PRO? (Required for API access)
3. **Progressive overload style:** Start with simple 2-for-2 rule, or jump to full RP volume landmarks?
4. **Deload preference:** Auto-scheduled every 4-6 weeks, or only when performance declines?
5. **Exercise variety:** Keep same exercises across mesocycle, or rotate variations (e.g., flat → incline bench)?
6. **Hevy as source of truth:** Should Hevy remain the place you *log* workouts, with Apex only *generating* them? Or should Apex also log?

---

## Sources

- [Hevy API Swagger Documentation](https://api.hevyapp.com/docs/)
- [Hevy MCP Server (GitHub)](https://github.com/chrisdoc/hevy-mcp)
- [HevyGPT: ChatGPT → Hevy Routine Import](https://www.hevyapp.com/features/hevy-gpt/)
- [Hevy Exercise Programming Options](https://www.hevyapp.com/features/exercise-programming-options/)
- [RP Strength: Training Volume Landmarks for Muscle Growth](https://rpstrength.com/blogs/articles/training-volume-landmarks-muscle-growth)
- [Dr. Mike Israetel MV/MEV/MAV/MRV Explained](https://drmikeisraetel.com/dr-mike-israetel-mv-mev-mav-mrv-explained/)
- [Volume Landmarks Per Muscle Group (Scribd)](https://www.scribd.com/document/475029266/Dr-Mike-Israetel-Training-Volume-Landmarks-Hypertrophy-Routine-LiftVault-com-Sets-Per-Week-Summary)
- [How to Program Volume Landmarks (FitnessRec)](https://fitnessrec.com/articles/how-to-program-volume-landmarks-mrv-mav-and-mev-explained-for-optimal-muscle-growth)
- [Fitbod: How Progressive Overload Works](https://fitbod.me/blog/what-is-progressive-overload-and-how-fitbod-builds-it-into-every-workout-automatically/)
- [Fitbod: How the Algorithm Knows When to Lift Heavier](https://fitbod.me/blog/how-fitbods-ai-knows-exactly-when-you-should-lift-heavier-and-when-to-recover/)
- [Fitbod: Smart Training Algorithms](https://fitbod.me/blog/how-fitbod-personalizes-your-workout-plan-using-smart-training-algorithms/)
- [Progressive Overload Guide (NASM)](https://blog.nasm.org/progressive-overload-explained)
- [Periodization Training Simplified (NASM)](https://blog.nasm.org/periodization-training-simplified)
- [Mesocycles Explained (TrainingPeaks)](https://www.trainingpeaks.com/blog/macrocycles-mesocycles-and-microcycles-understanding-the-3-cycles-of-periodization/)
- [Mike Israetel 5-Week Hypertrophy Spreadsheet (LiftVault)](https://liftvault.com/programs/bodybuilding/mike-israetel-5-week-hypertrophy-workout-routine-spreadsheet/)
- [My Lifting Coach: AI-Powered Training](https://myliftingcoach.com/blog/ai-powered-training-revolution)
