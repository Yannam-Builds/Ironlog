# IronLog Session Context — Quick Catch-Up

> Last updated: 2026-04-30 (Claude session)
> Branch: `codex/WatermelonDB`
> Repo root: `Z:\ironlog`

---

## What This Project Is

**IronlogDB** — React Native Android-only workout tracker.
- Package: `com.ironlog.app`
- Stack: React Native + WatermelonDB (offline-first SQLite ORM) + React Navigation + Reanimated/Skia
- Build: `cd android && gradlew.bat assembleDebug / assembleBundledDebug / assembleRelease`
- Install: `adb install -r app-release.apk`

---

## Where We Are Right Now

The app just completed a **full WatermelonDB migration** over the last 2 days:
- Legacy data layer: `AppContext` (boot-time AsyncStorage snapshot) — **fully removed from active runtime**
- New data layer: WatermelonDB + reactive hooks + `useWatermelonAppData()` central hook
- All 3 build variants pass. Release APK installed on device.
- **Codex did the AppContext cutover on 2026-04-30 02:25** — verified clean (zero remaining `AppContext` imports in `src/`)

---

## Current State of Key Files

### Entry Point
- **`App.js`** — Clean. No AppContextProvider. Boots WM db, seeds exercises, shows AppNavigator. Has retry on startup error.

### Central Data Hook
- **`src/hooks/useWatermelonAppData.js`** — Replaces AppContextProvider for ALL screens. Composes:
  - `useWatermelonHome` — reactive home screen data
  - `useWatermelonPlans` — plans CRUD via WM
  - `useWatermelonHistory` — history CRUD via WM (uses real WM UUIDs — history ID bug FIXED)
  - `useWatermelonStats` — PB/stats
  - `useWatermelonSettings` — settings with broadcast sync
  - `useWatermelonGymProfiles` — gym profiles
  - `useWatermelonManualRecovery` — recovery input
  - `useWatermelonBodyMeasurements` — body weight + measurements
  - Backup config/status loaded from AsyncStorage (known issue, not yet migrated to WM)
  - `addHistory` calls `createCompletedWorkout()` → writes to WM (not AsyncStorage)
  - `restoreData` calls `importAnyPayload()` from `importExportRepository`
  - Exposed API: plans, history, pb, settings, gymProfiles, addHistory, deleteHistoryEntry, updateHistoryEntry, savePlans, updateSettings, logBodyWeight, deleteBodyWeightEntry, restoreData, etc.

### Navigation
- **`src/navigation/AppNavigator.js`** — Uses `useWatermelonAppData()`, zero AppContext. 11 screens consume it.

### WatermelonDB Infrastructure (`src/db/`)
- `database.js` — singleton DB + schema
- `migrations.js` — migration steps
- `models/` — Workout, WorkoutExercise, WorkoutSet, Plan, PlanDay, PlanExercise, Exercise, ExerciseMuscle, AppSetting, BodyMeasurement, ProgressPhoto, GymProfile
- `repositories/` — workoutRepository, planRepository, settingsRepository, importExportRepository, exerciseRepository, bodyMeasurementRepository, statsRepository, progressPhotoRepository, smokeRepository (dead)
- `adapters/sessionAdapter.js` — maps WM rows → AppContext-shaped JS objects

### Backup/Restore
- **`src/services/backupImportNormalizer.js`** — CRITICAL restore path. Format detection:
  - `ironlog_watermelon_export` (type + version=1) → passes through intact as `watermelon_export_v1`
  - `IRONLOG_SQLITE_EXPORT_V1` → `sqlite_export_v1`
  - nested `payload` object → `nested_payload`
  - plain JSON with `plans`/`history` arrays → `plain_json`
  - Stats for WM format: `data.plans`, `data.workouts`, `data.body_measurements`, `data.exercises`
  - **DO NOT change this file without understanding the format detection logic**

---

## Known Bugs (From Gemini Audit 2026-04-30)

### Still Open (not yet fixed):

1. **Backup creates empty backups** — `getManagedStorageMap()` reads AsyncStorage keys that are empty (data is in WM). Fix: replace with `exportDatabase()` from `importExportRepository`. Files: `backupService.js`, `backupConstants.js`.

2. **Active session prefix mismatch** — `workoutSessionStore.js:7` uses `active_workout_session:`, but `backupConstants.js` and `ActiveWorkoutScreen.js` use `@ironlog/activeWorkoutSession/`. In-progress workouts not backed up. Fix: unify prefix in all 3 files.

3. **Dual `savePlans`** — Mostly resolved (AppContext gone), but verify `PlanEditorScreen` and `ProgramPickerScreen` both use `useWatermelonPlans().savePlans`.

4. **SmokeTest model not registered** — `src/db/models/SmokeTest.js` exists but not in `modelClasses` in `database.js`. Either register it or delete it + `smokeRepository.js`.

5. **No ErrorBoundary** — Any screen crash = white screen. Fix: add `ErrorBoundary` component wrapping tab screens in `AppNavigator.js`.

