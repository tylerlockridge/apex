# Apex Android App

## Workflow Contract
This repo inherits the workspace and CIC workflow rules. Those remain the main
source of truth. This file is the local reinforcement layer for this repo.

### Default Mode
- Assume handoff mode unless Tyler explicitly authorizes direct Codex execution in
  this repo.
- Claude is the default execution agent for broad research, implementation, and
  multi-step investigation.

### Research Requests Stay In Handoff Mode
These stay in handoff mode unless Tyler explicitly authorizes direct Codex research:
- `look into options`
- `research this`
- `conduct research`
- `compare tools`
- `compare vendors`
- `find the best fit`

Phrases like `then we will look into options` are not permission for Codex to do the
research itself.
Codex sends a bounded Claude Code research slice instead of doing that research locally.
Normal chat phrasing like `I want you to research X`, `research X, Y, Z`, or `can you research X` is still a handoff request, not permission for Codex to do the research locally. Direct Codex research requires explicit wording such as `Codex, research this yourself`, `conduct the research yourself`, or `do not send it to Claude Code`.

### Direct Execution Allowed Only When Explicit
Codex may execute directly only when:
- Tyler explicitly says Codex should research, edit, or implement locally in this repo
- the task is a narrow local verification or correction clearly assigned to Codex

### Shared CIC Tools Stay Available
- Designated CIC-built or CIC-governed shared tools, execution lanes, and MCP
  surfaces may be used for one bounded slice against this repo without a second
  repo-local permission round.
- Examples when relevant include the CIC operator/orchestrator, reusable
  validators, NotebookLM lanes, the Evidence-First audit runtime, OpenSpace, and
  shared structural tools such as `code-graph-mcp`.
- This does not authorize open-ended direct Codex execution in this repo or
  unrelated repo mutation. Keep the slice bounded and keep the target repo
  explicit.

### Explanation Default
- Default to ELI5 explanations: start with the bottom line, use plain language, include a short plain-language explanation of reasoning every time without exposing hidden chain-of-thought, and keep simple answers concise.

### Local Machine Baseline
- For Tyler's local machine, use a convenience-first full-access baseline unless he
  explicitly asks for tighter restrictions.

### Lessons Learned
- When Tyler explicitly says `update lessons learned`, `record this as a lesson
  learned`, or equivalent, treat that as a real write instruction before ending the
  pass. Do not assume lessons only exist in workflow-issue sessions; do a short
  discovery scan for what worked well in planning/approach, what worked well in
  tool/lane choice, what did not work well or caused friction, and what
  validation/recovery signal helped or was missing before deciding there are no
  reusable lessons.

### Completion Language
- Do not say `Current task complete.` unless the whole requested pass is finished.
- Never say it and then list ordinary remaining tasks from the same pass.

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
