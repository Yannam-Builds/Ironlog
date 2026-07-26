# Design: Watermelon Rename + Codebase Reorganization

**Date:** 2026-05-27  
**Status:** Approved (user selected Option C)  
**Scope:** Rename all WatermelonDB-derived identifiers to meaningful Kotlin names; reorganize `ui/screens/` into feature subdirectories; consolidate ObjectBox entity files; relocate `ExerciseTrackingTypeNormalizer`.

---

## 1. Background

IronLog was migrated from React Native (using WatermelonDB) to Kotlin. The migration left behind "Watermelon"-prefixed class names, internal function names, and code comments that refer to the old architecture. These names are noise — they carry no meaning in the Kotlin codebase and mislead readers (and AI assistants) about the actual architecture.

Additionally, `ui/screens/` has grown to 32 flat files, making it slow to navigate and easy to open the wrong file. Feature subdirectories group related screens together so any screen is findable in 1–2 steps.

---

## 2. Rename Map

### 2a. ViewModel Files and Classes

All files live in `ui/viewmodel/`. File renames require updating both the filename and the class declaration inside.

| Old filename | New filename | Old class(es) | New class(es) |
|---|---|---|---|
| `WatermelonAppDataViewModel.kt` | `AppDataViewModel.kt` | `WatermelonAppDataViewModel` | `AppDataViewModel` |
| `WatermelonStatsViewModel.kt` | `StatsViewModel.kt` | `WatermelonStatsViewModel` | `StatsViewModel` |
| `WatermelonPlansViewModel.kt` | `PlansViewModel.kt` | `WatermelonPlansViewModel` | `PlansViewModel` |
| `WatermelonBodyMeasurementsViewModel.kt` | `BodyMeasurementsViewModel.kt` | `WatermelonBodyMeasurementsViewModel` | `BodyMeasurementsViewModel` |
| | | `WatermelonBodyMeasurementsViewModelFactory` | `BodyMeasurementsViewModelFactory` |

All call sites across all screens and navigation must be updated to use the new class names. The package declaration (`com.ironlog.app.ui.viewmodel`) stays unchanged.

### 2b. `ui/state/WorkoutContextPort.kt` → `ui/state/WorkoutState.kt`

File rename only. The "Port" suffix is an RN migration leftover. Classes inside (`WorkoutState`, `WorkoutAction`, `WorkoutInput`, etc.) are already correctly named — no class renames needed. The package stays `com.ironlog.app.ui.state`. All imports across the codebase reference the class names directly, not the filename, so no import changes are needed (Kotlin imports by class, not file).

### 2c. `data/repository/ImportExportRepository.kt` — Internal Renames

These are private/internal identifiers — they affect only the file itself.

| Old identifier | New identifier | Notes |
|---|---|---|
| `fun isWatermelonExport()` | `fun isIronLogExport()` | Private function |
| `fun convertLegacyToWatermelonExport()` | `fun convertLegacyToIronLogExport()` | Private function |
| `"watermelon_v1"` (internal format tag) | `"ironlog_v1"` | Used in lines ~207, ~316; internal only, not stored in user files |

**Backward compat preserved:**  
`const val EXPORT_TYPE = "ironlog_watermelon_export"` — this string literal is embedded in every backup file ever written by the app. Its **value must not change**. Only the surrounding Kotlin function names change. `BackupCenterScreen.kt` line 304 checks `obj.optString("type") == "ironlog_watermelon_export"` — this check stays exactly as-is.

### 2d. Comment Cleanup (No Behavioral Changes)

| File | Old comment text | New comment text |
|---|---|---|
| `data/objectbox/Entities.kt` | `/** Watermelon table: X */` (on each entity class) | `/** ObjectBox entity: X */` |
| `data/objectbox/ObjectBox.kt` | Comment referencing "WatermelonDB database.js equivalent" | Reword to describe ObjectBox store role |
| `data/repository/BodyMeasurementRepository.kt` | Comment referencing WatermelonDB observe() | Reword to describe ObjectBox flow |
| `ui/model/UiModels.kt` | Comment referencing React screens and Watermelon rows | Reword to describe Kotlin/Compose usage |
| All ViewModel files | Class-level comment `"Kotlin replacement for useWatermelonXxx.js"` | Remove or replace with a brief description of the class's actual role |

---

## 3. `ui/screens/` Feature Subdirectory Split

Each subdirectory gets its own package declaration (`com.ironlog.app.ui.screens.<subdir>`). All import statements across the codebase that reference screen classes must be updated to the new package.

### Directory mapping

