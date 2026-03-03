# Feature: Architecture Overview

*Created: 2026-03-02 | Updated: 2026-03-02 | Project: Apex*

---

## Feature Overview

**What it does:**
Apex is a native Android health companion app that reads health data from Android Health Connect, syncs it to the Health-Platform-Desktop server, and displays trends and summaries. It uses Kotlin + Jetpack Compose + WorkManager following the MVVM pattern.

**What it does NOT do:**
- Apex does not store health data locally in a database (no Room)
- Apex does not process or run AI analysis on-device
- Apex does not sync gym/workout data to Health Connect (Hevy integration not implemented)

---

## Package Structure

| Package / File | Purpose |
|----------------|---------|
| `HealthSyncApp.kt` | Application class; provides WorkManager on-demand configuration |
| `Config.kt` | Central constants (server URL, sync interval, timeout values) |
| `data/` | Data models (request/response DTOs, Health Connect data classes) |
| `security/` | `SecurePrefs.kt`, `BiometricLockManager.kt` |
| `service/` | `ApiService.kt`, `ServerApiClient.kt`, `SyncWorker.kt`, `HealthConnectReader.kt` |
| `ui/` | Composable screens (Dashboard, Trends, Activity, Settings, Lock) + ViewModels |
| `widget/` | `HealthGlanceWidget.kt`, `HealthGlanceWidgetReceiver` |

Total source files: 25

---

## Data Flow

```
Android Health Connect
        |
        v
HealthConnectReader.kt  (reads 30-day window, paginates)
        |
        v
SyncWorker.kt  (WorkManager, periodic 15-min + manual)
        |
        v
ApiService.kt  (Retrofit POST /api/sync/health-connect)
        |
        v
Health-Platform-Desktop server  (https://tyler-health.duckdns.org)
```

Inbound read path (UI display):

```
Health-Platform-Desktop server
        |
        v
ServerApiClient.kt  (Retrofit GET /api/bp, /api/sleep, /api/body, /api/workouts)
        |
        v
ViewModels  (StateFlow → Compose UI)
```

---

## Build Configuration

| Setting | Value |
|---------|-------|
| compileSdk | 34 |
| targetSdk | 34 |
| minSdk | 34 |
| JDK | 17 |
| Build system | Gradle Kotlin DSL |
| Minification | Enabled on release (ProGuard) |
| ProGuard scope | Gson data classes preserved |

---

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Compose BOM | 2024.10.01 | Jetpack Compose UI toolkit |
| Navigation-Compose | 2.7.7 | Bottom nav + screen routing |
| Health Connect | 1.1.0-alpha06 | Read health sensor data |
| Retrofit | 2.9 | HTTP client for server API |
| OkHttp | 4.12 | HTTP layer + logging interceptor |
| WorkManager | 2.9.0 | Background periodic sync |
| Glance | 1.1.0 | Home screen widget (Compose) |
| Biometric | 1.2.0-alpha05 | Fingerprint/face auth |

---

## CI/CD

- GitHub Actions pipeline
- JDK 17 runner
- Builds and tests APK on push

---

## Status

| Item | Status | Notes |
|------|--------|-------|
| MVVM architecture | ✅ PASS | ViewModels + StateFlow + Compose |
| Package organization | ✅ PASS | Clean separation across 7 packages |
| Build configuration | ✅ PASS | SDK 34, JDK 17, Kotlin DSL |
| Release minification | ✅ PASS | ProGuard enabled with Gson rules |
| CI/CD | ✅ PASS | GitHub Actions, builds + tests |
| Offline data layer | ❌ FAIL | No Room database; no local persistence |
