# RESEARCH BRIEF: AI Fitness Coach

**Feature:** Conversational AI coach that uses all Apex health inputs (BP, sleep, HRV, workouts, nutrition, supplements) to guide training, nutrition, and recovery decisions
**Date:** 2026-03-15
**Status:** COMPLETE — ready for architecture review
**Depends on:** All other research briefs (nutrition, food photo, workout builder, supplements)

---

## 1. Competitive Landscape

| App | Model | Data Sources | UX | Price |
|-----|-------|-------------|-----|-------|
| **AthleteData.health** | Claude (Anthropic) | Strava, Hevy, WHOOP, Oura, Withings (50+ metrics) | Telegram chat | $15/mo |
| **PH-LLM (Google)** | Gemini fine-tuned | Fitbit wearables | Research prototype | N/A |
| **Healthify** | GPT (OpenAI) | Manual input + Indian food DB | In-app chat | $10/mo |
| **My Lifting Coach** | Apple Intelligence | Apple Health | In-app | $10/mo |
| **Momentum AI** | Claude via MCP | Apple Health (open source MCP server) | Desktop/API | Open source |

### Key Insight
**AthleteData.health is the closest competitor** — it already uses Claude + Hevy + Oura + WHOOP. But it's a **web/Telegram chat**, not a native Android app. Apex's advantage: **all data lives in one server**, no third-party aggregation needed. We already have BP, sleep, HRV, workouts, and will have nutrition + supplements. The coach has a richer context than any competitor.

---

## 2. LLM Selection

### Claude API (Recommended)

| Model | Input | Output | Context | Best For |
|-------|-------|--------|---------|----------|
| **Haiku 4.5** | $1/MTok | $5/MTok | 200K | Quick responses, daily check-ins, simple Q&A |
| **Sonnet 4.6** | $3/MTok | $15/MTok | 200K | Complex analysis, workout plan generation, trend interpretation |
| **Opus 4.6** | $5/MTok | $25/MTok | 200K+ | Deep reasoning (rarely needed for coaching) |

### Recommendation: **Haiku 4.5 (default) + Sonnet 4.6 (complex tasks)**
- Daily check-ins, reminders, simple questions → Haiku ($0.001-0.005 per interaction)
- Workout plan generation, trend analysis, PRT prep plans → Sonnet ($0.01-0.05 per interaction)
- Estimated monthly cost at 5-10 interactions/day: **$1-5/mo**

### Why Claude over GPT/Gemini:
1. Best structured output compliance (critical for parsing coaching responses)
2. Memory Tool (beta) — persistent knowledge across sessions without re-sending full context
3. Already using Anthropic ecosystem in Apex development
4. AthleteData.health validates Claude works well for fitness coaching

---

## 3. Context Architecture

### The Problem
The coach needs access to ALL user data to give good advice, but sending everything every message wastes tokens and hits context limits.

### Solution: Tiered Context Injection

```
Tier 1: ALWAYS included (system prompt + athlete profile)
  ├── System prompt with coaching persona + safety rules (~500 tokens)
  ├── Athlete profile: age, weight, goals, injuries, preferences (~200 tokens)
  └── Current state: today's readiness score, last night's sleep, today's nutrition (~300 tokens)
  Total: ~1,000 tokens per message

Tier 2: INCLUDED when relevant (retrieved based on user question)
  ├── Recent workouts (last 7 days from Hevy) — if asking about training
  ├── Nutrition trends (last 7 days) — if asking about diet
  ├── BP/HRV trends (last 14 days) — if asking about health/recovery
  ├── Supplement stack — if asking about supplements
  └── Mesocycle state — if asking about programming
  Total: 500-2,000 tokens depending on query

Tier 3: STORED in Memory Tool (persistent, not re-sent)
  ├── Goals history and progress
  ├── Injury history
  ├── Food preferences / allergies
  ├── Past coaching advice and outcomes
  └── PRT target dates and standards
  Total: managed by Claude Memory Tool, ~84% token reduction
```

### Conversation History
- Keep last 10 messages in conversation context
- Older messages summarized server-side and stored in Memory Tool
- Each new conversation inherits from Memory Tool (no cold start)

---

## 4. System Prompt Design

