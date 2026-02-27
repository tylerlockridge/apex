# Health Platform Sync — Android App

Android companion app for the Health Intelligence Platform. Syncs all Health Connect data, provides a local dashboard with charts, and features a polished dark-themed experience with animations.

**Backend:** https://tyler-health.duckdns.org
**Status:** v1 built (config screen + basic sync), v2 PRD below

---

## Build & Install

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android device with API 28+ (Android 9.0) and Health Connect installed
- Pixel phones come with Health Connect pre-installed

### Debug Build
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
```bash
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Install via ADB
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Install via Android Studio
1. Connect Pixel phone via USB (USB debugging enabled)
2. Select device in dropdown
3. Click Run (Shift+F10)

---

# PRD v2.0 — Full Android App

**Date:** 2026-01-31

---

## 1. Product Overview

### What
A fully branded Android companion app that syncs all 11 Health Connect data types to the backend, provides a local dashboard with interactive charts, home screen widgets, and a polished animated dark-themed experience with biometric security.

### Why
- Health data should sync automatically without manual entry
- Mobile access to health trends without opening a browser
- Home screen widget for instant BP/sleep visibility
- Pixel Watch + Oura Ring data flows through Health Connect natively

### User
Tyler Lockridge (sole user). Personal health monitoring for BP stability tracking (Vyvanse + Buspar medication monitoring), body composition, and sleep quality.

---

## 2. Design Language

### Theme
- **Dark theme only** (matches web dashboard)
- **No purple anywhere** in the color palette
- Glass-morphism buttons (translucent with blur backdrop)
- Dark background: `#0D1117` or `#121212`
- Primary accent: Teal/cyan (`#1DB9C3`) or health-green (`#00BA7C`)
- Secondary accent: Soft blue (`#1D9BF0`)
- Text: White (`#E6EDF3`) on dark surfaces
- Cards: Elevated dark surfaces (`#161B22`) with subtle borders

### Animations
- Screen transitions: shared element + fade/slide (Compose animation APIs)
- Button press: scale(0.95) + ripple + haptic feedback
- Chart data: animate points left-to-right on load
- Sync indicator: pulsing dot during active sync
- Card reveals: staggered fade-in on dashboard load
- Pull-to-refresh: custom animated health icon
- Number transitions: animated counter morphing old to new value

### Typography
- Headlines: Bold, slightly larger than Material defaults
- Body: Regular weight, high contrast on dark
- Numbers/metrics: Monospace or tabular figures for alignment

### App Icon
- Modern geometric design — stylized "H" or pulse line
- Dark background with accent color
- Adaptive icon support (Android 8+)

### Splash Screen
- Android 12+ SplashScreen API
- App icon centered on dark background
- Brief fade into onboarding or dashboard

---

## 3. Screens & Navigation

### Navigation: Bottom bar with 4 tabs

| Tab | Icon | Screen |
|-----|------|--------|
| Home | Dashboard | Latest readings + quick stats |
| Trends | Chart | Full charts (BP, sleep, body, activity) |
| Activity | Dumbbell | Workout history + exercise stats |
| Settings | Gear | Configuration, sync, security |

### 3.1 Onboarding (First Launch Only)

**Step 1: Welcome**
- App name + icon with fade-in
- "Connect your health data" tagline
- "Get Started" glass button

**Step 2: QR Code Setup**
- Camera viewfinder to scan QR code
- QR generated from web dashboard (/settings page)
- QR payload: `{"url":"https://tyler-health.duckdns.org","secret":"<device_sync_secret>"}`
- "Enter manually" fallback link
- On scan: checkmark animation, auto-fill server + secret

**Step 3: Biometric Setup**
- "Secure your health data" prompt
- Register fingerprint/face for app lock
- Skip option (enable later in settings)

**Step 4: Health Connect Permissions**
- 11 data types listed with toggle checkboxes (all checked default)
- "Grant Access" opens Health Connect permission dialog
- Shows granted/denied status per type
- "Continue" enabled when BP + Sleep granted minimum

**Step 5: Initial Sync**
- "Syncing your data..." animated progress
- Pull last 30 days of health data
- Show record counts per type
- "Done — Go to Dashboard" button

### 3.2 Dashboard (Home Tab)

**Top: Summary Cards (horizontal scroll)**
- Latest BP (systolic/diastolic, time ago, anomaly badge)
- Last night sleep (duration, score, stage mini-bar)
- Current weight (delta from last reading)
- Today's steps (goal progress ring)
- Today's HRV (trend arrow)

**Middle: Quick Charts**
- BP sparkline (last 7 days, tap to expand)
- Sleep bar chart (last 7 nights, tap to expand)

**Bottom: Recent Activity**
- Last 3 sync events with timestamps
- Last workout summary
- AI analysis snippet ("Read more" link)

**Interactions:**
- Pull-to-refresh triggers immediate sync
- FAB: manual sync with pulse animation while syncing

### 3.3 Trends Screen

**Sub-tabs:** BP | Sleep | Body | Activity

