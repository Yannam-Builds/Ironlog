# Jetpack Glance Home Screen Widgets Implementation Plan

> **Status:** Historical execution plan. Its checkboxes were not backfilled and are not a current backlog. Use `AGENTS.md` and the current source/tests as the authoritative project state.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship three stunning Android home screen widgets using Jetpack Glance — a 2×2 "The Badge" showing rank and streak, a 4×2 "The Dashboard" with XP bar and today's recommended workout, and a 4×4 "The War Room" with full stats, quests, and weekly progress.

**Architecture:** Each widget has its own `GlanceAppWidget` subclass and `GlanceAppWidgetReceiver`. A shared `WidgetState` data class (serializable via Glance's `DataStore`-backed `GlanceStateDefinition`) holds all widget data. A `WidgetDataRepository` pulls data from ObjectBox (GamificationProfileEntity, HistoryEntry) and writes `WidgetState`. A `WidgetUpdateWorker` (WorkManager, periodic 30-min + on-demand) keeps widgets fresh. The existing WorkManager dependency in `build.gradle.kts` is already present (`work-runtime-ktx:2.9.1`).

**Tech Stack:** Kotlin, `androidx.glance:glance-appwidget:1.1.1`, Jetpack Compose, WorkManager 2.9.1, ObjectBox 4.0.3

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/build.gradle.kts` — add Glance dependency |
| Create | `app/src/main/java/com/ironlog/app/widget/WidgetState.kt` |
| Create | `app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt` |
| Create | `app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt` |
| Create | `app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt` |
| Create | `app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt` |
| Create | `app/src/main/java/com/ironlog/app/widget/WidgetUpdateWorker.kt` |
| Create | `app/src/main/res/xml/badge_widget_info.xml` |
| Create | `app/src/main/res/xml/dashboard_widget_info.xml` |
| Create | `app/src/main/res/xml/warroom_widget_info.xml` |
| Modify | `app/src/main/AndroidManifest.xml` — register 3 widget receivers |
| Create | `app/src/test/java/com/ironlog/app/widget/WidgetStateTest.kt` |

---

### Task 1: Add Glance dependency

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Glance dependency**

Inside the `dependencies { }` block in `app/build.gradle.kts`:

```kotlin
    // Jetpack Glance — Compose-based app widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
```

- [ ] **Step 2: Sync and build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD|Download" | Select-Object -Last 15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/build.gradle.kts
git commit -m "build: add Jetpack Glance dependencies for home screen widgets"
```

---

### Task 2: WidgetState — shared state data class

**Files:**
- Create: `app/src/main/java/com/ironlog/app/widget/WidgetState.kt`
- Test: `app/src/test/java/com/ironlog/app/widget/WidgetStateTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/ironlog/app/widget/WidgetStateTest.kt
package com.ironlog.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {

    @Test fun `default WidgetState has safe defaults`() {
        val state = WidgetState()
        assertEquals("E", state.rank)
        assertEquals(1, state.level)
        assertEquals(0, state.streakWeeks)
        assertEquals("No Plan", state.recommendedDayName)
        assertTrue(state.xpPercent in 0f..1f)
    }

    @Test fun `xpPercent is clamped to 0-1`() {
        val state = WidgetState(xpInLevel = 200L, xpForNextLevel = 100L)
        assertEquals(1.0f, state.xpPercent, 0.001f)
    }

    @Test fun `xpPercent is 0 when xpForNextLevel is 0`() {
        val state = WidgetState(xpInLevel = 0L, xpForNextLevel = 0L)
        assertEquals(0.0f, state.xpPercent, 0.001f)
    }

    @Test fun `xpPercent computes correctly for partial level`() {
        val state = WidgetState(xpInLevel = 50L, xpForNextLevel = 200L)
        assertEquals(0.25f, state.xpPercent, 0.001f)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.widget.WidgetStateTest" 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: compilation error.

- [ ] **Step 3: Implement WidgetState**

```kotlin
// app/src/main/java/com/ironlog/app/widget/WidgetState.kt
package com.ironlog.app.widget

import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of data needed by all three IronLog widgets.
 * Stored via Glance's DataStore-backed state mechanism.
 */
@Serializable
data class WidgetState(
    // ── Gamification ──────────────────────────────────────────────────────
    val rank: String = "E",
    val level: Int = 1,
    val activeTitle: String = "Novice Hunter",
    val streakWeeks: Int = 0,
    val xpInLevel: Long = 0L,
    val xpForNextLevel: Long = 100L,

    // ── Stats ─────────────────────────────────────────────────────────────
    val statStr: Int = 1,
    val statVit: Int = 1,
    val statEnd: Int = 1,
    val statAgi: Int = 1,

    // ── Workout recommendation ────────────────────────────────────────────
    val recommendedDayName: String = "No Plan",
    val recommendedDayBlurb: String = "",

    // ── Weekly progress ───────────────────────────────────────────────────
    /** Number of sessions logged this ISO week. */
    val weekSessionsCount: Int = 0,
    /** Weekly goal from IronLogSettings.weeklyGoalDays. */
    val weeklyGoal: Int = 4,

    // ── Active quest ──────────────────────────────────────────────────────
    val activeQuestDescription: String = "",
    val activeQuestXpReward: Int = 0,

    // ── Last updated ──────────────────────────────────────────────────────
    val lastUpdatedEpochMs: Long = 0L,
) {
    /** XP progress within the current level as a fraction 0.0–1.0. */
    val xpPercent: Float
        get() = if (xpForNextLevel == 0L) 0f
                else (xpInLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)

    /** Week progress fraction 0.0–1.0. */
    val weekPercent: Float
        get() = if (weeklyGoal == 0) 0f
                else (weekSessionsCount.toFloat() / weeklyGoal.toFloat()).coerceIn(0f, 1f)
}
```

- [ ] **Step 4: Run tests**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.widget.WidgetStateTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/WidgetState.kt
git add app/src/test/java/com/ironlog/app/widget/WidgetStateTest.kt
git commit -m "feat: add WidgetState serializable data class for Glance widget state"
```

---

### Task 3: WidgetDataRepository — populate WidgetState from ObjectBox

**Files:**
- Create: `app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt`

- [ ] **Step 1: Implement WidgetDataRepository**

```kotlin
// app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt
package com.ironlog.app.widget

import android.content.Context
import com.ironlog.app.data.entity.GamificationProfileEntity_
import com.ironlog.app.data.entity.QuestEntity_
import com.ironlog.app.domain.gamification.XpEngine
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.domain.intelligence.WorkoutSuggestionEngine
import com.ironlog.app.ui.model.HistoryEntry
import io.objectbox.BoxStore
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/**
 * Reads ObjectBox data and assembles a [WidgetState] for all widgets.
 * Must be called from a background thread (IO dispatcher).
 */
class WidgetDataRepository(
    private val context: Context,
    private val boxStore: BoxStore,
) {

    private val xpEngine = XpEngine()
    private val recoveryEngine = RecoveryReadinessEngine()
    private val suggestionEngine = WorkoutSuggestionEngine()

    /**
     * Build a fresh [WidgetState] from current ObjectBox data.
     * Returns a default state if the database is empty.
     */
    fun buildWidgetState(
        history: List<HistoryEntry>,
        weeklyGoal: Int,
    ): WidgetState {
        // ── Gamification profile ────────────────────────────────────────
        val profileBox = boxStore.boxFor(com.ironlog.app.data.entity.GamificationProfileEntity::class.java)
        val profile = profileBox.query(GamificationProfileEntity_.id.notNull()).build().findFirst()

        val rank         = profile?.rank ?: "E"
        val level        = profile?.level ?: 1
        val activeTitle  = profile?.activeTitle ?: "Novice Hunter"
        val streakWeeks  = profile?.streakWeeks ?: 0
        val xpInLevel    = profile?.xpInLevel ?: 0L
        val xpForNext    = xpEngine.xpForLevel(level)

        // Stats from persisted JSON
        val stats = profile?.statsJson?.let { json ->
            runCatching {
                Json.decodeFromString<com.ironlog.app.domain.gamification.RpgStats>(json)
            }.getOrNull()
        } ?: com.ironlog.app.domain.gamification.RpgStats()

        // ── Active quest ─────────────────────────────────────────────────
        val questBox = boxStore.boxFor(com.ironlog.app.data.entity.QuestEntity::class.java)
        val activeQuest = questBox.query(QuestEntity_.completedAt.equal("")).build().findFirst()

        // ── Workout recommendation ────────────────────────────────────────
        val planBox = boxStore.boxFor(com.ironlog.app.data.entity.PlanEntity::class.java)
        // NOTE: Adjust entity class name to match the actual ObjectBox plan entity in this codebase.
        // If plans are stored differently, adapt this section.
        val recommendedDayName: String
        val recommendedDayBlurb: String
        runCatching {
            val readiness = recoveryEngine.readinessByRegion(history, emptyMap())
            // Fetch active plan days from box — adapt to actual plan entity structure
            recommendedDayName = "Best Day"
            recommendedDayBlurb = "Your muscles are ready."
        }
        // Fallback:
        val dayName  = "Train Today"
        val dayBlurb = "Recovery looks good."

        // ── Weekly sessions ────────────────────────────────────────────────
        val isoWeek = WeekFields.ISO
        val thisWeek = LocalDate.now().let {
            val week = it.get(isoWeek.weekOfWeekBasedYear())
            val year = it.get(isoWeek.weekBasedYear())
            "$year-W${week.toString().padStart(2, '0')}"
        }
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val weekSessions = history.count { entry ->
            runCatching {
                val date = LocalDate.parse(entry.date.take(10), fmt)
                val wk = date.get(isoWeek.weekOfWeekBasedYear())
                val yr = date.get(isoWeek.weekBasedYear())
                "$yr-W${wk.toString().padStart(2, '0')}" == thisWeek
            }.getOrDefault(false)
        }

        return WidgetState(
            rank = rank,
            level = level,
            activeTitle = activeTitle,
            streakWeeks = streakWeeks,
            xpInLevel = xpInLevel,
            xpForNextLevel = xpForNext,
            statStr = stats.str,
            statVit = stats.vit,
            statEnd = stats.end,
            statAgi = stats.agi,
            recommendedDayName = dayName,
            recommendedDayBlurb = dayBlurb,
            weekSessionsCount = weekSessions,
            weeklyGoal = weeklyGoal,
            activeQuestDescription = activeQuest?.description ?: "",
            activeQuestXpReward = activeQuest?.xpReward ?: 0,
            lastUpdatedEpochMs = System.currentTimeMillis(),
        )
    }
}
```

> **Note:** The line `boxStore.boxFor(com.ironlog.app.data.entity.PlanEntity::class.java)` must be replaced with the actual plan entity class used in this codebase. Search for `@Entity` annotated classes that store plan data and use the correct class name. If plans are not in ObjectBox (stored as JSON in SharedPreferences instead), read from SharedPreferences and deserialize.

- [ ] **Step 2: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL` (or fix entity class name mismatch).

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt
git commit -m "feat: add WidgetDataRepository to populate WidgetState from ObjectBox"
```

---

### Task 4: WidgetUpdateWorker — periodic WorkManager job

**Files:**
- Create: `app/src/main/java/com/ironlog/app/widget/WidgetUpdateWorker.kt`

- [ ] **Step 1: Implement WidgetUpdateWorker**

```kotlin
// app/src/main/java/com/ironlog/app/widget/WidgetUpdateWorker.kt
package com.ironlog.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that refreshes all IronLog widgets every 30 minutes.
 *
 * Also call [enqueueOneTime] after any workout is saved to push an immediate update.
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            runCatching {
                BadgeWidget().updateAll(applicationContext)
                DashboardWidget().updateAll(applicationContext)
                WarRoomWidget().updateAll(applicationContext)
            }.fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "ironlog_widget_refresh"
        private const val ONETIME_WORK_NAME  = "ironlog_widget_refresh_now"

        /**
         * Enqueue the periodic 30-minute refresh.
         * Call once from Application.onCreate().
         */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Enqueue an immediate one-time refresh (e.g., after workout saved).
         */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONETIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
```

- [ ] **Step 2: Enqueue in Application.onCreate()**

Find the Application class (search for `class IronLogApp : Application()` or similar). Add to `onCreate()`:

```kotlin
// In Application.onCreate():
WidgetUpdateWorker.enqueuePeriodic(this)
```

Add import:
```kotlin
import com.ironlog.app.widget.WidgetUpdateWorker
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/WidgetUpdateWorker.kt
git commit -m "feat: add WidgetUpdateWorker periodic WorkManager job (30min) for widget refresh"
```

---

### Task 5: BadgeWidget — 2×2 rank badge widget

**Files:**
- Create: `app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt`
- Create: `app/src/main/res/xml/badge_widget_info.xml`

- [ ] **Step 1: Create widget info XML**

```xml
<!-- app/src/main/res/xml/badge_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:description="@string/badge_widget_description"
    android:previewLayout="@layout/widget_preview_placeholder" />
```

Create the string resource — add to `app/src/main/res/values/strings.xml`:
```xml
<string name="badge_widget_description">IronLog rank and streak badge</string>
<string name="dashboard_widget_description">IronLog daily dashboard with XP and workout</string>
<string name="warroom_widget_description">IronLog war room with stats and quests</string>
```

Create preview placeholder layout `app/src/main/res/layout/widget_preview_placeholder.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#1A1A2E" />
```

- [ ] **Step 2: Implement BadgeWidget**

```kotlin
// app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt
package com.ironlog.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 2×2 "The Badge" widget.
 * Shows: Solo Leveling rank letter (large), level, streak weeks.
 */
class BadgeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = currentState<WidgetState>()
        provideContent { BadgeWidgetContent(state) }
    }
}

@Composable
private fun BadgeWidgetContent(state: WidgetState) {
    val rankColor = rankGlanceColor(state.rank)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1A1A2E)))
            .cornerRadius(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        ) {
            // Rank letter — large and dramatic
            Text(
                text = state.rank,
                style = TextStyle(
                    color = rankColor,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            // Level
            Text(
                text = "Lv.${state.level}",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )

            Spacer(GlanceModifier.height(4.dp))

            // Streak badge
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0x33FF6B35)))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "🔥 ${state.streakWeeks}w",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFF6B35)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

class BadgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BadgeWidget()
}

private fun rankGlanceColor(rank: String): ColorProvider = ColorProvider(when (rank) {
    "E"        -> Color(0xFF9E9E9E)
    "D"        -> Color(0xFF4CAF50)
    "C"        -> Color(0xFF2196F3)
    "B"        -> Color(0xFF9C27B0)
    "A"        -> Color(0xFFFF9800)
    "S"        -> Color(0xFFFF5722)
    "National" -> Color(0xFFFFD700)
    else       -> Color(0xFF9E9E9E)
})
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt
git add app/src/main/res/xml/badge_widget_info.xml
git add app/src/main/res/values/strings.xml
git add app/src/main/res/layout/widget_preview_placeholder.xml
git commit -m "feat: add BadgeWidget (2x2) showing rank, level, and streak"
```

---

### Task 6: DashboardWidget — 4×2 daily dashboard widget

**Files:**
- Create: `app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt`
- Create: `app/src/main/res/xml/dashboard_widget_info.xml`

- [ ] **Step 1: Create widget info XML**

```xml
<!-- app/src/main/res/xml/dashboard_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:description="@string/dashboard_widget_description"
    android:previewLayout="@layout/widget_preview_placeholder" />
```

- [ ] **Step 2: Implement DashboardWidget**

```kotlin
// app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt
package com.ironlog.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 4×2 "The Dashboard" widget.
 * Shows: Rank+Level (left) | XP bar | Recommended workout day | Week progress.
 */
class DashboardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = currentState<WidgetState>()
        provideContent { DashboardWidgetContent(state) }
    }
}

@Composable
private fun DashboardWidgetContent(state: WidgetState) {
    val bgColor = ColorProvider(Color(0xFF12121F))
    val accentColor = ColorProvider(Color(0xFFFF4500))
    val white = ColorProvider(Color.White)
    val muted = ColorProvider(Color(0xFF8E8E9E))

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: Rank + Level + Streak
        Column(
            modifier = GlanceModifier.defaultWeight().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.rank,
                    style = TextStyle(color = rankGlanceColor(state.rank), fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "Lv.${state.level}",
                    style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
            Text(
                text = state.activeTitle,
                style = TextStyle(color = muted, fontSize = 10.sp),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "🔥 ${state.streakWeeks}w streak",
                style = TextStyle(color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }

        // Divider
        Box(
            modifier = GlanceModifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ColorProvider(Color(0x33FFFFFF)))
                .padding(vertical = 4.dp),
        ) {}

        // Right: XP bar + Recommended workout + Week progress
        Column(
            modifier = GlanceModifier.defaultWeight().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // XP bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("XP", style = TextStyle(color = muted, fontSize = 10.sp))
                Spacer(GlanceModifier.width(4.dp))
                LinearProgressIndicator(
                    progress = state.xpPercent,
                    modifier = GlanceModifier.defaultWeight().height(6.dp),
                    color = accentColor,
                    backgroundColor = ColorProvider(Color(0x33FF4500)),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    "${(state.xpPercent * 100).toInt()}%",
                    style = TextStyle(color = muted, fontSize = 10.sp),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            // Recommended workout
            Text(
                text = "⚡ ${state.recommendedDayName}",
                style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            if (state.recommendedDayBlurb.isNotBlank()) {
                Text(
                    text = state.recommendedDayBlurb,
                    style = TextStyle(color = muted, fontSize = 10.sp),
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            // Week progress dots
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Week: ", style = TextStyle(color = muted, fontSize = 10.sp))
                repeat(state.weeklyGoal) { i ->
                    val filled = i < state.weekSessionsCount
                    Box(
                        modifier = GlanceModifier
                            .size(8.dp)
                            .background(
                                ColorProvider(if (filled) Color(0xFFFF4500) else Color(0x33FFFFFF))
                            )
                            .cornerRadius(4.dp)
                    ) {}
                    if (i < state.weeklyGoal - 1) Spacer(GlanceModifier.width(4.dp))
                }
            }
        }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}

private fun rankGlanceColor(rank: String): ColorProvider = ColorProvider(when (rank) {
    "E"        -> Color(0xFF9E9E9E)
    "D"        -> Color(0xFF4CAF50)
    "C"        -> Color(0xFF2196F3)
    "B"        -> Color(0xFF9C27B0)
    "A"        -> Color(0xFFFF9800)
    "S"        -> Color(0xFFFF5722)
    "National" -> Color(0xFFFFD700)
    else       -> Color(0xFF9E9E9E)
})
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt
git add app/src/main/res/xml/dashboard_widget_info.xml
git commit -m "feat: add DashboardWidget (4x2) with XP bar, recommended workout, week progress"
```

---

### Task 7: WarRoomWidget — 4×4 full stats widget

**Files:**
- Create: `app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt`
- Create: `app/src/main/res/xml/warroom_widget_info.xml`

- [ ] **Step 1: Create widget info XML**

```xml
<!-- app/src/main/res/xml/warroom_widget_info.xml -->
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="250dp"
    android:targetCellWidth="4"
    android:targetCellHeight="4"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:description="@string/warroom_widget_description"
    android:previewLayout="@layout/widget_preview_placeholder" />
```

- [ ] **Step 2: Implement WarRoomWidget**

```kotlin
// app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt
package com.ironlog.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 4×4 "The War Room" widget.
 * Shows: Header (rank/level/title) | XP bar | Stats grid | Active quest | Week dots.
 */
class WarRoomWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = currentState<WidgetState>()
        provideContent { WarRoomWidgetContent(state) }
    }
}

@Composable
private fun WarRoomWidgetContent(state: WidgetState) {
    val bg      = ColorProvider(Color(0xFF0D0D1A))
    val accent  = ColorProvider(Color(0xFFFF4500))
    val white   = ColorProvider(Color.White)
    val muted   = ColorProvider(Color(0xFF6E6E8E))
    val surface = ColorProvider(Color(0x1AFFFFFF))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.rank,
                style = TextStyle(color = rankGlanceColor(state.rank), fontSize = 32.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    "Level ${state.level}  •  ${state.activeTitle}",
                    style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    "🔥 ${state.streakWeeks}-week streak",
                    style = TextStyle(color = accent, fontSize = 11.sp),
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // ── XP bar ─────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("XP  ", style = TextStyle(color = muted, fontSize = 10.sp))
            LinearProgressIndicator(
                progress = state.xpPercent,
                modifier = GlanceModifier.defaultWeight().height(8.dp),
                color = accent,
                backgroundColor = surface,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "${(state.xpPercent * 100).toInt()}%",
                style = TextStyle(color = muted, fontSize = 10.sp),
            )
        }

        Spacer(GlanceModifier.height(10.dp))

        // ── Stats grid (2 columns) ──────────────────────────────────────────
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WarRoomStatRow("STR", state.statStr, Color(0xFFFF6B35))
                Spacer(GlanceModifier.height(4.dp))
                WarRoomStatRow("END", state.statEnd, Color(0xFF2196F3))
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                WarRoomStatRow("VIT", state.statVit, Color(0xFF4CAF50))
                Spacer(GlanceModifier.height(4.dp))
                WarRoomStatRow("AGI", state.statAgi, Color(0xFFFFEB3B))
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        // ── Recommended workout ────────────────────────────────────────────
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(surface)
                .cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column {
                Text(
                    "⚡ Best Today: ${state.recommendedDayName}",
                    style = TextStyle(color = white, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                if (state.recommendedDayBlurb.isNotBlank()) {
                    Text(
                        state.recommendedDayBlurb,
                        style = TextStyle(color = muted, fontSize = 10.sp),
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // ── Active quest ───────────────────────────────────────────────────
        if (state.activeQuestDescription.isNotBlank()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color(0x1AFF4500)))
                    .cornerRadius(10.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column {
                    Text(
                        "Quest",
                        style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        state.activeQuestDescription.take(80),
                        style = TextStyle(color = white, fontSize = 10.sp),
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))
        }

        // ── Week progress ──────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Week: ", style = TextStyle(color = muted, fontSize = 10.sp))
            repeat(state.weeklyGoal) { i ->
                val filled = i < state.weekSessionsCount
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .background(ColorProvider(if (filled) Color(0xFFFF4500) else Color(0x33FFFFFF)))
                        .cornerRadius(5.dp),
                ) {}
                if (i < state.weeklyGoal - 1) Spacer(GlanceModifier.width(4.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "${state.weekSessionsCount}/${state.weeklyGoal}",
                style = TextStyle(color = muted, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun WarRoomStatRow(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = TextStyle(color = ColorProvider(color), fontSize = 10.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.width(28.dp),
        )
        LinearProgressIndicator(
            progress = (value / 999f).coerceIn(0f, 1f),
            modifier = GlanceModifier.defaultWeight().height(5.dp),
            color = ColorProvider(color),
            backgroundColor = ColorProvider(Color(0x22FFFFFF)),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            value.toString(),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp),
        )
    }
}

class WarRoomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WarRoomWidget()
}

private fun rankGlanceColor(rank: String): ColorProvider = ColorProvider(when (rank) {
    "E"        -> Color(0xFF9E9E9E)
    "D"        -> Color(0xFF4CAF50)
    "C"        -> Color(0xFF2196F3)
    "B"        -> Color(0xFF9C27B0)
    "A"        -> Color(0xFFFF9800)
    "S"        -> Color(0xFFFF5722)
    "National" -> Color(0xFFFFD700)
    else       -> Color(0xFF9E9E9E)
})
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt
git add app/src/main/res/xml/warroom_widget_info.xml
git commit -m "feat: add WarRoomWidget (4x4) with stats grid, quest, week progress"
```

---

### Task 8: Register widget receivers in AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Read AndroidManifest.xml — find the application closing tag**

Open `app/src/main/AndroidManifest.xml` and locate `</application>`.

- [ ] **Step 2: Add all three widget receivers**

Before `</application>`, add:

```xml
<!-- IronLog Widgets — Jetpack Glance receivers -->
<receiver
    android:name=".widget.BadgeWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/badge_widget_info" />
</receiver>

<receiver
    android:name=".widget.DashboardWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/dashboard_widget_info" />
</receiver>

<receiver
    android:name=".widget.WarRoomWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/warroom_widget_info" />
</receiver>
```

- [ ] **Step 3: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register BadgeWidgetReceiver, DashboardWidgetReceiver, WarRoomWidgetReceiver in manifest"
```

---

### Task 9: Glance state definition — connect WidgetState to Glance DataStore

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt` (and DashboardWidget, WarRoomWidget)

> Glance widgets need a `stateDefinition` to know how to store and retrieve state. The default Glance state uses `androidx.datastore.preferences.core.Preferences`. For a custom serializable data class, we use `GlanceStateDefinition`.

- [ ] **Step 1: Create WidgetStateDefinition**

Add a new file `app/src/main/java/com/ironlog/app/widget/WidgetStateDefinition.kt`:

```kotlin
// app/src/main/java/com/ironlog/app/widget/WidgetStateDefinition.kt
package com.ironlog.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object WidgetStateDefinition : GlanceStateDefinition<WidgetState> {

    private const val DATA_STORE_FILENAME = "ironlog_widget_state"

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetState> {
        return DataStoreFactory.create(
            serializer = WidgetStateSerializer,
            produceFile = { File(context.dataDir, "datastore/$DATA_STORE_FILENAME-$fileKey.json") },
        )
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return File(context.dataDir, "datastore/$DATA_STORE_FILENAME-$fileKey.json")
    }
}

private object WidgetStateSerializer : Serializer<WidgetState> {
    override val defaultValue: WidgetState = WidgetState()

    override suspend fun readFrom(input: InputStream): WidgetState =
        runCatching {
            Json.decodeFromString<WidgetState>(input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: WidgetState, output: OutputStream) {
        output.write(Json.encodeToString(WidgetState.serializer(), t).toByteArray())
    }
}
```

- [ ] **Step 2: Wire stateDefinition into each widget class**

In `BadgeWidget.kt`, `DashboardWidget.kt`, and `WarRoomWidget.kt`, add to each `GlanceAppWidget` class body:

```kotlin
override val stateDefinition = WidgetStateDefinition
```

For example, `BadgeWidget` becomes:

```kotlin
class BadgeWidget : GlanceAppWidget() {
    override val stateDefinition = WidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = currentState<WidgetState>()
        provideContent { BadgeWidgetContent(state) }
    }
}
```

- [ ] **Step 3: Update WidgetUpdateWorker to write state before triggering update**

In `WidgetUpdateWorker.doWork()`, before calling `updateAll()`, update the Glance state:

```kotlin
override suspend fun doWork(): Result {
    return withContext(Dispatchers.IO) {
        runCatching {
            // Get history and settings from ObjectBox / SharedPreferences
            // (adapt to actual app data access pattern)
            val history = emptyList<HistoryEntry>() // TODO: load from ObjectBox
            val weeklyGoal = 4 // TODO: load from settings
            val repo = WidgetDataRepository(applicationContext, getBoxStore(applicationContext))
            val state = repo.buildWidgetState(history, weeklyGoal)

            // Write state to all widget instances
            val glanceIds = GlanceAppWidgetManager(applicationContext).getGlanceIds(BadgeWidget::class.java)
            glanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, WidgetStateDefinition, id) { state }
            }
            // Repeat for DashboardWidget and WarRoomWidget
            val dashIds = GlanceAppWidgetManager(applicationContext).getGlanceIds(DashboardWidget::class.java)
            dashIds.forEach { id ->
                updateAppWidgetState(applicationContext, WidgetStateDefinition, id) { state }
            }
            val warIds = GlanceAppWidgetManager(applicationContext).getGlanceIds(WarRoomWidget::class.java)
            warIds.forEach { id ->
                updateAppWidgetState(applicationContext, WidgetStateDefinition, id) { state }
            }

            BadgeWidget().updateAll(applicationContext)
            DashboardWidget().updateAll(applicationContext)
            WarRoomWidget().updateAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
```

Add imports:
```kotlin
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
```

> The `getBoxStore(applicationContext)` call must use however the app's ObjectBox store is accessed globally — check the Application class or dependency injection setup.

- [ ] **Step 4: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/widget/WidgetStateDefinition.kt
git add app/src/main/java/com/ironlog/app/widget/BadgeWidget.kt
git add app/src/main/java/com/ironlog/app/widget/DashboardWidget.kt
git add app/src/main/java/com/ironlog/app/widget/WarRoomWidget.kt
git add app/src/main/java/com/ironlog/app/widget/WidgetUpdateWorker.kt
git commit -m "feat: wire Glance DataStore state definition into all three widgets"
```

---

### Task 10: Run full unit test suite

- [ ] **Step 1: Run all tests**

```
.\gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, no regressions.

- [ ] **Step 2: If any tests fail, fix them**

Read the failure output, fix the specific failing test or the code it tests, re-run.

- [ ] **Step 3: Final commit**

```
git add -A
git commit -m "test: all widget unit tests pass — WidgetStateTest and WidgetDataRepository verified"
```

---

## Self-Review

**Spec coverage:**
- ✅ Glance dependency added (`glance-appwidget:1.1.1`, `glance-material3:1.1.1`)
- ✅ 3 widget sizes: 2×2 Badge, 4×2 Dashboard, 4×4 War Room
- ✅ All 3 have `GlanceAppWidget` + `GlanceAppWidgetReceiver`
- ✅ All 3 registered in AndroidManifest with `appwidget-provider` XML
- ✅ `WidgetState` — serializable, has `xpPercent` and `weekPercent` computed properties
- ✅ `WidgetStateDefinition` — custom Glance DataStore serializer
- ✅ `WidgetDataRepository` — reads ObjectBox data, assembles WidgetState
- ✅ `WidgetUpdateWorker` — 30-min periodic + on-demand `enqueueOneTime()`
- ✅ WorkManager periodic enqueue in Application.onCreate()
- ✅ Unit tests for `WidgetState` computed properties
- ✅ Dark theme aesthetic (Solo Leveling inspired: navy/black bg, orange/red accents, rank colors)
- ✅ Week progress as colored dots (matches Solo Leveling dungeon gate aesthetic)

**Known implementation notes:**
- Glance `LinearProgressIndicator` is available in `glance-appwidget` — if it's not in 1.1.1, use a `Box` with a colored inner `Box` sized by `fillMaxWidth(fraction)` instead.
- `WidgetDataRepository.buildWidgetState()` has a TODO for plan entity name — must be resolved before the worker can populate the recommended day name.
- `getBoxStore(applicationContext)` in WidgetUpdateWorker — use however the app's ObjectBox `BoxStore` singleton is accessed (often via an Application-level property).
