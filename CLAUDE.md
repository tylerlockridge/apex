# Apex Android App

## Critical Context
Android app (Kotlin) that syncs health data from Android Health Connect to the
Health Platform Desktop server. Previously lived at `health-platform/android-app/`.

## Tech Stack
Language: Kotlin | Build: Gradle (KTS) | Health Connect API | HTTP: OkHttp/Retrofit

## File Locations
```
Project Root: C:\Users\tyler\Documents\Claude Projects\Apex
Source:       app/src/main/java/com/healthplatform/
Server:       See Health-Platform-Desktop — 165.227.125.102 (tyler-health.duckdns.org)
```

## Quality Gates
```bash
./gradlew test          # unit tests
./gradlew lint          # lint checks
./gradlew assembleDebug # verify build
```

## Secrets Policy
**NEVER** commit: `local.properties`, `keys/` directory (signing keystore), API keys, sync secret tokens.

## Signing Credentials
Not yet set up (no release keystore created yet). When release prep begins:
- Create keystore at `keys/release.keystore` (same password pattern as Inkwell)
- Store `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in `local.properties`
- Add `ANDROID_FINGERPRINT` to the server's `.env` via SSH

## Repo
GitHub: https://github.com/tylerlockridge/apex
