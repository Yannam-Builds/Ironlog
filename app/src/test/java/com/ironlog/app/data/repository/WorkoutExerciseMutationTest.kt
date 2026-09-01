package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.data.model.SetInput
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import io.objectbox.BoxStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkoutExerciseMutationTest {
    @get:Rule val temporary = TemporaryFolder()

    private suspend fun withStore(block: suspend (BoxStore) -> Unit) {
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        try { block(store) } finally { store.closeThreadResources(); store.close() }
    }

    @Test fun `abandon uses injected transaction and cannot clear another active session`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repo = WorkoutRepository(settings, store)
            val first = WorkoutEntity().apply { uid = "old-active"; status = "active" }
            val current = WorkoutEntity().apply { uid = "current-active"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(first, current)
            settings.setActiveWorkoutId(current.uid)
            settings.setString("active_workout_start_ms", "123")
            assertEquals("abandoned", repo.abandonWorkout(first.uid).status)
            assertEquals(current.uid, settings.getActiveWorkoutId())
            assertEquals("123", settings.getString("active_workout_start_ms"))
            assertEquals("abandoned", repo.abandonWorkout(first.uid).status)
            assertEquals("abandoned", repo.abandonWorkout(current.uid).status)
            assertNull(settings.getActiveWorkoutId())
            assertNull(settings.getString("active_workout_start_ms"))
        }
    }

    @Test fun `completed session cannot be abandoned by stale action`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            val repo = WorkoutRepository(settings, store)
            val completed = WorkoutEntity().apply { uid = "completed"; status = "completed" }
            store.boxFor(WorkoutEntity::class.java).put(completed)
            settings.setActiveWorkoutId("other-session")
            val error = runCatching { repo.abandonWorkout(completed.uid) }.exceptionOrNull()
            assertTrue("reject completed state explicitly", error is IllegalStateException)
            assertEquals("completed", store.boxFor(WorkoutEntity::class.java).get(completed.objectBoxId).status)
            assertEquals("other-session", settings.getActiveWorkoutId())
        }
    }

    @Test fun `removing middle repeated exercise removes only its sets and compacts durable order`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val repo = WorkoutRepository(SettingsRepository(store.boxFor(AppSettingEntity::class.java)), store)
            val exercise = ExerciseEntity().apply { uid = "repeated"; name = "Bench" }
            store.boxFor(ExerciseEntity::class.java).put(exercise)
            val workout = WorkoutEntity().apply { uid = "active"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val first = repo.addExerciseToWorkout(workout.uid, exercise.uid)
            val middle = repo.addExerciseToWorkout(workout.uid, exercise.uid)
            val last = repo.addExerciseToWorkout(workout.uid, exercise.uid)
            repo.addSet(first.uid, SetInput(uid = "kept", weight = 40.0, reps = 8.0))
            repo.addSet(middle.uid, SetInput(uid = "removed", weight = 50.0, reps = 8.0))
            repo.deleteWorkoutExercise(middle.uid)
            val added = repo.addExerciseToWorkout(workout.uid, exercise.uid)
            val rows = store.boxFor(WorkoutExerciseEntity::class.java).all.sortedBy { it.orderIndex }
            assertEquals(listOf(first.uid, last.uid, added.uid), rows.map { it.uid })
            assertEquals(listOf(0, 1, 2), rows.map { it.orderIndex })
            assertEquals(listOf("kept"), store.boxFor(WorkoutSetEntity::class.java).all.map { it.uid })
        }
    }

    @Test fun `failed outer transaction rolls back exercise removal and its set cascade`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val repo = WorkoutRepository(SettingsRepository(store.boxFor(AppSettingEntity::class.java)), store)
            val exercise = ExerciseEntity().apply { uid = "exercise"; name = "Bench" }
            store.boxFor(ExerciseEntity::class.java).put(exercise)
            val workout = WorkoutEntity().apply { uid = "active"; status = "active" }
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val row = repo.addExerciseToWorkout(workout.uid, exercise.uid)
            repo.addSet(row.uid, SetInput(uid = "saved", weight = 40.0, reps = 8.0))
            // A repository-provided blocking transaction boundary makes cascade rollback deterministic.
            assertTrue(runCatching { store.runInTx {
                repo.deleteWorkoutExerciseBlocking(row.uid)
                error("rollback test")
            } }.isFailure)
            assertEquals(row.uid, store.boxFor(WorkoutExerciseEntity::class.java).all.single().uid)
            assertEquals("saved", store.boxFor(WorkoutSetEntity::class.java).all.single().uid)
        }
    }
}
