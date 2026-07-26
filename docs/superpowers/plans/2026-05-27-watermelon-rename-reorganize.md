# Watermelon Rename + Reorganize Implementation Plan

> **Status:** Historical execution plan. The rename/reorganization was committed, but these procedural checkboxes were never backfilled. They are not a current backlog; use `AGENTS.md` and Git history for status.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all WatermelonDB-derived names from the Kotlin codebase and reorganize `ui/screens/` into feature subdirectories.

**Architecture:** Pure mechanical refactoring — rename files/classes, update package declarations, update imports. No logic changes. The build is the test: `BUILD SUCCESSFUL` = done. Tasks ordered from zero-cascade (no import ripple) to maximum-cascade (screens split) to reduce compound failure risk.

**Tech Stack:** Kotlin, Jetpack Compose, ObjectBox 4.0.3, Gradle. Build: `.\gradlew.bat assembleDebug --no-daemon` in `Z:\KOTLIN\UnifiedPort`.

---

## File Map

**Renamed (file + class name):**
- `ui/viewmodel/WatermelonAppDataViewModel.kt` → `AppDataViewModel.kt` (class `WatermelonAppDataViewModel` → `AppDataViewModel`)
- `ui/viewmodel/WatermelonStatsViewModel.kt` → `StatsViewModel.kt` (class → `StatsViewModel`)
- `ui/viewmodel/WatermelonPlansViewModel.kt` → `PlansViewModel.kt` (class → `PlansViewModel`)
- `ui/viewmodel/WatermelonBodyMeasurementsViewModel.kt` → `BodyMeasurementsViewModel.kt` (classes → `BodyMeasurementsViewModel`, `BodyMeasurementsViewModelFactory`)

**Renamed (file only):**
- `ui/state/WorkoutContextPort.kt` → `WorkoutState.kt` (classes inside unchanged)

**Internal renames only (no file rename):**
- `data/repository/ImportExportRepository.kt`: private functions + internal string literal

**Moved (physical dir change only — package already correct):**
- `data/entity/AthleteCalibrationEntity.kt` → `data/objectbox/`
- `data/entity/GamificationProfileEntity.kt` → `data/objectbox/`
- `data/entity/IronLedgerEventEntity.kt` → `data/objectbox/`

**Moved (file + package change):**
- `data/exercise/ExerciseTrackingTypeNormalizer.kt` → `util/ExerciseTrackingTypeNormalizer.kt` (package: `data.exercise` → `util`)

**Moved (file + package change) — 39 screen files into 9 subdirs:**
- `ui/screens/*.kt` files distributed into `home/`, `workout/`, `plans/`, `body/`, `stats/`, `recovery/`, `intelligence/`, `gamification/`, `settings/`

**Import updates required:**
- 13 screens + `AppNavigator.kt` + `IronLogApp.kt` → new ViewModel class names
- `ExerciseRepository.kt`, `ExerciseSeed.kt` → new util package for Normalizer
- `AppNavigator.kt` + `IronLogApp.kt` → new screen packages after split

---

