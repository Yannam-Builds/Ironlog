# Fix Codex Uncommitted Work — Implementation Plan

> **Status:** Superseded by the 2026-07-21 stabilization pass documented in `AGENTS.md`. These procedural checkboxes are retained as history and are not a current backlog.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the broken bodymap layout, commit all Codex-authored uncommitted work (widget system, calibration backfill, onboarding enhancements, progress photo compare), and document the 19 Codex commits in AGENTS.md.

**Architecture:** Six sequential tasks in dependency order. Task 1 (bodymap) is the only true code fix — it removes the `Modifier.offset()` hacks that cause clipping on device and replaces them with clean layout containment. Tasks 2–5 are build-verify-and-commit passes for each body of uncommitted Codex work. Task 6 documents everything in AGENTS.md.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Glance (widgets), ObjectBox, Gradle (`gradlew.bat assembleRelease --no-daemon`), ADB (`adb install -r`)

---

## Files

| File | Action |
|---|---|
| `app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryHeatmapCard.kt` | Modify — remove offset/widthIn hacks |
| `app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryMapScreen.kt` | Modify — remove offset/widthIn hacks, keep pager |
| `app/src/main/java/com/ironlog/app/widget/WidgetVisualStateResolver.kt` | New — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetAssets.kt` | New — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetContent.kt` | New — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetPresentation.kt` | New — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetSampleStates.kt` | New — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt` | Modified — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt` | Modified — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt` | Modified — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt` | Modified — commit as-is |
| `app/src/main/java/com/ironlog/app/widget/WidgetState.kt` | Modified — commit as-is |
| `app/src/main/res/xml/badge_widget_info.xml` | Modified — commit as-is |
| `app/src/main/res/xml/dashboard_widget_info.xml` | Modified — commit as-is |
| `app/src/main/res/xml/warroom_widget_info.xml` | Modified — commit as-is |
| `app/src/main/res/drawable-nodpi/forgefox_widget_*.png` (10 files) | New — commit as-is |
| `app/src/main/res/drawable-nodpi/ic_forge_streak_dumbbell.png` | New — commit as-is |
| `app/src/test/java/com/ironlog/app/widget/ForgeFoxWidgetPresentationTest.kt` | New — commit as-is |
| `app/src/test/java/com/ironlog/app/widget/WidgetVisualStateResolverTest.kt` | New — commit as-is |
| `app/src/main/java/com/ironlog/app/data/objectbox/AthleteCalibrationEntity.kt` | Modified — commit after schema check |
| `app/objectbox-models/default.json` | Modified — commit with entity |
| `app/objectbox-models/default.json.bak` | Modified — commit with entity |
| `app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt` | Modified — commit with entity |
| `app/src/test/java/com/ironlog/app/data/repository/ImportExportRepositoryCalibrationBackfillTest.kt` | New — commit with entity |
| `app/src/main/java/com/ironlog/app/ui/screens/OnboardingScreen.kt` | Modified — commit with onboarding group |
| `app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModel.kt` | Modified — commit with onboarding group |
| `app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingLedgerPreview.kt` | New — commit with onboarding group |
| `app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step3Baseline.kt` | Modified — commit with onboarding group |
| `app/src/main/java/com/ironlog/app/domain/gamification/IronLedgerEngine.kt` | Modified — commit with onboarding group |
| `app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt` | Modified — commit with onboarding group |
| `app/src/main/AndroidManifest.xml` | Modified — check + commit with onboarding group |
| `app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt` | Modified — passes `historicalTrainingDaysPerWeek`; commit with onboarding group |
| `app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt` | Modified — commit with photo group |
| `app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotoCompareLogic.kt` | New — commit with photo group |
| `app/src/test/java/com/ironlog/app/ui/screens/ProgressPhotoCompareLogicTest.kt` | New — commit with photo group |
| `Z:\KOTLIN\AGENTS.md` | Update — document 19 Codex commits |

---

## Task 1: Fix bodymap `Modifier.offset()` clipping

**Root cause:** `RecoveryHeatmapCard` and `RecoveryMapScreen` both apply `Modifier.offset(x=-24.dp, y=-38.dp)` to `BodyHalfCanvas`. `offset()` moves the visual rendering AFTER Compose has measured and placed the composable — the canvas stays in its original measured slot but draws outside it, clipping against parent containers and overlapping adjacent composables. The SVG math in `BodyMapCanvas.kt` is correct and should not be changed.

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryHeatmapCard.kt`
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryMapScreen.kt`

