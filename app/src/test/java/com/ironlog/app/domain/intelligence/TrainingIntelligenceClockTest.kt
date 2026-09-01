package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TrainingIntelligenceClockTest {
    private fun workout(date: String) = HistoryEntry(date, date, exercises = listOf(
        HistoryExercise(name = "Barbell Bench Press", sets = List(8) { HistoryExerciseSet(weight = 60.0, reps = 8.0) })))

    @Test fun `ISO week uses the injected zone for the same instant`() {
        val instant = Instant.parse("2026-08-31T01:00:00Z")
        val history = listOf(workout("2026-08-30T16:00:00Z"))
        val india = TrainingIntelligenceEngine.build(history, clock = Clock.fixed(instant, ZoneId.of("Asia/Kolkata")))
        val america = TrainingIntelligenceEngine.build(history, clock = Clock.fixed(instant, ZoneId.of("America/Los_Angeles")))
        assertEquals(0, india.setsByMuscle.values.sum())
        assertTrue(america.setsByMuscle.values.sum() > 0)
    }
    @Test fun `date only imports never invent a nighttime performance advantage`() {
        val clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneId.of("Asia/Kolkata"))
        val result = TrainingIntelligenceEngine.build(listOf(workout("2026-08-28"), workout("2026-08-29"), workout("2026-08-30")), clock = clock)
        assertFalse(result.bestWindow.contains("Night leads"))
    }
    @Test fun `later today is still future history`() {
        val clock = Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC)
        val result = TrainingIntelligenceEngine.build(listOf(workout("2026-08-31T18:00:00Z")), clock = clock)
        assertEquals(0, result.setsByMuscle.values.sum())
        assertEquals(0.0, result.trainingAgeYears, .0001)
    }
}
