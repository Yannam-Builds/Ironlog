package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import io.objectbox.BoxStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkoutSessionNotesTest {
    @get:Rule val temporary = TemporaryFolder()

    private suspend fun withStore(block: suspend (BoxStore) -> Unit) {
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        try { block(store) } finally { store.closeThreadResources(); store.close() }
    }

    @Test fun `completion flushes notes by durable repeated-exercise row and explicit blank clears`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val workout = WorkoutEntity().apply { uid = "active"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val first = WorkoutExerciseEntity().apply { uid = "first"; workoutUid = workout.uid; exerciseUid = "same-exercise"; notes = "old first" }
            val second = WorkoutExerciseEntity().apply { uid = "second"; workoutUid = workout.uid; exerciseUid = "same-exercise"; notes = "old second" }
            store.boxFor(WorkoutExerciseEntity::class.java).put(first, second)
            settings.setActiveWorkoutId(workout.uid)
            settings.setString("active_workout_draft_${workout.uid}", "{}", "json")

            repository.completeWorkout(workout.uid, exerciseNotesByUid = mapOf(first.uid to "Pause at bottom", second.uid to ""))

            assertEquals("completed", store.boxFor(WorkoutEntity::class.java).get(workout.objectBoxId).status)
            assertEquals("Pause at bottom", store.boxFor(WorkoutExerciseEntity::class.java).get(first.objectBoxId).notes)
            assertEquals("", store.boxFor(WorkoutExerciseEntity::class.java).get(second.objectBoxId).notes)
            assertNull(settings.getActiveWorkoutId())
            assertNull(settings.getString("active_workout_draft_${workout.uid}"))
            repository.completeWorkout(workout.uid, exerciseNotesByUid = mapOf(first.uid to "stale retry"))
            assertEquals("Pause at bottom", store.boxFor(WorkoutExerciseEntity::class.java).get(first.objectBoxId).notes)
        }
    }

    @Test fun `native completion freezes current exercise metadata for later history reads`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val exercise = ExerciseEntity().apply {
                uid = "native-exercise"; name = "Paused squat"; primaryMuscle = "Quadriceps"
                trackingType = "weight_reps"; equipment = "Barbell"
            }
            store.boxFor(ExerciseEntity::class.java).put(exercise)
            val workout = WorkoutEntity().apply { uid = "native-workout"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val row = WorkoutExerciseEntity().apply {
                uid = "native-row"; workoutUid = workout.uid; exerciseUid = exercise.uid; this.exercise.target = exercise
            }
            store.boxFor(WorkoutExerciseEntity::class.java).put(row)

            repository.completeWorkout(workout.uid)

            val snapshot = HistoricalExerciseSnapshotCodec.decode(
                store.boxFor(WorkoutExerciseEntity::class.java).get(row.objectBoxId).exerciseSnapshotJson,
            )!!
            assertEquals("Paused squat", snapshot.name)
            assertEquals("weight_reps", snapshot.trackingType)
            assertEquals("Barbell", snapshot.equipment)

            exercise.name = "Renamed after workout"
            exercise.trackingType = "duration_distance"
            store.boxFor(ExerciseEntity::class.java).put(exercise)
            val history = HistoryRepository(store).completedSnapshotBlocking().single().exercises.single()
            assertEquals("Paused squat", history.name)
            assertEquals("weight_reps", history.trackingType)
        }
    }

    @Test fun `foreign exercise note rejects and rolls back completion notes and active cleanup`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val workout = WorkoutEntity().apply { uid = "active"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val owned = WorkoutExerciseEntity().apply { uid = "owned"; workoutUid = workout.uid; notes = "kept" }
            val foreign = WorkoutExerciseEntity().apply { uid = "foreign"; workoutUid = "other"; notes = "other note" }
            store.boxFor(WorkoutExerciseEntity::class.java).put(owned, foreign)
            settings.setActiveWorkoutId(workout.uid)
            settings.setString("active_workout_draft_${workout.uid}", "{\"notes\":\"preserved\"}", "json")
            val failure = runCatching {
                repository.completeWorkout(workout.uid, exerciseNotesByUid = linkedMapOf(owned.uid to "new", foreign.uid to "wrong session"))
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals("active", store.boxFor(WorkoutEntity::class.java).get(workout.objectBoxId).status)
            assertEquals("kept", store.boxFor(WorkoutExerciseEntity::class.java).get(owned.objectBoxId).notes)
            assertEquals("other note", store.boxFor(WorkoutExerciseEntity::class.java).get(foreign.objectBoxId).notes)
            assertEquals(workout.uid, settings.getActiveWorkoutId())
            assertEquals("{\"notes\":\"preserved\"}", settings.getString("active_workout_draft_${workout.uid}"))
        }
    }

    @Test fun `clearing active exercise notes also replaces the draft without touching other note domains`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val workout = WorkoutEntity().apply {
                uid = "active-notes"
                status = "active"
                notes = "Keep the session note"
            }
            val otherWorkout = WorkoutEntity().apply {
                uid = "other-workout"
                status = "active"
            }
            store.boxFor(WorkoutEntity::class.java).put(workout, otherWorkout)
            val first = WorkoutExerciseEntity().apply {
                uid = "active-first"
                workoutUid = workout.uid
                exerciseUid = "exercise-one"
                notes = "Plan cue one"
            }
            val second = WorkoutExerciseEntity().apply {
                uid = "active-second"
                workoutUid = workout.uid
                exerciseUid = "exercise-two"
                notes = "Plan cue two"
            }
            val other = WorkoutExerciseEntity().apply {
                uid = "other-row"
                workoutUid = otherWorkout.uid
                exerciseUid = "exercise-one"
                notes = "Keep another workout's note"
            }
            store.boxFor(WorkoutExerciseEntity::class.java).put(first, second, other)
            val set = WorkoutSetEntity().apply {
                uid = "set-note"
                workoutExerciseUid = first.uid
                notes = "Keep the set note"
            }
            store.boxFor(WorkoutSetEntity::class.java).put(set)
            settings.setActiveWorkoutId(workout.uid)
            settings.setString("active_workout_draft_${workout.uid}", "{\"exerciseNotes\":{\"0\":\"stale\"}}", "json")
            settings.setString("exercise_next_note:exercise-one", "Keep the next-session reminder")
            val sanitizedDraft = "{\"version\":3,\"exerciseNotes\":{}}"

            val cleared = repository.clearActiveWorkoutExerciseNotes(workout.uid, sanitizedDraft)

            assertEquals(2, cleared)
            val workoutRows = store.boxFor(WorkoutExerciseEntity::class.java).all.associateBy { it.uid }
            assertEquals("", workoutRows.getValue(first.uid).notes)
            assertEquals("", workoutRows.getValue(second.uid).notes)
            assertEquals("Keep another workout's note", workoutRows.getValue(other.uid).notes)
            assertEquals("Keep the session note", store.boxFor(WorkoutEntity::class.java).get(workout.objectBoxId).notes)
            assertEquals("Keep the set note", store.boxFor(WorkoutSetEntity::class.java).get(set.objectBoxId).notes)
            assertEquals("Keep the next-session reminder", settings.getString("exercise_next_note:exercise-one"))
            assertEquals(sanitizedDraft, settings.getString("active_workout_draft_${workout.uid}"))
        }
    }

    @Test fun `clearing exercise notes rejects a stale active identity without partial writes`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val workout = WorkoutEntity().apply { uid = "stale-session"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val row = WorkoutExerciseEntity().apply {
                uid = "stale-row"
                workoutUid = workout.uid
                notes = "Must survive rejection"
            }
            store.boxFor(WorkoutExerciseEntity::class.java).put(row)
            settings.setActiveWorkoutId("newer-session")
            val originalDraft = "{\"exerciseNotes\":{\"0\":\"Must survive rejection\"}}"
            settings.setString("active_workout_draft_${workout.uid}", originalDraft, "json")

            val failure = runCatching {
                repository.clearActiveWorkoutExerciseNotes(workout.uid, "{\"exerciseNotes\":{}}")
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals("Must survive rejection", store.boxFor(WorkoutExerciseEntity::class.java).get(row.objectBoxId).notes)
            assertEquals(originalDraft, settings.getString("active_workout_draft_${workout.uid}"))
        }
    }

    @Test fun `clearing exercise notes rejects completed history without mutation`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val workout = WorkoutEntity().apply { uid = "completed-session"; status = "completed" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val row = WorkoutExerciseEntity().apply {
                uid = "completed-row"
                workoutUid = workout.uid
                notes = "Immutable history note"
            }
            store.boxFor(WorkoutExerciseEntity::class.java).put(row)
            settings.setActiveWorkoutId(workout.uid)
            val originalDraft = "{\"exerciseNotes\":{\"0\":\"Immutable history note\"}}"
            settings.setString("active_workout_draft_${workout.uid}", originalDraft, "json")

            val failure = runCatching {
                repository.clearActiveWorkoutExerciseNotes(workout.uid, "{\"exerciseNotes\":{}}")
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals("Immutable history note", store.boxFor(WorkoutExerciseEntity::class.java).get(row.objectBoxId).notes)
            assertEquals(originalDraft, settings.getString("active_workout_draft_${workout.uid}"))
        }
    }

    @Test fun `completion uses monotonic duration override and clears dual clock state`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repository = WorkoutRepository(settings, store)
            val workout = WorkoutEntity().apply {
                uid = "clock-safe"
                status = "active"
                startedAt = System.currentTimeMillis()
            }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            settings.setActiveWorkoutId(workout.uid)
            settings.setString("active_workout_start_ms", "1")
            settings.setString("active_workout_start_elapsed_ms", "2000")
            settings.setString("active_workout_start_boot_count", "7")
            settings.setString("active_workout_rest_end_ms", "9")
            settings.setString("active_workout_rest_end_elapsed_ms", "10")
            settings.setString("active_workout_rest_boot_count", "7")

            repository.completeWorkout(
                workoutId = workout.uid,
                durationStartEpochMs = 1L,
                durationSecondsOverride = 321,
            )

            val saved = store.boxFor(WorkoutEntity::class.java).get(workout.objectBoxId)
            assertEquals(321, saved.durationSeconds)
            assertNull(settings.getString("active_workout_start_elapsed_ms"))
            assertNull(settings.getString("active_workout_start_boot_count"))
            assertNull(settings.getString("active_workout_rest_end_elapsed_ms"))
            assertNull(settings.getString("active_workout_rest_boot_count"))
        }
    }
}
