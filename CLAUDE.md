# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build & Run

```bash
# Start Metro bundler
npm start

# Debug build (JS bundle served by Metro)
npm run android

# Bundled debug APK (self-contained, no Metro needed)
npm run android:bundled-debug
# equivalent: cd android && gradlew.bat :app:installBundledDebug

# Release APK
npm run build:android
# equivalent: cd android && gradlew.bat assembleRelease

# Install release APK to connected device
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

**Signing:** Release builds require `ironlogdb-v111.keystore` (matches the signer on the installed APK).

## Pre-release QA Scripts

```bash
npm run qa:rc          # runs all four checks below in sequence
npm run validate:plans # validate program template JSON
npm run qa:notifications
npm run qa:migration   # migration contract checks
npm run qa:legacy-compat
```

There are no automated unit/integration tests — QA is script-based and manual device testing.

---

## Architecture

### Data Layer: WatermelonDB (primary) + AsyncStorage (legacy remnants)

All persistent app data lives in WatermelonDB (`@nozbe/watermelondb`), an offline-first SQLite ORM with reactive observables. The SQLite file is named `ironlog_watermelon`.

**WM tables / models** (`src/db/models/`):  
`Exercise`, `ExerciseMuscle`, `Plan`, `PlanDay`, `PlanExercise`, `Workout`, `WorkoutExercise`, `WorkoutSet`, `BodyMeasurement`, `AppSetting`, `ProgressPhoto`

> `SmokeTest.js` exists in models/ but is **not registered** in `modelClasses` — it is dead code.

**Repositories** (`src/db/repositories/`) are the only layer that touches the database directly. Screens and hooks must go through repositories, never import `database` directly.

**AsyncStorage** is still used for: backup config/status, notification settings, and active workout sessions (via `app_settings` WM table for some keys; prefix mismatch exists — see Known Issues below).

### State / Hook Architecture

`AppNavigator.js` is the single call-site for `useWatermelonAppData()`. It passes all data and callbacks down as props to every screen. **No screen calls `useWatermelonAppData()` directly.**

`useWatermelonAppData` (`src/hooks/useWatermelonAppData.js`) composes eight domain hooks:

| Hook | Owns |
|------|------|
| `useWatermelonHome` | reactive home screen data |
| `useWatermelonPlans` | plans CRUD |
| `useWatermelonHistory` | workout history CRUD |
| `useWatermelonStats` | personal bests / stats |
| `useWatermelonSettings` | app settings with broadcast sync |
| `useWatermelonGymProfiles` | gym profiles |
| `useWatermelonManualRecovery` | recovery input |
| `useWatermelonBodyMeasurements` | body weight + measurements |

### Boot Sequence

`App.js` → imports `src/db/database` (creates WM singleton) → calls `seedExercisesIfNeeded()` + `backfillExerciseMusclesIfNeeded()` (both idempotent, guarded by `app_settings` marker `exercise_seed_v1_complete`) → renders `AppNavigator`.

### Domain Intelligence (`src/domain/intelligence/`)

Pure-function engines — no I/O, no React. Called from hooks/screens for analytics:
- `muscleContributionEngine` — muscle heat-map data
- `engagementEngine` — streaks, weekly summary, milestones
- `performanceEngine`, `progressionEngine`, `recoveryReadinessEngine`
- `programIntelligenceEngine`, `trainingAnalyticsEngine`, `volumeInterpretationEngine`
- `exerciseProfileEngine`, `substitutionEngine`

### Backup / Restore

**`backupService.js`** — orchestrates local-file and (disabled) Drive backups. Encryption via `backupCrypto.js` (`@noble/ciphers` + `@noble/hashes`). Config and status are read from AsyncStorage keys defined in `backupConstants.js`.

**`backupImportNormalizer.js`** — CRITICAL restore path. Detects four formats and normalises to a common shape before passing to `importAnyPayload()`. Do not change format-detection logic without understanding all four branches:
- `ironlog_watermelon_export` (type + version=1) → `watermelon_export_v1`
- `IRONLOG_SQLITE_EXPORT_V1` → `sqlite_export_v1`
- nested `payload` object → `nested_payload`
- plain JSON with `plans`/`history` arrays → `plain_json`

**`importExportRepository.js`** — `exportDatabase()` / `importDatabase()` / `importAnyPayload()` are the canonical WM import/export functions.

### Navigation

Custom `FluidTabBar` (not React Navigation's default) with five tabs: Home, Plans, Log (History), Stats, Settings. Modal screens (ActiveWorkout, PlanEditor, etc.) are in a root `NativeStack` above the tab navigator.

---

## Known Issues (open bugs)

1. **Backup exports are empty** — `backupService.js` still reads from `BACKUP_MANAGED_KEYS` (AsyncStorage keys that are now empty). Fix: replace with `exportDatabase()` from `importExportRepository`.

2. **Active session prefix mismatch** — `workoutSessionStore.js` uses `active_workout_session:` but `backupConstants.js` uses `@ironlog/activeWorkoutSession/`. In-flight workouts are not captured in backups.

3. **`SmokeTest` model not registered** — `src/db/models/SmokeTest.js` + `src/db/repositories/smokeRepository.js` are dead code; delete or register.

4. **Backup config on AsyncStorage** — `backupService.js` reads/writes config from AsyncStorage; should migrate to `app_settings` WM table.

5. **No ErrorBoundary** — a screen crash produces a white screen. Wrap tab content in `AppNavigator.js` with an `ErrorBoundary`.

## Dead Code (safe to delete after QA)

- `src/domain/storage/trainingDatabase.js`
- `src/domain/storage/trainingRepository.js`
- `src/db/models/SmokeTest.js`
- `src/db/repositories/smokeRepository.js`
- `src/context/AppContext.js` (legacy, no longer imported anywhere)

---

## Platform Abstractions

Native module calls are wrapped in `src/platform/` (`filesystem`, `sharing`, `documentPicker`, `secureStore`, `crypto`, `appInfo`). Touch these wrappers rather than the native modules directly.

## Key Invariants

- **Never import `database` directly in screens or hooks** — go through a repository.
- **`useWatermelonAppData` is called exactly once**, in `AppNavigator.js`. Screens receive data as props.
- **Exercise seeding is idempotent** — guarded by the `exercise_seed_v1_complete` setting; do not bypass this guard.
- **Schema changes require a migration** — add a step in `src/db/migrations.js` and bump the schema version in `src/db/schema.js`. Forgetting the migration will crash the app on upgrade.
- **`backupImportNormalizer.js` must handle all four legacy formats** — do not simplify it without verifying every import path still works.
