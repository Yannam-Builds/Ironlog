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
