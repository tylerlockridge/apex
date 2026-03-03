# Apex — LLM Audit Report
**Date:** 2026-03-03
**Scope:** Full v2 codebase — UX, architecture, features, bugs
**Method:** Internal code review (Claude code-reviewer agent) + web research (Oura, WHOOP, Apple Health, Garmin, Hevy) + Gemini Code Assist PR review

---

## Scores

| Dimension | Score | Notes |
|-----------|-------|-------|
| Architecture | 7.5/10 | Clean ViewModel/StateFlow pattern, good separation; SyncWorker has correctness bugs |
| Code Quality | 7.0/10 | Generally clean; Activity screen indent/nesting issue; duplicate KDoc |
| Security | 8.5/10 | HMAC + cert pinning + EncryptedSharedPreferences — well implemented |
| Testing | 6.5/10 | 20 unit tests, no instrumented tests, no integration tests |
| UX / Feature Completeness | 5.5/10 | Functional but missing engagement features vs. premium health apps |
| Performance | 7.0/10 | HC reader runs on Main; widget updateAll always fires |
| **Overall** | **7.0/10** | Solid v2 base with clear improvement path |

---

## HIGH Severity Issues (Must Fix)

### H-1 — `SyncWorker` writes `LAST_SYNC` before checking for failures (misleading sync status)
**File:** `SyncWorker.kt:206`
`LAST_SYNC` is written unconditionally before the `anyFailure` check. A sync that partially fails still resets the dashboard sync dot to green after 15 minutes, masking errors from the user.
**Fix:** Only write `LAST_SYNC` when `!anyFailure`, or have the `SyncStatusCard` also check the most recent sync history `ok` flag.

### H-2 — `SettingsScreen` "Sync All Now" writes timestamp before sync completes
**File:** `SettingsScreen.kt:248-261`
`lastSyncMs = System.currentTimeMillis()` and `prefs.edit()...` are written immediately after `SyncWorker.runOnce()`, which is fire-and-forget. The screen shows "Last synced: just now" before any data has synced. Unlike `DashboardViewModel.triggerSync()` which observes WorkInfo to completion, this is purely optimistic.
**Fix:** Remove the timestamp write from SettingsScreen entirely — let `SyncWorker` write it on success as it already does.

### H-3 — `HealthConnectReader` body fat timestamp matching accepts records 1h 59m apart
**File:** `HealthConnectReader.kt:152-157`
`ChronoUnit.HOURS.between(it.time, weight.time)` is truncated (not rounded). A body fat measurement 1h 59m before a weight record returns `1`, passes `in -1..1`, and is incorrectly matched. Use seconds: `Math.abs(ChronoUnit.SECONDS.between(it.time, weight.time)) <= 3600`.

### H-4 — `ActivityViewModel.loadAll()` fires two concurrent coroutines with a shared `isLoading` flag
**File:** `ActivityViewModel.kt:92-95`
`loadWorkouts()` and `loadStats()` are launched simultaneously. `loadWorkouts` sets and clears `isLoading`; a parallel `refresh()` call during in-flight requests launches 4 total coroutines, with the last to finish "winning" the state. The `error = null` clear at the start of `loadWorkouts` also wipes any existing error before the new request even starts.
**Fix:** Sequence `loadStats()` after `loadWorkouts()` inside a single coroutine, or add a `if (state.isLoading) return` guard.

### H-5 — `DashboardViewModel` runs `HealthConnectReader` initialization on Main dispatcher
**File:** `DashboardViewModel.kt:84-86`
`HealthConnectReader(context)` + `hasAllPermissions()` (IPC into the HC service) runs on `Dispatchers.Main.immediate`. If the HC service bind takes any time, it blocks the UI thread.
**Fix:** Wrap the HC availability and permission checks in `withContext(Dispatchers.IO)`.

---

## MEDIUM Severity Issues (Should Fix)

