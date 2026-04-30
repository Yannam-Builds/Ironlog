# Codex Handoff — WatermelonDB Migration & Feature Backfill

**Branch:** `codex/WatermelonDB`  
**Date:** 2026-04-30  
**Written by:** Claude (post-migration session)

This document is a full handoff of everything done in the Claude sessions that ran after the initial Codex WatermelonDB migration. Read this before touching any file mentioned here.

---

## What Codex Missed or Got Wrong

### 1. Double-prefix bug in `workoutSessionStore.js`
Codex kept a `ACTIVE_SESSION_PREFIX = 'active_workout_session:'` constant and prepended it to keys that callers already passed with the full `@ironlog/activeWorkoutSession/` prefix. This created corrupted double-prefixed keys like:
```
active_workout_session:@ironlog/activeWorkoutSession/dayId_push
```
**Fix:** Removed the prefix constant entirely. Store functions now use the key exactly as passed:
```js
export async function getActiveSession(sessionKey) {
  if (!sessionKey) return null;
  return getSetting(sessionKey); // key used directly
}
```

### 2. `ExerciseLibraryService.js` — AsyncStorage still used for caching
Codex left the exercise library reading from AsyncStorage (`@ironlog/exerciseLibrary`, `@ironlog/exerciseIndex`) as a cache/fallback. Since WatermelonDB is now seeded with the full exercise library on first launch, AsyncStorage was redundant and caused stale reads.

**Fix:** Removed all AsyncStorage usage. Now uses:
- `wmGetExercisesSnapshot()` as the primary source (WM exercises table)
- `_bundledIndexCache` (module-level `let`) as a lightweight in-memory fallback
- `rebuildLibraryAndIndexFromBundled()` builds from bundled data + WM custom exercises

### 3. `backupService.js` — `getManagedStorageMap()` returned empty training data
Codex left `getManagedStorageMap()` reading from `AsyncStorage.getAllKeys()` + `multiGet()`. After migration, all training data lives in WM's SQLite tables, so AsyncStorage had nothing — the backup preview showed 0 records and any created backup was empty.

**Fix:** Rewrote `getManagedStorageMap()` to call `exportDatabase()` and translate the WM export to the expected domain-key map:
- `plans` table → `ironlog_plans`
- `workouts` + `workout_exercises` + `workout_sets` joined → `ironlog_history`
- `body_measurements` split by type → `ironlog_bw` / `@ironlog/bodyMeasurements`
- `exercises` where `is_custom=true` → `@ironlog/customExercises`
- All `app_settings` rows → their own keys (covers PRs, gym profiles, milestones, etc.)

### 4. `backupService.js` — restore path wrote to AsyncStorage, not WM
After decrypting a backup, the restore wrote all data back to AsyncStorage via `multiRemove` + `multiSet`. The app was reading from WM, so restored data never appeared.

**Fix:** Removed the `multiRemove`/`multiSet` block entirely. The existing "sync SQLite" block (which calls `replaceTrainingSnapshotCompat`) already handles restoring to WM — it just needed to be the only restore path.

### 5. `favoriteExercisesService.js` — still on AsyncStorage
Codex missed this one completely. It was reading/writing favourite exercise IDs via AsyncStorage.

**Fix:** Swapped to `getSetting` / `setSetting` from `settingsRepository`. The key `@ironlog/favoriteExerciseIds` is now stored as a `json` type in the `app_settings` WM table.

### 6. `backupService.js` — internal state (config, status, index) still on AsyncStorage
`readJsonStorage`/`writeJsonStorage` used `AsyncStorage.getItem/setItem` for backup config, status, index, queue, and device ID.

**Fix:** Replaced with `getSetting`/`setSetting`/`removeSetting` from `settingsRepository`. `getOrCreateDeviceId()` now reads/writes `@ironlog/deviceId` in WM.

### 7. Dead legacy files not removed
Five files had zero importers but were still in the repo:

| File | Why it existed |
|------|---------------|
| `src/storage/storage.js` | Original AsyncStorage CRUD layer |
| `src/services/migrations.js` | Legacy AS schema migration helpers |
| `src/services/nativeMigrationBridge.js` | One-time AS→SQLite bridge |
| `src/domain/storage/trainingRepository.js` | AS training repo |
| `src/domain/storage/trainingDatabase.js` | Companion to trainingRepository |

