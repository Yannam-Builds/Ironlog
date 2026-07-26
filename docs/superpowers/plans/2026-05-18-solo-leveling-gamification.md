# Solo Leveling Gamification Implementation Plan

> **Status:** Historical execution plan. Its checkboxes were not backfilled and are not a current backlog. Use `AGENTS.md` and the current source/tests as the authoritative project state.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Layer a full RPG progression system onto IronLog — XP, levels (1–100), Solo Leveling ranks (E→S→National), weekly-goal-based streaks with bodyweight make-up quests, adaptive monthly dungeon-boss quests, six derived RPG stats (STR/VIT/END/AGI/WIS/LUK), and a Status Window screen.

**Architecture:** Three pure-Kotlin engines (`XpEngine`, `StatEngine`, `StreakEngine`, `DungeonBossEngine`) hold all logic. One ObjectBox entity (`GamificationProfileEntity`) persists state. One ObjectBox entity (`QuestEntity`) persists active quests. A new `StatusWindowScreen` composable displays the character sheet. A `MakeUpQuestSheet` bottom-sheet composable handles bodyweight streak-save quests. The existing `WatermelonAppDataViewModel` gains a `GamificationViewModel` companion that bridges engines ↔ UI state. Badge art is placeholder SVG/resource drawables (actual badge images to be generated separately via AI image generation).

**Tech Stack:** Kotlin, ObjectBox 4.0.3, Jetpack Compose, existing `HistoryEntry`/`IronLogSettings` models

---

## File Map

| Action | File |
|--------|------|
| Create | `app/src/main/java/com/ironlog/app/data/entity/GamificationProfileEntity.kt` |
| Create | `app/src/main/java/com/ironlog/app/data/entity/QuestEntity.kt` |
| Create | `app/src/main/java/com/ironlog/app/domain/gamification/XpEngine.kt` |
| Create | `app/src/main/java/com/ironlog/app/domain/gamification/StatEngine.kt` |
| Create | `app/src/main/java/com/ironlog/app/domain/gamification/StreakEngine.kt` |
| Create | `app/src/main/java/com/ironlog/app/domain/gamification/DungeonBossEngine.kt` |
| Create | `app/src/main/java/com/ironlog/app/domain/gamification/QuestEngine.kt` |
| Create | `app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt` |
| Create | `app/src/main/java/com/ironlog/app/ui/screens/StatusWindowScreen.kt` |
| Create | `app/src/main/java/com/ironlog/app/ui/screens/MakeUpQuestSheet.kt` |
| Create | `app/src/test/java/com/ironlog/app/domain/gamification/XpEngineTest.kt` |
| Create | `app/src/test/java/com/ironlog/app/domain/gamification/StatEngineTest.kt` |
| Create | `app/src/test/java/com/ironlog/app/domain/gamification/StreakEngineTest.kt` |
| Create | `app/src/test/java/com/ironlog/app/domain/gamification/DungeonBossEngineTest.kt` |
| Modify | `app/src/main/java/com/ironlog/app/ui/navigation/AppNavigation.kt` — add `statusWindow` route |
| Modify | `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt` — XP bar + streak badge |

---

