# Forge Fox Widget Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Polish the existing Forge Fox Android home-screen widgets so they look less like placeholders while preserving real app data wiring.

**Architecture:** Keep the existing Jetpack Glance widget architecture and receiver classes. Extract pure widget presentation/layout decisions into small testable Kotlin helpers, then make `ForgeFoxWidgetContent.kt` use those helpers for size-specific composition. Replace the rough temporary vector icon with the cleaned reference-based official icon asset.

**Tech Stack:** Kotlin, Jetpack Glance, Android resource drawables, JVM unit tests, Gradle.

---

### Task 1: Lock State And Layout Behavior With Tests

**Files:**
- Modify: `app/src/test/java/com/ironlog/app/widget/WidgetVisualStateResolverTest.kt`
- Create: `app/src/test/java/com/ironlog/app/widget/ForgeFoxWidgetPresentationTest.kt`
- Modify: `app/src/main/java/com/ironlog/app/widget/WidgetVisualStateResolver.kt`
- Create: `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetPresentation.kt`

- [x] **Step 1: Add a failing resolver test for safe active streak**

Add this test to `WidgetVisualStateResolverTest.kt`:

```kotlin
@Test
fun `safe incomplete streak remains active instead of coach mode`() {
    val state = resolveWidgetVisualState(
        WidgetVisualInputs(
            streakDays = 23,
            todayCompleted = false,
            minutesUntilWorkout = null,
            isAtRisk = false,
            isRecoveryDay = false,
            hasRecentNewPb = false,
            returnedAfterGap = false,
            weekSessionsCount = 2,
            weeklyGoal = 4,
            scheduledWorkoutHour = null,
        )
    )

    assertEquals(WidgetVisualState.ACTIVE_STREAK, state)
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.ironlog.app.widget.WidgetVisualStateResolverTest --no-daemon
```

Expected: FAIL because the current resolver returns `NO_EXCUSES`.

- [x] **Step 3: Add failing presentation tests**

Create `ForgeFoxWidgetPresentationTest.kt`:

```kotlin
package com.ironlog.app.widget

import com.ironlog.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ForgeFoxWidgetPresentationTest {
    @Test
    fun `layout resolver classifies canonical widget sizes`() {
        assertEquals(ForgeWidgetLayoutClass.SMALL, resolveForgeWidgetLayoutClass(110f, 110f, ForgeWidgetLayoutClass.MEDIUM))
        assertEquals(ForgeWidgetLayoutClass.MEDIUM, resolveForgeWidgetLayoutClass(180f, 180f, ForgeWidgetLayoutClass.SMALL))
        assertEquals(ForgeWidgetLayoutClass.TALL, resolveForgeWidgetLayoutClass(220f, 320f, ForgeWidgetLayoutClass.MEDIUM))
        assertEquals(ForgeWidgetLayoutClass.WIDE, resolveForgeWidgetLayoutClass(320f, 160f, ForgeWidgetLayoutClass.MEDIUM))
    }

    @Test
    fun `streak icon uses the single official drawable resource`() {
        assertEquals(R.drawable.ic_forge_streak_dumbbell, ForgeFoxWidgetAssets.streakIcon)
    }

    @Test
    fun `presentation keeps short labels for constrained widgets`() {
        val active = forgePresentationFor(WidgetVisualState.ACTIVE_STREAK, null)
        val atRisk = forgePresentationFor(WidgetVisualState.AT_RISK, null)

        assertEquals("DAY STREAK", active.title)
        assertEquals("AT RISK", atRisk.title)
        assertEquals("Don't let it break!", atRisk.message)
    }
}
```

- [x] **Step 4: Run the focused presentation test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.ironlog.app.widget.ForgeFoxWidgetPresentationTest --no-daemon
```

Expected: FAIL because `resolveForgeWidgetLayoutClass`, `ForgeFoxWidgetAssets.streakIcon`, and `forgePresentationFor` do not exist yet.

### Task 2: Implement Testable Presentation Helpers

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetAssets.kt`
- Create: `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetPresentation.kt`
- Modify: `app/src/main/java/com/ironlog/app/widget/WidgetVisualStateResolver.kt`