## Task 1: Internal renames in ImportExportRepository.kt (zero cascade)

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt`

- [ ] **Step 1: Rename private function `isWatermelonExport` → `isIronLogExport`**

  In `ImportExportRepository.kt`, find line ~197:
  ```kotlin
  private fun isWatermelonExport(payload: JSONObject): Boolean {
  ```
  Change to:
  ```kotlin
  private fun isIronLogExport(payload: JSONObject): Boolean {
  ```
  Also update its call site at line ~207:
  ```kotlin
  if (isIronLogExport(payload)) return "ironlog_v1"
  ```

- [ ] **Step 2: Rename internal format tag `"watermelon_v1"` → `"ironlog_v1"`**

  Find ~line 316:
  ```kotlin
  if (format != "watermelon_v1") {
  ```
  Change to:
  ```kotlin
  if (format != "ironlog_v1") {
  ```

- [ ] **Step 3: Rename `convertLegacyToWatermelonExport` → `convertLegacyToIronLogExport`**

  Find ~line 317 (call site), ~line 403 (call site), ~line 406 (declaration):
  ```kotlin
  // line ~317
  val conv = convertLegacyToIronLogExport(raw)
  // line ~403
  return convertLegacyToIronLogExport(raw)
  // line ~406
  private fun convertLegacyToIronLogExport(raw: JSONObject): JSONObject {
  ```

- [ ] **Step 4: Confirm EXPORT_TYPE is untouched**

  Run:
  ```
  grep "ironlog_watermelon_export" app\src\main\java\com\ironlog\app\data\repository\ImportExportRepository.kt
  ```
  Expected: line with `const val EXPORT_TYPE = "ironlog_watermelon_export"` still present.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt
  git commit -m "refactor: rename watermelon internal functions in ImportExportRepository"
  ```

---

## Task 2: Comment cleanup (zero cascade)

**Files:**
- Modify: `data/objectbox/Entities.kt`
- Modify: `data/objectbox/ObjectBox.kt`
- Modify: `data/repository/BodyMeasurementRepository.kt`
- Modify: `ui/model/UiModels.kt`

- [ ] **Step 1: Update entity comments in Entities.kt**

  Replace every `/** Watermelon table: X */` with `/** ObjectBox entity: X */` (there is one per entity class).

- [ ] **Step 2: Update ObjectBox.kt comment**

  Find the comment referencing "WatermelonDB database.js equivalent" and reword to: `/** ObjectBox store — persistent on-device database for all IronLog entities. */`

- [ ] **Step 3: Update BodyMeasurementRepository.kt comment**

  Find the comment referencing WatermelonDB observe() and reword to describe ObjectBox flow (e.g. `// Emits updates via ObjectBox reactive query`).

- [ ] **Step 4: Update UiModels.kt comment**

  Find the comment referencing React screens and Watermelon rows and replace with a brief description of the Kotlin/Compose model.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/ironlog/app/data/objectbox/Entities.kt
  git add app/src/main/java/com/ironlog/app/data/objectbox/ObjectBox.kt
  git add app/src/main/java/com/ironlog/app/data/repository/BodyMeasurementRepository.kt
  git add app/src/main/java/com/ironlog/app/ui/model/UiModels.kt
  git commit -m "refactor: remove WatermelonDB comments, replace with ObjectBox equivalents"
  ```

---

## Task 3: Rename WorkoutContextPort.kt → WorkoutState.kt (file only, zero import cascade)

**Files:**
- Rename: `ui/state/WorkoutContextPort.kt` → `ui/state/WorkoutState.kt`

No class names inside change. Kotlin imports reference classes, not filenames — zero callers need updating.

- [ ] **Step 1: Rename the file**
  ```powershell
  Rename-Item "app\src\main\java\com\ironlog\app\ui\state\WorkoutContextPort.kt" "WorkoutState.kt"
  ```

- [ ] **Step 2: Commit**
  ```bash
  git add -A app/src/main/java/com/ironlog/app/ui/state/
  git commit -m "refactor: rename WorkoutContextPort.kt to WorkoutState.kt"
  ```

---

## Task 4: ViewModel class renames + caller updates (13 screens + AppNavigator)

**Files to modify (callers — class name substitution only):**
- `navigation/AppNavigator.kt`
- `ui/screens/AIPlanScreen.kt`
- `ui/screens/ActiveWorkoutScreen.kt`
- `ui/screens/HistoryScreen.kt`
- `ui/screens/HomeScreen.kt`
- `ui/screens/PlanEditorScreen.kt`
- `ui/screens/PlansScreen.kt`
- `ui/screens/ProgramInsightsScreen.kt`
- `ui/screens/RecoveryMapScreen.kt`
- `ui/screens/SettingsScreen.kt`
- `ui/screens/StatsScreen.kt`
- `ui/screens/TrainingIntelligenceScreen.kt`
- `ui/screens/VolumeAnalyticsScreen.kt`

**Files to rename + edit (ViewModel sources):**
- `ui/viewmodel/WatermelonAppDataViewModel.kt` → `AppDataViewModel.kt`
- `ui/viewmodel/WatermelonStatsViewModel.kt` → `StatsViewModel.kt`
- `ui/viewmodel/WatermelonPlansViewModel.kt` → `PlansViewModel.kt`
- `ui/viewmodel/WatermelonBodyMeasurementsViewModel.kt` → `BodyMeasurementsViewModel.kt`

- [ ] **Step 1: Mass-replace class names across all caller files**

  Run these four replacements (sed-style, across all `.kt` files):
  ```
  WatermelonAppDataViewModel → AppDataViewModel
  WatermelonStatsViewModel   → StatsViewModel
  WatermelonPlansViewModel   → PlansViewModel
  WatermelonBodyMeasurementsViewModel      → BodyMeasurementsViewModel
  WatermelonBodyMeasurementsViewModelFactory → BodyMeasurementsViewModelFactory
  ```
  This updates both import lines and usage sites. The ViewModel source files get updated too (class declarations inside them).

- [ ] **Step 2: Rename the four ViewModel source files**
  ```powershell
  $base = "app\src\main\java\com\ironlog\app\ui\viewmodel"
  Rename-Item "$base\WatermelonAppDataViewModel.kt"          "AppDataViewModel.kt"
  Rename-Item "$base\WatermelonStatsViewModel.kt"            "StatsViewModel.kt"
  Rename-Item "$base\WatermelonPlansViewModel.kt"            "PlansViewModel.kt"
  Rename-Item "$base\WatermelonBodyMeasurementsViewModel.kt" "BodyMeasurementsViewModel.kt"
  ```

- [ ] **Step 3: Remove old "Kotlin replacement for useWatermelonXxx.js" class-level comments**

  In each ViewModel file, remove or replace the top-level class doc comment. Replace with a one-line summary of actual purpose:
  - `AppDataViewModel` → `/** Aggregates plans, history, settings, and personal bests for the entire app. */`
  - `StatsViewModel` → `/** Provides statistics and analytics data for the Stats screen. */`
  - `PlansViewModel` → `/** Manages training plan data including CRUD and program scheduling. */`
  - `BodyMeasurementsViewModel` → `/** Tracks body measurement entries and history. */`

- [ ] **Step 4: Build check**
  ```
  cd Z:\KOTLIN\UnifiedPort && .\gradlew.bat assembleDebug --no-daemon 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
  ```bash
  git add -A app/src/main/java/com/ironlog/app/ui/viewmodel/
  git add app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt
  git add app/src/main/java/com/ironlog/app/ui/screens/
  git commit -m "refactor: rename Watermelon ViewModels to AppDataViewModel, StatsViewModel, PlansViewModel, BodyMeasurementsViewModel"
  ```

---

## Task 5: Entity file consolidation (physical move only — packages already correct)

**Files:**
- Move: `data/entity/AthleteCalibrationEntity.kt` → `data/objectbox/`
- Move: `data/entity/GamificationProfileEntity.kt` → `data/objectbox/`
- Move: `data/entity/IronLedgerEventEntity.kt` → `data/objectbox/`
- Delete: `data/entity/` (empty after move)

All three entity files already declare `package com.ironlog.app.data.objectbox`. All callers already import from `com.ironlog.app.data.objectbox`. This is a physical directory move with zero code changes.

- [ ] **Step 1: Move files**
  ```powershell
  $src = "app\src\main\java\com\ironlog\app\data\entity"
  $dst = "app\src\main\java\com\ironlog\app\data\objectbox"
  Move-Item "$src\AthleteCalibrationEntity.kt"   $dst
  Move-Item "$src\GamificationProfileEntity.kt"  $dst
  Move-Item "$src\IronLedgerEventEntity.kt"       $dst
  Remove-Item $src
  ```

- [ ] **Step 2: Commit**
  ```bash
  git add -A app/src/main/java/com/ironlog/app/data/
  git commit -m "refactor: consolidate data/entity/ files into data/objectbox/ (packages were already identical)"
  ```

---

## Task 6: Relocate ExerciseTrackingTypeNormalizer to util/

**Files:**
- Move: `data/exercise/ExerciseTrackingTypeNormalizer.kt` → `util/ExerciseTrackingTypeNormalizer.kt`
- Modify: `data/repository/ExerciseRepository.kt` (import line)
- Modify: `data/seed/ExerciseSeed.kt` (import line)

- [ ] **Step 1: Update package declaration in the file**

  In `data/exercise/ExerciseTrackingTypeNormalizer.kt`, change line 1:
  ```kotlin
  package com.ironlog.app.util
  ```

- [ ] **Step 2: Move file**
  ```powershell
  Move-Item "app\src\main\java\com\ironlog\app\data\exercise\ExerciseTrackingTypeNormalizer.kt" `
            "app\src\main\java\com\ironlog\app\util\ExerciseTrackingTypeNormalizer.kt"
  Remove-Item "app\src\main\java\com\ironlog\app\data\exercise"
  ```

- [ ] **Step 3: Update import in ExerciseRepository.kt**

  Change:
  ```kotlin
  import com.ironlog.app.data.exercise.ExerciseTrackingTypeNormalizer
  ```
  To:
  ```kotlin
  import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
  ```

- [ ] **Step 4: Update import in ExerciseSeed.kt**

  Change:
  ```kotlin
  import com.ironlog.app.data.exercise.ExerciseTrackingTypeNormalizer
  ```
  To:
  ```kotlin
  import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
  ```

- [ ] **Step 5: Build check**
  ```
  cd Z:\KOTLIN\UnifiedPort && .\gradlew.bat assembleDebug --no-daemon 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**
  ```bash
  git add -A app/src/main/java/com/ironlog/app/data/exercise/
  git add app/src/main/java/com/ironlog/app/util/ExerciseTrackingTypeNormalizer.kt
  git add app/src/main/java/com/ironlog/app/data/repository/ExerciseRepository.kt
  git add app/src/main/java/com/ironlog/app/data/seed/ExerciseSeed.kt
  git commit -m "refactor: move ExerciseTrackingTypeNormalizer from data/exercise/ to util/"
  ```

---

## Task 7: ui/screens/ → feature subdirectories

**Directory mapping:**

| Subdir | Files |
|--------|-------|
| `home/` | `HomeScreen.kt` |
| `workout/` | `ActiveWorkoutScreen.kt`, `WorkoutCalendarScreen.kt`, `WorkoutExerciseBinding.kt` |
| `plans/` | `PlansScreen.kt`, `PlanEditorScreen.kt`, `ProgramPickerScreen.kt`, `ProgramInsightsScreen.kt`, `AIPlanScreen.kt`, `PlanQrScanScreen.kt`, `PlanQrShareSheet.kt` |
| `body/` | `BodyWeightScreen.kt`, `BodyMeasurementsScreen.kt`, `BodyMapCanvas.kt`, `ProgressPhotosScreen.kt` |
| `stats/` | `StatsScreen.kt`, `HistoryScreen.kt`, `ExerciseProgressScreen.kt`, `VolumeAnalyticsScreen.kt` |
| `recovery/` | `RecoveryMapScreen.kt`, `RecoveryHeatmapCard.kt`, `RecoveryCircuitSheet.kt` |
| `intelligence/` | `TrainingIntelligenceScreen.kt`, `ApexEngineCard.kt`, `CloudAiCard.kt` |
| `gamification/` | `StatusWindowScreen.kt` |
| `settings/` | `SettingsScreen.kt`, `GymProfilesScreen.kt`, `GymProfileEditorScreen.kt`, `CreateExerciseScreen.kt`, `ExerciseLibraryScreen.kt`, `DataPortabilityScreen.kt`, `ImportCenterScreen.kt`, `BackupCenterScreen.kt`, `RestoreDataScreen.kt`, `HealthConnectScreen.kt`, `HealthConnectPermissionSheet.kt`, `PrivacyScreen.kt` |
| *(root)* | `SplashScreen.kt`, `OnboardingScreen.kt` — stay at `ui/screens/` root |
| `onboarding/` | Already organized — leave untouched |

**Package change:** every moved file's `package com.ironlog.app.ui.screens` → `com.ironlog.app.ui.screens.<subdir>`.

**Import update required in:** `navigation/AppNavigator.kt`, `ui/IronLogApp.kt`, `ui/screens/ActiveWorkoutScreen.kt` (imports `WorkoutExerciseBinding`).

- [ ] **Step 1: Create subdirectories**
  ```powershell
  $base = "app\src\main\java\com\ironlog\app\ui\screens"
  "home","workout","plans","body","stats","recovery","intelligence","gamification","settings" | ForEach-Object { New-Item -ItemType Directory "$base\$_" -Force }
  ```

- [ ] **Step 2: Move files into subdirs**
  ```powershell
  $s = "app\src\main\java\com\ironlog\app\ui\screens"
  # home
  Move-Item "$s\HomeScreen.kt" "$s\home\"
  # workout
  Move-Item "$s\ActiveWorkoutScreen.kt","$s\WorkoutCalendarScreen.kt","$s\WorkoutExerciseBinding.kt" "$s\workout\"
  # plans
  Move-Item "$s\PlansScreen.kt","$s\PlanEditorScreen.kt","$s\ProgramPickerScreen.kt","$s\ProgramInsightsScreen.kt","$s\AIPlanScreen.kt","$s\PlanQrScanScreen.kt","$s\PlanQrShareSheet.kt" "$s\plans\"
  # body
  Move-Item "$s\BodyWeightScreen.kt","$s\BodyMeasurementsScreen.kt","$s\BodyMapCanvas.kt","$s\ProgressPhotosScreen.kt" "$s\body\"
  # stats
  Move-Item "$s\StatsScreen.kt","$s\HistoryScreen.kt","$s\ExerciseProgressScreen.kt","$s\VolumeAnalyticsScreen.kt" "$s\stats\"
  # recovery
  Move-Item "$s\RecoveryMapScreen.kt","$s\RecoveryHeatmapCard.kt","$s\RecoveryCircuitSheet.kt" "$s\recovery\"
  # intelligence
  Move-Item "$s\TrainingIntelligenceScreen.kt","$s\ApexEngineCard.kt","$s\CloudAiCard.kt" "$s\intelligence\"
  # gamification
  Move-Item "$s\StatusWindowScreen.kt" "$s\gamification\"
  # settings
  Move-Item "$s\SettingsScreen.kt","$s\GymProfilesScreen.kt","$s\GymProfileEditorScreen.kt","$s\CreateExerciseScreen.kt","$s\ExerciseLibraryScreen.kt","$s\DataPortabilityScreen.kt","$s\ImportCenterScreen.kt","$s\BackupCenterScreen.kt","$s\RestoreDataScreen.kt","$s\HealthConnectScreen.kt","$s\HealthConnectPermissionSheet.kt","$s\PrivacyScreen.kt" "$s\settings\"
  ```

- [ ] **Step 3: Update package declarations in all moved files**

  For each subdir, change `package com.ironlog.app.ui.screens` to `package com.ironlog.app.ui.screens.<subdir>` in every file in that subdir. Do not touch `SplashScreen.kt`, `OnboardingScreen.kt`, or anything in `onboarding/`.

- [ ] **Step 4: Update imports in AppNavigator.kt**

  `AppNavigator.kt` imports screen composables by class. Add granular imports for each new package:
  ```kotlin
  import com.ironlog.app.ui.screens.home.HomeScreen
  import com.ironlog.app.ui.screens.workout.ActiveWorkoutScreen
  import com.ironlog.app.ui.screens.workout.WorkoutCalendarScreen
  import com.ironlog.app.ui.screens.plans.PlansScreen
  import com.ironlog.app.ui.screens.plans.PlanEditorScreen
  import com.ironlog.app.ui.screens.plans.ProgramPickerScreen
  import com.ironlog.app.ui.screens.plans.ProgramInsightsScreen
  import com.ironlog.app.ui.screens.plans.AIPlanScreen
  import com.ironlog.app.ui.screens.plans.PlanQrScanScreen
  import com.ironlog.app.ui.screens.plans.PlanQrShareSheet
  import com.ironlog.app.ui.screens.body.BodyWeightScreen
  import com.ironlog.app.ui.screens.body.BodyMeasurementsScreen
  import com.ironlog.app.ui.screens.body.BodyMapCanvas
  import com.ironlog.app.ui.screens.body.ProgressPhotosScreen
  import com.ironlog.app.ui.screens.stats.StatsScreen
  import com.ironlog.app.ui.screens.stats.HistoryScreen
  import com.ironlog.app.ui.screens.stats.ExerciseProgressScreen
  import com.ironlog.app.ui.screens.stats.VolumeAnalyticsScreen
  import com.ironlog.app.ui.screens.recovery.RecoveryMapScreen
  import com.ironlog.app.ui.screens.recovery.RecoveryHeatmapCard
  import com.ironlog.app.ui.screens.recovery.RecoveryCircuitSheet
  import com.ironlog.app.ui.screens.intelligence.TrainingIntelligenceScreen
  import com.ironlog.app.ui.screens.intelligence.ApexEngineCard
  import com.ironlog.app.ui.screens.intelligence.CloudAiCard
  import com.ironlog.app.ui.screens.gamification.StatusWindowScreen
  import com.ironlog.app.ui.screens.settings.SettingsScreen
  import com.ironlog.app.ui.screens.settings.GymProfilesScreen
  import com.ironlog.app.ui.screens.settings.GymProfileEditorScreen
  import com.ironlog.app.ui.screens.settings.CreateExerciseScreen
  import com.ironlog.app.ui.screens.settings.ExerciseLibraryScreen
  import com.ironlog.app.ui.screens.settings.DataPortabilityScreen
  import com.ironlog.app.ui.screens.settings.ImportCenterScreen
  import com.ironlog.app.ui.screens.settings.BackupCenterScreen
  import com.ironlog.app.ui.screens.settings.RestoreDataScreen
  import com.ironlog.app.ui.screens.settings.HealthConnectScreen
  import com.ironlog.app.ui.screens.settings.HealthConnectPermissionSheet
  import com.ironlog.app.ui.screens.settings.PrivacyScreen
  ```
  Remove any wildcard `import com.ironlog.app.ui.screens.*` if present.

- [ ] **Step 5: Update imports in IronLogApp.kt**

  Same pattern — replace `com.ironlog.app.ui.screens.XxxScreen` with the new package path for each screen referenced.

- [ ] **Step 6: Update intra-screen imports**

  `ui/screens/workout/ActiveWorkoutScreen.kt` imports `WorkoutExerciseBinding` — update to:
  ```kotlin
  import com.ironlog.app.ui.screens.workout.WorkoutExerciseBinding
  ```
  (Same package now, so this import may become unnecessary — check if they're in the same file or separate.)

- [ ] **Step 7: Build check**
  ```
  cd Z:\KOTLIN\UnifiedPort && .\gradlew.bat assembleDebug --no-daemon 2>&1 | tail -20
  ```
  Expected: `BUILD SUCCESSFUL`. If errors, read error lines and fix missing imports one by one.

- [ ] **Step 8: Commit**
  ```bash
  git add -A app/src/main/java/com/ironlog/app/ui/screens/
  git add app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt
  git add app/src/main/java/com/ironlog/app/ui/IronLogApp.kt
  git commit -m "refactor: split ui/screens/ into feature subdirectories (home/workout/plans/body/stats/recovery/intelligence/gamification/settings)"
  ```

---

## Task 8: Final verification + AGENTS.md update

- [ ] **Step 1: Full build**
  ```
  cd Z:\KOTLIN\UnifiedPort && .\gradlew.bat assembleDebug --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify no Watermelon class names remain**
  ```
  grep -r "WatermelonAppDataViewModel\|WatermelonStatsViewModel\|WatermelonPlansViewModel\|WatermelonBodyMeasurementsViewModel\|WatermelonBodyMeasurementsViewModelFactory" app/src --include="*.kt"
  ```
  Expected: 0 results.

- [ ] **Step 3: Verify EXPORT_TYPE preserved**
  ```
  grep "ironlog_watermelon_export" app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt
  ```
  Expected: line with `const val EXPORT_TYPE = "ironlog_watermelon_export"` present.

- [ ] **Step 4: Update AGENTS.md**

  Append entry to `Z:\KOTLIN\AGENTS.md`:
  ```
  ## Watermelon Rename + Reorganize — 2026-05-27

  Eliminated all WatermelonDB-derived names and reorganized ui/screens/ into feature subdirectories.

  **Renames:**
  - WatermelonAppDataViewModel → AppDataViewModel (file + class)
  - WatermelonStatsViewModel → StatsViewModel (file + class)
  - WatermelonPlansViewModel → PlansViewModel (file + class)
  - WatermelonBodyMeasurementsViewModel → BodyMeasurementsViewModel (file + class + Factory)
  - WorkoutContextPort.kt → WorkoutState.kt (file only)
  - ImportExportRepository: isWatermelonExport→isIronLogExport, convertLegacyToWatermelonExport→convertLegacyToIronLogExport, "watermelon_v1"→"ironlog_v1"
  - Comments: "Watermelon table:" → "ObjectBox entity:" across Entities.kt, ObjectBox.kt, BodyMeasurementRepository.kt, UiModels.kt
  - EXPORT_TYPE string value "ironlog_watermelon_export" PRESERVED (backward compat)

  **Reorganized:**
  - ui/screens/ split into 9 feature subdirs: home/workout/plans/body/stats/recovery/intelligence/gamification/settings
  - data/entity/ (3 files) physically moved into data/objectbox/ (packages were already com.ironlog.app.data.objectbox)
  - data/exercise/ExerciseTrackingTypeNormalizer.kt moved to util/

  **Build:** `.\gradlew.bat assembleDebug --no-daemon` → BUILD SUCCESSFUL
  ```

- [ ] **Step 5: Final commit**
  ```bash
  git add Z:/KOTLIN/AGENTS.md
  git commit -m "docs: AGENTS.md — watermelon rename + reorganize complete"
  ```
