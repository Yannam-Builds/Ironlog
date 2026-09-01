package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.LocalDate
import java.time.ZoneId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingIntelligenceEngineTest {

    @Test
    fun `warmups do not duplicate bar weight or equal working weight`() {
        assertTrue(TrainingIntelligenceEngine.generateWarmupSets(20.0).isEmpty())
        val warmups = TrainingIntelligenceEngine.generateWarmupSets(30.0)
        assertEquals(warmups.map { it.first }.distinct().size, warmups.size)
        assertTrue(warmups.all { it.first < 30.0 })
    }

    @Test
    fun `three distinct recent heavy days flag load density`() {
        val history = (0L..2L).map { offset -> heavySession("day-$offset", LocalDate.now().minusDays(offset)) }
        val result = TrainingIntelligenceEngine.build(history).neuralFatigue
        assertTrue(result.isFlagged)
        assertEquals(3, result.consecutiveDays)
    }

    @Test
    fun `duplicate sessions on one day do not inflate consecutive count`() {
        val today = LocalDate.now()
        val history = listOf(
            heavySession("one", today),
            heavySession("two", today),
            heavySession("three", today.minusDays(1)),
        )
        val result = TrainingIntelligenceEngine.build(history).neuralFatigue
        assertFalse(result.isFlagged)
        assertEquals(2, result.consecutiveDays)
    }

    @Test
    fun `old heavy cluster does not create a current warning`() {
        val history = (8L..10L).map { offset -> heavySession("day-$offset", LocalDate.now().minusDays(offset)) }
        val result = TrainingIntelligenceEngine.build(history).neuralFatigue
        assertFalse(result.isFlagged)
        assertEquals(0, result.consecutiveDays)
    }

    @Test
    fun `performance window waits for sufficient observations`() {
        val single = HistoryEntry(id = "one", date = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toString())
        assertTrue(TrainingIntelligenceEngine.build(listOf(single)).bestWindow.contains("at least 3"))
    }

    @Test
    fun `PR velocity starts a new baseline after the reset cutoff`() {
        val now = Instant.parse("2026-09-01T12:00:00Z")
        val cutoff = Instant.parse("2026-08-20T00:00:00Z")
        val history = listOf(
            strengthSession("old-record", "2026-08-19T12:00:00Z", 100.0),
            strengthSession("new-baseline", "2026-08-21T12:00:00Z", 80.0),
            strengthSession("new-record", "2026-08-25T12:00:00Z", 90.0),
        )

        val result = TrainingIntelligenceEngine.build(
            history = history,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            prResetAt = cutoff,
        )

        assertEquals(1, result.prLast30)
    }

    private fun heavySession(id: String, date: LocalDate): HistoryEntry = HistoryEntry(
        id = id,
        date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
        exercises = listOf(
            HistoryExercise(
                name = "Barbell Squat",
                sets = listOf(HistoryExerciseSet(weight = 100.0, reps = 5.0, rpe = 8.0)),
            ),
        ),
    )

    private fun strengthSession(id: String, date: String, weight: Double): HistoryEntry = HistoryEntry(
        id = id,
        date = date,
        exercises = listOf(
            HistoryExercise(
                exerciseId = "bench",
                name = "Bench Press",
                trackingType = "weight_reps",
                sets = listOf(HistoryExerciseSet(id = "set-$id", weight = weight, reps = 5.0)),
            ),
        ),
    )
}
