# RESEARCH BRIEF: Photo-Based Food Estimation

**Feature:** Take a photo of food → AI estimates calories and macros → user confirms/adjusts → logs
**Date:** 2026-03-15
**Status:** COMPLETE — ready for architecture review
**Depends on:** RESEARCH-BRIEF-nutrition.md (food data model, food_entries schema)

---

## 1. Competitive Landscape

### Photo-First Apps
| App | Approach | Accuracy | Notes |
|-----|----------|----------|-------|
| **Cal AI** | Depth sensor + CNN | 82% on simple foods, ~50% on homemade | $70/yr, fastest growing in category |
| **Peony AI** | Vision model + structured output | 79% accuracy | Good at mixed plates |
| **Calorie Mama** | Custom CNN food classifier | Older tech, lower accuracy | Pioneer in the space |
| **Fitia** | Photo + voice + verified DB lookup | High (DB-backed, not pure vision) | Hybrid approach — best of both |

### Key Insight
Pure photo estimation averages **50-82% accuracy** depending on meal complexity. The winning approach is **photo → AI estimate → user correction** with a verified food database backing the estimates. Cal AI's 82% is the ceiling for simple foods; homemade meals drop to ~50%.

---

## 2. Vision Model Evaluation

### Research Evidence
- **GPT-4 (Feb 2025 study, 114 meals):** Food identification F1 = 88.6% (precision 93%, recall 84.6%). Calorie estimation error 10-20% for macros. Tends to **underestimate** portion sizes. Micronutrient accuracy poor.
- **GPT-4o (2025 study):** Customized configurations achieved 10-20% error on calories and macros. Less consistent for fats and proteins specifically.
- **Gemini + EfficientNet-B4 (2025):** Research combining Gemini LLM with visual backbone showed promising results for recipe identification + nutrition description.
- **General finding:** All models struggle with hidden ingredients (oils, sauces, butter), mixed dishes, and ethnic cuisines.

### Model Comparison for Apex

| Model | Input Cost | Output Cost | Image Tokens | Cost per Photo | Free Tier | Best For |
|-------|-----------|-------------|-------------|----------------|-----------|----------|
| **Gemini 2.5 Flash** | $0.15/MTok | $0.60/MTok | ~560 tokens fixed | **~$0.0001** | 1,000 req/day free | Budget pick |
| **Claude Sonnet 4.6** | $3/MTok | $15/MTok | ~1,334 tokens (1000x1000) | **~$0.004** | None | Best structured output |
| **Claude Haiku 4.5** | $1/MTok | $5/MTok | ~1,334 tokens | **~$0.001** | None | Good balance |
| **GPT-4o** | $2.50/MTok | $10/MTok | ~765 tokens (low detail) | **~$0.002** | None | Most researched |

### Recommendation: **Gemini 2.5 Flash (primary) + Claude Haiku (fallback)**

**Rationale:**
- Gemini 2.5 Flash is **40x cheaper** than Claude Sonnet and has a **free tier** (1,000 req/day = more than enough for single user)
- At 3-5 food photos per day × 365 days = ~1,500 photos/year
- Gemini cost: **~$0.15/year** (essentially free)
- Claude Haiku fallback: **~$1.50/year** if Gemini is down
- Claude Sonnet reserved for AI Coach conversations where quality matters more

---

## 3. Before/After Photo Flow

