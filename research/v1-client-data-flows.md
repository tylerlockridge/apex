# R-5 — Current-State Client Data Flows

## Executive Summary
- The client now has a durable outbound sync path: Health Connect reads are staged into a Room queue (`apex.db` / `sync_queue`) and only deleted after successful server acknowledgement. This is materially different from the older project docs that still describe the app as having no Room/offline queue. Evidence: [ApexDatabase.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/ApexDatabase.kt#L8), [SyncQueueEntity.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/SyncQueueEntity.kt#L8), [SyncWorker.kt#L61](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L61), [07-background-sync-and-workers.md#L14](C:/Users/tyler/Documents/Claude%20Projects/Apex/documentation/07-background-sync-and-workers.md#L14), [01-architecture-overview.md#L13](C:/Users/tyler/Documents/Claude%20Projects/Apex/documentation/01-architecture-overview.md#L13)
- Client state is split across four local layers: Room queue for pending outbound records, plain `health_sync` prefs for summary/cache/config/change tokens, encrypted `apex_secure` prefs for secrets, and in-memory `StateFlow`/saved state for screen state. Evidence: [ApexDatabase.kt#L21](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/ApexDatabase.kt#L21), [SyncPrefsKeys.kt#L5](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/SyncPrefsKeys.kt#L5), [SecurePrefs.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/security/SecurePrefs.kt#L8), [DashboardViewModel.kt#L65](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L65), [TrendsViewModel.kt#L99](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L99)
- The app has multiple freshness models, not one unified one: Dashboard/widget/weekly summary read cached prefs, while Trends and Activity fetch live server data and do not persist it locally. Evidence: [DashboardViewModel.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L63), [HealthGlanceWidget.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L37), [WeeklySummaryWorker.kt#L20](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L20), [TrendsViewModel.kt#L125](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L125), [ActivityViewModel.kt#L94](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L94)
- There is no repository layer mediating these flows. `SyncWorker` directly coordinates Health Connect, Room, prefs, and network; screen viewmodels directly instantiate `ServerApiClient`. Evidence: [SyncWorker.kt#L47](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L47), [DashboardViewModel.kt#L101](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L101), [TrendsViewModel.kt#L104](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L104), [ActivityViewModel.kt#L59](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L59)
- The highest planning constraint surfaced by code is the split between durable outbound sync and non-durable inbound reads. Any future architecture has to respect that the app currently protects uploads with a queue, but not trend/workout read models. Evidence: [SyncWorker.kt#L176](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L176), [TrendsViewModel.kt#L135](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L135), [ActivityViewModel.kt#L103](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L103)

## Scope and Method
- Inspected current client code under `app/src/main/java/com/healthplatform/sync`, with emphasis on database classes, sync workers, viewmodels, widget code, onboarding/config flows, and network clients.
- Used code as the primary evidence source. Existing docs were read only to detect drift between docs and code; they were not treated as authoritative when contradicted by source.
- Server-side behavior was only included where the client code makes a boundary explicit through endpoint definitions or request construction. No server repository or runtime trace inspection was performed.
- No DataStore usage was found in a repo-wide search of `app/src/main/java`; local state storage is Room, `SharedPreferences`, encrypted `SharedPreferences`, `SavedStateHandle`, `savedInstanceState`, and in-memory Compose/ViewModel state.
- Blind spots:
  - No runtime observation of WorkManager scheduling, retry timing, or widget refresh behavior.
  - No direct validation of server semantics behind the endpoints named by the client.
  - No inspection of server-side Hevy sync implementation beyond client call sites.

## System Inventory
### Persistence
| Store | Type | What is persisted | Writers | Readers | Notes | Evidence |
|---|---|---|---|---|---|---|
| `apex.db` / `sync_queue` | Room | Pending outbound health records with `dataType`, `measuredAt`, serialized `payload`, `recordHash`, `createdAt` | `SyncWorker.doWork()` inserts queue entities after Health Connect reads; Settings clear-data path deletes all | `SyncWorker.doWork()` / `flushDataType()` read, batch, delete, count, and evict | Only Room entity/DAO found in the inspected tree; DB is version 1 and uses destructive migration because queue data is treated as disposable retry state | [ApexDatabase.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/ApexDatabase.kt#L8), [SyncQueueEntity.kt#L22](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/SyncQueueEntity.kt#L22), [SyncQueueDao.kt#L9](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/SyncQueueDao.kt#L9), [SyncWorker.kt#L97](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L97), [SyncWorker.kt#L344](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L344), [SettingsScreen.kt#L592](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L592) |

### Preferences / Local State
| Store | Exact keys / state | Owners / writers | Readers / consumers | Notes | Evidence |
|---|---|---|---|---|---|
| Plain `SharedPreferences` file `health_sync` | Metric snapshot keys: `last_bp_systolic`, `last_bp_diastolic`, `last_bp_time`, `last_sleep_duration_min`, `last_sleep_deep_min`, `last_sleep_rem_min`, `last_sleep_time`, `last_weight_kg`, `last_weight_time`, `last_hrv_ms`, `last_hrv_time` | `SyncWorker` writes snapshots after successful per-type uploads | `DashboardViewModel`, `HealthGlanceWidget`, `WeeklySummaryWorker`, `SettingsScreen` | This is the lightweight client read model for the dashboard/widget/weekly summary. Body snapshot stores weight only; body-fat and lean-mass are not cached here. | [SyncPrefsKeys.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/SyncPrefsKeys.kt#L8), [SyncWorker.kt#L198](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L198), [SyncWorker.kt#L223](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L223), [SyncWorker.kt#L241](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L241), [SyncWorker.kt#L261](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L261), [DashboardViewModel.kt#L67](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L67), [HealthGlanceWidget.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L37), [WeeklySummaryWorker.kt#L21](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L21) |
| Plain `SharedPreferences` file `health_sync` | Sync/control keys: `last_sync`, `last_bp_alert_time`, `auto_sync`, `sync_history`, `change_token_bp`, `change_token_sleep`, `change_token_hrv` | `SyncWorker` updates `last_sync`, alert dedupe, history, and change tokens; `SettingsScreen` writes `auto_sync` | `DashboardViewModel`, `SettingsScreen`, `WeeklySummaryWorker`, `HealthGlanceWidget`, `SyncWorker` | `sync_history` stores the last 10 sync outcomes as JSON; `last_sync` advances only on runs with no failures. | [SyncPrefsKeys.kt#L23](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/SyncPrefsKeys.kt#L23), [SyncWorker.kt#L81](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L81), [SyncWorker.kt#L204](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L204), [SyncWorker.kt#L271](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L271), [SyncWorker.kt#L407](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L407), [SettingsScreen.kt#L177](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L177) |
| Plain `SharedPreferences` file `health_sync` | Config key: `server_url` | QR onboarding persists it via `MainActivity` | `Config.getServerUrl()`; anything that constructs `ApiService` or `ServerApiClient`; `SettingsViewModel` ping path | Server URL is stored in plain prefs, not encrypted. | [SyncPrefsKeys.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/SyncPrefsKeys.kt#L37), [MainActivity.kt#L344](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L344), [Config.kt#L26](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/Config.kt#L26) |
| Encrypted `SharedPreferences` file `apex_secure` | `api_key`, `biometric_enabled`, `device_secret` | `MainActivity` QR flow writes API key and device secret; `MainActivity` may auto-seed API key from `BuildConfig`; `BiometricLockManager` writes biometric flag; `SettingsScreen` writes API key; clear-data wipes it | `SyncWorker`, `DashboardViewModel`, `TrendsViewModel`, `ActivityViewModel`, `SettingsViewModel`, `BiometricLockManager`, `MainActivity` | One-time migration moves legacy plain `api_key` and `biometric_enabled` into encrypted storage. | [SecurePrefs.kt#L10](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/security/SecurePrefs.kt#L10), [SecurePrefs.kt#L41](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/security/SecurePrefs.kt#L41), [MainActivity.kt#L110](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L110), [MainActivity.kt#L344](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L344), [DashboardViewModel.kt#L104](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L104), [TrendsViewModel.kt#L84](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L84), [ActivityViewModel.kt#L59](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L59), [SettingsViewModel.kt#L121](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsViewModel.kt#L121), [SettingsScreen.kt#L598](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L598) |
| Other local state | `SavedStateHandle["selected_tab"]`, `savedInstanceState` auth flag, per-screen `MutableStateFlow`/Compose state | `TrendsViewModel` writes `selected_tab`; `MainActivity` persists auth state to `savedInstanceState`; all viewmodels manage in-memory UI state | Same component on restore / UI recompose | This state affects UX continuity, not durable data sync. | [TrendsViewModel.kt#L99](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L99), [TrendsViewModel.kt#L110](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L110), [MainActivity.kt#L105](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L105), [DashboardViewModel.kt#L65](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L65), [ActivityViewModel.kt#L65](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L65), [SettingsViewModel.kt#L44](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsViewModel.kt#L44) |

### Background Work
| Component | Unique work name | Trigger | Cadence / constraints | Side effects | Evidence |
|---|---|---|---|---|---|
| `SyncWorker` periodic sync | `health_sync_periodic` | Settings auto-sync toggle ON | Every 15 minutes, `NetworkType.CONNECTED`, exponential backoff from 1 minute, `ExistingPeriodicWorkPolicy.KEEP` | Reads Health Connect, writes queue, flushes queue to server, updates summary prefs/history, updates widget if pinned | [SyncWorker.kt#L429](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L429), [SyncWorker.kt#L442](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L442), [SettingsScreen.kt#L177](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L177) |
| `SyncWorker` one-time full sync | `health_sync_once` | Dashboard sync button, Settings “Sync All Now”, code-driven one-shot syncs | One-time, `NetworkType.CONNECTED`, unique work `KEEP` | Same as periodic sync, but caller may or may not observe completion | [SyncWorker.kt#L463](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L463), [DashboardViewModel.kt#L215](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L215), [SettingsScreen.kt#L257](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L257) |
| `SyncWorker` filtered one-time sync | `sync_once_<dataType>` | Dashboard BP/Sleep quick actions (and code can filter body/HRV) | One-time, `NetworkType.CONNECTED`, unique per data type | Syncs one data type only | [SyncWorker.kt#L460](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L460), [DashboardViewModel.kt#L242](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L242), [DashboardScreen.kt#L198](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardScreen.kt#L198) |
| `WeeklySummaryWorker` | `weekly_health_summary` | App startup schedules it; clear-data cancels it | Every 7 days, initial delay to next Sunday 9:00 AM, no network constraint, `ExistingPeriodicWorkPolicy.KEEP` | Reads summary prefs and posts local notification if there has been at least one sync | [HealthSyncApp.kt#L7](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/HealthSyncApp.kt#L7), [WeeklySummaryWorker.kt#L20](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L20), [WeeklySummaryWorker.kt#L82](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L82), [SettingsScreen.kt#L592](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L592) |

### Network / Sync Touchpoints
| Component | Endpoints / role | Auth / transport notes | Evidence |
|---|---|---|---|
| `ApiService` | Outbound health upload: `POST api/sync/health-connect` | Bearer token, HMAC `X-Signature` + `X-Timestamp`, certificate pinning, 15s connect / 30s read timeout | [ApiService.kt#L24](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ApiService.kt#L24), [ApiService.kt#L80](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ApiService.kt#L80), [ApiService.kt#L97](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ApiService.kt#L97), [ApiService.kt#L128](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ApiService.kt#L128) |
| `ServerApiClient` | Inbound reads: `/api/bp`, `/api/sleep`, `/api/body`, `/api/workouts`, `/api/workouts/stats/summary`, `/api/hrv/recent`, `/api/version`; Hevy trigger: `POST /api/sync/hevy/workouts` | Bearer token, optional HMAC signing for GET/POST, shared OkHttp client per host with certificate pinning | [ServerApiClient.kt#L141](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L141), [ServerApiClient.kt#L152](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L152), [ServerApiClient.kt#L222](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L222), [ServerApiClient.kt#L246](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L246), [ServerApiClient.kt#L294](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L294), [ServerApiClient.kt#L303](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L303) |
| `SettingsViewModel` ping path | Direct `GET /api/version` connectivity/version check | Uses its own `OkHttpClient`, not `ServerApiClient`; bearer auth only in the inspected code path | [SettingsViewModel.kt#L77](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsViewModel.kt#L77), [SettingsViewModel.kt#L119](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsViewModel.kt#L119) |

### Key Flow-Orchestrating Components
| Component | Role in current flow graph | Evidence |
|---|---|---|
| `HealthSyncApp` | App-level bootstrap for notification channels and weekly summary scheduling; also provides WorkManager configuration | [HealthSyncApp.kt#L7](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/HealthSyncApp.kt#L7) |
| `HealthConnectReader` | Reads BP, sleep, body, and HRV from Health Connect; owns change-token reads and Oura-preference filters | [HealthConnectReader.kt#L44](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L44), [HealthConnectReader.kt#L69](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L69), [HealthConnectReader.kt#L116](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L116), [HealthConnectReader.kt#L181](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L181), [HealthConnectReader.kt#L222](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L222) |
| `SyncWorker` | Main sync orchestrator across Health Connect, Room queue, server upload, summary cache update, alerting, and widget refresh | [SyncWorker.kt#L39](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L39), [SyncWorker.kt#L332](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L332) |
| `DashboardViewModel` | Loads cached summary state, computes readiness locally, fetches most recent workout from server, and observes one-time sync completion | [DashboardViewModel.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L63), [DashboardViewModel.kt#L138](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L138), [DashboardViewModel.kt#L162](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L162), [DashboardViewModel.kt#L215](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L215) |
| `TrendsViewModel` | Direct server-read screen for BP, sleep, body, and HRV history; cancels stale fetch jobs and persists selected tab only | [TrendsViewModel.kt#L99](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L99), [TrendsViewModel.kt#L106](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L106), [TrendsViewModel.kt#L125](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L125), [TrendsViewModel.kt#L222](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L222) |
| `ActivityViewModel` | Direct server-read screen for workouts/stats plus direct Hevy sync trigger | [ActivityViewModel.kt#L70](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L70), [ActivityViewModel.kt#L78](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L78), [ActivityViewModel.kt#L103](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L103) |
| `SettingsViewModel` / `SettingsScreen` | Permission/version checks, auto-sync control, API-key management, sync-history display, and destructive clear-data flow | [SettingsViewModel.kt#L77](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsViewModel.kt#L77), [SettingsViewModel.kt#L119](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsViewModel.kt#L119), [SettingsScreen.kt#L177](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L177), [SettingsScreen.kt#L257](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L257), [SettingsScreen.kt#L592](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L592) |
| `MainActivity` / `QrScanScreen` | Authentication gate persistence, API-key auto-seed, QR onboarding persistence of `server_url`, `api_key`, and `device_secret` | [MainActivity.kt#L105](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L105), [MainActivity.kt#L110](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L110), [MainActivity.kt#L344](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L344), [QrScanScreen.kt#L46](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/QrScanScreen.kt#L46), [QrScanScreen.kt#L97](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/QrScanScreen.kt#L97) |
| `HealthGlanceWidget` / `WeeklySummaryWorker` | Secondary consumers of the summary cache and `last_sync` state | [HealthGlanceWidget.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L37), [WeeklySummaryWorker.kt#L20](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L20) |

## End-to-End Data Flows
### Flow 1 — Health Connect -> Room queue -> server sync -> summary cache
- Flow name: Outbound health sync
- Trigger:
  - Periodic auto-sync when `auto_sync` is enabled.
  - Manual dashboard sync.
  - Manual settings sync.
  - Filtered BP/Sleep one-time sync from dashboard quick actions.
- Components involved: `SettingsScreen`/`DashboardViewModel` -> WorkManager -> `SyncWorker` -> `HealthConnectReader` -> `ApexDatabase.syncQueueDao()` -> `ApiService` -> `health_sync` prefs -> widget/notification side effects.
- Source of truth:
  - Health Connect for raw device records.
  - `sync_queue` for pending outbound records that have not been acknowledged by the server.
- Storage touchpoints:
  - Reads and updates `change_token_bp`, `change_token_sleep`, `change_token_hrv`.
  - Writes queue rows after HC reads.
  - Deletes queue rows only after successful upload.
  - Updates summary snapshot keys plus `last_sync`, `last_bp_alert_time`, and `sync_history`.
- Network touchpoints: `ApiService` posts batches to `POST api/sync/health-connect`.
- Worker / scheduler involvement:
  - Periodic `health_sync_periodic`.
  - One-time `health_sync_once` or `sync_once_<dataType>`.
- Freshness / invalidation behavior:
  - BP, sleep, and HRV use change tokens for incremental reads.
  - Body measurements always do a full 30-day read and rely on queue dedupe.
  - Expired/invalid change tokens are cleared and the same run falls back to a full 30-day read.
  - Queue is capped at 5,000 rows and uploads in batches of 200.
  - `last_sync` advances only when the full run has no failures.
- Failure or ambiguity notes:
  - HC read failures do not stop queue flush; previously queued records still attempt upload.
  - Malformed queued payloads are dropped locally.
  - Transient failures keep records in queue and return `Result.retry()`.
  - Permanent failures return `Result.failure()`.
  - Dashboard manual sync explicitly guards against overlapping the periodic worker; Settings “Sync All Now” does not show the same guard/observation path in the inspected code.
- Code citations: [SyncWorker.kt#L39](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L39), [SyncWorker.kt#L79](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L79), [SyncWorker.kt#L169](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L169), [SyncWorker.kt#L192](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L192), [SyncWorker.kt#L271](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L271), [SyncWorker.kt#L332](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L332), [SyncWorker.kt#L423](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L423), [HealthConnectReader.kt#L44](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L44), [HealthConnectReader.kt#L222](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/HealthConnectReader.kt#L222), [SettingsScreen.kt#L177](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L177), [DashboardViewModel.kt#L215](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L215)

### Flow 2 — Dashboard summary and readiness
- Flow name: Dashboard cached summary path
- Trigger:
  - `DashboardViewModel` init.
  - Dashboard pull-to-refresh.
  - Dashboard sync completion observer.
- Components involved: `DashboardViewModel` -> `health_sync` prefs -> local readiness calculation -> optional `ServerApiClient.getWorkouts(limit = 1)`.
- Source of truth:
  - Cached health metric snapshots in plain prefs for BP/sleep/body/HRV summary values.
  - Server response for the “recent workout” card.
- Storage touchpoints: Reads metric snapshot keys and `last_sync` from `health_sync`; no local write path in this viewmodel.
- Network touchpoints: Direct server read for the recent workout only.
- Worker / scheduler involvement:
  - None for read-only refresh.
  - Manual sync path delegates to `SyncWorker.runOnce()` and then waits for completion before reloading cached prefs.
- Freshness / invalidation behavior:
  - Readiness is computed locally from the cached BP/sleep/HRV snapshot; no server call is involved.
  - Pull-to-refresh calls `loadFromPrefs()`, which reloads cached metrics and also refetches the most recent workout, but does not enqueue a Health Connect sync.
  - If the server workout fetch fails, the cached metric summary still loads and the workout card quietly remains unchanged.
- Failure or ambiguity notes:
  - Dashboard freshness can diverge internally: health metrics come from local cache, while recent workout comes live from server.
  - `Inference`: the dashboard is operating as a mixed-source screen rather than a single-source read model.
- Code citations: [DashboardViewModel.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L63), [DashboardViewModel.kt#L110](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L110), [DashboardViewModel.kt#L138](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L138), [DashboardViewModel.kt#L215](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L215), [DashboardScreen.kt#L89](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardScreen.kt#L89), [DashboardScreen.kt#L95](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardScreen.kt#L95)

### Flow 3 — Trends history reads
- Flow name: Trends live server-read flow
- Trigger:
  - `TrendsViewModel` init.
  - Tab change.
  - Range change.
  - Manual refresh.
- Components involved: `TrendsViewModel` -> `ServerApiClient` -> in-memory `TrendsState`.
- Source of truth: Server responses for BP, sleep, body, and HRV history; there is no inspected local persisted cache for these lists.
- Storage touchpoints:
  - `SavedStateHandle["selected_tab"]` persists the selected tab across process death/navigation.
  - Selected range and fetched datasets remain in memory only.
- Network touchpoints: `getBloodPressure`, `getSleep`, `getBodyMeasurements`, `getHrv`.
- Worker / scheduler involvement: None.
- Freshness / invalidation behavior:
  - Each tab/range load cancels the previous `fetchJob`.
  - Data is refetched on every tab/range change and on explicit refresh.
- Failure or ambiguity notes:
  - No offline cache or persisted retry path is visible for trend data.
  - `Inference`: trends are as fresh as the server copy, not as fresh as local Health Connect state.
- Code citations: [TrendsViewModel.kt#L99](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L99), [TrendsViewModel.kt#L106](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L106), [TrendsViewModel.kt#L121](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L121), [TrendsViewModel.kt#L135](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L135), [TrendsViewModel.kt#L153](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L153), [TrendsViewModel.kt#L180](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L180), [TrendsViewModel.kt#L204](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L204), [TrendsViewModel.kt#L222](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L222)

### Flow 4 — Activity screen and Hevy sync trigger
- Flow name: Workout/activity live-read flow
- Trigger:
  - `ActivityViewModel` init.
  - Manual refresh.
  - Manual Hevy sync action.
- Components involved: `ActivityViewModel` -> `ServerApiClient` -> in-memory `ActivityState`.
- Source of truth: Server responses for workouts and workout stats; Hevy sync is triggered through the server boundary.
- Storage touchpoints: No inspected local persistence for workouts or workout stats beyond in-memory `StateFlow`.
- Network touchpoints:
  - `GET /api/workouts`
  - `GET /api/workouts/stats/summary`
  - `POST /api/sync/hevy/workouts`
- Worker / scheduler involvement: None. Hevy sync is not run through WorkManager in the inspected code.
- Freshness / invalidation behavior:
  - Screen init calls `loadAll()`.
  - Successful Hevy sync immediately calls `loadAll()` again.
- Failure or ambiguity notes:
  - If workouts/stats fetch fails, there is no local fallback.
  - `Inference`: Hevy-related data freshness is entirely server-mediated in the current client.
- Code citations: [ActivityViewModel.kt#L70](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L70), [ActivityViewModel.kt#L78](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L78), [ActivityViewModel.kt#L94](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L94), [ActivityViewModel.kt#L103](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L103), [ActivityViewModel.kt#L131](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L131), [ServerApiClient.kt#L270](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L270), [ServerApiClient.kt#L278](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L278), [ServerApiClient.kt#L294](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/ServerApiClient.kt#L294)

### Flow 5 — Summary-cache consumers: widget and weekly summary
- Flow name: Secondary cached-summary consumption
- Trigger:
  - Widget refresh after sync if any widget is pinned.
  - Weekly summary periodic run.
- Components involved: `SyncWorker` -> `HealthGlanceWidget`; `HealthSyncApp` -> `WeeklySummaryWorker` -> notification manager.
- Source of truth: `health_sync` snapshot keys and `last_sync`.
- Storage touchpoints: Both consumers read from plain prefs only.
- Network touchpoints: None in the inspected code.
- Worker / scheduler involvement:
  - Widget refresh is called from `SyncWorker` after a sync run and only when pinned widget IDs exist.
  - `WeeklySummaryWorker` is always scheduled from app startup and cancelled only by the explicit clear-data flow.
- Freshness / invalidation behavior:
  - Weekly summary exits early if `last_sync` is zero.
  - Widget shows last-sync label derived from cached `last_sync`.
  - Widget masks raw values when the device is locked.
- Failure or ambiguity notes:
  - Weekly summary scheduling is independent of the settings auto-sync toggle once the app has scheduled it.
  - `Inference`: widget and weekly summary can remain stale if server reads advance elsewhere but summary prefs are not refreshed by outbound sync.
- Code citations: [HealthSyncApp.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/HealthSyncApp.kt#L8), [SyncWorker.kt#L276](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L276), [WeeklySummaryWorker.kt#L20](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L20), [WeeklySummaryWorker.kt#L84](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L84), [HealthGlanceWidget.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L37), [HealthGlanceWidget.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L63)

### Flow 6 — Configuration, credentials, and local teardown
- Flow name: Client configuration and reset
- Trigger:
  - QR onboarding.
  - Initial app launch with baked-in API key.
  - Settings save/clear actions.
- Components involved: `QrScanScreen` -> `MainActivity`; `SettingsScreen`; `SecurePrefs`; plain prefs; WorkManager; `ApexDatabase`.
- Source of truth:
  - QR payload for `serverUrl`, `apiKey`, `deviceSecret`.
  - `BuildConfig.API_KEY` fallback on first install if secure prefs are blank.
- Storage touchpoints:
  - QR onboarding writes `server_url` to plain prefs and API key/device secret to secure prefs.
  - Biometric enabled flag lives in secure prefs.
  - Clear-data cancels work, deletes Room queue, clears plain prefs, and clears encrypted prefs.
- Network touchpoints: None during scan/save/clear; subsequent clients consume the updated config.
- Worker / scheduler involvement:
  - Clear-data cancels periodic sync and weekly summary before wiping storage.
- Freshness / invalidation behavior:
  - `Config.getServerUrl()` always reads the current plain pref value.
  - New `ServerApiClient` instances use the current config when constructed.
- Failure or ambiguity notes:
  - Server URL is stored in plain prefs while API key and device secret are stored encrypted.
  - Queue deletion on clear-data is dispatched on `Dispatchers.IO`; cancellation happens first to reduce risk of stale uploads.
- Code citations: [QrScanScreen.kt#L46](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/QrScanScreen.kt#L46), [QrScanScreen.kt#L97](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/QrScanScreen.kt#L97), [MainActivity.kt#L105](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L105), [MainActivity.kt#L110](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L110), [MainActivity.kt#L344](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/MainActivity.kt#L344), [Config.kt#L26](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/Config.kt#L26), [SecurePrefs.kt#L74](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/security/SecurePrefs.kt#L74), [SettingsScreen.kt#L587](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L587)

## Freshness, Sync, and Invalidation Rules
- Explicit rules confirmed from code:
  - Auto-sync is opt-in through `auto_sync`; enabling it schedules `health_sync_periodic`, disabling it cancels that unique periodic work. Evidence: [SettingsScreen.kt#L177](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L177), [SyncWorker.kt#L442](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L442), [SyncWorker.kt#L488](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L488)
  - BP, sleep, and HRV use change tokens for incremental HC reads; body does not. Evidence: [SyncWorker.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L63), [SyncWorker.kt#L79](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L79), [SyncWorker.kt#L105](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L105), [SyncWorker.kt#L129](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L129), [SyncWorker.kt#L141](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L141)
  - Change-token expiry/invalidity clears the stored token and immediately falls back to a full 30-day read in the same run. Evidence: [SyncWorker.kt#L90](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L90), [SyncWorker.kt#L116](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L116), [SyncWorker.kt#L152](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L152)
  - The queue is durable across app restarts, capped at 5,000 rows, and flushed in 200-record batches. Evidence: [ApexDatabase.kt#L21](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/ApexDatabase.kt#L21), [SyncWorker.kt#L169](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L169), [SyncWorker.kt#L423](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L423), [SyncWorker.kt#L344](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L344)
  - Queue rows are only deleted after successful upload; transient failures leave them queued, malformed rows are dropped, permanent failures end the worker with `Result.failure()`. Evidence: [SyncQueueEntity.kt#L19](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/SyncQueueEntity.kt#L19), [SyncWorker.kt#L183](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L183), [SyncWorker.kt#L347](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L347), [SyncWorker.kt#L362](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L362), [SyncWorker.kt#L285](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L285)
  - `last_sync` advances only when the overall run has no failures; `sync_history` records success/failure for every run. Evidence: [SyncWorker.kt#L271](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L271), [SyncWorker.kt#L407](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L407)
  - Dashboard manual sync waits for the one-time worker to finish before reloading cached state and skips if the periodic worker is already running. Evidence: [DashboardViewModel.kt#L215](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L215)
  - Settings “Sync All Now” starts a one-time sync and shows a snackbar, but the inspected settings code does not observe completion before updating UI state. Evidence: [SettingsScreen.kt#L257](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/SettingsScreen.kt#L257)
  - Weekly summary runs on a 7-day cadence anchored to next Sunday 9:00 AM, reads cached summary values only, and exits early if the app has never synced. Evidence: [HealthSyncApp.kt#L8](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/HealthSyncApp.kt#L8), [WeeklySummaryWorker.kt#L23](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L23), [WeeklySummaryWorker.kt#L84](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L84)
  - Widget refresh occurs only after sync and only when a widget is pinned; the widget itself reads cached prefs and masks values on the lock screen. Evidence: [SyncWorker.kt#L277](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L277), [HealthGlanceWidget.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L37), [HealthGlanceWidget.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L63)
- Implicit / likely rules (`Inference`):
  - `Inference`: summary keys can become newer than `last_sync` when one data type uploads successfully but another fails in the same run, because per-type summary writes happen before the aggregate `last_sync` write gate. Basis: [SyncWorker.kt#L192](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L192), [SyncWorker.kt#L271](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L271)
  - `Inference`: trends/activity can be fresher than dashboard/widget because they fetch live server data while dashboard/widget depend on local cached summary prefs. Basis: [DashboardViewModel.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L63), [TrendsViewModel.kt#L125](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L125), [ActivityViewModel.kt#L103](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L103)
  - `Inference`: the plain `health_sync` file is functioning as a deliberately small client-side read model, not just incidental settings storage, because three separate consumers depend on it for user-visible data. Basis: [DashboardViewModel.kt#L63](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/DashboardViewModel.kt#L63), [WeeklySummaryWorker.kt#L21](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/WeeklySummaryWorker.kt#L21), [HealthGlanceWidget.kt#L37](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/widget/HealthGlanceWidget.kt#L37)
- Unresolved gaps:
  - No explicit TTL or staleness timestamp exists for the per-metric snapshot keys beyond `last_sync`.
  - No inspected code explains whether the settings sync button intentionally omits the periodic-running guard and completion observation used by the dashboard.
  - No client-side persisted audit trail exists for per-type sync outcomes beyond `sync_history` boolean/time entries.

## Architecture Implications
- Current constraints the architecture must respect:
  - The client already has a durable outbound queue. Planning that assumes fire-and-forget outbound sync would be out of date. Evidence: [ApexDatabase.kt#L21](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/data/db/ApexDatabase.kt#L21), [SyncWorker.kt#L176](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L176)
  - The client does not have a comparable durable inbound read model for trends/workouts. Those paths are server-live and in-memory only. Evidence: [TrendsViewModel.kt#L125](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/TrendsViewModel.kt#L125), [ActivityViewModel.kt#L103](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/ui/ActivityViewModel.kt#L103)
- Areas of tight coupling:
  - `SyncWorker` directly owns HC reads, queue writes, queue flush, prefs updates, alerting, and widget refresh.
  - Screen viewmodels directly construct `ServerApiClient` instead of going through a shared repository/service abstraction.
  - `SettingsViewModel` uses a separate networking path for `/api/version`, which means connectivity/version checks are not routed through the same client abstraction as other server reads.
- Data ownership concerns:
  - Source ownership is split by feature: Health Connect is upstream truth for raw health inputs, `sync_queue` is truth for pending outbound writes, plain prefs are truth for dashboard/widget summary state, and server responses are truth for trends/workouts screens.
  - Server URL is kept in plain prefs while API key/device secret are encrypted, so configuration ownership is split across security boundaries.
  - Body composition is reduced in the summary cache to weight only; body-fat and lean-mass remain available only through Health Connect read/queue payloads or server history paths. Evidence: [SyncWorker.kt#L236](C:/Users/tyler/Documents/Claude%20Projects/Apex/app/src/main/java/com/healthplatform/sync/service/SyncWorker.kt#L236)
- Offline / cache / sync implications:
  - Outbound capture is resilient to transient server/network failures because the queue survives until ack.
  - Inbound screen reads are not resilient in the same way; no local persisted cache was found for workouts or trends.
  - Weekly summary and widget are cache consumers, so their freshness depends on the summary-pref update path, not on direct server reads.
- Observability / reliability concerns visible from code:
  - The only persisted sync outcome history in client storage is the rolling `sync_history` JSON with time + boolean success.
  - Existing Apex docs still say “no Room / no offline queue,” so architecture planning must treat current code as authoritative until documentation is reconciled. Evidence: [03-data-sync-protocol.md#L15](C:/Users/tyler/Documents/Claude%20Projects/Apex/documentation/03-data-sync-protocol.md#L15), [07-background-sync-and-workers.md#L14](C:/Users/tyler/Documents/Claude%20Projects/Apex/documentation/07-background-sync-and-workers.md#L14), [01-architecture-overview.md#L13](C:/Users/tyler/Documents/Claude%20Projects/Apex/documentation/01-architecture-overview.md#L13)

## Open Questions
- Is the divergence between dashboard manual sync orchestration and settings manual sync orchestration intentional, or just UI inconsistency?
- Should the planning work treat `health_sync` as an explicit client read model going forward, or as a convenience cache that may be replaced?
- Is body-composition summary intentionally weight-only in prefs, or is omission of body-fat/lean-mass from the snapshot layer an accidental gap?
- Are the existing architecture docs expected to be authoritative for future sessions, or should planning now assume code-first truth until those docs are corrected?

## Confidence Assessment
| Topic | Confidence | Basis | Notes |
|---|---|---|---|
| Room / queue inventory | High | Direct inspection of the only `@Database`, `@Entity`, and `@Dao` definitions plus their call sites | No other Room persistence found in `app/src/main/java` |
| Plain vs secure preference inventory | High | Direct key definitions and read/write call sites | Exact key strings are explicit in code |
| WorkManager / scheduler inventory | High | Direct inspection of `SyncWorker`, `WeeklySummaryWorker`, `HealthSyncApp`, and settings scheduling paths | No other scheduler code was found in inspected client sources |
| Dashboard data-source split | High | Direct inspection of `loadFromPrefs()`, `loadRecentWorkout()`, and `triggerSync()` | Mixed local-cache plus live-server model is explicit |
| Trends / Activity live-read model | High | Direct inspection of viewmodel fetch paths and absence of persisted local stores for those data sets | No local cache found for workouts/trend lists |
| Freshness semantics (`last_sync`, tokens, queue retention) | High | Explicit branch logic in `SyncWorker` | Aggregate freshness is clear; user-visible implications still need product judgment |
| Architecture implications | Medium | Derived from direct code evidence across multiple layers | These are synthesis statements, not explicit code comments |
| Documentation drift claim | High | Direct contradiction between inspected code and current documentation files | This affects planning continuity, not runtime behavior |

## Proposed Codex Audit Prompt
```text
Audit C:\Users\tyler\Documents\Claude Projects\Apex\research\v1-client-data-flows.md against the current Apex client codebase.

Review for:
- unsupported claims
- missing major flow coverage
- ambiguity or mixed fact/inference
- architecture relevance
- scope drift beyond current-state client behavior

Verify specifically:
1. Room / sync queue inventory and whether any local persistence was missed
2. Plain prefs vs encrypted prefs inventory and exact ownership/read paths
3. WorkManager inventory, scheduling semantics, and manual-sync differences
4. Health Connect -> queue -> server -> summary cache flow details
5. Dashboard vs Trends vs Activity data-source and freshness split
6. Widget / weekly-summary consumption of cached state
7. QR config / secure storage / clear-data teardown flow
8. The claim that current docs are stale relative to code

Constraints:
- findings first, ordered by severity
- use file references for every finding
- flag any inference presented as fact
- do not propose implementation changes
- keep the review focused on planning relevance
```

## User Handoff
- Status: `R-5` complete. Artifact is ready for Codex audit.
- What changed:
  - Mapped the current client into one evidence-backed baseline covering Room, prefs, workers, network touchpoints, end-to-end flows, and freshness rules.
  - Captured the main planning constraint: durable outbound sync exists, but inbound read paths remain non-durable/live-server.
  - Flagged documentation drift where current docs still describe “no Room / no offline queue.”
- What the human should do next:
  - Update `COORDINATION-PROTOCOL.md` so `R-5` moves from `queued` to `complete` and set `Active Handoff` to Codex audit.
  - Paste the `Proposed Codex Audit Prompt` into Codex.
- Which tool to use next: Codex
- What success looks like: Codex confirms this artifact covers the real client data flows, calls out any unsupported or missing claims, and either accepts it or requests one bounded rework pass.