- [x] **Step 1: Add `streakIcon` to `ForgeFoxWidgetAssets`**

Add:

```kotlin
@DrawableRes
val streakIcon: Int = R.drawable.ic_forge_streak_dumbbell
```

- [x] **Step 2: Create presentation helper**

Create `ForgeFoxWidgetPresentation.kt` with `ForgeWidgetPresentation`, `resolveForgeWidgetLayoutClass`, and `forgePresentationFor`. Use the same state colors as the current widget, but centralize them outside the composable.

- [x] **Step 3: Fix active-streak resolver priority**

Change `resolveWidgetVisualState()` so the default safe incomplete state is `ACTIVE_STREAK`; reserve `NO_EXCUSES` for a late-day nudge when there is a streak, no completion, no workout-soon, and the scheduled workout hour is missing or already earlier than the current risk window.

- [x] **Step 4: Verify GREEN for focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.ironlog.app.widget.WidgetVisualStateResolverTest --tests com.ironlog.app.widget.ForgeFoxWidgetPresentationTest --no-daemon
```

Expected: both test classes pass.

### Task 3: Replace The Rough Icon With The Official Extracted Mark

**Files:**
- Delete: `app/src/main/res/drawable/ic_forge_streak_dumbbell.xml`
- Create: `app/src/main/res/drawable-nodpi/ic_forge_streak_dumbbell.png`
- Source: `artifacts/fire-dumbbell-symbol/forge_streak_dumbbell_extracted_transparent.png`

- [x] **Step 1: Remove the temporary vector**

Delete the rough vector drawable.

- [x] **Step 2: Add the cleaned extracted PNG under the same resource name**

Copy the extracted transparent PNG to `drawable-nodpi/ic_forge_streak_dumbbell.png`, preserving the `R.drawable.ic_forge_streak_dumbbell` resource name.

- [x] **Step 3: Confirm only one official icon resource exists**

Run:

```powershell
Get-ChildItem -Path .\app\src\main\res -Recurse -File | Where-Object { $_.Name -like 'ic_forge_streak_dumbbell*' } | Select-Object FullName
```

Expected: one file, the PNG in `drawable-nodpi`.

### Task 4: Polish Glance Layout Composition

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetContent.kt`

- [x] **Step 1: Use the extracted presentation helpers**

Remove the private presentation data class and private layout resolver from `ForgeFoxWidgetContent.kt`. Use `forgePresentationFor(state.visualState, state.minutesUntilWorkout)` and `resolveForgeWidgetLayoutClass(...)`.

- [x] **Step 2: Tighten small widgets**

Small widgets use icon size `30.dp`, number size `34.sp`, title `12.sp`, no weekly row, and a mascot area that emphasizes the face.

- [x] **Step 3: Tighten medium widgets**

Medium widgets use a fixed top lockup, a larger mascot area, and a compact weekly row band at the bottom. The mascot should not compete with the weekly row.

- [x] **Step 4: Tighten tall widgets**

Tall widgets reserve top for lockup/title/message, middle for large mascot, and bottom for weekly row. Avoid floating raw dots.

- [x] **Step 5: Tighten wide widgets**

Wide widgets use a fixed left column and a right mascot area. The layout should not look like a stretched square.

### Task 5: Generate Preview And Verify

**Files:**
- Create: `artifacts/actual-widget-preview-2026-06-07/forgefox_actual_widget_preview_board_polished.png`

- [x] **Step 1: Generate a local preview board**

Render the actual current icon, colors, text, and mascot assets into a comparison board.

- [x] **Step 2: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: unit tests pass.

- [x] **Step 3: Run debug build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: debug APK builds successfully.

- [x] **Step 4: Check emulator availability**

Run:

```powershell
adb devices
```

Expected: if no device is attached, report that live launcher screenshot validation is unavailable.