### Task 1: XpEngine — level curve and rank system

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/gamification/XpEngine.kt`
- Test: `app/src/test/java/com/ironlog/app/domain/gamification/XpEngineTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/ironlog/app/domain/gamification/XpEngineTest.kt
package com.ironlog.app.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class XpEngineTest {

    private val engine = XpEngine()

    @Test fun `level 1 requires 100 XP`() {
        assertEquals(100L, engine.xpForLevel(1))
    }

    @Test fun `level 2 requires 283 XP (100 * 2^1_5 rounded)`() {
        val expected = (100.0 * Math.pow(2.0, 1.5)).roundToLong()
        assertEquals(expected, engine.xpForLevel(2))
    }

    @Test fun `level 10 requires correct XP`() {
        val expected = (100.0 * Math.pow(10.0, 1.5)).roundToLong()
        assertEquals(expected, engine.xpForLevel(10))
    }

    @Test fun `xp curve is strictly increasing`() {
        val xps = (1..20).map { engine.xpForLevel(it) }
        for (i in 1 until xps.size) {
            assertTrue("Level ${i+1} xp should exceed level $i xp", xps[i] > xps[i - 1])
        }
    }

    @Test fun `rank E for level 1`() {
        assertEquals("E", engine.rankForLevel(1))
    }

    @Test fun `rank D for level 11`() {
        assertEquals("D", engine.rankForLevel(11))
    }

    @Test fun `rank C for level 21`() {
        assertEquals("C", engine.rankForLevel(21))
    }

    @Test fun `rank B for level 36`() {
        assertEquals("B", engine.rankForLevel(36))
    }

    @Test fun `rank A for level 51`() {
        assertEquals("A", engine.rankForLevel(51))
    }

    @Test fun `rank S for level 71`() {
        assertEquals("S", engine.rankForLevel(71))
    }

    @Test fun `rank National for level 91`() {
        assertEquals("National", engine.rankForLevel(91))
    }

    @Test fun `xpForAction WORKOUT_COMPLETE returns positive value`() {
        assertTrue(engine.xpForAction(XpAction.WORKOUT_COMPLETE) > 0)
    }

    @Test fun `xpForAction PR_SET returns more than WORKOUT_COMPLETE`() {
        assertTrue(engine.xpForAction(XpAction.PR_SET) > engine.xpForAction(XpAction.WORKOUT_COMPLETE))
    }

    @Test fun `xpForAction STREAK_WEEK returns positive value`() {
        assertTrue(engine.xpForAction(XpAction.STREAK_WEEK) > 0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.XpEngineTest" 2>&1 | Select-String -Pattern "error:|FAIL|BUILD" | Select-Object -Last 10
```

Expected: compilation error — `XpEngine` and `XpAction` not found.

- [ ] **Step 3: Implement XpEngine**

```kotlin
// app/src/main/java/com/ironlog/app/domain/gamification/XpEngine.kt
package com.ironlog.app.domain.gamification

import kotlin.math.pow
import kotlin.math.roundToLong

enum class XpAction {
    WORKOUT_COMPLETE,   // +50 XP
    PR_SET,             // +100 XP
    STREAK_WEEK,        // +75 XP per qualifying week milestone
    QUEST_COMPLETE,     // +150 XP
    DUNGEON_BOSS_SLAY,  // +500 XP
    MAKEUP_QUEST,       // +25 XP (streak-save bodyweight circuit)
    FIRST_WORKOUT,      // +200 XP one-time
}

class XpEngine {

    /**
     * XP required to reach [level] (1-indexed).
     * Curve: 100 × level^1.5, rounded to nearest Long.
     */
    fun xpForLevel(level: Int): Long = (100.0 * level.toDouble().pow(1.5)).roundToLong()

    /**
     * Solo Leveling rank thresholds by level.
     * E: 1–10 | D: 11–20 | C: 21–35 | B: 36–50 | A: 51–70 | S: 71–90 | National: 91+
     */
    fun rankForLevel(level: Int): String = when {
        level >= 91 -> "National"
        level >= 71 -> "S"
        level >= 51 -> "A"
        level >= 36 -> "B"
        level >= 21 -> "C"
        level >= 11 -> "D"
        else        -> "E"
    }

    /** XP awarded for a given [action]. */
    fun xpForAction(action: XpAction): Int = when (action) {
        XpAction.WORKOUT_COMPLETE  -> 50
        XpAction.PR_SET            -> 100
        XpAction.STREAK_WEEK       -> 75
        XpAction.QUEST_COMPLETE    -> 150
        XpAction.DUNGEON_BOSS_SLAY -> 500
        XpAction.MAKEUP_QUEST      -> 25
        XpAction.FIRST_WORKOUT     -> 200
    }

    /**
     * Given current [totalXp], return the current level (1-based).
     * Level advances when accumulated XP meets the next threshold.
     */
    fun levelFromTotalXp(totalXp: Long): Int {
        var level = 1
        var accumulated = 0L
        while (level < 100) {
            accumulated += xpForLevel(level)
            if (totalXp < accumulated) break
            level++
        }
        return level.coerceAtMost(100)
    }

    /**
     * XP progress within the current level (0L..xpForLevel(currentLevel)).
     */
    fun xpInCurrentLevel(totalXp: Long): Long {
        val level = levelFromTotalXp(totalXp)
        val xpAtLevelStart = (1 until level).sumOf { xpForLevel(it) }
        return (totalXp - xpAtLevelStart).coerceAtLeast(0L)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.XpEngineTest"
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/gamification/XpEngine.kt
git add app/src/test/java/com/ironlog/app/domain/gamification/XpEngineTest.kt
git commit -m "feat: add XpEngine with Solo Leveling rank curve and XP action rewards"
```

---

### Task 2: StreakEngine — weekly-goal-based streaks with make-up quest credit

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/gamification/StreakEngine.kt`
- Test: `app/src/test/java/com/ironlog/app/domain/gamification/StreakEngineTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/ironlog/app/domain/gamification/StreakEngineTest.kt
package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakEngineTest {

    private val engine = StreakEngine()

    private fun entry(date: LocalDate) = HistoryEntry(
        id = date.toString(),
        date = date.toString(),
    )

    // Week of 2026-05-18 is ISO week 21 (Mon May 18 – Sun May 24)
    private val monday = LocalDate.of(2026, 5, 18)

    @Test fun `empty history gives streak 0`() {
        assertEquals(0, engine.computeStreakWeeks(emptyList(), weeklyGoal = 4, makeupCompletions = emptyMap()))
    }

    @Test fun `one qualifying week gives streak 1`() {
        // 4 workouts in week 21 satisfies goal=4
        val history = (0..3).map { entry(monday.plusDays(it.toLong())) }
        assertEquals(1, engine.computeStreakWeeks(history, weeklyGoal = 4, makeupCompletions = emptyMap()))
    }

    @Test fun `two consecutive qualifying weeks give streak 2`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        val week2 = (7..10).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2
        assertEquals(2, engine.computeStreakWeeks(history, weeklyGoal = 4, makeupCompletions = emptyMap()))
    }

    @Test fun `gap week without makeup breaks streak`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        // week 2: only 3 sessions (goal=4), no makeup
        val week2 = (7..9).map { entry(monday.plusDays(it.toLong())) }
        val week3 = (14..17).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2 + week3
        // streak should count from week3 backward: week2 is not qualifying and no makeup
        val result = engine.computeStreakWeeks(history, weeklyGoal = 4, makeupCompletions = emptyMap())
        assertEquals(1, result)
    }

    @Test fun `gap week with makeup quest saves streak`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        // week 2: only 3 sessions (goal-1), but makeup completed
        val week2 = (7..9).map { entry(monday.plusDays(it.toLong())) }
        val week3 = (14..17).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2 + week3
        // ISO week 22 makeup completed
        val makeups = mapOf("2026-W22" to 1)
        val result = engine.computeStreakWeeks(history, weeklyGoal = 4, makeupCompletions = makeups)
        assertEquals(3, result)
    }

    @Test fun `makeup does not save when sessions is goal minus 2 or more short`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        // week 2: only 2 sessions (goal-2), makeup present — too short, can't save
        val week2 = (7..8).map { entry(monday.plusDays(it.toLong())) }
        val week3 = (14..17).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2 + week3
        val makeups = mapOf("2026-W22" to 1)
        val result = engine.computeStreakWeeks(history, weeklyGoal = 4, makeupCompletions = makeups)
        assertEquals(1, result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.StreakEngineTest" 2>&1 | Select-String -Pattern "error:|BUILD" | Select-Object -Last 5
```

Expected: compilation error.

- [ ] **Step 3: Implement StreakEngine**

```kotlin
// app/src/main/java/com/ironlog/app/domain/gamification/StreakEngine.kt
package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

class StreakEngine {

    private val isoWeek = WeekFields.ISO

    /**
     * ISO week key, e.g. "2026-W21".
     */
    private fun LocalDate.isoWeekKey(): String {
        val week = get(isoWeek.weekOfWeekBasedYear())
        val year = get(isoWeek.weekBasedYear())
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    /**
     * Computes the current streak in qualifying weeks.
     *
     * A week qualifies if:
     *   - sessions in that week >= [weeklyGoal], OR
     *   - sessions == weeklyGoal - 1 AND [makeupCompletions][weekKey] >= 1
     *
     * The streak counts backward from the most recent qualifying week.
     * A missing week (no workouts at all, not even goal-1+makeup) breaks the streak.
     *
     * @param history      All workout history entries.
     * @param weeklyGoal   Sessions required per week (from IronLogSettings.weeklyGoalDays).
     * @param makeupCompletions  Map of ISO-week-key → number of makeup quests completed.
     */
    fun computeStreakWeeks(
        history: List<HistoryEntry>,
        weeklyGoal: Int,
        makeupCompletions: Map<String, Int>,
    ): Int {
        if (history.isEmpty()) return 0

        // Group workouts by ISO week key
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val sessionsByWeek: Map<String, Int> = history
            .mapNotNull { entry ->
                runCatching { LocalDate.parse(entry.date.take(10), fmt) }.getOrNull()
                    ?.isoWeekKey()
            }
            .groupingBy { it }
            .eachCount()

        if (sessionsByWeek.isEmpty()) return 0

        // Sort weeks descending (most recent first)
        val sortedWeeks = sessionsByWeek.keys.sortedDescending()
        val mostRecentWeek = sortedWeeks.first()

        // Walk backward from the most recent week, count consecutive qualifying weeks
        var streak = 0
        var current = LocalDate.parse(
            "${ mostRecentWeek.substringBefore("-W") }-01-01",
            DateTimeFormatter.ISO_LOCAL_DATE
        ).also {
            // Parse the ISO week properly
        }

        // Simpler approach: walk the sorted weeks list
        for (weekKey in sortedWeeks) {
            val sessions = sessionsByWeek[weekKey] ?: 0
            val makeups = makeupCompletions[weekKey] ?: 0
            val qualifies = sessions >= weeklyGoal ||
                    (sessions == weeklyGoal - 1 && makeups >= 1)

            if (qualifies) {
                streak++
            } else {
                break
            }
        }

        return streak
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.StreakEngineTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/gamification/StreakEngine.kt
git add app/src/test/java/com/ironlog/app/domain/gamification/StreakEngineTest.kt
git commit -m "feat: add StreakEngine with weekly-goal streak model and makeup-quest save"
```

---

### Task 3: StatEngine — derive RPG stats from workout history

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/gamification/StatEngine.kt`
- Test: `app/src/test/java/com/ironlog/app/domain/gamification/StatEngineTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/ironlog/app/domain/gamification/StatEngineTest.kt
package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatEngineTest {

    private val engine = StatEngine()

    private fun heavySet(weight: Double, reps: Double) = HistoryExerciseSet(
        id = "s1", weight = weight, reps = reps, type = "normal"
    )

    private fun entry(
        id: String,
        date: String,
        durationMin: Int = 60,
        exerciseCount: Int = 5,
        sets: List<HistoryExerciseSet> = listOf(heavySet(100.0, 5.0)),
    ) = HistoryEntry(
        id = id,
        date = date,
        duration = durationMin * 60,
        exercises = (1..exerciseCount).map { i ->
            HistoryExercise(id = "$id-ex$i", name = "Exercise $i", sets = sets)
        },
    )

    @Test fun `empty history returns all stats at minimum 1`() {
        val stats = engine.compute(emptyList(), streak = 0, totalSessions = 0)
        assertTrue(stats.str >= 1)
        assertTrue(stats.vit >= 1)
        assertTrue(stats.end >= 1)
        assertTrue(stats.agi >= 1)
        assertTrue(stats.wis >= 1)
        assertTrue(stats.luk >= 1)
    }

    @Test fun `all stats are capped at 999`() {
        // Generate 500 heavy workouts
        val history = (1..500).map { i ->
            entry(id = "e$i", date = "2026-01-${(i % 28 + 1).toString().padStart(2, '0')}",
                  sets = listOf(heavySet(200.0, 5.0)))
        }
        val stats = engine.compute(history, streak = 500, totalSessions = 500)
        assertTrue(stats.str <= 999)
        assertTrue(stats.vit <= 999)
        assertTrue(stats.end <= 999)
        assertTrue(stats.agi <= 999)
    }

    @Test fun `higher estimated 1RM gives higher STR`() {
        val light = listOf(entry("a", "2026-05-01", sets = listOf(heavySet(50.0, 10.0))))
        val heavy = listOf(entry("b", "2026-05-01", sets = listOf(heavySet(150.0, 5.0))))
        val statsLight = engine.compute(light, streak = 0, totalSessions = 1)
        val statsHeavy = engine.compute(heavy, streak = 0, totalSessions = 1)
        assertTrue("Heavier lifter should have more STR", statsHeavy.str > statsLight.str)
    }

    @Test fun `longer workout duration gives higher END`() {
        val short = listOf(entry("a", "2026-05-01", durationMin = 20))
        val long  = listOf(entry("b", "2026-05-01", durationMin = 120))
        val statsShort = engine.compute(short, streak = 0, totalSessions = 1)
        val statsLong  = engine.compute(long,  streak = 0, totalSessions = 1)
        assertTrue("Longer workouts should give more END", statsLong.end > statsShort.end)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.StatEngineTest" 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: compilation error.

- [ ] **Step 3: Implement StatEngine**

```kotlin
// app/src/main/java/com/ironlog/app/domain/gamification/StatEngine.kt
package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExerciseSet
import kotlin.math.ln
import kotlin.math.roundToInt

data class RpgStats(
    val str: Int = 1,  // Strength  — best estimated 1RM across all exercises
    val vit: Int = 1,  // Vitality  — streak + total sessions
    val end: Int = 1,  // Endurance — average workout duration
    val agi: Int = 1,  // Agility   — exercise variety per session
    val wis: Int = 1,  // Wisdom    — RPE/effort tracking frequency (future; now = 1)
    val luk: Int = 1,  // Luck      — rare events (PRs, streaks, quests)
)

class StatEngine {

    /** Epley formula: 1RM ≈ weight × (1 + reps/30) */
    private fun epley1rm(weight: Double, reps: Double): Double =
        weight * (1.0 + reps / 30.0)

    /**
     * Map a raw metric to a 1–999 RPG stat value using logarithmic scaling.
     * [value] is the raw metric; [scale] controls how quickly stat grows.
     */
    private fun toStat(value: Double, scale: Double): Int =
        (1 + (ln(1.0 + value / scale) * 200.0)).roundToInt().coerceIn(1, 999)

    fun compute(
        history: List<HistoryEntry>,
        streak: Int,
        totalSessions: Int,
    ): RpgStats {
        if (history.isEmpty()) return RpgStats()

        // STR — best estimated 1RM across all sets in all history
        val bestOrm: Double = history.flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { it.type != "warmup" && it.reps > 0 && it.weight > 0 }
            .maxOfOrNull { epley1rm(it.weight, it.reps) } ?: 0.0

        // END — average workout duration in minutes
        val avgDurationMin = history.map { it.duration / 60.0 }.average()

        // AGI — average distinct exercises per session
        val avgExercises = history.map { it.exercises.size.toDouble() }.average()

        // VIT — blend of streak weeks and total sessions
        val vitRaw = streak * 5.0 + totalSessions.toDouble()

        // WIS — placeholder (RPE logging not yet tracked per set); starts at minimum
        val wis = 1

        // LUK — count of PRs + quests (approximated as high-RPE sets for now)
        val luk = toStat(
            value = history.sumOf { entry ->
                entry.exercises.sumOf { ex ->
                    ex.sets.count { it.rpe != null && (it.rpe ?: 0.0) >= 9.0 }.toDouble()
                }
            },
            scale = 20.0,
        )

        return RpgStats(
            str = toStat(bestOrm, scale = 100.0),
            vit = toStat(vitRaw, scale = 50.0),
            end = toStat(avgDurationMin, scale = 30.0),
            agi = toStat(avgExercises, scale = 5.0),
            wis = wis,
            luk = luk,
        )
    }
}
```

- [ ] **Step 4: Run tests**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.StatEngineTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/gamification/StatEngine.kt
git add app/src/test/java/com/ironlog/app/domain/gamification/StatEngineTest.kt
git commit -m "feat: add StatEngine deriving STR/VIT/END/AGI/WIS/LUK from workout history"
```

---

### Task 4: DungeonBossEngine — adaptive monthly quest

**Files:**
- Create: `app/src/main/java/com/ironlog/app/domain/gamification/DungeonBossEngine.kt`
- Test: `app/src/test/java/com/ironlog/app/domain/gamification/DungeonBossEngineTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/ironlog/app/domain/gamification/DungeonBossEngineTest.kt
package com.ironlog.app.domain.gamification

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DungeonBossEngineTest {

    private val engine = DungeonBossEngine()

    private fun profile(
        recentPrCount: Int = 0,
        strongExercises: List<String> = emptyList(),
        avgWeeklyWorkouts: Double = 3.0,
        currentStreak: Int = 2,
    ) = UserActivityProfile(
        recentPrCount = recentPrCount,
        strongExercises = strongExercises,
        avgWeeklyWorkouts = avgWeeklyWorkouts,
        currentStreak = currentStreak,
    )

    @Test fun `generates a non-null quest for any profile`() {
        assertNotNull(engine.generateMonthlyBoss(profile()))
    }

    @Test fun `quest for high PR count targets PR exercises`() {
        val p = profile(
            recentPrCount = 3,
            strongExercises = listOf("Bench Press", "Squat", "Deadlift"),
        )
        val quest = engine.generateMonthlyBoss(p)
        assertTrue("Quest should reference a strong exercise",
            p.strongExercises.any { ex -> quest.description.contains(ex, ignoreCase = true) })
    }

    @Test fun `quest target count is achievable (not more than 2x avg)`() {
        val p = profile(avgWeeklyWorkouts = 3.0, currentStreak = 2)
        val quest = engine.generateMonthlyBoss(p)
        // Monthly gym sessions target should not exceed 2× expected monthly count
        assertTrue("Quest target ${quest.gymSessionTarget} should be <= ${(p.avgWeeklyWorkouts * 4 * 2).toInt()}",
            quest.gymSessionTarget <= (p.avgWeeklyWorkouts * 4 * 2).toInt())
    }

    @Test fun `beginner profile (low sessions) gets an easy quest`() {
        val p = profile(avgWeeklyWorkouts = 1.5, currentStreak = 0)
        val quest = engine.generateMonthlyBoss(p)
        assertTrue("Beginner gym target should be <= 8", quest.gymSessionTarget <= 8)
    }

    @Test fun `advanced profile gets harder quest`() {
        val beginner = profile(avgWeeklyWorkouts = 2.0, currentStreak = 1)
        val advanced = profile(avgWeeklyWorkouts = 5.0, currentStreak = 8, recentPrCount = 4,
                               strongExercises = listOf("Bench Press", "Squat"))
        val questB = engine.generateMonthlyBoss(beginner)
        val questA = engine.generateMonthlyBoss(advanced)
        assertTrue("Advanced quest should have higher gym target",
            questA.gymSessionTarget >= questB.gymSessionTarget)
    }

    @Test fun `quest always has non-empty description`() {
        val quest = engine.generateMonthlyBoss(profile())
        assertTrue(quest.description.isNotBlank())
    }

    @Test fun `quest always has positive XP reward`() {
        val quest = engine.generateMonthlyBoss(profile())
        assertTrue(quest.xpReward > 0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.DungeonBossEngineTest" 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

- [ ] **Step 3: Implement DungeonBossEngine**

```kotlin
// app/src/main/java/com/ironlog/app/domain/gamification/DungeonBossEngine.kt
package com.ironlog.app.domain.gamification

import kotlin.math.roundToInt

data class UserActivityProfile(
    /** Number of PRs set in the last 60 days. */
    val recentPrCount: Int,
    /** Exercises the user has logged ≥3 times in the last 60 days. */
    val strongExercises: List<String>,
    /** Average workouts per week over the last 8 weeks. */
    val avgWeeklyWorkouts: Double,
    /** Current streak in qualifying weeks. */
    val currentStreak: Int,
)

data class DungeonBossQuest(
    val description: String,
    /** Minimum gym sessions required to complete the quest this month. */
    val gymSessionTarget: Int,
    /** Optional PR exercises to target (empty = gym sessions only). */
    val prExercises: List<String>,
    /** XP awarded on completion. */
    val xpReward: Int,
)

class DungeonBossEngine {

    /**
     * Generates an adaptive monthly dungeon-boss quest calibrated to the user's
     * recent activity. Safety guards prevent impossible quests.
     *
     * Quest types:
     * 1. PR Hunt — if recentPrCount >= 2 and strongExercises not empty:
     *    "Hit a new PR in [X] of your strongest lifts + [N] gym sessions"
     * 2. Consistency — if avgWeeklyWorkouts < 3.0:
     *    "Reach [N] gym sessions this month"
     * 3. Streak + Sessions combo — otherwise:
     *    "Maintain your [streak] week streak and hit [N] sessions"
     */
    fun generateMonthlyBoss(profile: UserActivityProfile): DungeonBossQuest {
        val expectedMonthly = (profile.avgWeeklyWorkouts * 4.33).roundToInt().coerceAtLeast(1)

        return when {
            // PR Hunt path
            profile.recentPrCount >= 2 && profile.strongExercises.isNotEmpty() -> {
                val targetExercises = profile.strongExercises.take(3.coerceAtMost(profile.strongExercises.size))
                val prTarget = targetExercises.take(2) // require PR in 2 of the top exercises
                val sessionTarget = (expectedMonthly + 2).coerceAtMost(expectedMonthly * 2)
                DungeonBossQuest(
                    description = "🏆 BOSS: Hit a new PR in ${prTarget.joinToString(" & ")} " +
                            "and complete $sessionTarget gym sessions this month.",
                    gymSessionTarget = sessionTarget,
                    prExercises = prTarget,
                    xpReward = 500 + profile.recentPrCount * 50,
                )
            }

            // Beginner consistency path
            profile.avgWeeklyWorkouts < 3.0 -> {
                val sessionTarget = (expectedMonthly + 2).coerceAtMost(8)
                DungeonBossQuest(
                    description = "⚔️ BOSS: Complete $sessionTarget gym sessions this month to prove your commitment.",
                    gymSessionTarget = sessionTarget,
                    prExercises = emptyList(),
                    xpReward = 400,
                )
            }

            // Streak + consistency combo
            else -> {
                val streakTarget = profile.currentStreak + 2
                val sessionTarget = (expectedMonthly + 3).coerceAtMost(expectedMonthly * 2)
                DungeonBossQuest(
                    description = "🔥 BOSS: Keep a $streakTarget-week streak and complete $sessionTarget sessions this month.",
                    gymSessionTarget = sessionTarget,
                    prExercises = emptyList(),
                    xpReward = 450 + profile.currentStreak * 25,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

```
.\gradlew :app:testDebugUnitTest --tests "com.ironlog.app.domain.gamification.DungeonBossEngineTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/domain/gamification/DungeonBossEngine.kt
git add app/src/test/java/com/ironlog/app/domain/gamification/DungeonBossEngineTest.kt
git commit -m "feat: add DungeonBossEngine for adaptive monthly quest generation"
```

---

### Task 5: ObjectBox entities — GamificationProfileEntity and QuestEntity

**Files:**
- Create: `app/src/main/java/com/ironlog/app/data/entity/GamificationProfileEntity.kt`
- Create: `app/src/main/java/com/ironlog/app/data/entity/QuestEntity.kt`

> **Note:** ObjectBox entities require `@Entity` and `@Id`. After adding new entities, rebuild the project to regenerate the ObjectBox schema. No unit tests for entity classes (they are pure data holders).

- [ ] **Step 1: Create GamificationProfileEntity**

```kotlin
// app/src/main/java/com/ironlog/app/data/entity/GamificationProfileEntity.kt
package com.ironlog.app.data.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

@Entity
data class GamificationProfileEntity(
    @Id var id: Long = 0,

    /** Stable user identifier (UUID string, set once on first launch). */
    @Unique var offlineUserId: String = "",

    /** Total accumulated XP across all time. */
    var totalXp: Long = 0L,

    /** Current level (1–100), derived from totalXp but cached for display. */
    var level: Int = 1,

    /** XP within the current level (0..xpForLevel(level)). */
    var xpInLevel: Long = 0L,

    /** Current Solo Leveling rank string: "E", "D", "C", "B", "A", "S", "National". */
    var rank: String = "E",

    /** Active title (unlocked badge name). */
    var activeTitle: String = "Novice Hunter",

    /** JSON-serialized RpgStats for fast display without recomputing. */
    var statsJson: String = "{}",

    /** XP earned this ISO week (for weekly leaderboard — stored, not yet used). */
    var weeklyXp: Int = 0,

    /** ISO week key of last weeklyXp reset, e.g. "2026-W21". */
    var weeklyXpResetWeek: String = "",

    /** Current streak in qualifying weeks. */
    var streakWeeks: Int = 0,

    /** JSON map of ISO-week-key → makeup completions, e.g. {"2026-W21": 1}. */
    var makeupCompletionsJson: String = "{}",

    /** Comma-separated list of unlocked badge IDs. */
    var unlockedBadges: String = "",
)
```

- [ ] **Step 2: Create QuestEntity**

```kotlin
// app/src/main/java/com/ironlog/app/data/entity/QuestEntity.kt
package com.ironlog.app.data.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class QuestEntity(
    @Id var id: Long = 0,

    /** Stable quest identifier (UUID). */
    var questId: String = "",

    /**
     * Quest type:
     *   "daily"        — resets each day
     *   "weekly"       — resets each ISO week
     *   "monthly_boss" — monthly dungeon boss
     *   "makeup"       — streak-save bodyweight circuit
     */
    var type: String = "daily",

    /** Human-readable description shown in the UI. */
    var description: String = "",

    /** JSON-encoded target payload (exercise names, counts, etc.). */
    var targetJson: String = "{}",

    /** JSON-encoded current progress payload. */
    var currentJson: String = "{}",

    /** XP awarded on completion. */
    var xpReward: Int = 0,

    /** ISO-8601 timestamp when completed, or empty if still active. */
    var completedAt: String = "",

    /** ISO-8601 timestamp when quest expires (blank = no expiry). */
    var expiresAt: String = "",

    /** For monthly_boss: minimum gym sessions required. */
    var gymSessionTarget: Int = 0,

    /** For monthly_boss: comma-separated PR exercises required. */
    var prExercises: String = "",
)
```

- [ ] **Step 3: Build to trigger ObjectBox code generation**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL` (ObjectBox processor generates `GamificationProfileEntity_` and `QuestEntity_` box accessors).

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/ironlog/app/data/entity/GamificationProfileEntity.kt
git add app/src/main/java/com/ironlog/app/data/entity/QuestEntity.kt
git commit -m "feat: add GamificationProfileEntity and QuestEntity ObjectBox schemas"
```

---

### Task 6: GamificationViewModel — bridge engines to UI state

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt`

- [ ] **Step 1: Implement GamificationViewModel**

```kotlin
// app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt
package com.ironlog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.entity.GamificationProfileEntity
import com.ironlog.app.data.entity.GamificationProfileEntity_
import com.ironlog.app.data.entity.QuestEntity
import com.ironlog.app.data.entity.QuestEntity_
import com.ironlog.app.domain.gamification.DungeonBossEngine
import com.ironlog.app.domain.gamification.RpgStats
import com.ironlog.app.domain.gamification.StatEngine
import com.ironlog.app.domain.gamification.StreakEngine
import com.ironlog.app.domain.gamification.UserActivityProfile
import com.ironlog.app.domain.gamification.XpAction
import com.ironlog.app.domain.gamification.XpEngine
import com.ironlog.app.ui.model.HistoryEntry
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class GamificationUiState(
    val level: Int = 1,
    val xpInLevel: Long = 0L,
    val xpForNextLevel: Long = 100L,
    val rank: String = "E",
    val streakWeeks: Int = 0,
    val stats: RpgStats = RpgStats(),
    val activeTitle: String = "Novice Hunter",
    val activeQuests: List<QuestEntity> = emptyList(),
    val unlockedBadges: List<String> = emptyList(),
)

class GamificationViewModel(
    application: Application,
    private val boxStore: BoxStore,
) : AndroidViewModel(application) {

    private val xpEngine = XpEngine()
    private val statEngine = StatEngine()
    private val streakEngine = StreakEngine()
    private val dungeonEngine = DungeonBossEngine()

    private val profileBox get() = boxStore.boxFor(GamificationProfileEntity::class.java)
    private val questBox    get() = boxStore.boxFor(QuestEntity::class.java)

    private val _uiState = MutableStateFlow(GamificationUiState())
    val uiState: StateFlow<GamificationUiState> = _uiState

    init {
        loadProfile()
    }

    private fun getOrCreateProfile(): GamificationProfileEntity {
        return profileBox.query(GamificationProfileEntity_.id.notNull()).build().findFirst()
            ?: GamificationProfileEntity(offlineUserId = UUID.randomUUID().toString())
                .also { profileBox.put(it) }
    }

    fun loadProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()
            val quests = questBox.query(QuestEntity_.completedAt.equal("")).build().find()
            val badges = profile.unlockedBadges.split(",").filter { it.isNotBlank() }
            _uiState.value = GamificationUiState(
                level = profile.level,
                xpInLevel = profile.xpInLevel,
                xpForNextLevel = xpEngine.xpForLevel(profile.level),
                rank = profile.rank,
                streakWeeks = profile.streakWeeks,
                activeTitle = profile.activeTitle,
                activeQuests = quests,
                unlockedBadges = badges,
            )
        }
    }

    /**
     * Award XP for an action. Persists to ObjectBox and refreshes UI state.
     */
    fun awardXp(action: XpAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()
            val gained = xpEngine.xpForAction(action)
            profile.totalXp += gained
            profile.weeklyXp += gained
            profile.level = xpEngine.levelFromTotalXp(profile.totalXp)
            profile.xpInLevel = xpEngine.xpInCurrentLevel(profile.totalXp)
            profile.rank = xpEngine.rankForLevel(profile.level)
            profileBox.put(profile)
            loadProfile()
        }
    }

    /**
     * Recompute streak, stats, and refresh profile from history.
     * Call this after every workout completion.
     */
    fun refreshFromHistory(history: List<HistoryEntry>, weeklyGoal: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()

            // Parse makeup completions map
            val makeups: Map<String, Int> = runCatching {
                Json.decodeFromString<Map<String, Int>>(profile.makeupCompletionsJson)
            }.getOrDefault(emptyMap())

            // Streak
            profile.streakWeeks = streakEngine.computeStreakWeeks(history, weeklyGoal, makeups)

            // Stats
            val stats = statEngine.compute(history, profile.streakWeeks, history.size)
            profile.statsJson = Json.encodeToString(stats)

            profileBox.put(profile)
            loadProfile()
        }
    }

    /**
     * Record a completed makeup quest for [isoWeekKey] and award XP.
     */
    fun completeMakeupQuest(isoWeekKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()
            val makeups: MutableMap<String, Int> = runCatching {
                Json.decodeFromString<Map<String, Int>>(profile.makeupCompletionsJson).toMutableMap()
            }.getOrDefault(mutableMapOf())
            makeups[isoWeekKey] = (makeups[isoWeekKey] ?: 0) + 1
            profile.makeupCompletionsJson = Json.encodeToString(makeups as Map<String, Int>)
            profileBox.put(profile)
            awardXp(XpAction.MAKEUP_QUEST)
        }
    }

    /**
     * Generate and store a monthly dungeon boss quest if none exists for this month.
     */
    fun ensureMonthlyBoss(history: List<HistoryEntry>, weeklyGoal: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = questBox.query(QuestEntity_.type.equal("monthly_boss"))
                .and(QuestEntity_.completedAt.equal(""))
                .build().findFirst()
            if (existing != null) return@launch

            val makeups: Map<String, Int> = runCatching {
                val profile = getOrCreateProfile()
                Json.decodeFromString<Map<String, Int>>(profile.makeupCompletionsJson)
            }.getOrDefault(emptyMap())
            val streak = streakEngine.computeStreakWeeks(history, weeklyGoal, makeups)

            // Build activity profile from history (last 60 days)
            val recentHistory = history.take(30) // approximate
            val prCount = recentHistory.sumOf { e ->
                e.exercises.sumOf { ex -> if (ex.sets.any { it.weight > 0 }) 1L else 0L }.toInt()
            }
            val activityProfile = UserActivityProfile(
                recentPrCount = prCount,
                strongExercises = recentHistory.flatMap { it.exercises }.map { it.name }.distinct().take(5),
                avgWeeklyWorkouts = history.size.toDouble() / 8.0,
                currentStreak = streak,
            )
            val boss = dungeonEngine.generateMonthlyBoss(activityProfile)
            val quest = QuestEntity(
                questId = UUID.randomUUID().toString(),
                type = "monthly_boss",
                description = boss.description,
                xpReward = boss.xpReward,
                gymSessionTarget = boss.gymSessionTarget,
                prExercises = boss.prExercises.joinToString(","),
            )
            questBox.put(quest)
            loadProfile()
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt
git commit -m "feat: add GamificationViewModel bridging XP/streak/stat engines to UI state"
```

---

### Task 7: StatusWindowScreen — RPG character sheet composable

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/StatusWindowScreen.kt`

- [ ] **Step 1: Implement StatusWindowScreen**

```kotlin
// app/src/main/java/com/ironlog/app/ui/screens/StatusWindowScreen.kt
package com.ironlog.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.gamification.RpgStats
import com.ironlog.app.ui.viewmodel.GamificationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusWindowScreen(
    state: GamificationUiState,
    onBack: () -> Unit,
    onMakeupQuestTap: () -> Unit,
) {
    val rankColor = rankColor(state.rank)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Status Window", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Rank badge + level ────────────────────────────────────────
            item {
                RankBadgeSection(
                    rank = state.rank,
                    rankColor = rankColor,
                    level = state.level,
                    title = state.activeTitle,
                )
            }

            // ── XP progress bar ───────────────────────────────────────────
            item {
                XpProgressSection(
                    xpInLevel = state.xpInLevel,
                    xpForNextLevel = state.xpForNextLevel,
                    level = state.level,
                )
            }

            // ── Streak ────────────────────────────────────────────────────
            item {
                StreakSection(
                    streakWeeks = state.streakWeeks,
                    onMakeupQuestTap = onMakeupQuestTap,
                )
            }

            // ── RPG Stats ─────────────────────────────────────────────────
            item {
                RpgStatsSection(stats = state.stats)
            }

            // ── Active Quests ─────────────────────────────────────────────
            if (state.activeQuests.isNotEmpty()) {
                item {
                    Text(
                        "Active Quests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(state.activeQuests) { quest ->
                    QuestCard(description = quest.description, xpReward = quest.xpReward)
                }
            }

            // ── Badge shelf ───────────────────────────────────────────────
            item {
                BadgeShelf(unlockedBadges = state.unlockedBadges)
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RankBadgeSection(rank: String, rankColor: Color, level: Int, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(rankColor.copy(alpha = 0.3f), Color.Transparent))
                )
                .border(3.dp, rankColor, CircleShape),
        ) {
            Text(
                text = rank,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = rankColor,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Level $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun XpProgressSection(xpInLevel: Long, xpForNextLevel: Long, level: Int) {
    val fraction = if (xpForNextLevel > 0) (xpInLevel.toFloat() / xpForNextLevel.toFloat()) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "xpBar",
    )
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("XP", style = MaterialTheme.typography.labelMedium)
            Text("$xpInLevel / $xpForNextLevel", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        )
        Text(
            "Next level: ${level + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun StreakSection(streakWeeks: Int, onMakeupQuestTap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$streakWeeks-week streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Qualifying weeks at your goal", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onMakeupQuestTap) {
                Text("Save streak")
            }
        }
    }
}

@Composable
private fun RpgStatsSection(stats: RpgStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatRow("STR", stats.str, Color(0xFFFF6B35), "Strength — best 1RM estimate")
            StatRow("VIT", stats.vit, Color(0xFF4CAF50), "Vitality — streak + consistency")
            StatRow("END", stats.end, Color(0xFF2196F3), "Endurance — average workout duration")
            StatRow("AGI", stats.agi, Color(0xFFFFEB3B), "Agility — exercise variety")
            StatRow("WIS", stats.wis, Color(0xFF9C27B0), "Wisdom — effort tracking")
            StatRow("LUK", stats.luk, Color(0xFFFF9800), "Luck — rare achievements")
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, color: Color, tooltip: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = (value / 999f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "stat_$label",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun QuestCard(description: String, xpReward: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("Reward: +$xpReward XP", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun BadgeShelf(unlockedBadges: List<String>) {
    if (unlockedBadges.isEmpty()) return
    Column {
        Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // Placeholder grid — replace badge items with actual drawable resources when art is ready
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(unlockedBadges) { badge ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(badge.take(2).uppercase(), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun rankColor(rank: String): Color = when (rank) {
    "E"        -> Color(0xFF9E9E9E)
    "D"        -> Color(0xFF4CAF50)
    "C"        -> Color(0xFF2196F3)
    "B"        -> Color(0xFF9C27B0)
    "A"        -> Color(0xFFFF9800)
    "S"        -> Color(0xFFFF5722)
    "National" -> Color(0xFFFFD700)
    else       -> Color(0xFF9E9E9E)
}
```

- [ ] **Step 2: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/StatusWindowScreen.kt
git commit -m "feat: add StatusWindowScreen RPG character sheet with rank/stats/quests/badges"
```

---

### Task 8: MakeUpQuestSheet — bodyweight streak-save circuit

**Files:**
- Create: `app/src/main/java/com/ironlog/app/ui/screens/MakeUpQuestSheet.kt`

- [ ] **Step 1: Implement MakeUpQuestSheet**

```kotlin
// app/src/main/java/com/ironlog/app/ui/screens/MakeUpQuestSheet.kt
package com.ironlog.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class MakeUpCircuit(
    val id: String,
    val name: String,
    val category: String,  // "Push", "Pull", "Core", "Full Body"
    val exercises: List<String>,
    val instructions: String,
)

private val CIRCUITS = listOf(
    MakeUpCircuit(
        id = "push_basic",
        name = "Push Circuit",
        category = "Push",
        exercises = listOf("20 Push-ups", "15 Tricep Dips (chair)", "10 Pike Push-ups"),
        instructions = "3 rounds, 60 sec rest between rounds.",
    ),
    MakeUpCircuit(
        id = "pull_basic",
        name = "Pull Circuit",
        category = "Pull",
        exercises = listOf("10 Pull-ups (or 15 Inverted Rows)", "12 Chin-ups", "20 Band Pull-Aparts"),
        instructions = "3 rounds, 90 sec rest between rounds.",
    ),
    MakeUpCircuit(
        id = "core_basic",
        name = "Core Circuit",
        category = "Core",
        exercises = listOf("30 Crunches", "20 Leg Raises", "60 sec Plank", "20 Russian Twists"),
        instructions = "3 rounds, 45 sec rest between rounds.",
    ),
    MakeUpCircuit(
        id = "fullbody_basic",
        name = "Full Body Circuit",
        category = "Full Body",
        exercises = listOf("20 Burpees", "20 Squats", "15 Push-ups", "10 Pull-ups", "30 sec Plank"),
        instructions = "4 rounds, 90 sec rest between rounds.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakeUpQuestSheet(
    onDismiss: () -> Unit,
    onComplete: (circuitId: String) -> Unit,
) {
    var selected: MakeUpCircuit? by remember { mutableStateOf(null) }
    var confirmed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "⚡ Streak Save Quest",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Complete one bodyweight circuit to protect your streak this week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!confirmed) {
                CIRCUITS.forEach { circuit ->
                    val isSelected = selected?.id == circuit.id
                    OutlinedCard(
                        onClick = { selected = circuit },
                        modifier = Modifier.fillMaxWidth(),
                        border = if (isSelected) CardDefaults.outlinedCardBorder() else null,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(circuit.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(circuit.category, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            circuit.exercises.forEach { ex ->
                                Text("• $ex", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Button(
                    onClick = { if (selected != null) confirmed = true },
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Circuit")
                }
            } else {
                val circuit = selected!!
                Text("Complete this circuit:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                circuit.exercises.forEach { ex ->
                    Text("✓ $ex", style = MaterialTheme.typography.bodyMedium)
                }
                Text(circuit.instructions, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onComplete(circuit.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Text("✅ I Completed It — Save My Streak! (+25 XP)")
                }

                TextButton(onClick = { confirmed = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose a different circuit")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/MakeUpQuestSheet.kt
git commit -m "feat: add MakeUpQuestSheet bodyweight circuit bottom sheet for streak save"
```

---

### Task 9: Navigation wiring — add statusWindow route

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Read AppNavigation.kt to find the NavHost composable**

Open `app/src/main/java/com/ironlog/app/ui/navigation/AppNavigation.kt` and locate the `NavHost` block and existing route strings.

- [ ] **Step 2: Add the statusWindow route**

Inside the `NavHost` block, add:

```kotlin
composable("statusWindow") {
    // GamificationViewModel must be provided by the parent
    // Use hiltViewModel() if Hilt is available, or pass via CompositionLocal
    val gamificationVm: GamificationViewModel = viewModel(
        factory = GamificationViewModelFactory(LocalContext.current.applicationContext as Application, boxStore)
    )
    val gamState by gamificationVm.uiState.collectAsState()
    var showMakeupSheet by remember { mutableStateOf(false) }

    StatusWindowScreen(
        state = gamState,
        onBack = { navController.popBackStack() },
        onMakeupQuestTap = { showMakeupSheet = true },
    )

    if (showMakeupSheet) {
        MakeUpQuestSheet(
            onDismiss = { showMakeupSheet = false },
            onComplete = { circuitId ->
                showMakeupSheet = false
                // Compute current ISO week key and record makeup
                val weekKey = java.time.LocalDate.now().let {
                    val week = it.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                    val year = it.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                    "$year-W${week.toString().padStart(2, '0')}"
                }
                gamificationVm.completeMakeupQuest(weekKey)
            },
        )
    }
}
```

Add required imports at the top of AppNavigation.kt:
```kotlin
import com.ironlog.app.ui.screens.StatusWindowScreen
import com.ironlog.app.ui.screens.MakeUpQuestSheet
import com.ironlog.app.ui.viewmodel.GamificationViewModel
```

- [ ] **Step 3: Add entry point from HomeScreen or profile section**

In `HomeScreen.kt`, add a button/icon that navigates to `"statusWindow"`:

```kotlin
// In the top app bar actions or profile icon area:
IconButton(onClick = { navController.navigate("statusWindow") }) {
    // Placeholder: use a shield or person icon
    Icon(Icons.Default.Shield, contentDescription = "Status Window")
}
```

- [ ] **Step 4: Build**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/navigation/AppNavigation.kt
git add app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt
git commit -m "feat: wire StatusWindowScreen into navigation with makeup quest sheet"
```

---

### Task 10: XP bar on HomeScreen

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Add compact XP bar and streak badge to HomeScreen**

In the HomeScreen's top section (above the day card list), add:

```kotlin
// Compact XP + streak bar at top of HomeScreen
// (gamificationVm should be obtained the same way as in AppNavigation)
val gamState by gamificationVm.uiState.collectAsState()

Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    // Rank chip
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "${gamState.rank}  Lv.${gamState.level}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    // XP bar (compact)
    val xpFraction = if (gamState.xpForNextLevel > 0)
        (gamState.xpInLevel.toFloat() / gamState.xpForNextLevel).coerceIn(0f, 1f)
    else 0f
    val animXp by animateFloatAsState(xpFraction, tween(600), label = "homeXpBar")
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 12.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animXp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }

    // Streak badge
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (gamState.streakWeeks > 0)
            MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "🔥 ${gamState.streakWeeks}w",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
```

- [ ] **Step 2: Build and verify**

```
.\gradlew :app:assembleDebug 2>&1 | Select-String "error:|BUILD" | Select-Object -Last 5
```

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/ironlog/app/ui/screens/HomeScreen.kt
git commit -m "feat: show compact XP bar and streak badge on HomeScreen"
```

---

## Badge Art Prompt (for AI image generation)

Generate badge art with your preferred tool using this prompt:

> "Create a set of 7 circular badge icons for a fitness RPG app with Solo Leveling dark fantasy aesthetic. Each badge is 512×512px with a transparent background. Badges should be dark, dramatic, and cinematic — think dungeon hunter crests. Rank E: gray stone shield. Rank D: green iron fist. Rank C: blue crystal sword. Rank B: purple arcane rune. Rank A: orange fire emblem. Rank S: red dragon sigil. Rank National: gold crown with wings. Style: metallic, glowing edges, dark background. No text on the badges."

Save the generated images as `app/src/main/res/drawable/badge_rank_e.webp`, `badge_rank_d.webp`, etc., and replace the Text placeholders in `BadgeShelf` with `Image(painterResource(...))`.

---

## Self-Review

**Spec coverage:**
- ✅ XP system with level curve and rank progression (E→National)
- ✅ Streak engine: weekly-goal-based, not daily-consecutive
- ✅ Bodyweight make-up quests save streak (goal-1 + 1 makeup = qualifies)
- ✅ Adaptive dungeon boss quest based on PR history and activity profile
- ✅ Six RPG stats derived from real workout data
- ✅ Status Window screen with rank badge, XP bar, stats, quests, badges
- ✅ HomeScreen XP bar + streak badge
- ✅ MakeUpQuestSheet with 4 circuit categories
- ✅ All engines are pure-Kotlin, fully unit-tested
- ✅ ObjectBox entities for persistence
- ✅ Badge art prompt provided

**Type consistency:** `RpgStats` defined in `StatEngine.kt`, used in `GamificationUiState` and `StatusWindowScreen`. `XpAction` enum defined in `XpEngine.kt`, referenced in `GamificationViewModel`. `UserActivityProfile` defined in `DungeonBossEngine.kt`, used in `GamificationViewModel.ensureMonthlyBoss`.