### Research-Backed Approach
Academic research (University of Padova, clinical studies) validates the **before/after tray method:**
1. **Before photo:** AI identifies food items, estimates portion sizes and full-plate calories
2. **User eats** (optional: doesn't finish everything)
3. **After photo:** AI identifies remaining food, estimates leftover calories
4. **Net intake:** `calories_consumed = before_estimate - after_estimate`

### Accuracy Results
- Before/after estimation achieves **3.75-5.07% error** in controlled settings (rice, chicken)
- YOLO-based detection: mAP = 0.873 for food segmentation
- Real-world accuracy is lower but significantly better than single-photo estimation

### Proposed UX Flow
```
[Camera Screen]
  ├── "Before" button → takes photo → AI processes → shows estimate
  │     └── User can adjust portions, confirm, or re-take
  ├── "Quick snap" → single photo → estimate → log (most common)
  └── "After" button → takes photo of leftovers → subtracts from before estimate
       └── Only available if a "before" photo was logged for this meal

[Estimation Result Screen]
  ├── Detected items listed with confidence %
  ├── Per-item: name, estimated grams, calories, protein/carbs/fat
  ├── Total meal summary
  ├── "Edit" each item (tap to adjust weight or swap food)
  ├── "Add missing" (AI missed an item)
  └── "Log meal" button → saves to food_entries with ai_estimated=true
```

---

## 4. Scale Integration (Optional Enhancement)

### Bluetooth Smart Scales
- **Open-source options:** Decent Scale (BLE, open API), SKALE (open SDK), Libra (fully open source)
- **Consumer scales:** Most use proprietary BLE protocols but can be reverse-engineered
- **Android BLE requirement:** Bluetooth 4.0+, needs BLUETOOTH_CONNECT permission (Android 12+)

### Integration Approach
1. User places food on scale → Apex reads weight via BLE
2. User takes photo → AI identifies food type
3. **Weight from scale + food type from AI = precise calorie calculation** (no portion guessing)
4. This is the highest-accuracy path: food identification (AI strength) + exact weight (scale strength)

### Recommendation
**Defer to Phase 2.** Start with photo-only estimation. Scale integration is a premium feature that requires hardware purchase. The photo flow alone delivers 80%+ of the value.

---

## 5. Prompt Engineering Strategy

### Chain-of-Thought Structured Output
Based on research and real-world implementations, the optimal prompt structure:

```
System: You are a nutrition estimation assistant. Analyze the food photo and return
a JSON object. Use chain-of-thought reasoning internally but only output the JSON.

Rules:
- Identify every visible food item
- Estimate portion sizes using visual cues (plate diameter ~26cm, standard bowl ~400ml)
- Account for hidden calories (oils, sauces, dressings, butter)
- If uncertain about a food, provide your best guess with lower confidence
- All weights in grams, calories in kcal

Output JSON schema:
{
  "items": [
    {
      "name": "grilled chicken breast",
      "estimated_grams": 170,
      "confidence": 0.85,
      "calories": 280,
      "protein_g": 53,
      "carbs_g": 0,
      "fat_g": 6
    }
  ],
  "total": {
    "calories": 650,
    "protein_g": 53,
    "carbs_g": 45,
    "fat_g": 22
  },
  "notes": "Appears to have olive oil drizzle on chicken, estimated 1 tbsp"
}
```

### Validation Rules (Client-Side)
- Reject if total calories < 10 or > 5000 (single meal)
- Reject if protein + carbs + fat (in kcal) deviates > 20% from total calories
- Reject if any item has 0 grams but non-zero calories
- Flag if confidence < 0.5 on any item → prompt user to verify

### Before/After Delta Prompt
```
System: You previously estimated this meal at {before_json}. The user has taken
an "after" photo showing what remains on the plate. Estimate the remaining food
and calculate what was consumed.

Output: { "remaining": [...], "consumed": {...}, "consumption_percent": 0.75 }
```

---

## 6. Privacy & Security

### Health Data + Photos = High Sensitivity
- **On-device:** Photos should NOT be stored permanently. Process → estimate → delete original.
- **API transit:** Photos sent to Gemini/Claude API are subject to their data policies. Both Anthropic and Google state API data is not used for training (with standard API agreements).
- **Server storage:** Store only the AI estimation result (JSON), not the photo itself. If user wants photo history, store encrypted and offer deletion.
- **Manifest:** Already have CAMERA permission. No new permissions needed.

### Data Flow
```
Camera → JPEG (local) → resize to 1024px max → API call → JSON result → delete JPEG
                                                              ↓
                                                    food_entries table (server)
                                                    with ai_estimated=true
```

---

## 7. Technical Architecture

### Android Client
```
QrScanScreen.kt infrastructure (CameraX + ML Kit) → reuse for food photo
  BUT: food photos need still capture, not continuous scanning

New components:
- FoodPhotoScreen.kt — camera preview + capture button + before/after toggle
- FoodEstimationViewModel.kt — sends photo to API, parses response, validates
- FoodEstimationResultScreen.kt — shows detected items, allows editing
- GeminiVisionClient.kt — Gemini 2.5 Flash API wrapper (primary)
- ClaudeVisionClient.kt — Claude Haiku fallback
```

### Server
```
POST /api/food-entries/estimate — receives photo (multipart), calls vision API,
                                   returns structured estimate
  OR
Client-side API call (preferred) — Android app calls Gemini directly,
                                    no photo transits through our server
```

### Recommended: **Client-Side API Call**
- Photo never leaves the device except to Gemini/Claude API
- No server bandwidth cost for image uploads
- Lower latency (direct to AI API vs hop through server)
- API key stored in SecurePrefs (same as existing pattern)

---

## 8. Cost Projection

| Usage Pattern | Photos/Day | Monthly Cost (Gemini Flash) | Monthly Cost (Claude Haiku) |
|---------------|-----------|----------------------------|----------------------------|
| Light (1 meal/day) | 1 | $0.003 | $0.03 |
| Moderate (3 meals) | 3 | $0.009 | $0.09 |
| Heavy (5 + snacks) | 5 | $0.015 | $0.15 |
| With before/after | 10 | $0.03 | $0.30 |

**Annual cost at heavy usage: $0.18 (Gemini) or $1.80 (Claude Haiku).** Negligible.

---

## 9. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| AI underestimates hidden calories (oils, sauces) | HIGH | Prompt explicitly asks for hidden ingredients. Post-processing adds 10% buffer for "cooked with oil" items. User can adjust. |
| Mixed/homemade meals low accuracy (~50%) | MEDIUM | Show confidence scores. Allow manual editing. Offer "quick-add" fallback for when photo fails. |
| User trusts AI blindly, eats too little/much | MEDIUM | Show confidence indicator. Always editable. AI Coach (Feature 4) can flag suspicious patterns. |
| API latency (2-5 seconds for vision) | LOW | Show loading animation. Pre-warm connection. Local food cache for instant re-logs. |
| Gemini API changes or deprecation | LOW | Claude Haiku fallback. Abstracted behind interface for easy swap. |
| Photo privacy concerns | LOW | Delete original photo after processing. Never send to our server. Clear data policy in app. |

---

## 10. Implementation Phases

| Phase | Scope | Dependencies |
|-------|-------|-------------|
| **Phase 1** | Single-photo capture → Gemini Flash estimation → result screen with editing | Nutrition Feature Phase 2 (food_entries exist) |
| **Phase 2** | Before/after photo flow with delta calculation | Phase 1 |
| **Phase 3** | Confidence-based UX (low confidence → suggest manual entry) | Phase 1 |
| **Phase 4** | Learning from corrections (track AI vs user-edited values to tune prompts) | Phase 1 |
| **Phase 5** | Scale integration via BLE (deferred, optional) | Phase 1 + hardware |

---

## 11. Open Questions for User

1. **Photo storage:** Keep photos in a gallery for review, or delete immediately after estimation?
2. **Before/after priority:** Is the before/after flow critical for MVP, or can we start with single-photo only?
3. **Scale:** Do you own a Bluetooth kitchen scale, or is this purely aspirational?
4. **Gemini API key:** Do you have a Google AI Studio account, or should we set one up?
5. **Accuracy tolerance:** Are you okay with 10-20% calorie estimation error, or do you want mandatory user confirmation on every item?

---

## Sources

- [GPT-4 Nutrient Estimation from Meal Photos (PMC, Feb 2025)](https://pmc.ncbi.nlm.nih.gov/articles/PMC11858203/)
- [AI vs Dietitians: Can ChatGPT Accurately Assess Meals?](https://www.news-medical.net/news/20250210/AI-vs-dietitians-Can-ChatGPT-accurately-assess-your-meals.aspx)
- [Customized GPT-4o Dietary Assessment Validation](https://www.sciencedirect.com/science/article/abs/pii/S0002916525006173)
- [AI-Based Digital Image Dietary Assessment Systematic Review](https://pmc.ncbi.nlm.nih.gov/articles/PMC10836267/)
- [Food Recognition and Leftover Estimation (GitHub)](https://github.com/Piero24/Food-recognition-and-leftover-estimation)
- [Automated Food Weight Estimation with Computer Vision](https://pmc.ncbi.nlm.nih.gov/articles/PMC11644939/)
- [AI Leftover Estimation in Clinical Environments](https://pmc.ncbi.nlm.nih.gov/articles/PMC11576600/)
- [Image-Based Portion Estimation Without Fiducial Marker](https://pmc.ncbi.nlm.nih.gov/articles/PMC8115205/)
- [Best AI Calorie Counter Apps 2025 (Expert Testing)](https://www.heypeony.com/blog/best-a-i-calorie-counter)
- [Why Nutrition Apps Need More Than Photo Tracking (Fitia)](https://fitia.app/learn/article/photo-tracking-in-nutrition-apps-accuracy-and-benefits/)
- [Building AI Nutrition Tracker (Jake Steelman)](https://www.jakesteelman.com/blog/macroscanner-building-ai-nutrition-tracker/)
- [From Pixels to Calories: GPT-4o Vision Meal Tracker](https://dev.to/wellallytech/from-pixels-to-calories-building-a-high-precision-meal-tracker-with-gpt-4o-vision-5018)
- [Claude API Vision Documentation](https://platform.claude.com/docs/en/build-with-claude/vision)
- [Claude API Pricing](https://platform.claude.com/docs/en/about-claude/pricing)
- [Gemini API Pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Gemini Vision: Food Image Analysis with EfficientNet-B4](https://arxiv.org/html/2511.08215v1)
- [Decent Scale (BLE, Open API)](https://decentespresso.com/decentscale)
- [SKALE Open Source SDK](https://skale.cc/en/skale_open_sdk.html)