| ID | Issue | File |
|----|-------|------|
| M-1 | `TrendsViewModel` tab 0 resets to BP on every navigation — use `SavedStateHandle` for `selectedTab` | `TrendsViewModel.kt:192` |
| M-2 | Dashboard pull-to-refresh uses an 800ms `delay()` instead of observing actual completion | `DashboardScreen.kt:56` |
| M-3 | `HealthGlanceWidget().updateAll()` fires on every sync even when no widget is pinned — check for IDs first | `SyncWorker.kt:209` |
| M-4 | `SettingsScreen` builds a new `OkHttpClient` instance on every screen open — move ping to a ViewModel | `SettingsScreen.kt:99` |
| M-5 | `ActivityScreen` indentation broken — `Row` and children appear at same indent level as `Column` body | `ActivityScreen.kt:52` |
| M-6 | `TrendsScreen` single `error` field shared across all tabs — switching tabs clears errors silently | `TrendsViewModel.kt:85` |
| M-7 | `HealthConnectReader` Oura package hardcode with silent fallback — user can't see or control source filtering | `HealthConnectReader.kt:80` |

---

## LOW / UX Issues (Polish)

| ID | Issue | File |
|----|-------|------|
| L-1 | Dashboard `LazyRow` metric cards have no scroll affordance — add partial card peek via `contentPadding` | `DashboardScreen.kt:118` |
| L-2 | Card entrance animation replays every tab switch — use `rememberSaveable` to fire only once | `DashboardScreen.kt:65` |
| L-3 | Sync status dot is color-only (inaccessible for color-blind users) — add icon for error state | `DashboardScreen.kt:230` |
| L-4 | "Sync BP" / "Sync Sleep" quick-action buttons not disabled during active sync — taps silently no-op | `DashboardScreen.kt:684` |
| L-5 | Trends loading shimmer always shows 3 skeletons regardless of tab — count should match expected cards | `TrendsScreen.kt:94` |
| L-6 | Activity empty state message says "sync Hevy workouts" but the sync button is hidden in the header | `ActivityScreen.kt:138` |
| L-7 | Settings HC permissions show all-green or all-red — individual permission statuses not distinguishable | `SettingsScreen.kt:329` |
| L-8 | Widget shows time-of-day only ("Synced 6:00 AM") — stale data from yesterday is indistinguishable | `HealthGlanceWidget.kt:43` |
| L-9 | Duplicate KDoc block on `DashboardViewModel.triggerSync()` | `DashboardViewModel.kt:142` |
| L-10 | `SyncWorker` does not check for blank API key — will retry 401s indefinitely, wasting battery | `SyncWorker.kt:38` |

---

## Gemini Code Assist Observations (README diff only)

- README says "Vico line/bar charts" but charts are custom-built Canvas composables (no Vico dependency)
- README states 22 unit tests; actual count is 20

---

## Feature Opportunities (from Oura / WHOOP / Apple Health / Garmin / Hevy research)

### Priority 1 — High Impact, Low Effort (data already collected)

| Feature | Pattern Source | Notes |
|---------|---------------|-------|
| **Readiness score card on Dashboard** | Garmin Body Battery + Oura Readiness | Compute from HRV vs. 30-day mean + sleep duration vs. 7-day mean + BP. Show "Good to go / Take it easy / Recovery day" with one sentence. All data already in SharedPrefs/server. |
| **BP anomaly alert notification** | Apple Health Trends / Hypertension detection | In `SyncWorker.doWork()`, compare latest BP to 14-day average. If systolic >15 mmHg above mean, post a notification with context. Zero new data required. |
| **Weekly summary push notification** | Oura weekly digest + Garmin weekly report | Sunday 9am `PeriodicWorkRequest`. Compute week-over-week delta for sleep, BP, workouts, HRV from existing SharedPrefs. Rich notification with expandable body. |
| **HRV 7-day rolling average overlay** | Oura HRV Balance, Elite HRV | Second dashed-line series on HRV LineChart. Rolling average computed from the already-fetched HRV list. LineChart already supports multi-series (baseline bands). |
| **Spring physics animations** | Material 3 Expressive (2025) | Replace `tween(300)` in card entrances with `spring(DampingRatioMediumBouncy)`. Drop-in change; immediately feels more premium. |
| **Widget time-relative display** | Best practices | Show "Today 6:00 AM" vs. "Mon 6:00 AM" based on whether `lastSyncMs` is same day. Fix L-8 above. |

