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
**NEVER** commit: `local.properties`, API keys, sync secret tokens.

## Repo
GitHub: https://github.com/tylerlockridge/apex
