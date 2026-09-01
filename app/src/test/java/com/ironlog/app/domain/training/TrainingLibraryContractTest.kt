package com.ironlog.app.domain.training

import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.repository.projectHistory
import com.ironlog.app.ui.model.HistoryExercise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real bundled library rows, projected through the production history adapter. */
class TrainingLibraryContractTest {
    @Test fun `weighted pullup library equipment corrects stale false bodyweight flag`() {
        val exercise = projected("weighted_pull_ups")
        assertTrue(exercise.isBodyweight)
        assertEquals(TrackingMode.ADDED_LOAD_REPS, TrainingSetPolicy.tracking(exercise))
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, exercise.sets.single()))
        assertEquals(160.0, TrainingSetPolicy.externalLoadVolume(exercise, exercise.sets.single()), 0.0)
    }

    @Test fun `band assistance cannot be mistaken for external load strength`() {
        val exercise = projected("band_assisted_pullup")
        assertEquals(TrackingMode.ASSISTED_REPS, TrainingSetPolicy.tracking(exercise))
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, exercise.sets.single()))
        assertEquals(0.0, TrainingSetPolicy.externalLoadVolume(exercise, exercise.sets.single()), 0.0)
        assertEquals(TrackingMode.ASSISTED_REPS, TrainingSetPolicy.tracking(exercise.copy(trackingType = "assisted_bodyweight", isBodyweight = true)))
    }

    @Test fun `real cardio requiresExternalLoad flag cannot demand load for duration or distance`() {
        val rows = library.filter { it["id"]?.jsonPrimitive?.content in setOf("recumbent_bike", "battle_rope_alternating_waves") }
        assertEquals("Both real regression fixtures must be found", 2, rows.size)
        rows.forEach { row ->
            val exercise = projected(row.getValue("id").jsonPrimitive.content)
            val set = exercise.sets.single().copy(weight = 0.0, reps = 600.0)
            assertTrue(exercise.name, exercise.requiresExternalLoad)
            assertTrue(exercise.name, TrainingSetPolicy.isValidWorkingSet(exercise, set))
            assertEquals(exercise.name, 600.0, TrainingSetPolicy.cardioSeconds(exercise, set), 0.0)
            assertNull(TrainingSetPolicy.estimatedOneRm(exercise, set))
        }
    }

    @Test fun `stale rep metadata on real plank resolves as duration without overriding explicit timed types`() {
        val exercise = projected("plank").copy(trackingType = "weight_reps")
        assertEquals(TrackingMode.DURATION, TrainingSetPolicy.tracking(exercise))
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, exercise.sets.single()))
        assertEquals(TrackingMode.WEIGHTED_DURATION, TrainingSetPolicy.tracking(exercise.copy(trackingType = "duration_weight")))
        assertEquals(TrackingMode.UNKNOWN, TrainingSetPolicy.tracking(exercise.copy(trackingType = "unrecognized")))
        assertEquals(TrackingMode.UNKNOWN, TrainingSetPolicy.tracking(exercise.copy(trackingType = null)))
    }

    private val library get() = Json.parseToJsonElement(File("src/main/assets/exerciseLibrary.json").readText())
        .jsonObject.getValue("exercises").jsonArray.map { it.jsonObject }

    private fun projected(id: String): HistoryExercise {
        val row = library.single { it["id"]?.jsonPrimitive?.content == id }
        val exercise = ExerciseEntity().apply {
            uid = id; name = row.getValue("name").jsonPrimitive.content
            equipment = row["equipment"]?.jsonPrimitive?.content.orEmpty()
            category = row["category"]?.jsonPrimitive?.content.orEmpty()
            trackingType = row["trackingType"]?.jsonPrimitive?.content
            isBodyweight = row["isBodyweight"]?.jsonPrimitive?.booleanOrNull ?: false
            requiresExternalLoad = row["requiresExternalLoad"]?.jsonPrimitive?.booleanOrNull ?: false
        }
        val workout = WorkoutEntity().apply { uid = "synthetic-workout"; status = "completed" }
        val link = WorkoutExerciseEntity().apply { uid = "link"; workoutUid = workout.uid; exerciseUid = id }
        val set = WorkoutSetEntity().apply { uid = "set"; workoutExerciseUid = link.uid; weight = 20.0; reps = 8.0 }
        return projectHistory(listOf(workout), listOf(link), listOf(set), listOf(exercise)).single().exercises.single()
    }
}