| Subdirectory | Files | New package |
|---|---|---|
| `home/` | `HomeScreen.kt` | `com.ironlog.app.ui.screens.home` |
| `workout/` | `ActiveWorkoutScreen.kt`, `WorkoutCalendarScreen.kt`, `WorkoutExerciseBinding.kt` | `com.ironlog.app.ui.screens.workout` |
| `plans/` | `PlansScreen.kt`, `PlanEditorScreen.kt`, `ProgramPickerScreen.kt`, `ProgramInsightsScreen.kt`, `AIPlanScreen.kt`, `PlanQrScanScreen.kt`, `PlanQrShareSheet.kt` | `com.ironlog.app.ui.screens.plans` |
| `body/` | `BodyWeightScreen.kt`, `BodyMeasurementsScreen.kt`, `BodyMapCanvas.kt`, `ProgressPhotosScreen.kt` | `com.ironlog.app.ui.screens.body` |
| `stats/` | `StatsScreen.kt`, `HistoryScreen.kt`, `ExerciseProgressScreen.kt`, `VolumeAnalyticsScreen.kt` | `com.ironlog.app.ui.screens.stats` |
| `recovery/` | `RecoveryMapScreen.kt`, `RecoveryHeatmapCard.kt`, `RecoveryCircuitSheet.kt` | `com.ironlog.app.ui.screens.recovery` |
| `intelligence/` | `TrainingIntelligenceScreen.kt`, `ApexEngineCard.kt`, `CloudAiCard.kt` | `com.ironlog.app.ui.screens.intelligence` |
| `gamification/` | `StatusWindowScreen.kt` | `com.ironlog.app.ui.screens.gamification` |
| `settings/` | `SettingsScreen.kt`, `GymProfilesScreen.kt`, `GymProfileEditorScreen.kt`, `CreateExerciseScreen.kt`, `ExerciseLibraryScreen.kt`, `DataPortabilityScreen.kt`, `ImportCenterScreen.kt`, `BackupCenterScreen.kt`, `RestoreDataScreen.kt`, `HealthConnectScreen.kt`, `HealthConnectPermissionSheet.kt`, `PrivacyScreen.kt` | `com.ironlog.app.ui.screens.settings` |
| `onboarding/` | Already organized with its own `steps/` subdir — **leave as-is** | `com.ironlog.app.ui.screens.onboarding` (unchanged) |
| *(root)* | `SplashScreen.kt` — leave at `ui/screens/` root | `com.ironlog.app.ui.screens` (unchanged) |

**Primary callers to update:** `AppNavigator.kt` (imports all screens for navigation graph) and any composable that hosts another screen directly.

---

## 4. Entity File Consolidation

`data/entity/` (3 gamification entity files) merges into `data/objectbox/`. All ObjectBox entities live in one place.

### Files moved

| Old path | New path | Package change |
|---|---|---|
| `data/entity/AthleteCalibrationEntity.kt` | `data/objectbox/AthleteCalibrationEntity.kt` | `com.ironlog.app.data.entity` → `com.ironlog.app.data.objectbox` |
| `data/entity/GamificationProfileEntity.kt` | `data/objectbox/GamificationProfileEntity.kt` | same |
| `data/entity/IronLedgerEventEntity.kt` | `data/objectbox/IronLedgerEventEntity.kt` | same |

After the move, the `data/entity/` directory is deleted (it will be empty).

All files that import `com.ironlog.app.data.entity.*` or individual entity classes must be updated to `com.ironlog.app.data.objectbox.*`. Likely callers: `ObjectBox.kt` (entity registration), gamification domain files, repositories that read/write these entities.

---

## 5. `ExerciseTrackingTypeNormalizer` Relocation

| Old path | New path | Package change |
|---|---|---|
| `data/exercise/ExerciseTrackingTypeNormalizer.kt` | `util/ExerciseTrackingTypeNormalizer.kt` | `com.ironlog.app.data.exercise` → `com.ironlog.app.util` |

After the move, `data/exercise/` is deleted (it will be empty). All callers that import `com.ironlog.app.data.exercise.ExerciseTrackingTypeNormalizer` must be updated to `com.ironlog.app.util.ExerciseTrackingTypeNormalizer`.

---

## 6. What Does NOT Change

- `const val EXPORT_TYPE = "ironlog_watermelon_export"` — value stays exactly as-is.
- The string `"ironlog_watermelon_export"` in `BackupCenterScreen.kt` line ~304 — stays as-is.
- `onboarding/` subdir — already organized; left untouched.
- `SplashScreen.kt` — stays at `ui/screens/` root (it's the one screen that precedes any feature context).
- All class names that were already correct (`WorkoutState`, `WorkoutAction`, `PlansViewModel` internal logic, etc.) — no changes.
- No React Native, JS, TS, or Expo files are touched.
- No changes to build system, Gradle scripts, or manifest beyond what's required by package changes.

---

## 7. Verification Strategy

1. After all renames and moves: `.\gradlew.bat assembleDebug --no-daemon` → must exit with `BUILD SUCCESSFUL`.
2. Zero Kotlin compile errors — all import paths resolve.
3. No references to old class names remain in `.kt` files (grep check post-build).
4. `EXPORT_TYPE` value unchanged (grep check: `"ironlog_watermelon_export"` still present in `ImportExportRepository.kt`).

---

## 8. Out of Scope

- Renaming `IronLogApplication`, `MainActivity`, or any other non-Watermelon class.
- Changing ObjectBox schema version or entity annotations.
- Updating navigation route strings (they reference composable function names, not class names).
- Any refactoring of business logic.