6. **Backup config still on AsyncStorage** — `backupService.js` reads/writes config from AsyncStorage. Should migrate to `app_settings` WM table.

### Fixed by Codex This Session:
- ✅ History ID mismatch — `deleteHistoryEntry` now uses real WM UUIDs via `deleteWorkoutWithData(id)`
- ✅ Dual source of truth (AppContext stale snapshot) — AppContext fully removed
- ✅ `useWatermelonHistory` uses `deleteWorkoutWithData` not timestamp-based delete

---

## Priority Fix Order (What To Do Next)

From highest to lowest impact:

1. **Fix backup export** — `backupService.js`: replace `getManagedStorageMap` with `exportDatabase()` call. This is the most critical data-loss bug — backups currently capture nothing.

2. **Unify active session prefix** — `workoutSessionStore.js`, `backupConstants.js`, `ActiveWorkoutScreen.js`. Choose `active_workout_session:` and use it everywhere.

3. **Register or delete SmokeTest** — Quick 2-min fix: either add `SmokeTest` to `modelClasses` in `database.js` + add table to `schema.js`, or delete `src/db/models/SmokeTest.js` + `src/db/repositories/smokeRepository.js`.

4. **Add ErrorBoundary** — Create `src/components/ErrorBoundary.js`, wrap tab content in `AppNavigator.js`.

5. **Verify PlanEditorScreen / ProgramPickerScreen** — Confirm they use `useWatermelonPlans().savePlans` not legacy AppContext path.

6. **Manual runtime QA** (per Codex's own note): launch, active workout, plan edit, import/backup, settings/theme, training max, athlete/gym profile defaults.

7. **Migrate backup config to WM** — Move backup/notification config from AsyncStorage to `app_settings` table.

8. **Delete dead files** after QA:
   - `src/domain/storage/trainingDatabase.js`
   - `src/domain/storage/trainingRepository.js`
   - `src/db/repositories/smokeRepository.js`
   - `src/db/models/SmokeTest.js`
   - `src/context/AppContext.js` (legacy, no longer imported)

---

## Change Log Files

All session notes live in `docs/changes/`:
- `docs/changes/INDEX.md` — index
- `docs/changes/2026-04-30.md` — today: Gemini audit findings, Codex AppContext cutover, Claude session work
- `docs/changes/2026-04-29.md` — yesterday: archive pass, Watermelon migration execution

---

## Build Commands

```bash
# from Z:\ironlog\android
gradlew.bat assembleDebug
gradlew.bat assembleBundledDebug
gradlew.bat assembleRelease

# install to connected device
adb install -r app\build\outputs\apk\release\app-release.apk

# check remaining AppContext imports (should be 0 in src/)
grep -r "from.*AppContext\|require.*AppContext" src/
```

---

## Repo Architecture

```
Z:\ironlog\
  App.js                          ← entry point, WM bootstrap
  src/
    context/
      AppContext.js               ← LEGACY, no longer imported — delete after QA
      ThemeContext.js             ← uses useWatermelonSettings() directly
      GlassModeContext.js
      ActiveWorkoutBannerContext.js
    db/
      database.js                 ← WM singleton + modelClasses + schema
      schema.js
      migrations.js
      models/                     ← WM model classes (one per table)
      repositories/               ← all DB operations
      adapters/sessionAdapter.js  ← WM rows → JS shape
      seed/                       ← exercise library JSON seed
    hooks/
      useWatermelonAppData.js     ← CENTRAL HOOK — all screens use this
      useWatermelonHome.js
      useWatermelonHistory.js
      useWatermelonPlans.js
      useWatermelonSettings.js
      useWatermelonStats.js
      useWatermelonBodyMeasurements.js
      useWatermelonGymProfiles.js
      useWatermelonManualRecovery.js
    navigation/
      AppNavigator.js             ← calls useWatermelonAppData(), passes props down
    screens/                      ← each screen receives data from AppNavigator props
    services/
      backupService.js            ← STILL uses AsyncStorage for config (known issue)
      backupImportNormalizer.js   ← restore format detection (DO NOT break)
      workoutSessionStore.js      ← active session persistence (prefix mismatch bug)
      backupConstants.js          ← prefix mismatch bug here too
    domain/
      intelligence/               ← PR/volume/engagement engines (pure functions)
      storage/                    ← trainingDatabase.js, trainingRepository.js (DEAD CODE)
  scripts/
    gemini-bridge.js              ← Gemini CLI bridge (use cc-gemini-plugin:gemini-agent subagent instead)
  docs/
    changes/                      ← daily session logs
    SESSION_CONTEXT.md            ← THIS FILE
```

---

## Notes for New Session

- The branch is `codex/WatermelonDB` — **all work happens here**, not main
- `git status` will show ~159 modified/untracked files — this is expected (WM migration is uncommitted work)
- The most recent commit is documentation-only (`docs: mark legacy alpha...`)
- All actual migration work is in the working tree, not committed
- When you're ready to commit: stage specific files, not `git add -A` (could pick up secrets/large binaries)
- The app's keystore is `ironlogdb-v111.keystore` (matched to installed APK signer)
