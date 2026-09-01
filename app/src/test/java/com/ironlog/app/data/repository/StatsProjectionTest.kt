package com.ironlog.app.data.repository

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StatsProjectionTest {
    private val now = Instant.parse("2026-09-01T01:00:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun `stats uses local calendar dates rather than UTC prefix`() {
        val result = projectStats(listOf(workout("2026-08-31T23:30:00Z", strength())), "kg", now, zone)
        assertEquals(1, result.streak)
        assertEquals(1, result.chartData.last().value)
        assertEquals("2026-09-01", result.personalBests.single().date)
        assertEquals("set", result.personalBests.single().setId)
        assertEquals("press", result.personalBests.single().exerciseId)
    }

    @Test fun `date only legacy history is interpreted on local date`() {
        val result = projectStats(listOf(workout("2026-09-01", strength())), "kg", now, zone)
        assertEquals(1, result.chartData.last().value)
        assertEquals("2026-09-01", result.personalBests.single().date)
    }

    @Test fun `timed sets cannot create PB even when duration is large`() {
        val timed = strength().copy(exerciseId = "hold", trackingType = "duration_weight")
        val result = projectStats(listOf(workout("2026-09-01", timed)), "kg", now, zone)
        assertTrue(result.personalBests.isEmpty())
        assertTrue(result.pb.isEmpty())
        assertTrue(result.pbGroups.isEmpty())
        assertEquals(0.0, result.history.single().volume, 0.0)
    }

    @Test fun `historical edit and deletion recompute the best from surviving eligible sets`() {
        val record = strength().copy(sets = listOf(
            HistoryExerciseSet(id = "best", weight = 100.0, reps = 10.0),
            HistoryExerciseSet(id = "other", weight = 80.0, reps = 10.0),
        ))
        val original = projectStats(listOf(workout("2026-09-01", record)), "lbs", now, zone)
        val edited = projectStats(listOf(workout("2026-09-01", record.copy(sets = record.sets.drop(1)))), "lbs", now, zone)
        assertTrue(original.personalBests.single().estOneRm > edited.personalBests.single().estOneRm)
        assertEquals("lbs", edited.weightUnit)
        assertTrue(projectStats(emptyList(), "kg", now, zone).personalBests.isEmpty())
    }

    @Test fun `PR reset cutoff hides old records without hiding workout history or other stats`() {
        val beforeReset = workout(
            "2026-08-31T18:00:00Z",
            strength().copy(sets = listOf(HistoryExerciseSet(id = "old-best", weight = 120.0, reps = 8.0))),
        )
        val cutoff = Instant.parse("2026-08-31T20:00:00Z")

        val result = projectStats(listOf(beforeReset), "kg", now, zone, prResetAt = cutoff)

        assertEquals(listOf(beforeReset), result.history)
        assertEquals(1, result.totalSets)
        assertEquals(1, result.streak)
        assertTrue(result.personalBests.isEmpty())
        assertTrue(result.pb.isEmpty())
        assertTrue(result.pbGroups.isEmpty())
    }

    @Test fun `set completed after reset can establish a new PR even when its workout started before reset`() {
        val cutoff = Instant.parse("2026-08-31T20:00:00Z")
        val spanningWorkout = workout(
            "2026-08-31T19:30:00Z",
            strength().copy(
                sets = listOf(
                    HistoryExerciseSet(
                        id = "new-best",
                        weight = 90.0,
                        reps = 8.0,
                        completedAt = Instant.parse("2026-08-31T20:05:00Z").toEpochMilli(),
                    ),
                ),
            ),
        )

        val result = projectStats(listOf(spanningWorkout), "kg", now, zone, prResetAt = cutoff)

        assertEquals("new-best", result.personalBests.single().setId)
        assertEquals(setOf("press"), historicalPrBaselines(listOf(spanningWorkout), cutoff, now, zone).keys)
    }

    @Test fun `legacy set without completion timestamp uses workout time at the reset boundary`() {
        val cutoff = Instant.parse("2026-08-31T20:00:00Z")
        val before = workout("2026-08-31T19:59:59Z", strength()).copy(id = "before")
        val after = workout("2026-08-31T20:00:01Z", strength()).copy(id = "after")

        val result = projectStats(listOf(before, after), "kg", now, zone, prResetAt = cutoff)

        assertEquals("set", result.personalBests.single().setId)
        assertEquals(1, estimatedPerformances(listOf(before, after), now, zone, cutoff).size)
    }

    @Test fun `future and malformed entries remain editable but never contribute derived statistics`() {
        val eligible = workout("2026-08-31T23:30:00Z", strength())
        val future = workout("2026-09-01T02:00:00Z", strength().copy(sets = listOf(
            HistoryExerciseSet(id = "future-best", weight = 1000.0, reps = 8.0),
        ))).copy(id = "future", duration = 7200)
        val malformed = future.copy(id = "malformed", date = "not-a-date")
        val result = projectStats(listOf(eligible, future, malformed), "kg", now, zone)
        assertEquals(3, result.history.size)
        assertEquals(1, result.totalSets)
        assertEquals(20, result.avgDurationMin)
        assertEquals(1, result.chartData.last().value)
        assertEquals("set", result.personalBests.single().setId)
        assertTrue(projectStats(listOf(future, malformed), "kg", now, zone).personalBests.isEmpty())
        assertEquals(0, projectStats(listOf(future, malformed), "kg", now, zone).streak)
    }

    @Test fun `weekly and muscle volume reject future and malformed timestamps at the same instant`() {
        val exercise = strength().copy(muscleContributions = mapOf("Chest" to 0.5))
        val eligible = workout("2026-08-31T23:30:00Z", exercise)
        val future = eligible.copy(id = "future", date = "2026-09-01T02:00:00Z")
        val malformed = eligible.copy(id = "malformed", date = "bad-date")
        val rows = listOf(eligible, future, malformed)
        assertEquals(800.0, projectWeeklyVolume(rows, now, zone), 0.0)
        assertEquals(mapOf("Chest" to 400.0), projectMuscleVolume(rows, 14, now, zone))
    }

    private fun strength() = HistoryExercise(
        exerciseId = "press", name = "Synthetic press", trackingType = "weight_reps", category = "strength",
        sets = listOf(HistoryExerciseSet(id = "set", weight = 100.0, reps = 8.0)),
    )
    private fun workout(date: String, exercise: HistoryExercise) = HistoryEntry(
        id = "synthetic", date = date, exercises = listOf(exercise), duration = 1200,
    )
}
