# Apex — LLM Audit Action Plan
**Audit Date:** 2026-03-03
**Based On:** `llm-audit-report-2026-03-03.md`
**Previous audit:** 2026-02-28 (all items closed — see git history)

---

## Sprint 1 — Bug Fixes (This Sprint)

### 1. H-2 · Remove premature timestamp write in SettingsScreen *(~30 min)*
**File:** `SettingsScreen.kt:248-261`
**Fix:** Delete `lastSyncMs = System.currentTimeMillis()` and the `prefs.edit()` call from the `onClick` handler of "Sync All Now". `SyncWorker` already writes `LAST_SYNC` on success — the premature write is redundant and misleading.

---

### 2. H-1 · Guard LAST_SYNC write on `!anyFailure` in SyncWorker *(~30 min)*
**File:** `SyncWorker.kt:206`
**Fix:** Move `prefs.edit().putLong(SyncPrefsKeys.LAST_SYNC, ...)` inside `if (!anyFailure)` block. Partial failures should not reset the sync indicator to green.

---

### 3. H-3 · Fix HealthConnectReader body fat timestamp matching *(~15 min)*
**File:** `HealthConnectReader.kt:152-157`
**Fix:** Replace:
```kotlin
ChronoUnit.HOURS.between(it.time, weight.time) in -1..1
```
With:
```kotlin
Math.abs(ChronoUnit.SECONDS.between(it.time, weight.time)) <= 3600
```

---

### 4. L-10 · Add blank API key early-exit in SyncWorker *(~10 min)*
**File:** `SyncWorker.kt:38`
**Fix:** At the top of `doWork()`, add:
```kotlin
val apiKey = SecurePrefs.getApiKey(applicationContext)
if (apiKey.isBlank()) return Result.failure()
```
Prevents infinite 401 retry loops that drain battery.

---

### 5. L-4 · Disable sync buttons during active sync *(~15 min)*
**File:** `DashboardScreen.kt:684`
**Fix:** Pass `syncState.isSyncing` into the "Sync BP" and "Sync Sleep" buttons as `enabled = !syncState.isSyncing`. Currently, taps during active sync are silently ignored but the buttons appear interactive.

---

### 6. L-6 · Add "Sync Hevy" button to Activity empty state *(~15 min)*
**File:** `ActivityScreen.kt:138-158`
**Fix:** Add a `Button("Sync Hevy")` inside the empty state `Column`, wired to `viewModel.triggerHevySync()`. The sync icon in the header is not discoverable for first-time users.

---

### 7. L-8 · Fix widget stale-date display *(~15 min)*
**File:** `HealthGlanceWidget.kt:43`
**Fix:** Replace time-only format with date-aware display:
- Same calendar day → "Today 6:00 AM"
- Yesterday → "Yesterday 6:00 AM"
- Older → "Mon 6:00 AM" (day + time)

---

## Sprint 2 — Feature Work (Next Sprint)

### 8. Readiness Score Card on Dashboard *(medium)*
Compute from: HRV vs. 30-day mean + sleep duration vs. 7-day mean + latest BP.
Display: "Good to go / Take it easy / Recovery day" card with one-sentence rationale.
All data already available in SharedPrefs/server — no new endpoints required.
Pattern: Garmin Body Battery, Oura Readiness Score.

---

### 9. BP Anomaly Alert Notification *(small)*
In `SyncWorker.doWork()`, after syncing BP:
- Fetch last 14 days of BP from SharedPrefs history
- If systolic > 15 mmHg above 14-day mean, post a NotificationCompat notification
- Include context: "Your BP is notably elevated today (X/Y). 14-day avg: A/B."
Zero new data required. Pattern: Apple Health Trends hypertension detection.

---

### 10. Weekly Summary Push Notification *(small)*
`PeriodicWorkRequest` targeting Sunday 9 AM.
Content: week-over-week delta for sleep, BP, workouts, HRV from existing SharedPrefs.
Rich notification with expandable body.
Pattern: Oura Weekly Digest, Garmin Weekly Report.

---

