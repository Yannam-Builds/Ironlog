// app/src/test/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngineTest.kt
package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.health.BiometricSnapshot
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryReadinessEngineTest {

    @Test fun `blendWithBiometric returns training score when no biometrics`() {
        val snap = BiometricSnapshot()  // all null
        val result = RecoveryReadinessEngine.blendWithBiometric(0.8, snap)
        assertEquals(0.8, result, 0.001)
    }

    @Test fun `blendWithBiometric blends down when sleep is poor`() {
        val snap = BiometricSnapshot(sleepHours = 4.0)  // worst sleep score
        val result = RecoveryReadinessEngine.blendWithBiometric(1.0, snap)
        assertTrue("Poor sleep should pull blend below 1.0", result < 1.0)
    }

    @Test fun `blendWithBiometric is 1_0 when training and biometrics are both perfect`() {
        val snap = BiometricSnapshot(sleepHours = 8.0, hrvRmssd = 80.0)
        val result = RecoveryReadinessEngine.blendWithBiometric(1.0, snap)
        assertEquals(1.0, result, 0.001)
    }

    @Test fun `blendWithBiometric stays within 0_0 to 1_0`() {
        val snap = BiometricSnapshot(sleepHours = 0.0, hrvRmssd = 0.0)
        val result = RecoveryReadinessEngine.blendWithBiometric(0.0, snap)
        assertTrue(result in 0.0..1.0)
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
