package com.ironlog.app.ui.screens.stats

import com.ironlog.app.ui.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ExerciseProgressTrackingTest {
    private fun trend(tracking: String, sets: List<HistoryExerciseSet>) = buildExerciseTrendLocal(listOf(
        HistoryEntry("one", "2026-08-01T10:00:00Z", exercises = listOf(
            HistoryExercise(name = "Test movement", trackingType = tracking, sets = sets)))), "Test movement")

    @Test fun `warmup flags and invalid sets do not appear in trend`() {
        assertTrue(trend("weight_reps", listOf(HistoryExerciseSet(weight = 100.0, reps = 5.0, isWarmup = true),
            HistoryExerciseSet(weight = 60.0, reps = 0.0))).isEmpty())
    }
    @Test fun `timed and added bodyweight trends have no fictitious one rep max`() {
        val set = listOf(HistoryExerciseSet(weight = 10.0, reps = 12.0))
        assertEquals(0.0, trend("bodyweight_plus_weight_reps", set).single().e1rm, 0.0)
        val hold = trend("duration_weight", set).single()
        assertEquals(0.0, hold.e1rm, 0.0)
        assertEquals(0.0, hold.volume, 0.0)
    }
    @Test fun `single rep uses actual load and reps metric is mean not maximum`() {
        val row = trend("weight_reps", listOf(HistoryExerciseSet(weight = 100.0, reps = 1.0),
            HistoryExerciseSet(weight = 20.0, reps = 9.0))).single()
        assertEquals(100.0, row.e1rm, .0001)
        assertEquals(5.0, row.reps, .0001)
    }
    @Test fun `all time range still excludes future and malformed dates`() {
        fun row(date: String) = ExerciseTrendRow(date, 100.0, 80.0, 8.0, 640.0)
        val past = row("2026-01-01")
        assertEquals(listOf(past), filterExerciseRowsByRange(listOf(past, row("2999-01-01"), row("invalid")), null))
    }

    @Test fun `weekly chart labels align with aggregated values across ISO year boundary`() {
        fun row(date: String) = ExerciseTrendRow(date, 100.0, 80.0, 8.0, 640.0)
        val metric = computeMetricConfig(listOf(row("2025-12-29"), row("2025-12-30"), row("2026-01-05")), "CONSISTENCY", "kg")
        assertEquals(listOf(2.0, 1.0), metric.values)
        assertEquals(listOf("2026-W01", "2026-W02"), metric.chartLabels)
    }

    @Test fun `session history excludes future and malformed dates just like trend`() {
        fun session(id: String, date: String) = HistoryEntry(id, date, exercises = listOf(
            HistoryExercise(name = "Squat", trackingType = "weight_reps", sets = listOf(HistoryExerciseSet(weight = 80.0, reps = 8.0)))))
        val rows = buildSessionHistoryRows(listOf(session("past", "2026-01-01"), session("future", "2999-01-01"), session("bad", "invalid")), "Squat", "kg")
        assertEquals(listOf("2026-01-01"), rows.map { it.date })
    }

    @Test fun `PR reset hides old e1rm records while retaining their non-PR training data`() {
        fun session(id: String, date: String, load: Double) = HistoryEntry(id, date, exercises = listOf(
            HistoryExercise(name = "Squat", trackingType = "weight_reps", sets = listOf(
                HistoryExerciseSet(id = "set-$id", weight = load, reps = 5.0),
            )),
        ))
        val cutoff = Instant.parse("2026-08-15T00:00:00Z")

        val rows = buildExerciseTrendLocal(
            history = listOf(
                session("old", "2026-08-01T10:00:00Z", 120.0),
                session("new", "2026-08-20T10:00:00Z", 90.0),
            ),
            exerciseName = "Squat",
            prResetAt = cutoff,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(2, rows.size)
        assertFalse(rows.first().hasEstimate)
        assertTrue(rows.first().loadAvailable)
        assertEquals(120.0, rows.first().load, 0.0)
        assertTrue(rows.last().hasEstimate)
    }
}