**Fix:** All five deleted via `git rm`. 1,693 lines removed.

---

## New Files Added

### `src/screens/DataPortabilityScreen.js`
Simple, no-encryption export/import screen. Replaces the old JSON export/import rows that were in SettingsScreen.

- **Export:** calls `exportDatabase()` → writes JSON to `documentDirectory` → `shareAsync()`
- **Import:** `DocumentPicker.getDocumentAsync()` → `readAsStringAsync()` → `importAnyPayload()`
- No passphrase, no encryption — plain JSON only
- Handles both WM native format (`ironlog_watermelon_export`) and legacy formats via `importAnyPayload()`'s format detection

### `src/services/BackupScheduler.js` (rewritten)
Replaced the old one-time-work + self-reschedule pattern with proper `PeriodicWorkRequest`:
```js
export async function schedulePeriodicBackupAt(hour, minute) { ... }
export async function cancelPeriodicBackup() { ... }
export async function checkBatteryOptimizationIgnored() { ... }
export async function requestBatteryOptimizationExemption() { ... }
```

---

## Modified Files

### `src/navigation/AppNavigator.js`
- Added `DataPortabilityScreen` import and Stack route:
  ```js
  <Stack.Screen name="DataPortability" ... title="BACKUP & EXPORT" />
  ```
- Added `AppErrorBoundary` React class component wrapping `NavigationContainer` — catches render crashes and shows a recovery UI with a restart button

### `src/screens/SettingsScreen.js` — Backup section restructure
The backup section now has **two rows** instead of the old mixed-in export buttons:

| Row label | Navigates to | Purpose |
|-----------|-------------|---------|
| `Backup & Export` | `DataPortability` | Simple plain-JSON export/import (primary) |
| `Advanced Backup Centre` | `BackupCenter` | Encrypted snapshots, scheduling, retention |

Removed from SettingsScreen: the old "Export JSON", "Import JSON", and "Legacy backup" rows that used the old AsyncStorage-based format.

### `src/screens/BackupCenterScreen.js`
Two classes of changes:

**Config key renames** — Codex used wrong key names that didn't match `DEFAULT_BACKUP_CONFIG`:
```
dailyBackupEnabled  →  scheduledBackupEnabled
dailyBackupHour     →  scheduledBackupHour
dailyBackupMinute   →  scheduledBackupMinute
```

**Import renames** — the old `BackupScheduler` exported `scheduleDailyBackup` and `cancelBackupJob`. The new API is:
```js
import {
  schedulePeriodicBackupAt,
  cancelPeriodicBackup,
  checkBatteryOptimizationIgnored,
  requestBatteryOptimizationExemption,
} from '../services/BackupScheduler';
```

**Added UI:**
- Retention count stepper (2–20 files) for local backup rolling window
- Battery optimisation warning card — shown when scheduled backup is enabled but `isIgnoringBatteryOptimizations()` returns false. Tapping it calls `requestBatteryOptimizationExemption()` which fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

### `src/services/backupConstants.js`
- Removed `GOOGLE_DRIVE_SCOPES` and `GOOGLE_DISCOVERY` constants (Drive is disabled)
- Updated `DEFAULT_BACKUP_CONFIG`:
  ```js
  localRetentionCount: 4,  // was 8
  scheduledBackupEnabled: false,
  scheduledBackupHour: 2,
  scheduledBackupMinute: 0,
  ```

### `android/.../IronlogBackupSchedulerModule.kt`
- Added `schedulePeriodicBackup(hour, minute, promise)` — computes initial delay to next occurrence of that hour:minute, then creates a `PeriodicWorkRequestBuilder(24, TimeUnit.HOURS)`
- Added `cancelPeriodicBackup(promise)` — cancels by `PERIODIC_WORK_NAME`
- Added `isBatteryOptimizationIgnored(promise)` — `PowerManager.isIgnoringBatteryOptimizations()`
- Added `requestBatteryOptimizationExemption(promise)` — `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Companion object: `const val PERIODIC_WORK_NAME = "ironlog_scheduled_daily_backup"`

### `android/.../IronlogBackupWorker.kt`
- Removed the old self-reschedule logic (it enqueued a new one-time work request after each run). `PeriodicWorkRequest` handles recurrence natively — no self-scheduling needed.

### `android/app/src/main/AndroidManifest.xml`
- Added permissions:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
  <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
  ```