### Priority 2 — Medium Impact, Requires New Work

| Feature | Pattern Source | Notes |
|---------|---------------|-------|
| **Sleep score arc gauge on Dashboard** | Garmin sleep score + Oura readiness ring | Replace current "sleep duration" text in dashboard card with a circular arc gauge (Canvas `drawArc()`). Color maps: 0-59 red, 60-79 amber, 80-100 green. Sleep score already in data pipeline. |
| **QR code onboarding** | Universal device pairing UX | CameraX + ML Kit Barcode Scanning. Closes the single biggest documented onboarding gap. Decodes JSON: `{serverUrl, apiKey, deviceSecret}`. Replaces the current stub "Re-scan QR" button. |
| **Individual permission status in Settings** | Standard permission UI | Each HC type shows its own granted/denied icon. Requires passing the set of granted permissions into `ActivityState` rather than the single `hasAllPermissions` boolean. |
| **Activity empty state sync button** | WHOOP/Oura empty state design | Add a visible `Button("Sync Hevy")` inside the empty state column in `ActivityScreen.kt`. Currently the only trigger is the icon in the header (not discoverable for first-time users). Closes L-6. |
| **Per-exercise volume chart** | Hevy progressive overload graph | Navigate from an expanded workout card to a per-exercise line chart showing volume (weight × reps × sets) over 90 days. Requires new server endpoint or client-side aggregation. PR badge on the max-volume session. |
| **"Month in Review" shareable card** | Health Wrapped + Hevy PR trophy | A visually distinct card rendered at the end of each month. Content: avg sleep delta, BP trend, workout count. Share button exports via Android share sheet using `drawToBitmap()`. |

### Priority 3 — Lower Priority / Higher Effort

| Feature | Notes |
|---------|-------|
| **Daily behavior journal + correlation** | 3-question daily prompt (alcohol, exercise, stress 1-5). Store in Room DB. After 30 days, correlate with BP/HRV readings. WHOOP's most differentiated feature, adapted for a personal app. |
| **Server version validation** | Compare server version against `MIN_SERVER_VERSION` on startup. Show persistent warning banner if server is outdated. Closes documented gap in `11-unimplemented-features.md`. |
| **Incremental HC sync (change tokens)** | Replace full 30-day read with `getChanges(token)` per HC type. Reduces background battery use significantly over time. |
| **`NavigationSuiteScaffold`** | Swap hardcoded bottom nav for `NavigationSuiteScaffold` — gets navigation rail on tablets/foldables for free. |
| **Predictive back gesture support** | Android 15 users will notice the missing preview animation. `PredictiveBackHandler` in Compose 1.7+. |

---

## Prioritized Fix Order

**This sprint (bugs before features):**
1. H-2 — Remove premature timestamp write in SettingsScreen "Sync All Now" *(30 min)*
2. H-1 — Guard LAST_SYNC write on `!anyFailure` in SyncWorker *(30 min)*
3. H-3 — Fix HealthConnectReader body fat timestamp matching *(15 min)*
4. L-10 — Add blank API key early-exit in SyncWorker *(10 min)*
5. L-4 — Disable sync buttons during active sync *(15 min)*
6. L-6 — Add "Sync Hevy" button to Activity empty state *(15 min)*
7. L-8 — Fix widget stale-date display *(15 min)*

**Next sprint (features):**
1. Readiness score card *(medium)*
2. BP anomaly notification *(small)*
3. Weekly digest notification *(small)*
4. HRV rolling average overlay *(small)*
5. Sleep score arc gauge *(medium)*
6. QR code onboarding *(large — closes known gap)*

---

## Action Plan

See `llm-audit-action-plan.md` for prioritized implementation tasks.