**BP Tab:**
- Line chart: systolic + diastolic (7d/30d/90d toggle)
- Horizontal baseline bands (normal, elevated, high)
- Anomaly markers (red dots)
- Stats: average, min, max, std deviation
- In-app alert banner if latest reading anomalous

**Sleep Tab:**
- Stacked bar: deep/REM/light/awake per night
- Sleep score line overlay
- Duration trend
- Stats: avg duration, avg score, best/worst

**Body Tab:**
- Weight line + body fat % overlay
- Lean mass trend
- Configurable goal line (target weight)
- Stats: current, starting, change, rate

**Activity Tab:**
- Steps bar chart (daily)
- Calories line chart
- Distance trend
- Active minutes per day

**All charts:**
- Animated data reveal (left-to-right)
- Tap point for tooltip (value + time)
- Pinch to zoom time axis
- Time range: 7d / 30d / 90d / 1y

### 3.4 Activity Screen (Workouts)

- Workout list (most recent first)
- Card: name, date, duration, exercise count
- Tap to expand: sets/reps/weight per exercise
- Weekly volume summary card at top
- Workout frequency chart (per week)

### 3.5 Settings Screen

**Account:** Server URL (read-only), connection status, device secret (masked), re-scan QR

**Sync:** Auto-sync toggle, event-driven trigger, WiFi+Cellular/WiFi-only, sync history (last 10), "Sync Now"

**Health Connect:** Permission status (11 types), "Manage Permissions" button, per-type sync toggles

**Security:** Biometric lock toggle, cert pinning status, "Clear all data" (biometric confirm)

**About:** App version, server version, last sync, lifetime records synced

---

## 4. Home Screen Widgets

### Health Glance (4x2)
- Dark background matching app theme
- Left: Latest BP (large) + time ago
- Right: Sleep score + duration
- Bottom: Steps today + HRV
- Tap opens dashboard
- Auto-updates after sync

### BP Quick (2x1)
- Compact: "120/80" large text
- "2h ago" + green/yellow/red indicator
- Tap opens BP trends

---

## 5. Data Sync

### 5.1 All 11 Health Connect Types

| # | Type | Record | Priority |
|---|------|--------|----------|
| 1 | Blood Pressure | BloodPressureRecord | Critical |
| 2 | Heart Rate | HeartRateRecord | High |
| 3 | Sleep | SleepSessionRecord | Critical |
| 4 | Weight | WeightRecord | High |
| 5 | Body Fat | BodyFatRecord | High |
| 6 | Lean Body Mass | LeanBodyMassRecord | Medium |
| 7 | Steps | StepsRecord | Medium |
| 8 | Distance | DistanceRecord | Medium |
| 9 | Active Calories | ActiveCaloriesBurnedRecord | Medium |
| 10 | Total Calories | TotalCaloriesBurnedRecord | Medium |
| 11 | HRV | HeartRateVariabilityRmssdRecord | High |

### 5.2 Sync Triggers

**Event-driven (primary):**
- Health Connect `getChanges()` change listener
- On change: read new records since last sync token
- Batch and send within 30 seconds

**Periodic fallback:**
- WorkManager every 1 hour (catch missed events)
- Any network (WiFi or cellular)

**Manual:**
- Pull-to-refresh, FAB, "Sync Now" in settings

### 5.3 Sync Protocol

```json
{
  "device_secret": "<encrypted>",
  "device_id": "<android_id>",
  "timestamp": "2026-01-31T12:00:00Z",
  "hmac": "<sha256_hmac>",
  "data_type": "blood_pressure",
  "records": [...]
}
```

**Deduplication:** Backend uses `measured_at` + `data_type` + `source_device` as unique key.

### 5.4 Offline Behavior
- Queue in Room database when offline
- Retry with exponential backoff on reconnect
- "Pending sync: X records" badge in UI
- Auto-purge queue items older than 7 days

---

## 6. Security

### 6.1 EncryptedSharedPreferences
- Android Keystore-backed encryption at rest
- Stores device secret, server URL, sync tokens
- Key requires user authentication (biometric)

### 6.2 Biometric Lock
- App locked on cold start + after 5 min background
- BiometricPrompt (fingerprint/face), device PIN fallback
- Enabled by default after onboarding, can disable in settings

