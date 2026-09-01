package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LiveProofClockTest {
    private fun entry(date: String, day: String? = null) = HistoryEntry("w", date, name = "Push", planDayUid = day,
        exercises = listOf(HistoryExercise(name = "Bench Press", sets = List(8) { HistoryExerciseSet(weight = 60.0, reps = 8.0) })))
    @Test fun `future later today cannot qualify current week`() {
        val now = Instant.parse("2026-09-01T10:00:00Z")
        assertEquals(0, StreakEngine(ZoneOffset.UTC).computeStreakWeeks(listOf(entry("2026-09-01T18:00:00Z")), 1, emptyMap(), now = now))
    }
    @Test fun `date only credit uses constructor zone not system zone`() {
        val now = Instant.parse("2026-08-30T20:00:00Z")
        val history = listOf(entry("2026-08-31"))
        assertEquals(1, StreakEngine(ZoneId.of("Asia/Kolkata")).computeStreakWeeks(history, 1, emptyMap(), now = now))
        assertEquals(0, StreakEngine(ZoneId.of("America/Los_Angeles")).computeStreakWeeks(history, 1, emptyMap(), now = now))
    }
    @Test fun `weekly plan proof uses local week and does not fallback across different stable IDs`() {
        val now = Instant.parse("2026-08-31T01:00:00Z")
        val history = listOf(entry("2026-08-30T20:00:00Z", "other"))
        assertFalse(hasPlanDayProofThisWeek(history, "target", "Push", now, ZoneId.of("Asia/Kolkata")))
        assertTrue(hasPlanDayProofThisWeek(history, "other", "Push", now, ZoneId.of("Asia/Kolkata")))
        assertTrue(hasPlanDayProofThisWeek(history.map { it.copy(planDayUid = null) }, "target", "Push", now, ZoneId.of("Asia/Kolkata")))
    }
}