- [ ] **Step 1: Fix RecoveryHeatmapCard — remove offset/widthIn, delete dead constants**

  Open `RecoveryHeatmapCard.kt`. Delete the three constant declarations near the top of the file:
  ```kotlin
  // DELETE these three lines:
  private val HOME_RECOVERY_BODY_MAP_MAX_WIDTH = 170.dp
  private val BODY_MAP_LABEL_LIFT = (-38).dp
  private val BODY_MAP_VISUAL_LEFT_NUDGE = (-24).dp
  ```
  Also remove the `import androidx.compose.foundation.layout.offset` and `import androidx.compose.foundation.layout.widthIn` lines (they'll be unused after this change).

  Then locate the `mapModifier` val inside the `RecoveryHeatmapCard` composable (roughly 10 lines after the `sharedAspect` assignment) and replace it:
  ```kotlin
  // BEFORE:
  val mapModifier = Modifier
      .widthIn(max = HOME_RECOVERY_BODY_MAP_MAX_WIDTH)
      .fillMaxWidth()
      .padding(top = 2.dp)
      .aspectRatio(sharedAspect)
      .offset(x = BODY_MAP_VISUAL_LEFT_NUDGE, y = BODY_MAP_LABEL_LIFT)

  // AFTER:
  val mapModifier = Modifier
      .fillMaxWidth()
      .aspectRatio(sharedAspect)
  ```

- [ ] **Step 2: Fix RecoveryMapScreen — remove offset/widthIn, delete dead constants**

  Open `RecoveryMapScreen.kt`. Delete the three constant declarations near the top of `RecoveryBodyMap`:
  ```kotlin
  // DELETE these three lines:
  private val RECOVERY_BODY_MAP_MAX_WIDTH = 132.dp
  private val RECOVERY_BODY_MAP_SINGLE_WIDTH = 286.dp
  private val BODY_MAP_VISUAL_LEFT_NUDGE = (-24).dp
  ```
  Remove `import androidx.compose.foundation.layout.offset` and `import androidx.compose.foundation.layout.widthIn` if unused after the change.

  Locate `mapModifier` inside `RecoveryBodyMap` and replace:
  ```kotlin
  // BEFORE:
  val mapModifier = Modifier
      .widthIn(max = RECOVERY_BODY_MAP_SINGLE_WIDTH)
      .fillMaxWidth()
      .aspectRatio(sharedAspect)
      .offset(x = BODY_MAP_VISUAL_LEFT_NUDGE)

  // AFTER:
  val mapModifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 6.dp)
      .aspectRatio(sharedAspect)
  ```

- [ ] **Step 3: Build to verify no compile errors**

  Run:
  ```
  .\gradlew.bat :app:compileDebugKotlin --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL` with zero errors or warnings about the deleted identifiers.

- [ ] **Step 4: Build release APK**

  Run:
  ```
  .\gradlew.bat assembleRelease --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`. APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 5: Install on device and verify visually**

  Run:
  ```
  adb install -r app\build\outputs\apk\release\app-release.apk
  ```
  Expected: `Success`.
  
  Open the app. Navigate to: Home screen → scroll to Muscle Recovery card. Verify:
  - FRONT and BACK body outlines are fully visible within the card bounds
  - No body SVG clips into the header above the card
  - Labels "FRONT" / "BACK" appear above their respective maps without overlap

  Navigate to: Home → Recovery → (full Recovery screen). Verify:
  - FRONT/BACK pill buttons appear at the top
  - Tapping a pill shows the full body map BELOW the pills with no clipping
  - Body map does not overlap the pill row

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryHeatmapCard.kt
  git add app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryMapScreen.kt
  git commit -m "fix(bodymap): remove Modifier.offset hacks that caused clipping on device

  Modifier.offset() moves visual rendering after layout measurement, so
  the canvas drew outside its slot and clipped against parent containers.
  Fix: replace offset/widthIn modifier chain with fillMaxWidth + aspectRatio
  in both RecoveryHeatmapCard and RecoveryMapScreen. The SVG transform math
  in BodyMapCanvas.kt (fitWidth/fitHeight canonical box, TOP anchor) is
  correct and unchanged.

  Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
  ```

---

## Task 2: Commit ForgeFox widget system

**Context:** Five new Kotlin files implement a responsive Glance widget system with a visual-state machine (`WidgetVisualState` enum + `WidgetVisualStateResolver`) and per-state mascot art (`ForgeFoxWidgetAssets`). Three existing widgets (`BadgeWidget`, `DashboardWidget`, `WarRoomWidget`) are updated to use `SizeMode.Responsive` and delegate to the new `ForgeFoxWidgetContent` composable. `WidgetState` gains 6 new fields. 11 PNG assets + 3 XML updates are included.

**Files:**
- New (commit as-is): `ForgeFoxWidgetAssets.kt`, `ForgeFoxWidgetContent.kt`, `ForgeFoxWidgetPresentation.kt`, `ForgeFoxWidgetSampleStates.kt`, `WidgetVisualStateResolver.kt`
- Modified (commit as-is): `BadgeWidget.kt`, `DashboardWidget.kt`, `WarRoomWidget.kt`, `WidgetDataRepository.kt`, `WidgetState.kt`
- New XML: `badge_widget_info.xml`, `dashboard_widget_info.xml`, `warroom_widget_info.xml`
- New PNG assets (11 files under `drawable-nodpi/`)
- New tests: `ForgeFoxWidgetPresentationTest.kt`, `WidgetVisualStateResolverTest.kt`

- [ ] **Step 1: Run widget unit tests**

  Run:
  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.ironlog.app.widget.*" --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`, all widget tests pass (0 failures).

- [ ] **Step 2: Full release build to confirm no compile regressions**

  Run:
  ```
  .\gradlew.bat assembleRelease --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetAssets.kt
  git add app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetContent.kt
  git add app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetPresentation.kt
  git add app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetSampleStates.kt
  git add app/src/main/java/com/ironlog/app/widget/WidgetVisualStateResolver.kt
  git add app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt
  git add app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt
  git add app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt
  git add app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt
  git add app/src/main/java/com/ironlog/app/widget/WidgetState.kt
  git add app/src/main/res/xml/badge_widget_info.xml
  git add app/src/main/res/xml/dashboard_widget_info.xml
  git add app/src/main/res/xml/warroom_widget_info.xml
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_active.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_at_risk.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_coach.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_comeback.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_done.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_early_workout.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_mission_complete.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_pb.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_recovery.png"
  git add "app/src/main/res/drawable-nodpi/forgefox_widget_workout_soon.png"
  git add "app/src/main/res/drawable-nodpi/ic_forge_streak_dumbbell.png"
  git add app/src/test/java/com/ironlog/app/widget/ForgeFoxWidgetPresentationTest.kt
  git add app/src/test/java/com/ironlog/app/widget/WidgetVisualStateResolverTest.kt
  git commit -m "feat: ForgeFox widget system — responsive layouts, visual state machine, mascot art

  Adds WidgetVisualState enum (10 states) + WidgetVisualStateResolver that maps
  live WidgetState fields to the correct visual state. ForgeFoxWidgetAssets maps
  states to drawable resources. ForgeFoxWidgetContent provides the unified Glance
  composable. BadgeWidget, DashboardWidget, and WarRoomWidget switch to
  SizeMode.Responsive (SMALL/MEDIUM/TALL/WIDE breakpoints). WidgetState gains
  weeklyCompletion, todayCompleted, minutesUntilWorkout, isAtRisk, isRecoveryDay,
  hasNewPb, and visualState fields. 11 PNG mascot assets included.

  Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
  ```

---

## Task 3: Commit calibration backfill + import/export

**Context:** `AthleteCalibrationEntity` gains a new `historicalTrainingDaysPerWeek: Int = 3` field (default 3). ObjectBox schema in `default.json` / `default.json.bak` is updated to include this property. `ImportExportRepository` gains export serialization for the new field, import deserialization with `optInt("historical_training_days_per_week", 3).coerceIn(1,7)`, and a new `backfillMissingAthleteStateRows()` function that synthesizes calibration + gamification rows from legacy exports that predate the calibration entity. A new test covers the backfill logic.

**Files:**
- Modify: `AthleteCalibrationEntity.kt`, `ImportExportRepository.kt`, `default.json`, `default.json.bak`
- New: `ImportExportRepositoryCalibrationBackfillTest.kt`

- [ ] **Step 1: Verify ObjectBox schema is consistent**

  Run:
  ```
  .\gradlew.bat :app:compileDebugKotlin --no-daemon
  ```
  ObjectBox annotation processing runs at compile time. If the schema is inconsistent (e.g., property ID collision), it will fail here with an error containing "entity" or "property" in the message. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the backfill test**

  Run:
  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.ironlog.app.data.repository.ImportExportRepositoryCalibrationBackfillTest" --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 3: Full release build**

  Run:
  ```
  .\gradlew.bat assembleRelease --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/ironlog/app/data/objectbox/AthleteCalibrationEntity.kt
  git add app/objectbox-models/default.json
  git add app/objectbox-models/default.json.bak
  git add app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt
  git add app/src/test/java/com/ironlog/app/data/repository/ImportExportRepositoryCalibrationBackfillTest.kt
  git commit -m "feat: add historicalTrainingDaysPerWeek to calibration + backfill legacy exports

  AthleteCalibrationEntity gains historicalTrainingDaysPerWeek (default 3).
  ImportExportRepository serializes/deserializes the new field and adds
  backfillMissingAthleteStateRows() to synthesize calibration + gamification
  rows from legacy exports that predate the calibration table.

  Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
  ```

---

## Task 4: Commit onboarding + gamification enhancements

**Context:** `OnboardingDraft` and `OnboardingViewModel` gain `historicalTrainingDaysPerWeek`. `Step3Baseline` receives the new field + two display props (`seededGrade`, `seededStats`) from `OnboardingScreen`. `OnboardingScreen` builds a `seededSnapshot` via `buildOnboardingLedgerSnapshot(draft)` (from new `OnboardingLedgerPreview.kt`) and passes it to Step 3 and the final badge display. `IronLedgerEngine` and `GamificationViewModel` have related changes. Several onboarding persistence and view model tests are updated. `AppNavigator.kt` passes the new field through to settings commit.

**Files:**
- Modify: `OnboardingScreen.kt`, `OnboardingViewModel.kt`, `Step3Baseline.kt`, `IronLedgerEngine.kt`, `GamificationViewModel.kt`, `AppNavigator.kt`, `AndroidManifest.xml`
- New: `OnboardingLedgerPreview.kt`
- Modify (tests): `OnboardingPersistenceTest.kt`, `OnboardingViewModelTest.kt`, `ParityClosureLogicTest.kt`, `IronLedgerBaselineSeedingTest.kt`

- [ ] **Step 1: Run onboarding + gamification tests**

  Run:
  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.ironlog.app.navigation.OnboardingPersistenceTest" --tests "com.ironlog.app.ui.screens.onboarding.OnboardingViewModelTest" --tests "com.ironlog.app.ui.screens.ParityClosureLogicTest" --tests "com.ironlog.app.domain.gamification.IronLedgerBaselineSeedingTest" --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 2: Full release build**

  Run:
  ```
  .\gradlew.bat assembleRelease --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check AndroidManifest.xml diff for anything unexpected**

  Run:
  ```
  git diff HEAD -- app/src/main/AndroidManifest.xml
  ```
  Verify the diff contains only expected changes (e.g., new activity/receiver declarations for widgets or onboarding). No `<uses-permission>` additions should be present unless they are intentional. If something looks wrong, do NOT stage it and investigate before proceeding.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/ironlog/app/ui/screens/OnboardingScreen.kt
  git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModel.kt
  git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingLedgerPreview.kt
  git add app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step3Baseline.kt
  git add app/src/main/java/com/ironlog/app/domain/gamification/IronLedgerEngine.kt
  git add app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt
  git add app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt
  git add app/src/main/AndroidManifest.xml
  git add app/src/test/java/com/ironlog/app/navigation/OnboardingPersistenceTest.kt
  git add app/src/test/java/com/ironlog/app/domain/gamification/IronLedgerBaselineSeedingTest.kt
  git add app/src/test/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModelTest.kt
  git add app/src/test/java/com/ironlog/app/ui/screens/ParityClosureLogicTest.kt
  git commit -m "feat: onboarding tracks historicalTrainingDaysPerWeek, live IronLedger grade preview

  OnboardingDraft/VM gain historicalTrainingDaysPerWeek. Step3Baseline shows
  a live grade preview seeded from current draft answers via
  buildOnboardingLedgerSnapshot(). Final badge on Step8 uses the same snapshot
  instead of calculateQualifiedBadge(). AppNavigator persists the new field.
  IronLedgerEngine and GamificationViewModel updated for baseline seeding.

  Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
  ```

---

## Task 5: Commit progress photo compare logic

**Context:** New `ProgressPhotoCompareLogic.kt` encapsulates date-bucketing and comparison state for before/after photo pairs. `ProgressPhotosScreen.kt` is updated to use it. A new test `ProgressPhotoCompareLogicTest.kt` covers the logic.

**Files:**
- New: `ProgressPhotoCompareLogic.kt`, `ProgressPhotoCompareLogicTest.kt`
- Modify: `ProgressPhotosScreen.kt`

- [ ] **Step 1: Run the compare logic test**

  Run:
  ```
  .\gradlew.bat :app:testDebugUnitTest --tests "com.ironlog.app.ui.screens.ProgressPhotoCompareLogicTest" --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 2: Full release build**

  Run:
  ```
  .\gradlew.bat assembleRelease --no-daemon
  ```
  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotoCompareLogic.kt
  git add app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt
  git add app/src/test/java/com/ironlog/app/ui/screens/ProgressPhotoCompareLogicTest.kt
  git commit -m "feat: progress photo before/after compare logic

  Extracts ProgressPhotoCompareLogic for date-bucketed before/after photo pair
  selection. ProgressPhotosScreen updated to use the new logic class.

  Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
  ```

---

## Task 6: Update AGENTS.md with the 19 Codex commits

**Context:** `Z:\KOTLIN\AGENTS.md` already has a 2026-06-07 Codex entry documenting the failed bodymap work and uncommitted changes — but it does NOT document the 19 actual commits Codex made (from `8088972` to `7c66fb9`). Add a new entry for those commits.

**File:** `Z:\KOTLIN\AGENTS.md`

- [ ] **Step 1: Insert the 19-commit summary entry into AGENTS.md**

  Open `Z:\KOTLIN\AGENTS.md`. Locate the existing 2026-06-07 Codex entry. Add the following NEW entry ABOVE it (directly below the `---` separator at the top of the file's entry list):

  ```markdown
  ### 2026-06-07 — Codex: features and polish (19 commits, `8088972`–`7c66fb9`)

  **ExerciseProgressScreen — Vico charts (commits `0546a45`, `3450046`)**
  - Replaced ~75-line manual `Canvas` drawing in `LineChartCard` / `BarChartCard` with Vico 2.1.2 `CartesianChartHost` + `CartesianChartModelProducer`.
  - Added `com.patrykandpatrick.vico:compose-m3:2.1.2` to `app/build.gradle.kts`.
  - Fixed Compose composition-rules violation: hoisted producer + `LaunchedEffect` above empty-state early-return.
  - Added `CartesianValueFormatter` to x-axis so dates render instead of indices.
  - File: `app/src/main/java/com/ironlog/app/ui/screens/stats/ExerciseProgressScreen.kt`

  **ProgressPhotosScreen — Coil 3 (commits `ed9d69b`, `cb2e0e5`, `8a9bcea`)**
  - Replaced `BitmapFactory` manual bitmap loading with `AsyncImage` from Coil 3.
  - Added `coil-android` artifact to `app/build.gradle.kts` for `content://` URI support.
  - Added `isNotBlank()` guard on all 4 `AsyncImage` model expressions.
  - File: `app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt`

  **PlanQrScanScreen — Accompanist + permission fix (commits `bd0d197`, `3e6d853`)**
  - Replaced `ActivityResultContracts` boilerplate with Accompanist permission API.
  - Fixed rationale branches: `shouldShowRationale` triggers re-request; permanent denial opens Settings.
  - Fixed scanner executor leak via `DisposableEffect` shutdown.
  - File: `app/src/main/java/com/ironlog/app/ui/screens/plans/PlanQrScanScreen.kt`

  **CloudAiEngine — Ktor 3.1.3 (commits `e43dad5`, `13b747f`, `d920ab0`)**
  - Replaced raw `OkHttp + JSONObject` with `Ktor 3.1.3` HTTP client.
  - Added `expectSuccess = true`; clarified `jsonMode` KDoc.
  - File: `app/src/main/java/com/ironlog/app/domain/intelligence/CloudAiEngine.kt`

  **Timber logging (commits `8283182`, `6f49573`)**
  - Added `Timber 5.0.1` dependency; planted `DebugTree` in `IronLogApplication`.
  - Instrumented 5 critical `runCatching` sites with `Timber.e(e, ...)`.
  - Quality-reviewed and improved instrumentation coverage.

  **kotlinx-datetime migration (commits `dccd7a0`, `ad8f074`)**
  - Migrated `formatHistoryDate` and `startOfWeekMillis` to `kotlinx-datetime 0.6.2`.
  - Completed migration in `BodyWeightScreen.kt` and `Dates.kt`.

  **Polish, onboarding, misc (commits `13b492d`, `3cfe3c8`, `755f1c7`, `80bd42c`, `7c66fb9`)**
  - Added clarifying comments per final code review.
  - Added missing stub fields to unblock release build.
  - Restored IronLog onboarding design.
  - Replaced placeholder iron ledger badges with image-backed assets.
  - Fixed onboarding completion reveal hold timing.

  **UI polish (commit `8088972`)**
  - Active workout: "Watch on YouTube" in exercise 3-dot menu; `contentPadding` bottom fix; `RestTimerPanel` `navigationBarsPadding`; MINIMIZE touch target; swap-sheet empty state / `maxLines`.
  - Stats: Regenerate row touch target; `QuickNavButton`/`MiniAction`/`CoachMiniAction` clip-before-background for ripple containment; PB empty state horizontal padding.
  - CreateExercise: `navigationBarsPadding` bottom spacer; copy-picker `maxLines`.
  - PlanQrScan: executor `DisposableEffect` shutdown; scan-error `maxLines`; instruction overlay scrim + `navigationBarsPadding`.

  **Build verification:** All 19 commits build successfully. Release APK installed on device `R5CY925TFHT` via `adb install -r`.
  ```

- [ ] **Step 2: Also add an entry for the bodymap fix + uncommitted commits (from Task 1–5)**

  Directly below the entry just inserted (still above the existing Codex entry), add:

  ```markdown
  ### 2026-06-07 — Fix bodymap clipping + commit Codex uncommitted work

  **Bodymap fix (`RecoveryHeatmapCard.kt`, `RecoveryMapScreen.kt`)**
  - Removed `Modifier.offset(x=-24.dp, y=-38.dp)` and `Modifier.widthIn()` hacks from both files. These caused body maps to render outside their Compose layout slot, clipping against parent containers on device.
  - Kept all `BodyMapCanvas.kt` changes from Codex (fitWidth/fitHeight canonical box, TOP anchor offsetY=0f) — those are correct.
  - Pager (FRONT/BACK pills + HorizontalPager) in RecoveryMapScreen preserved.

  **ForgeFox widget system committed** — 5 new Kotlin files, 3 modified widgets, 11 PNG mascot assets, 3 XML updates. `WidgetVisualState` enum + `WidgetVisualStateResolver` + `ForgeFoxWidgetContent` composable.

  **Calibration backfill committed** — `AthleteCalibrationEntity` gains `historicalTrainingDaysPerWeek`. `ImportExportRepository` serializes it and adds `backfillMissingAthleteStateRows()` for legacy export compatibility. ObjectBox schema updated.

  **Onboarding/gamification committed** — `OnboardingDraft`/`VM` gain `historicalTrainingDaysPerWeek`. Step3Baseline shows live IronLedger grade preview via `buildOnboardingLedgerSnapshot()`. `IronLedgerEngine` + `GamificationViewModel` updated for baseline seeding.

  **Progress photo compare committed** — New `ProgressPhotoCompareLogic.kt` + test. `ProgressPhotosScreen` updated to use it.
  ```

---

## Self-Review

**Spec coverage:** All 5 bodies of uncommitted work are covered (bodymap, widgets, calibration, onboarding, photos). AGENTS.md update included.

**Placeholder scan:** None found. All code snippets show actual before/after values from git diff.

**Type consistency:** `BodyHalfCanvas` still receives `fitWidth`/`fitHeight` params as defined in `BodyMapCanvas.kt`; mapModifier chains are complete Modifier expressions with no references to deleted constants.

**Scope:** Six independent tasks, each producting a clean commit. Largest single task (onboarding, Task 4) runs its own tests before committing.
