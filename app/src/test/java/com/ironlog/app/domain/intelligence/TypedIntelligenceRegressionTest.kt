package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class TypedIntelligenceRegressionTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-01T18:00:00Z"), ZoneOffset.UTC)
    private fun session(id: String, date: String, exercise: HistoryExercise) = HistoryEntry(id, date, exercises = listOf(exercise))
    private val bench = HistoryExercise(exerciseId = "bench", name = "Barbell Bench Press", primaryMuscle = "Chest",
        trackingType = "weight_reps", sets = listOf(HistoryExerciseSet(weight = 60.0, reps = 8.0)))

    @Test fun `first observation is baseline not a personal record improvement`() {
        val baseline = session("one", "2026-08-30T10:00:00Z", bench)
        assertEquals(0, TrainingIntelligenceEngine.build(listOf(baseline), clock = clock).prLast30)
        val improvement = session("two", "2026-08-31T10:00:00Z", bench.copy(sets = listOf(HistoryExerciseSet(weight = 65.0, reps = 8.0))))
        assertEquals(1, TrainingIntelligenceEngine.build(listOf(improvement, baseline), clock = clock).prLast30)
    }

    @Test fun `added bodyweight and weighted duration never produce load based PR velocity`() {
        listOf("bodyweight_plus_weight_reps", "duration_weight").forEach { tracking ->
            val exercise = bench.copy(trackingType = tracking)
            val history = listOf(session("one", "2026-08-30", exercise),
                session("two", "2026-08-31", exercise.copy(sets = listOf(HistoryExerciseSet(weight = 80.0, reps = 8.0)))))
            assertEquals(tracking, 0, TrainingIntelligenceEngine.build(history, clock = clock).prLast30)
        }
    }

    @Test fun `independent warmup flag excludes all intelligence exposure and load density`() {
        val exercise = bench.copy(sets = List(8) { HistoryExerciseSet(weight = 100.0, reps = 5.0, isWarmup = true, rpe = 9.0) })
        val history = listOf("2026-08-30", "2026-08-31", "2026-09-01").mapIndexed { i, date -> session("$i", date, exercise) }
        val result = TrainingIntelligenceEngine.build(history, clock = clock)
        assertEquals(0, result.setsByMuscle.values.sum())
        assertFalse(result.neuralFatigue.isFlagged)
        assertTrue(computeGranularVolume(history).isEmpty())
    }

    @Test fun `invalid sets never contribute granular exposure`() {
        val exercise = bench.copy(sets = listOf(HistoryExerciseSet(weight = 60.0, reps = 0.0),
            HistoryExerciseSet(weight = Double.NaN, reps = 8.0)))
        assertTrue(computeGranularVolume(listOf(session("invalid", "2026-08-31", exercise))).isEmpty())
    }
}
