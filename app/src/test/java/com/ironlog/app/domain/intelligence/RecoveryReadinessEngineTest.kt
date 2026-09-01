// app/src/test/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngineTest.kt
package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryReadinessEngineTest {

    private fun pushWorkout(
        at: Instant,
        setCount: Int = 8,
        rir: Double? = 2.0,
        type: String = "normal",
    ) = HistoryEntry(
        id = "push",
        date = at.toString(),
        duration = 55 * 60,
        name = "Push",
        exercises = listOf(
            HistoryExercise(
                exerciseId = "bench",
                name = "Barbell Bench Press",
                primaryMuscle = "chest",
                equipment = "barbell",
                sets = List(setCount) {
                    HistoryExerciseSet(weight = 80.0, reps = 8.0, rir = rir, type = type)
                },
            ),
        ),
    )

    @Test fun `yesterdays push session cannot be hidden by untouched regions`() {
        val workoutAt = Instant.parse("2026-08-12T10:00:00Z")
        val now = workoutAt.plusSeconds(24 * 3600)

        val regions = RecoveryReadinessEngine.readinessByRegion(
            listOf(pushWorkout(workoutAt)),
            nowEpochMs = now.toEpochMilli(),
        )
        val score = RecoveryReadinessEngine.score(regions)

        assertTrue("Push should still be recovering after 24 hours", regions.getValue("Push") < 0.75)
        assertTrue("Fresh regions must not dilute the limiting muscle", score.score < 78)
    }

    @Test fun `failure work creates more next day fatigue than submaximal work`() {
        val workoutAt = Instant.parse("2026-08-12T10:00:00Z")
        val now = workoutAt.plusSeconds(24 * 3600).toEpochMilli()

        val submaximal = RecoveryReadinessEngine.readinessByRegion(
            listOf(pushWorkout(workoutAt, rir = 3.0)), nowEpochMs = now,
        ).getValue("Push")
        val failure = RecoveryReadinessEngine.readinessByRegion(
            listOf(pushWorkout(workoutAt, rir = 0.0, type = "failure")), nowEpochMs = now,
        ).getValue("Push")

        assertTrue("Failure training should recover more slowly", failure < submaximal)
    }

    @Test fun `future dated workouts do not create recovery fatigue`() {
        val now = Instant.parse("2026-08-13T10:00:00Z")
        val future = pushWorkout(now.plusSeconds(24 * 3600))

        val regions = RecoveryReadinessEngine.readinessByRegion(listOf(future), nowEpochMs = now.toEpochMilli())

        assertTrue(regions.values.all { it == 1.0 })
    }

    @Test fun `neutral manual check in does not inflate readiness`() {
        val now = Instant.parse("2026-08-13T10:00:00Z").toEpochMilli()
        val readiness = mapOf("Push" to 0.60, "Pull" to 0.90, "Legs" to 0.95)
        val withoutCheckIn = RecoveryReadinessEngine.score(readiness, nowEpochMs = now)
        val neutralCheckIn = RecoveryReadinessEngine.score(
            readiness,
            ManualRecoveryInput(soreness = 3, sleepQuality = 3, energy = 3, recordedAt = now),
            nowEpochMs = now,
        )

        assertEquals(withoutCheckIn.score, neutralCheckIn.score)
    }

    @Test fun `readiness can be evaluated against a historical clock`() {
        val workoutAt = Instant.parse("2026-01-01T12:00:00Z")
        val history = listOf(
            HistoryEntry(
                id = "w1",
                date = workoutAt.toString(),
                exercises = listOf(
                    HistoryExercise(
                        exerciseId = "bench",
                        name = "Bench Press",
                        primaryMuscle = "chest",
                        sets = listOf(HistoryExerciseSet(weight = 100.0, reps = 5.0)),
                    )
                ),
            )
        )
        val immediatelyAfter = RecoveryReadinessEngine.readinessByRegion(
            history,
            nowEpochMs = workoutAt.plusSeconds(60).toEpochMilli(),
        )
        val aWeekLater = RecoveryReadinessEngine.readinessByRegion(
            history,
            nowEpochMs = workoutAt.plusSeconds(7 * 24 * 3600).toEpochMilli(),
        )
        assertTrue(aWeekLater.getValue("Push") > immediatelyAfter.getValue("Push"))
    }

    @Test fun `fully recovered regions do not recommend reducing volume`() {
        val suggestions = RecoveryReadinessEngine.suggestions(
            mapOf("Push" to 1.0, "Pull" to 1.0, "Legs" to 1.0),
        )
        assertTrue(suggestions.none { it.contains("reduce volume", ignoreCase = true) })
    }

    @Test fun `fatigued region recommends reducing volume`() {
        val suggestions = RecoveryReadinessEngine.suggestions(
            mapOf("Push" to 0.95, "Pull" to 0.55),
        )
        assertTrue(suggestions.any { it.contains("Pull") && it.contains("reduce volume") })
    }
}
