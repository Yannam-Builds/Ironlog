package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.history.HistoricalExerciseSnapshot
import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HistoryProjectionTest {
    @Test fun `completed projection prefers immutable exercise snapshot over edited library metadata`() {
        val workout = workout("completed")
        val editedLibrary = ExerciseEntity().apply {
            uid = "library"
            name = "Renamed cable movement"
            primaryMuscle = "Back"
            equipment = "Cable"
            category = "cardio"
            trackingType = "duration_distance"
            isBodyweight = false
            requiresExternalLoad = false
        }
        val link = link("performed", workout.uid, editedLibrary.uid).apply {
            exerciseSnapshotJson = HistoricalExerciseSnapshotCodec.encode(
                HistoricalExerciseSnapshot(
                    name = "Paused bodyweight row",
                    primaryMuscles = listOf("Upper Back"),
                    primaryMuscle = "Upper Back",
                    secondaryMuscles = listOf("Biceps"),
                    muscleContributions = mapOf("Upper Back" to 0.75, "Biceps" to 0.25),
                    equipment = "Bodyweight",
                    category = "strength",
                    trackingType = "bodyweight_reps",
                    isBodyweight = true,
                    requiresExternalLoad = false,
                ),
            )
        }

        val projected = projectHistory(listOf(workout), listOf(link), emptyList(), listOf(editedLibrary)).single()
            .exercises.single()

        assertEquals("Paused bodyweight row", projected.name)
        assertEquals("bodyweight_reps", projected.trackingType)
        assertEquals("Bodyweight", projected.equipment)
        assertEquals(listOf("Upper Back"), projected.primaryMuscles)
        assertEquals(listOf("Biceps"), projected.secondaryMuscles)
        assertEquals(mapOf("Upper Back" to 0.75, "Biceps" to 0.25), projected.muscleContributions)
        assertTrue(projected.isBodyweight)
    }

    @Test fun `legacy and malformed snapshots fall back to current library metadata`() {
        val workout = workout("completed")
        val library = ExerciseEntity().apply {
            uid = "library"; name = "Legacy row"; trackingType = "weight_reps"; equipment = "Barbell"
        }
        val blank = link("blank", workout.uid, library.uid)
        val malformed = link("malformed", workout.uid, library.uid).apply { exerciseSnapshotJson = "{not-json" }

        val projected = projectHistory(listOf(workout), listOf(blank, malformed), emptyList(), listOf(library)).single().exercises

        assertEquals(listOf("Legacy row", "Legacy row"), projected.map { it.name })
        assertTrue(projected.all { it.trackingType == "weight_reps" && it.equipment == "Barbell" })
    }

    @Test fun `captured null metadata does not adopt fields added to the library later`() {
        val workout = workout("completed")
        val editedLibrary = ExerciseEntity().apply {
            uid = "library"; name = "Current"; trackingType = "duration_distance"; equipment = "Machine"
        }
        val link = link("performed", workout.uid, editedLibrary.uid).apply {
            exerciseSnapshotJson = HistoricalExerciseSnapshotCodec.encode(HistoricalExerciseSnapshot(name = "Original"))
        }

        val projected = projectHistory(listOf(workout), listOf(link), emptyList(), listOf(editedLibrary)).single()
            .exercises.single()

        assertNull(projected.trackingType)
        assertNull(projected.equipment)
        assertNull(projected.category)
    }

    @Test fun `projection preserves instant metadata fractional effort and independent flags`() {
        val workout = workout("completed")
        val exercise = ExerciseEntity().apply {
            uid = "library"; name = "Synthetic hold"; trackingType = "duration_weight"
            primaryMuscle = "Shoulders"; equipment = "Dumbbell"; category = "strength"
            secondaryMusclesJson = "[\"Core\"]"; isBodyweight = true; requiresExternalLoad = true
        }
        val link = link("we", workout.uid, exercise.uid)
        link.supersetGroup = "group"; link.notes = "Exercise note"
        val set = WorkoutSetEntity().apply {
            uid = "set"; workoutExerciseUid = link.uid; weight = 20.0; reps = 60.0
            isWarmup = true; isDropset = true; isAmrap = true; toFailure = true
            rpe = 7.5; rir = 2.5; notes = "Set note"; restSeconds = 95; completedAt = null
        }
        val result = projectHistory(listOf(workout), listOf(link), listOf(set), listOf(exercise)).single()
        assertEquals("2026-08-31T23:30:00Z", result.date)
        assertEquals("day", result.planDayUid)
        assertTrue(result.imported)
        assertEquals("Workout note", result.summaryText)
        val projected = result.exercises.single()
        assertEquals("duration_weight", projected.trackingType)
        assertEquals(listOf("Core"), projected.secondaryMuscles)
        assertEquals("Shoulders", projected.primaryMuscle)
        assertEquals("Dumbbell", projected.equipment)
        assertTrue(projected.isBodyweight && projected.requiresExternalLoad)
        assertEquals("group", projected.supersetGroup)
        assertEquals("Exercise note", projected.note)
        val performed = projected.sets.single()
        assertTrue(performed.isWarmup && performed.isDropset && performed.isAmrap && performed.toFailure)
        assertEquals(2.5, performed.rir!!, 0.0)
        assertEquals(7.5, performed.rpe!!, 0.0)
        assertEquals("Set note", performed.note)
        assertEquals(95, performed.restSeconds)
        assertNull(performed.completedAt)
    }

    @Test fun `projection filters active parents and orphan children but preserves invalid visible sets`() {
        val completed = workout("completed")
        val active = workout("active").apply { uid = "active" }
        val links = listOf(link("first", completed.uid, "missing"), link("active-link", active.uid, "missing"))
        val sets = listOf("first", "active-link", "orphan").map { parent ->
            WorkoutSetEntity().apply { uid = parent; workoutExerciseUid = parent; weight = Double.NaN; reps = 0.0 }
        }
        val result = projectHistory(listOf(active, completed), links, sets, emptyList())
        assertEquals(listOf(completed.uid), result.map { it.id })
        val exercise = result.single().exercises.single()
        assertNull(exercise.trackingType)
        assertEquals(1, exercise.sets.size)
        assertTrue(exercise.sets.single().weight.isNaN())
    }

    @Test fun `projection uses deterministic exercise and set order and muscle metadata`() {
        val workout = workout("completed")
        val first = link("first", workout.uid, "library").apply { orderIndex = 0 }
        val second = link("second", workout.uid, "library").apply { orderIndex = 1 }
        val muscle = ExerciseMuscleEntity().apply {
            exerciseUid = "library"; this.muscle = "Chest"; role = "primary"; contributionFraction = 0.7
        }
        val sets = listOf(2, 1).map { order -> WorkoutSetEntity().apply {
            uid = "set-$order"; workoutExerciseUid = "first"; setIndex = order
        } }
        val result = projectHistory(listOf(workout), listOf(second, first), sets, emptyList(), listOf(muscle)).single()
        assertEquals(listOf("first", "second"), result.exercises.map { it.id })
        assertEquals(listOf("set-1", "set-2"), result.exercises.first().sets.map { it.id })
        assertEquals(listOf("Chest"), result.exercises.first().primaryMuscles)
        assertEquals(mapOf("Chest" to 0.7), result.exercises.first().muscleContributions)
    }

    private fun workout(status: String) = WorkoutEntity().apply {
        uid = "workout"; this.status = status; startedAt = Instant.parse("2026-08-31T23:30:00Z").toEpochMilli()
        planDayUid = "day"; imported = true; notes = "Workout note"; name = "Synthetic workout"
    }

    private fun link(id: String, parent: String, library: String) = WorkoutExerciseEntity().apply {
        uid = id; workoutUid = parent; exerciseUid = library
    }
}
