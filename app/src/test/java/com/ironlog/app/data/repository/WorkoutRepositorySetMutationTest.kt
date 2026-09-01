package com.ironlog.app.data.repository

import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkoutRepositorySetMutationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val store by lazy {
        MyObjectBox.builder().directory(temporaryFolder.newFolder("objectbox")).build()
    }

    @After
    fun closeStore() {
        if (!store.isClosed) store.close()
    }

    @Test
    fun `delete set compacts durable order and next add remains unique`() = databaseTest {
        val workoutExercise = seedWorkoutExercise()
        val repository = WorkoutRepository(
            settingsRepository = SettingsRepository(store.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java)),
            boxStore = store,
        )
        val first = repository.addSet(workoutExercise.uid, SetInput(uid = "set-a", weight = 40.0, reps = 8.0))
        val middle = repository.addSet(workoutExercise.uid, SetInput(uid = "set-b", weight = 50.0, reps = 8.0))
        repository.addSet(workoutExercise.uid, SetInput(uid = "set-c", weight = 60.0, reps = 8.0))

        repository.deleteSetAndCompact(workoutExercise.uid, middle.uid)
        repository.addSet(workoutExercise.uid, SetInput(uid = "set-d", weight = 65.0, reps = 5.0))

        val rows = store.boxFor(WorkoutSetEntity::class.java).all.sortedBy { it.setIndex }
        assertEquals(listOf("set-a", "set-c", "set-d"), rows.map { it.uid })
        assertEquals(listOf(1, 2, 3), rows.map { it.setIndex })
        assertEquals(first.uid, rows.first().uid)
    }

    @Test
    fun `set note and stable uid survive repository round trip`() = databaseTest {
        val workoutExercise = seedWorkoutExercise()
        val repository = WorkoutRepository(
            settingsRepository = SettingsRepository(store.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java)),
            boxStore = store,
        )

        val created = repository.addSet(
            workoutExercise.uid,
            SetInput(uid = "stable-note-set", weight = 20.0, reps = 10.0, notes = "Pause at the bottom"),
        )

        assertEquals("stable-note-set", created.uid)
        assertEquals("Pause at the bottom", created.notes)
    }

    @Test
    fun `explicit effort clear persists while unrelated updates keep effort`() = databaseTest {
        val exercise = seedWorkoutExercise()
        val repository = WorkoutRepository(SettingsRepository(store.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java)), store)
        val set = repository.addSet(exercise.uid, SetInput(uid = "effort", weight = 40.0, reps = 8.0, rpe = 8.5, rir = 1.5))
        repository.updateSet(set.uid, SetInput(weight = 42.5))
        assertEquals(8.5, store.boxFor(WorkoutSetEntity::class.java).get(set.objectBoxId).rpe)
        repository.updateSet(set.uid, SetInput(rpeUpdate = com.ironlog.app.data.model.EffortUpdate.Clear))
        assertEquals(null, store.boxFor(WorkoutSetEntity::class.java).get(set.objectBoxId).rpe)
        assertEquals(1.5, store.boxFor(WorkoutSetEntity::class.java).get(set.objectBoxId).rir)
        repository.updateSet(set.uid, SetInput(rirUpdate = com.ironlog.app.data.model.EffortUpdate.Clear))
        assertEquals(null, store.boxFor(WorkoutSetEntity::class.java).get(set.objectBoxId).rir)
    }

    @Test
    fun `start empty workout creates an active session without plan exercises`() = databaseTest {
        val settings = SettingsRepository(store.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java))
        val repository = WorkoutRepository(settings, store)

        val workout = repository.startEmptyWorkout("Open Workout")

        assertEquals("Open Workout", workout.name)
        assertEquals("active", workout.status)
        assertNull(workout.planUid)
        assertNull(workout.planDayUid)
        assertEquals(workout.uid, settings.getActiveWorkoutId())
        assertNull(settings.getString("active_workout_day_id"))
        assertEquals(0L, store.boxFor(WorkoutExerciseEntity::class.java).count())
    }

    @Test
    fun `clear completed workouts removes their graph and preserves active workout graph`() = databaseTest {
        val activeLink = seedWorkoutExercise()
        val repository = WorkoutRepository(
            SettingsRepository(store.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java)),
            store,
        )
        repository.addSet(activeLink.uid, SetInput(uid = "active-set", weight = 40.0, reps = 8.0))
        val completedWorkout = WorkoutEntity().apply {
            uid = "completed-workout"
            name = "Finished Push"
            status = "completed"
            startedAt = 2L
            completedAt = 3L
        }.also { store.boxFor(WorkoutEntity::class.java).put(it) }
        val completedLink = WorkoutExerciseEntity().apply {
            uid = "completed-link"
            workoutUid = completedWorkout.uid
            workout.target = completedWorkout
            exerciseUid = activeLink.exerciseUid
            exercise.target = activeLink.exercise.target
        }.also { store.boxFor(WorkoutExerciseEntity::class.java).put(it) }
        repository.addSet(completedLink.uid, SetInput(uid = "completed-set", weight = 60.0, reps = 5.0))

        repository.clearCompletedWorkouts()

        assertEquals(listOf("workout-1"), store.boxFor(WorkoutEntity::class.java).all.map { it.uid })
        assertEquals(listOf("workout-exercise-1"), store.boxFor(WorkoutExerciseEntity::class.java).all.map { it.uid })
        assertEquals(listOf("active-set"), store.boxFor(WorkoutSetEntity::class.java).all.map { it.uid })
        assertTrue(store.boxFor(WorkoutEntity::class.java).all.single().status == "active")
    }

    // Repository methods use blocking IO transactions. Own and close readers on that same IO thread.
    private fun databaseTest(block: suspend () -> Unit) = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        try { block() } finally { store.closeThreadResources() }
    }

    private fun seedWorkoutExercise(): WorkoutExerciseEntity {
        val exercise = ExerciseEntity().apply {
            uid = "exercise-1"
            name = "Bench Press"
            createdAt = 1L
            updatedAt = 1L
        }
        store.boxFor(ExerciseEntity::class.java).put(exercise)
        val workout = WorkoutEntity().apply {
            uid = "workout-1"
            name = "Push"
            status = "active"
            startedAt = 1L
            createdAt = 1L
            updatedAt = 1L
        }
        store.boxFor(WorkoutEntity::class.java).put(workout)
        return WorkoutExerciseEntity().apply {
            uid = "workout-exercise-1"
            this.workout.target = workout
            workoutUid = workout.uid
            this.exercise.target = exercise
            exerciseUid = exercise.uid
            orderIndex = 0
            createdAt = 1L
            updatedAt = 1L
            store.boxFor(WorkoutExerciseEntity::class.java).put(this)
        }
    }
}