```
You are Apex Coach, a knowledgeable fitness and nutrition coach integrated into
the Apex health app. You have direct access to the user's real health data
including blood pressure, sleep, HRV, workout history, nutrition logs, and
supplement intake.

PERSONA:
- Supportive but direct. Don't sugarcoat when rest is needed.
- Evidence-based. Cite research principles (not specific papers) when relevant.
- Practical. Give actionable advice, not theoretical lectures.
- Concise. Keep responses under 200 words unless the user asks for detail.

SAFETY RULES (NEVER VIOLATE):
- You are NOT a doctor. Never diagnose conditions or recommend stopping medications.
- If BP is consistently ≥ 140/90, always recommend seeing a doctor alongside any advice.
- Never recommend calorie intake below 1,200 kcal/day (women) or 1,500 kcal/day (men).
- Never recommend training through sharp/acute pain. Muscle soreness is okay.
- Always recommend rest days. Minimum 1 per week, more if HRV/sleep indicate.
- If user mentions mental health struggles, express empathy and suggest professional support.
- Prefix any supplement recommendation with "based on current evidence" and note it's not medical advice.

CAPABILITIES:
- Analyze workout progression and suggest next session adjustments
- Review nutrition logs and suggest macro adjustments for goals
- Interpret HRV/sleep trends and recommend recovery strategies
- Build goal-specific training plans (hypertrophy, PRT prep, race training)
- Suggest supplement timing and dosing based on current stack
- Explain readiness scores and what's driving them

DATA ACCESS:
You will receive structured health data in JSON format with each message.
Reference specific numbers when giving advice (e.g., "Your HRV averaged 42ms
this week, down from 58ms — that's a clear signal to reduce volume").

RESPONSE FORMAT:
- Use short paragraphs or bullet points
- Bold key recommendations
- If suggesting a workout change, format as a clear before/after
- End complex advice with "Want me to go deeper on any of this?"
```

---

## 5. Goal-Specific Coaching Modes

### Navy PRT Prep
```
Inputs: Target PRT date, current scores, age group
Standards (Male 25-29): 38 push-ups, 1:18 plank, 13:45 run (minimum)
                        Max: 80+ push-ups, 3:20+ plank, 8:55 run

Coach generates:
- 8-12 week progressive plan
- Push-up progression (grease-the-groove + max sets)
- Plank progression (isometric hold building)
- Run training (interval + tempo + long run)
- Weekly mock PRT tests
- Taper week before test
```

### Race Training (5K, 10K, Half Marathon)
```
Inputs: Race date, current pace, goal time, weekly mileage
Coach generates:
- Periodized plan (base → build → peak → taper)
- Easy/tempo/interval/long run distribution
- Cross-training recommendations based on gym work
- Nutrition adjustments for training volume
- HRV-based recovery monitoring
```

### Hypertrophy Block
```
Inputs: Current mesocycle data from Workout Builder
Coach provides:
- Volume adjustment recommendations (approaching MRV?)
- Deload timing advice
- Nutrition alignment (surplus size, protein targets)
- Sleep/recovery optimization
- Exercise swap suggestions for weak points
```

---

## 6. UX Design

### Chat Interface
```
[Coach Tab] — New bottom nav destination OR floating action button

┌─────────────────────────────────────────┐
│  Apex Coach                        ⚙️  │
├─────────────────────────────────────────┤
│                                         │
│  [Coach] Good morning! Your readiness   │
│  is "Take it easy" today — HRV dropped  │
│  to 38ms (your baseline is 55ms) and    │
│  you only got 5h42m of sleep.           │
│                                         │
│  **Recommendation:** Light cardio or    │
│  mobility work today. Push your heavy   │
│  pull day to tomorrow.                  │
│                                         │
│  [You] What about just doing arms?      │
│                                         │
│  [Coach] Arms are lower-stress than a   │
│  full pull day — that works. I'd keep   │
│  it to 12-15 sets total, avoid heavy    │
│  barbell curls. Your biceps got 14 sets │
│  this week already (MAV is 14-20), so   │
│  you have room.                         │
│                                         │
│  Want me to generate a light arms       │
│  session and push it to Hevy?           │
│                                         │
├─────────────────────────────────────────┤
│  [Message input]              [Send] 📷 │
└─────────────────────────────────────────┘

📷 = attach food photo for estimation (integrates with Feature 2)
```

### Proactive Notifications
The coach doesn't just respond — it initiates:
- **Morning briefing:** "Good morning. Readiness: Good to go. Today's plan: Push Day A. Don't forget creatine post-workout."
- **Post-workout:** "Great session — you hit a PR on bench (72.5kg × 10). Volume this week: 16 chest sets. One more session before deload."
- **Evening:** "You're 400 calories under target with 35g protein remaining. Consider a casein shake before bed."
- **Weekly summary:** "This week: 5 workouts, avg 7.2h sleep, HRV trending up (+8%), 2100 avg kcal (target: 2400). Protein was low on Wednesday and Thursday."

---

## 7. Server Architecture

### API Endpoints
```
POST /api/coach/message     — Send message, receive coaching response
GET  /api/coach/history     — Conversation history (paginated)
GET  /api/coach/context     — Current athlete context (for debugging)
POST /api/coach/plan        — Generate a training plan (PRT, race, hypertrophy)
DELETE /api/coach/history    — Clear conversation history
```

### Server-Side Flow
```
1. Android sends user message + current health snapshot
2. Server builds context:
   a. Retrieve athlete profile from DB
   b. Retrieve relevant data based on message intent (keyword/embedding match)
   c. Retrieve Memory Tool state (persistent knowledge)
   d. Assemble tiered context
3. Server calls Claude API (Haiku or Sonnet based on complexity)
4. Parse response, extract any actionable items (workout changes, nutrition adjustments)
5. Return response + any structured actions to client
6. Client renders chat message + optionally executes actions (push to Hevy, etc.)
```

### Why Server-Side (not client-side API call):
- Server has all health data — no need to send it from client
- Server manages conversation history and Memory Tool
- Server can route to Haiku vs Sonnet based on complexity
- Rate limiting and cost control centralized
- API key never on device (unlike food photo which is low-risk)

