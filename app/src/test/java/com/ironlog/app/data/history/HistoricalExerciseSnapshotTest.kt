package com.ironlog.app.data.history

import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalExerciseSnapshotTest {
    @Test fun `capture and codec retain display tracking and contribution metadata`() {
        val exercise = ExerciseEntity().apply {
            name = "Weighted dip"
            primaryMuscle = "Chest"
            secondaryMusclesJson = "[\"Triceps\"]"
            equipment = "Bodyweight"
            category = "strength"
            trackingType = "bodyweight_weight_reps"
            isBodyweight = true
            requiresExternalLoad = true
        }
        val muscles = listOf(
            muscle("Chest", "primary", 0.7),
            muscle("Triceps", "secondary", 0.3),
        )

        val decoded = HistoricalExerciseSnapshotCodec.decode(
            HistoricalExerciseSnapshotCodec.encode(captureHistoricalExerciseSnapshot(exercise, muscles)),
        )!!

        assertEquals(1, decoded.version)
        assertEquals("Weighted dip", decoded.name)
        assertEquals(listOf("Chest"), decoded.primaryMuscles)
        assertEquals(listOf("Triceps"), decoded.secondaryMuscles)
        assertEquals(mapOf("Chest" to 0.7, "Triceps" to 0.3), decoded.muscleContributions)
        assertEquals("bodyweight_weight_reps", decoded.trackingType)
        assertTrue(decoded.isBodyweight && decoded.requiresExternalLoad)
    }

    @Test fun `capture accepts performed metadata overrides without mutating library identity`() {
        val exercise = ExerciseEntity().apply {
            name = "Current library name"; primaryMuscle = "Back"; equipment = "Cable"; category = "strength"
        }

        val snapshot = captureHistoricalExerciseSnapshot(
            exercise = exercise,
            muscles = emptyList(),
            override = HistoricalExerciseSnapshotOverride(
                name = "Imported original name",
                primaryMuscles = listOf("Lats"),
                equipment = "Machine",
                trackingType = "duration_weight",
                isBodyweight = false,
                requiresExternalLoad = true,
            ),
        )

        assertEquals("Imported original name", snapshot.name)
        assertEquals(listOf("Lats"), snapshot.primaryMuscles)
        assertEquals("Machine", snapshot.equipment)
        assertEquals("duration_weight", snapshot.trackingType)
        assertEquals("Current library name", exercise.name)
    }

    @Test fun `unknown versions malformed json and blank names are rejected safely`() {
        assertNull(HistoricalExerciseSnapshotCodec.decode(""))
        assertNull(HistoricalExerciseSnapshotCodec.decode("{bad"))
        assertNull(HistoricalExerciseSnapshotCodec.decode("{\"version\":99,\"name\":\"Future\"}"))
        assertNull(HistoricalExerciseSnapshotCodec.decode("{\"version\":1,\"name\":\"   \"}"))
    }

    private fun muscle(name: String, role: String, fraction: Double) = ExerciseMuscleEntity().apply {
        muscle = name
        this.role = role
        contributionFraction = fraction
    }
}