### 6.3 Certificate Pinning
- Pin ISRG Root X1 (Let's Encrypt intermediate CA)
- OkHttp CertificatePinner — survives leaf cert renewal
- Soft-fail: log warning if pin fails, allow standard chain (personal app)

### 6.4 HMAC Request Signing
- HMAC-SHA256 on every sync request
- Key: device_sync_secret
- Payload: `timestamp + data_type + record_count`
- Timestamp within 5 min of server time (replay protection)

### 6.5 Data at Rest
- Health data not cached beyond sync queue
- Sync queue encrypted via Room + SQLCipher
- No health data values in logs (counts only)

---

## 7. Backend Changes Required

| Change | Endpoint | Description |
|--------|----------|-------------|
| QR code generation | GET /api/settings/qr-code | Returns PNG with server URL + device secret |
| HMAC validation | Middleware on /api/sync/* | Validate signature + timestamp |
| New data types | POST /api/sync/health-connect | Accept heart_rate, steps, distance, calories, HRV |
| Sync token storage | GET/POST /api/sync/token | Incremental sync support per device |
| Widget summary | GET /api/dashboard/summary | Latest reading per type (minimal payload) |

---

## 8. Testing

### 8.1 Unit Tests (JUnit 5 + MockK)

| Module | Coverage Target |
|--------|----------------|
| HealthConnectReader (all 11 types) | 90% |
| ApiService (HMAC, requests, responses) | 95% |
| SyncWorker (queue, retry, offline) | 90% |
| EncryptedStorage (prefs, biometric) | 85% |
| QRCodeParser (parse, validate) | 95% |
| ViewModels (state, transforms) | 85% |

### 8.2 Instrumented Tests (Compose Testing)

| Screen | Scope |
|--------|-------|
| Onboarding | QR scan, permissions, biometric, manual fallback |
| Dashboard | Cards render, pull-to-refresh, FAB, navigation |
| Trends | Charts with mock data, time range, tap interactions |
| Settings | Toggle persistence, sync history, permissions |
| Widget | Renders with mock data, tap targets |

### 8.3 Integration Tests

| Test | Description |
|------|------------|
| E2E sync | Mock Health Connect -> sync to mock server -> verify payload |
| Offline queue | Disconnect -> sync -> verify queued -> reconnect -> verify sent |
| HMAC validation | Verify signed requests match expected format |
| Cert pinning | Reject non-pinned hosts |
| Biometric gate | Require auth after background timeout |

### 8.4 Manual Acceptance Tests

| # | Test | Pass Criteria |
|---|------|--------------|
| M1 | Install on Pixel | App opens, splash shows |
| M2 | QR onboarding | Server + secret auto-filled |
| M3 | Grant all permissions | 11 types show "granted" |
| M4 | Sync after BP reading | BP on web dashboard < 10 sec |
| M5 | Overnight sleep sync | Sleep data on dashboard next morning |
| M6 | Widget shows data | BP + sleep visible on home screen |
| M7 | Biometric lock | Prompt after backgrounding app |
| M8 | Airplane mode queue | Records queue, sync on reconnect |
| M9 | 30-day charts | All chart types render with real data |
| M10 | Dark theme check | All screens dark, no white flash, no purple |

---

## 9. Implementation Phases

### Phase 1: Foundation (Security + Full Sync)
- EncryptedSharedPreferences + biometric lock
- Certificate pinning (ISRG Root X1)
- HMAC request signing
- Event-driven sync (Health Connect change listener)
- All 11 data type readers
- Offline queue (Room)
- Unit tests for sync + security modules

### Phase 2: UI Overhaul
- Dark theme + glass button design system
- Bottom navigation (4 tabs)
- Dashboard with summary cards
- Settings screen
- Animated screen transitions
- Pull-to-refresh + FAB sync
- Splash screen

### Phase 3: Charts & Trends
- BP trend chart (line + anomaly markers)
- Sleep stacked bar chart
- Body composition chart
- Activity charts (steps, calories, distance)
- Time range selectors
- Chart animations
- Workout history list

### Phase 4: Onboarding + Widgets
- QR scanner (CameraX + ML Kit)
- QR generation endpoint (backend)
- 5-step onboarding flow
- Health Glance widget (4x2)
- BP Quick widget (2x1)
- In-app anomaly alert banners

### Phase 5: Testing + Polish
- Instrumented UI tests
- Integration tests
- Manual acceptance tests (M1-M10)
- Animation polish + performance
- App icon (adaptive)
- Final branding pass

---

## 10. Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| Charts | Vico (Compose-native) or MPAndroidChart |
| Animations | Compose Animation APIs |
| Health Data | Health Connect SDK 1.1.0+ |
| Networking | Retrofit 2.9 + OkHttp (cert pinning) |
| Local DB | Room (offline queue) |
| Encryption | EncryptedSharedPreferences + SQLCipher |
| Security | BiometricPrompt API |
| Background | WorkManager + Health Connect change listener |
| Camera | CameraX + ML Kit Barcode |
| DI | Hilt |
| Testing | JUnit 5, MockK, Compose Testing, MockWebServer |
| Widgets | Glance (Compose for widgets) |

---

## 11. Definition of Done

- [ ] All 11 Health Connect data types sync to backend
- [ ] Event-driven sync within 30 seconds of new data
- [ ] Offline queue stores and replays missed syncs
- [ ] Dashboard shows latest readings with animated charts
- [ ] Home screen widget displays current BP + sleep
- [ ] Biometric lock protects app access
- [ ] Certificate pinning on all API calls
- [ ] HMAC signing on all sync requests
- [ ] QR code onboarding works end-to-end
- [ ] All screens dark-themed with glass buttons and animations
- [ ] Unit test coverage > 85% on sync/security modules
- [ ] All 10 manual acceptance tests pass on Pixel phone
- [ ] No purple anywhere in the UI
