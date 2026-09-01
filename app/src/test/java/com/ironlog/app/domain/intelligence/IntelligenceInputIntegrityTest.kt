package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.model.ExerciseFilters
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.ui.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class IntelligenceInputIntegrityTest {
    @Test fun `future history never establishes training age or a performance baseline`() {
        val future = HistoryEntry("future", Instant.now().plusSeconds(86400).toString(), exercises = listOf(
            HistoryExercise(name = "Barbell Bench Press", sets = List(8) { HistoryExerciseSet( weight = 100.0, reps = 8.0) })))
        val result = TrainingIntelligenceEngine.build(listOf(future))
        assertEquals(0.0, result.trainingAgeYears, .0001)
        assertEquals(0, result.prLast30)
    }

    @Test fun `weighted partial week exposure is not called a personal target deficiency`() {
        val insight = VolumeInterpretationEngine.buildMuscleInsight(mapOf("Chest" to VolumeLandmark(3, "low", 10, 20, 14)))
        assertTrue(insight.contains("weighted", ignoreCase = true))
        assertFalse(insight.contains("your target"))
    }
    @Test fun `unknown metadata is not evidence of an exercise match`() {
        val result = SubstitutionEngine.buildExerciseAlternatives(exercise("a"), listOf(exercise("b")), ExerciseFilters(), 5)
        assertTrue(result.bestMatches.isEmpty())
        assertTrue(result.sameMuscle.isEmpty())
        assertTrue(result.sameEquipment.isEmpty())
    }

    @Test fun `negative substitution limit is empty not a crash`() {
        assertEquals(SubstitutionEngine.AlternativesResult.EMPTY,
            SubstitutionEngine.buildExerciseAlternatives(exercise("a"), listOf(exercise("b")), ExerciseFilters(), -1))
    }

    @Test fun `future wellness checkin cannot change recovery`() {
        val now = Instant.parse("2026-08-31T10:00:00Z").toEpochMilli()
        val regions = mapOf("Push" to .5)
        assertEquals(RecoveryReadinessEngine.score(regions, nowEpochMs = now).score,
            RecoveryReadinessEngine.score(regions, ManualRecoveryInput(1, 5, 5, recordedAt = now + 86400000), now).score)
    }

    @Test fun `uppercase warmups produce no intelligence volume or prs`() {
        val workout = HistoryEntry("warmup", Instant.now().minusSeconds(60).toString(), exercises = listOf(
            HistoryExercise(name = "Barbell Bench Press", sets = listOf(HistoryExerciseSet(weight = 60.0, reps = 10.0, type = "WARMUP")))))
        val result = TrainingIntelligenceEngine.build(listOf(workout))
        assertEquals(0, result.setsByMuscle.values.sum())
        assertEquals(0, result.prLast30)
    }

    @Test fun `unknown exercises do not invent core training volume`() {
        val workout = HistoryEntry("unknown", Instant.now().minusSeconds(60).toString(), exercises = listOf(
            HistoryExercise(name = "Custom movement XYZ", sets = List(10) { HistoryExerciseSet(reps = 10.0) })))
        assertEquals(0, TrainingIntelligenceEngine.build(listOf(workout)).setsByMuscle.values.sum())
    }

    private fun exercise(id: String) = LegacyExerciseShape(id, id, "Unknown $id", emptyList(), null,
        emptyList(), "", "", "weight_reps", true, emptyList(), false, null, null, null, null, emptyList(), "")
}