### 11. HRV 7-Day Rolling Average Overlay *(small)*
Add a second dashed-line series to the HRV `LineChart` in `TrendsScreen`.
Rolling average computed client-side from the already-fetched HRV list.
`LineChart` already supports multi-series (baseline bands) — wire up the second series.
Pattern: Oura HRV Balance, Elite HRV.

---

### 12. Sleep Score Arc Gauge on Dashboard *(medium)*
Replace the "sleep duration" text in the sleep summary card with a circular arc gauge using `Canvas.drawArc()`.
Color mapping: 0–59 red, 60–79 amber, 80–100 green.
Sleep score is already in the data pipeline.
Pattern: Garmin Sleep Score, Oura Readiness Ring.

---

### 13. QR Code Onboarding *(large — closes known gap)*
CameraX + ML Kit Barcode Scanning.
Decodes JSON: `{"serverUrl": "...", "apiKey": "...", "deviceSecret": "..."}`.
Replaces the current stub "Re-scan QR" button in SettingsScreen.
Closes the single largest documented onboarding gap.
See `documentation/11-unimplemented-features.md`.

---

## Sprint 3 — Backlog / Polish

| Item | Effort | Notes |
|------|--------|-------|
| H-4 — ActivityViewModel concurrent coroutine race | Small | Sequence `loadStats()` after `loadWorkouts()`, or add `isLoading` guard |
| H-5 — DashboardViewModel HC reader on Main dispatcher | Small | Wrap in `withContext(Dispatchers.IO)` |
| M-1 — TrendsViewModel tab resets to BP on nav | Small | Use `SavedStateHandle` for `selectedTab` |
| M-2 — Dashboard PTR uses 800ms delay | Small | Observe WorkInfo to completion instead |
| M-3 — Widget updateAll fires even with no widget | Small | Check for pinned widget IDs before calling updateAll |
| M-4 — OkHttpClient built on every Settings open | Small | Move ping to a ViewModel |
| M-5 — ActivityScreen indentation | Trivial | Fix `Row` indent to be inside `Column` body |
| M-6 — Single error field shared across Trends tabs | Small | Per-tab error fields |
| M-7 — Oura package hardcode (silent fallback) | Small | Expose source filter as user setting |
| L-1 — Dashboard LazyRow no scroll affordance | Trivial | Add `contentPadding` for partial card peek |
| L-2 — Card animation replays on tab switch | Small | Use `rememberSaveable` to fire only once |
| L-3 — Sync dot color-only (inaccessible) | Small | Add error icon for colorblind users |
| L-5 — Trends shimmer always shows 3 skeletons | Trivial | Match skeleton count to expected cards per tab |
| L-7 — Settings HC permissions all-green or all-red | Small | Per-type granted/denied status |
| L-9 — Duplicate KDoc on triggerSync | Trivial | Remove duplicate |
| Daily behavior journal + correlation | Large | WHOOP's most differentiated feature |
| Server version validation | Small | Banner if server < MIN_SERVER_VERSION |
| Incremental HC sync (change tokens) | Large | Reduces battery use significantly |
| NavigationSuiteScaffold | Small | Navigation rail on tablets/foldables |
| Predictive back gesture | Small | Android 15 polish |
| Spring physics animations | Trivial | Replace `tween(300)` with `spring(DampingRatioMediumBouncy)` |

---

## Score Projections

| Dimension | Current (2026-03-03) | After Sprint 1 | After Sprint 2 |
|-----------|---------------------|----------------|----------------|
| Architecture | 7.5 | 7.5 | 8.0 |
| Code Quality | 7.0 | 7.5 | 7.5 |
| Security | 8.5 | 8.5 | 8.5 |
| Testing | 6.5 | 6.5 | 7.0 |
| UX / Feature Completeness | 5.5 | 6.0 | 7.5 |
| Performance | 7.0 | 7.5 | 7.5 |
| **Overall** | **7.0** | **7.5** | **8.0** |

---

## Tracking

| Sprint | Items | Status |
|--------|-------|--------|
| Sprint 1 (bugs) | #1–7 | ✅ Complete — commit e0bb642 (2026-03-03) |
| Sprint 2 (features) | #8–12 | ✅ Complete — commit 4acfded (2026-03-03). QR onboarding (#13) deferred to Sprint 3 |
| Sprint 3 (polish) | multiple | ⬜ Backlog |