- Removed the `ironlog://oauth` intent filter (Drive OAuth is disabled)

### `src/db/database.js`
- Registered `SmokeTest` model in `modelClasses` array (was seeded but not registered, causing WM init warnings)

### `src/screens/StatsScreen.js`
All heavy per-render computations wrapped in `useMemo`:
```js
const { streak, totalSets, avgDur } = useMemo(() => { ... }, [history]);
const { chartData, chartMax, pointSpacing } = useMemo(() => { ... }, [history, width]);
const pbEntries = useMemo(() => Object.entries(pb).sort(...), [pb]);
const STATS = useMemo(() => [...], [history.length, totalSets, avgDur, streak]);
```

### `src/screens/HomeScreen.js`
Inline computations memoized:
```js
const weeklyGoalDays = useMemo(() => ..., [settings?.weeklyGoalDays]);
const goalStreak = useMemo(() => getGoalStreak(history, weeklyGoalDays), [history, weeklyGoalDays]);
const { weekSessions, hitDays, avgDur } = useMemo(() => { ... }, [history]);
const activePlanDays = useMemo(() => ..., [activePlan]);
```

### `src/components/GoldConfettiBurst.js`
Fixed stale `useEffect` dependency array in `ConfettiParticle`. Codex had included `progress` (a shared value ref) in the deps which caused the animation to restart on every render:
```js
// eslint-disable-next-line react-hooks/exhaustive-deps
}, [particle.delayMs, particle.durationMs]);
```

Celebration variants: only `fireworks` and `wave` remain active. Particle counts reduced to prevent frame drops on mid-range Android.

### `src/screens/HistoryScreen.js`
Stable exercise keys in history card list — was using array index `key={ei}` which caused React to remount items on reorder:
```js
key={ex.name ? `${ex.name}_${ei}` : ei}
```

---

## Key Invariants to Preserve

1. **WM is the source of truth.** Never write training data to AsyncStorage. The `@react-native-async-storage/async-storage` package is still a dependency (needed by some third-party libs) but no first-party code should import it.

2. **`exportDatabase()` is the canonical backup source.** All backup creation paths must go through `importExportRepository.exportDatabase()`. Do not reconstruct training data from AsyncStorage.

3. **`importAnyPayload()` is the canonical restore entry point.** It detects the format (`watermelon_v1`, `legacy_async`, `sqlite_v1`) and routes accordingly. `DataPortabilityScreen` uses it directly. `BackupCenterScreen`'s restore path uses `replaceTrainingSnapshotCompat` which internally calls WM write operations.

4. **`BackupScheduler.js` API.** The exported functions are `schedulePeriodicBackupAt`, `cancelPeriodicBackup`, `checkBatteryOptimizationIgnored`, `requestBatteryOptimizationExemption`. The old names (`scheduleDailyBackup`, `cancelBackupJob`) do not exist.

5. **Backup config keys.** Always use `scheduledBackupEnabled`, `scheduledBackupHour`, `scheduledBackupMinute` — not `dailyBackup*`. These match the keys in `DEFAULT_BACKUP_CONFIG` in `backupConstants.js`.

6. **`AppErrorBoundary` wraps the whole navigator** in `AppNavigator.js`. Do not remove it or move it inside the navigator — it must catch errors from screen renders.

---

## What Still Has AsyncStorage (intentional)

`@react-native-async-storage/async-storage` is still installed. These things are fine to leave:

- **`nativeMigrationBridge.js`** — deleted (was reading migration marker from AS; confirmed no importers)
- Some third-party libraries may use AsyncStorage internally (react-navigation persistence, etc.) — don't block on it

---

## Commit Log (most recent first)

```
354b463 chore: remove dead AsyncStorage-based legacy files
f7b341b refactor: complete AsyncStorage migration for all live service files
438fd72 perf: memoize heavy screen computations and fix misc issues
d40b65c chore: remove Expo config files (replaced by bare React Native)
f3642e8 feat: complete WatermelonDB migration with Claude feature improvements
```