---

## 8. Cost Projection

| Usage Pattern | Messages/Day | Monthly Cost |
|--------------|-------------|-------------|
| Light (2-3 questions) | 3 | ~$0.50 (Haiku) |
| Moderate (daily check-in + questions) | 8 | ~$1.50 (mostly Haiku) |
| Heavy (coaching + plan generation) | 15 | ~$4.00 (Haiku + Sonnet mix) |
| Plan generation (weekly) | 4/week | ~$0.80 (Sonnet) |

**Estimated monthly total: $2-5.** Negligible for a single-user app.

---

## 9. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| User treats coach as doctor | HIGH | System prompt safety rails. Persistent "Not medical advice" disclaimer. Auto-flag BP ≥ 140/90. |
| Hallucinated advice (wrong numbers, bad recommendations) | HIGH | Always inject real data into context. Validate any numbers in response against source data. Never let model invent health metrics. |
| Context rot (model forgets earlier conversation) | MEDIUM | Memory Tool for persistent facts. Summarize old messages. Keep active context under 50K tokens. |
| Over-reliance on coach (user stops thinking) | LOW | Coach encourages self-awareness: "What do you think?" prompts. Doesn't make decisions, offers options. |
| API latency (2-5 sec for Haiku, 5-10 sec for Sonnet) | MEDIUM | Streaming responses (Claude API supports SSE). Show typing indicator. Pre-fetch morning briefing. |
| Monthly API cost creep | LOW | Default to Haiku. Sonnet only for explicit plan generation. Hard cap at $10/mo with warning. |

---

## 10. Implementation Phases

| Phase | Scope | Dependencies |
|-------|-------|-------------|
| **Phase 1** | Server: coach endpoint with system prompt + basic health context injection | Claude API key, server access |
| **Phase 2** | Android: Chat UI screen with message input + response display | Phase 1 |
| **Phase 3** | Context enrichment: workout history, nutrition, supplement data in context | Nutrition + Workout features |
| **Phase 4** | Streaming responses (SSE) for real-time typing feel | Phase 2 |
| **Phase 5** | Memory Tool integration for cross-session persistence | Phase 1 |
| **Phase 6** | Proactive notifications (morning briefing, post-workout, evening) | Phase 3 + notification system |
| **Phase 7** | Goal-specific plan generation (PRT, race, hypertrophy) | Phase 3 + Workout Builder |
| **Phase 8** | Food photo integration (attach photo in chat → estimate) | Phase 2 + Food Photo feature |

---

## 11. Open Questions for User

1. **Chat as new tab?** Add a 5th bottom nav item "Coach", or use a floating chat bubble overlay?
2. **Voice input?** Android speech-to-text is free — should the coach accept voice messages?
3. **Morning briefing:** Push notification with summary, or only show when opening the app?
4. **PRT prep:** Is Navy PRT your primary use case for event-based training? Any other events (races, rucks)?
5. **Proactivity level:** Should the coach initiate conversations (notifications), or only respond when asked?
6. **Conversation history:** Keep forever, or auto-delete after 30 days?

---

## Sources

- [AthleteData.health — Claude-Powered AI Fitness Coach](https://www.athletedata.health)
- [Building AI Fitness Coach Using Claude Code (Medium)](https://medium.com/@natetang/building-my-own-ai-fitness-coach-using-claude-code-cf52663370c2)
- [PH-LLM: Personal Health LLM for Sleep & Fitness (Nature Medicine)](https://www.nature.com/articles/s41591-025-03888-0)
- [PH-LLM Overview (Dev.to)](https://dev.to/mitanshgor/ph-llm-a-llm-that-gives-personalized-sleep-and-fitness-coaching-using-wearable-data-2m7f)
- [Fine-Tuning LLMs for Health Coaching (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC12454129/)
- [Infusing Behavior Science into LLMs for Coaching (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC10986996/)
- [LLM as Interactive Sports Coach: Half Marathon (arXiv)](https://arxiv.org/html/2509.26593v1)
- [Claude API Context Windows](https://platform.claude.com/docs/en/build-with-claude/context-windows)
- [Claude API Memory Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool)
- [Open Wearables: Open-Source Wearable Health API](https://www.themomentum.ai/blog/introducing-open-wearables-the-open-source-api-for-wearable-health-intelligence)
- [Navy PRT Standards 2026](https://www.operationmilitarykids.org/navy-prt-standards/)
- [Navy PRT Guide (Official)](https://www.mynavyhr.navy.mil/Portals/55/Support/Culture%20Resilience/Physical/Guide-5A%20Physical%20Readiness%20Test.pdf)
- [Navy PRT Male 25-29 Standards](https://www.navy-prt.com/us-navy-overall-fitness-standards/navy-pt-standards-for-men/pt-standards-males-25-29/)
- [Healthify + OpenAI Case Study](https://openai.com/index/healthify/)
- [Building Personal Fitness Coach with LangChain](https://www.analyticsvidhya.com/blog/2025/07/langchain-fitness-coach/)
