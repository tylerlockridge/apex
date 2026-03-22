# Apex v2 Lessons Learned

### 2026-03-16 Hevy completed workouts include `routine_id` — discovered only by inspecting live API

**Context:** The Hevy API documentation and existing `HevyClient` code gave no indication that completed workouts carry a `routine_id` linking back to the routine they were started from. This field was discovered only by calling `GET /v1/workouts` and inspecting the response.

**Lesson:** The existing `HevyClient.transformWorkout()` actively drops `routine_id` during sync — it was never captured. This single field enables the entire prescribed-to-actual reconciliation model (ADR-004). Always inspect live API responses for fields beyond what current code uses, especially before making architecture decisions about data relationships.

**Application:** Before designing any data model that depends on external API linkage, call the actual API and inspect the full response — don't rely on existing client code or documentation alone.

---

### 2026-03-16 Architecture must be conditional on unvalidated external API capabilities

**Context:** The Hevy API supports reading routines (`GET /v1/routines`). Whether it supports creating routines (`POST /v1/routines`) is unknown. ADR-004 initially drafted language implying Apex could push routines to Hevy — which is an unvalidated assumption.

**Lesson:** Design architecture to work for both the positive and negative validation outcomes. ADR-004 was revised to define Path A (routine creation available) and Path B (not available), with the same schema working for both. The schema fields are safe to create regardless; only their behavioral utility depends on validation.

**Application:** When architecture depends on an external API capability that hasn't been tested, structure the design as conditional paths with a shared schema. Never assume external write capabilities exist just because read capabilities do.
