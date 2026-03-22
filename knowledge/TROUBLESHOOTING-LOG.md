# Apex v2 Troubleshooting Log

### 2026-03-16 A-02: Hevy muscle group data exists but is insufficient for RP landmarks

**Error/Symptom:** Planning assumed Hevy exercise templates might not include muscle group data at all (A-02). Validated that data exists, but discovered a different problem: Hevy's taxonomy uses 20 groups while RP volume landmarks need 16 groups with finer subdivisions (especially shoulders → front/side/rear delts).

**Root Cause:** Hevy uses a single "shoulders" label for 42 exercises. RP tracks front delts, side delts, and rear delts as independent muscle groups with different volume landmarks. Also, 42 "full_body" exercises have no useful muscle attribution.

**Fix:** System-scoped `exercise_muscle_overrides` table with ~55 curated rows. Resolution order: override → Hevy base → exclude. 12 of 20 Hevy groups map 1:1 to RP groups with zero overrides needed.

**Prevention:** When depending on external API data for domain-specific logic, validate not just data existence but granularity sufficiency against the actual use case requirements.
