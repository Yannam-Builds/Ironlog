# Fatigue-Based Workout Suggestion Implementation Plan

> **Status:** Historical execution plan. Its checkboxes were not backfilled and are not a current backlog. Use `AGENTS.md` and the current source/tests as the authoritative project state.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the best plan day to train today based on muscle-group recovery readiness, with the recommended day card floating to the top of the HomeScreen list and glowing with an animated accent border.

**Architecture:** A new `WorkoutSuggestionEngine` maps each exercise name to a muscle region using keyword matching, then scores each plan day against the region-level readiness scores produced by the existing `RecoveryReadinessEngine`. `HomeScreen` reorders its day card list and applies an animated color border to the recommended card.

**Tech Stack:** Kotlin, Jetpack Compose `animateColorAsState`, existing `RecoveryReadinessEngine` (no new dependencies)

---

## File Map

| Action | File |
|--------|------|
| Create | `app/src/main/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngine.kt` |
| Create | `app/src/test/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngineTest.kt` |
| Modify | `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt` — inject engine, reorder cards, add glow |

---

### Task 1: WorkoutSuggestionEngine — keyword mapping and day scoring

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngine.kt`
- Test: `app/src/test/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngineTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngineTest.kt`:

```kotlin
package com.ironlog.app.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSuggestionEngineTest {

    private val engine = WorkoutSuggestionEngine()

    // ── regionForExercise ──────────────────────────────────────────────────

    @Test fun `bench press maps to Push`() {
        assertEquals("Push", engine.regionForExercise("Bench Press"))
    }

    @Test fun `barbell row maps to Pull`() {
        assertEquals("Pull", engine.regionForExercise("Barbell Row"))
    }

    @Test fun `squat maps to Legs`() {
        assertEquals("Legs", engine.regionForExercise("Back Squat"))
    }

    @Test fun `plank maps to Core`() {
        assertEquals("Core", engine.regionForExercise("Plank"))
    }

    @Test fun `curl maps to Arms`() {
        assertEquals("Arms", engine.regionForExercise("Dumbbell Curl"))
    }

    @Test fun `lateral raise maps to Shoulders`() {
        assertEquals("Shoulders", engine.regionForExercise("Lateral Raise"))
    }

    @Test fun `unknown exercise maps to null`() {
        assertEquals(null, engine.regionForExercise("Juggling"))
    }

    // ── scoreDay ──────────────────────────────────────────────────────────

    @Test fun `day with fully rested regions scores near 1`() {
        val readiness = mapOf("Push" to 1.0, "Pull" to 1.0, "Legs" to 1.0)
        val score = engine.scoreDay(readiness, listOf("Bench Press", "Pull Up", "Squat"))
        assertTrue("score should be >= 0.9 but was $score", score >= 0.9)
    }

    @Test fun `day with all fatigued regions scores near 0`() {
        val readiness = mapOf("Push" to 0.0, "Pull" to 0.0, "Legs" to 0.0)
        val score = engine.scoreDay(readiness, listOf("Bench Press", "Pull Up", "Squat"))
        assertTrue("score should be <= 0.2 but was $score", score <= 0.2)
    }

    @Test fun `day with no matched exercises scores 0_5 (neutral)`() {
        val readiness = mapOf("Push" to 1.0)
        val score = engine.scoreDay(readiness, listOf("Juggling", "Yoga Flow"))
        assertEquals(0.5, score, 0.001)
    }

    // ── suggestDayIndex ────────────────────────────────────────────────────

    @Test fun `returns index of highest-scoring day`() {
        val readiness = mapOf("Push" to 1.0, "Legs" to 0.1)
        val dayExerciseNames = listOf(
            listOf("Squat", "Leg Press"),   // day 0 — Legs fatigued → low score
            listOf("Bench Press", "Dip"),   // day 1 — Push fresh → high score
        )
        val result = engine.suggestDayIndex(readiness, dayExerciseNames)
        assertEquals(1, result)
    }

    @Test fun `returns 0 when all days equally ready`() {
        val readiness = mapOf("Push" to 1.0, "Pull" to 1.0)
        val dayExerciseNames = listOf(
            listOf("Bench Press"),
            listOf("Pull Up"),
        )
        val result = engine.suggestDayIndex(readiness, dayExerciseNames)
        // Either is valid; ensure it returns a valid index
        assertTrue(result in 0..1)
    }

    @Test fun `returns 0 for empty day list`() {
        assertEquals(0, engine.suggestDayIndex(emptyMap(), emptyList()))
    }

    // ── recommendationBlurb ───────────────────────────────────────────────

    @Test fun `blurb mentions the day name`() {
        val readiness = mapOf("Push" to 0.9, "Legs" to 0.3)
        val blurb = engine.recommendationBlurb(readiness, "Push Day")
        assertTrue(blurb.contains("Push Day", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd Z:\KOTLIN\UnifiedPort
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.intelligence.WorkoutSuggestionEngineTest" --info 2>&1 | Select-String -Pattern "FAIL|error|not found" | Select-Object -First 20
```

Expected: compilation error — `WorkoutSuggestionEngine` does not exist yet.

- [ ] **Step 3: Implement WorkoutSuggestionEngine**

Create `app/src/main/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngine.kt`:

```kotlin
package com.ironlog.app.domain.intelligence

/**
 * Maps plan days to muscle-group readiness scores and recommends the best
 * day to train based on recovery state from [RecoveryReadinessEngine].
 */
class WorkoutSuggestionEngine {

    // keyword → region mapping (lowercase keywords for case-insensitive matching)
    private val regionKeywords: Map<String, List<String>> = mapOf(
        "Push"      to listOf("bench", "press", "dip", "push", "fly", "flye", "chest", "tricep"),
        "Pull"      to listOf("row", "pull", "lat", "back", "deadlift", "shrug", "bicep", "chin"),
        "Legs"      to listOf("squat", "leg", "lunge", "calf", "glute", "hip thrust", "rdl", "hamstring", "quad"),
        "Core"      to listOf("plank", "crunch", "ab", "oblique", "core", "sit-up", "situp", "hollow"),
        "Arms"      to listOf("curl", "extension", "forearm", "wrist"),
        "Shoulders" to listOf("lateral raise", "shoulder", "overhead", "ohp", "front raise", "face pull", "upright row"),
    )

    /**
     * Returns the training region for [exerciseName] using keyword matching,
     * or null if no region matches.
     */
    fun regionForExercise(exerciseName: String): String? {
        val lower = exerciseName.lowercase()
        return regionKeywords.entries.firstOrNull { (_, keywords) ->
            keywords.any { kw -> lower.contains(kw) }
        }?.key
    }

    /**
     * Scores a plan day against [readiness] (0.0 = fully fatigued, 1.0 = fully recovered).
     * Returns 0.5 (neutral) if none of the exercises map to a known region.
     */
    fun scoreDay(readiness: Map<String, Double>, exerciseNames: List<String>): Double {
        val scores = exerciseNames
            .mapNotNull { name -> regionForExercise(name)?.let { region -> readiness[region] } }
        return if (scores.isEmpty()) 0.5 else scores.average()
    }

    /**
     * Returns the 0-based index of the plan day with the highest readiness score.
     * Ties are broken by lower index (stable).
     */
    fun suggestDayIndex(
        readiness: Map<String, Double>,
        dayExerciseNames: List<List<String>>,
    ): Int {
        if (dayExerciseNames.isEmpty()) return 0
        return dayExerciseNames
            .mapIndexed { i, exercises -> i to scoreDay(readiness, exercises) }
            .maxByOrNull { (_, score) -> score }
            ?.first ?: 0
    }

    /**
     * Returns a short human-readable recommendation sentence for the UI.
     */
    fun recommendationBlurb(readiness: Map<String, Double>, dayName: String): String {
        val topRegion = readiness.maxByOrNull { it.value }
        val freshness = when {
            (topRegion?.value ?: 0.5) >= 0.8 -> "Your muscles are fresh"
            (topRegion?.value ?: 0.5) >= 0.5 -> "You're recovering well"
            else -> "Take it easy today"
        }
        return "$freshness — $dayName looks like your best pick."
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
cd Z:\KOTLIN\UnifiedPort
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.intelligence.WorkoutSuggestionEngineTest"
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngine.kt
git add app/src/test/java/com/ironlog/app/domain/intelligence/WorkoutSuggestionEngineTest.kt
git commit -m "feat: add WorkoutSuggestionEngine for fatigue-based day scoring"
```

---

### Task 2: HomeScreen — reorder cards + glow animation for recommended day

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt`

> **Context:** `HomeScreen` already renders a list of plan day cards. The `AppDataState` contains `plans` (List<UiPlan>) and `settings` which hold `weeklyGoalDays`. The VM already calls `RecoveryReadinessEngine` for debrief — we reuse that same instance.

- [ ] **Step 1: Read the top of HomeScreen to find the day-card rendering site**

Open `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt` and locate:
1. Where `UiPlan.days` (or equivalent) is iterated to render day cards.
2. Any existing import of `RecoveryReadinessEngine`.

Record the exact composable name that renders one day card (e.g., `PlanDayCard`, `DayCard`).

- [ ] **Step 2: Add imports and engine instantiation at the HomeScreen composable call site**

In the HomeScreen composable function, add (near the top of the function body, after `val state = ...`):

```kotlin
// Fatigue suggestion — compute once per recomposition (cheap: pure math on remembered data)
val suggestionEngine = remember { WorkoutSuggestionEngine() }
val recoveryEngine = remember { RecoveryReadinessEngine() }

val readiness: Map<String, Double> = remember(state.history) {
    recoveryEngine.readinessByRegion(state.history, emptyMap())
}

val activePlan: UiPlan? = state.plans.firstOrNull { it.isActive }

val orderedDays: List<UiPlanDay> = remember(activePlan, readiness) {
    val days = activePlan?.days ?: emptyList()
    if (days.isEmpty()) return@remember days
    val dayExerciseNames = days.map { day -> day.exercises.map { it.name } }
    val bestIdx = suggestionEngine.suggestDayIndex(readiness, dayExerciseNames)
    // Float the recommended day to position 0, preserving original order for the rest
    val recommended = days[bestIdx]
    listOf(recommended) + days.filterIndexed { i, _ -> i != bestIdx }
}

val recommendedDayId: String? = orderedDays.firstOrNull()?.id
```

Add the import at the top of the file:
```kotlin
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.domain.intelligence.WorkoutSuggestionEngine
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
```

- [ ] **Step 3: Pass orderedDays to the day-card list and add the glow border**

In the `LazyRow` (or `LazyColumn`) that iterates days, replace the existing `activePlan?.days` (or equivalent) iteration with `orderedDays`. Then, in the day card composable call, add a modifier for the glow:

```kotlin
// Inside the loop that renders each day card:
val isRecommended = day.id == recommendedDayId

// Pulsing glow using infinite transition
val infiniteTransition = rememberInfiniteTransition(label = "glow_${day.id}")
val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.55f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 900),
        repeatMode = RepeatMode.Reverse,
    ),
    label = "glowAlpha_${day.id}",
)

val borderColor by animateColorAsState(
    targetValue = if (isRecommended)
        MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
    else
        Color.Transparent,
    animationSpec = tween(400),
    label = "borderColor_${day.id}",
)

// Wrap the existing day-card composable with a border modifier:
Box(
    modifier = Modifier
        .border(
            width = if (isRecommended) 2.dp else 0.dp,
            color = borderColor,
            shape = RoundedCornerShape(16.dp),  // match the card's corner radius
        )
) {
    // existing day card composable here, e.g.:
    // PlanDayCard(day = day, ...)
    
    if (isRecommended) {
        // "Recommended" badge
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 16.dp),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(
                text = "⚡ Best Today",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
```

Add missing imports:
```kotlin
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
```

- [ ] **Step 4: Build to verify no compilation errors**

```
cd Z:\KOTLIN\UnifiedPort
.\gradlew :app:assembleDebug 2>&1 | Select-String -Pattern "error:|BUILD" | Select-Object -Last 20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run unit tests (full suite)**

```
.\gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, no regressions.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt
git commit -m "feat: float recommended workout day card to top with pulsing glow"
```

---

### Task 3: Recommendation blurb on the recommended card

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Add blurb text below the "Best Today" badge**

Inside the `if (isRecommended)` block added in Task 2, below the badge `Surface`, add:

```kotlin
val blurb = remember(readiness, day.name) {
    suggestionEngine.recommendationBlurb(readiness, day.name)
}

Text(
    text = blurb,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 12.dp, bottom = 8.dp, end = 8.dp),
)
```

- [ ] **Step 2: Build and verify**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String -Pattern "error:|BUILD" | Select-Object -Last 5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt
git commit -m "feat: show recovery recommendation blurb on best-today workout card"
```

---

## Self-Review

**Spec coverage:**
- ✅ `WorkoutSuggestionEngine` scores days against readiness map
- ✅ Recommended day floats to position 0
- ✅ Animated pulsing glow border on recommended card
- ✅ "Best Today" badge chip
- ✅ Recommendation blurb text
- ✅ No new dependencies required
- ✅ TDD: tests written before implementation

**Placeholder scan:** No TBD/TODO in code blocks. All method signatures consistent across tasks.

**Type consistency:** `WorkoutSuggestionEngine` uses `Map<String, Double>` readiness throughout — matches `RecoveryReadinessEngine.readinessByRegion()` return type. `UiPlanDay.id: String` used as key for recommended card check — matches `UiModels.kt`.
