# Apex Android Code Quality Audit (2026-02-28)

This audit reviews architecture, code quality, testing, security, performance, and documentation for the Android app in this repository.

## Scores (1-10)

- Architecture: 5
- Code Quality: 6
- Testing: 1
- Security: 3
- Performance: 5
- Documentation: 4
- Overall: 4

## Key findings

### 1) Architecture
- App structure is organized by feature areas (`data`, `service`, `ui`, `security`, `widget`) and uses `WorkManager`, Retrofit, and Compose.
- Dependency wiring is manual across UI and worker layers (no DI container), creating direct coupling and harder testability.
- All persistence is done through one SharedPreferences file (`health_sync`) accessed from Worker, UI, widget, and security flows.

### 2) Code quality
- Naming is generally readable and Kotlin idioms are used.
- Error handling is broad and conflates failure categories (permission, network, serialization, server errors) into generic retry behavior.
- API response handling uses non-null assertion (`response.body()!!`) after `isSuccessful` without body null safety.

### 3) Testing
- No unit tests or instrumentation tests are present in the repository.
- Gradle test dependencies exist but test source sets/files are absent.

### 4) Security
- API key and state are stored in plaintext SharedPreferences despite having `security-crypto` dependency available.
- Release build has minification disabled (`isMinifyEnabled = false`), increasing reverse-engineering exposure.
- Biometric prompt does not constrain authenticators to strong biometrics and lacks explicit policy hardening.
- Logging level is BASIC (not BODY), which is better than BODY but still logs request/response metadata in all builds.

### 5) Performance
- Health Connect reads are single-shot and do not iterate pagination/pageToken, risking incomplete sync for larger datasets.
- Worker always fetches 30 days every run and performs multiple full reads each cycle.
- Sync API results are not checked in `SyncWorker`, so failed sync calls can still lead to local state updates and success signaling.

### 6) Documentation
- README is detailed but mainly PRD/spec content and includes stale/inconsistent operational details.
- Build prerequisites conflict with module config (README says API 28+, module sets minSdk 34).

## Top 3 improvements

1. **Security hardening of persisted secrets and release config**
   - Migrate API key + sensitive settings from SharedPreferences to EncryptedSharedPreferences/DataStore with encryption.
   - Enable R8/ProGuard minification for release and review rules.
2. **Sync reliability and correctness**
   - In `SyncWorker`, validate each API sync Result and return retry/failure per category; avoid marking success when remote sync fails.
   - Track last successful cursor/time per data type to avoid full 30-day rereads.
3. **Health Connect paging + test baseline**
   - Implement pagination loops for record reads and add unit tests around mapping/error paths.
   - Add integration-style tests for Worker retry behavior and permission denial handling.

## Strengths
- Clear modular package layout and Compose-first UI.
- Uses WorkManager with network constraints and exponential backoff configuration.
- Explicit Health Connect permission set and source selection logic for Oura-first sleep/HRV.

## Risks
- Plaintext secrets and non-minified release increase compromise/reverse-engineering risk.
- Lack of tests means regressions in sync/auth flows are likely to slip to production.
- Pagination omission and broad retry policy can cause silent data loss or repeated unnecessary work.

## Severity summary
- Critical: 1
- High: 3
- Medium: 5
- Low: 4
