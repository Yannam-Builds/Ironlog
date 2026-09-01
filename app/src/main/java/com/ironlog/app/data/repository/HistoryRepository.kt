package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity_
import com.ironlog.app.ui.model.HistoryEntry
import io.objectbox.BoxStore

class HistoryRepository(private val boxStore: BoxStore? = null) {
    private val store get() = boxStore ?: ObjectBox.store
    private val workoutBox get() = store.boxFor(WorkoutEntity::class.java)
    private val workoutExerciseBox get() = store.boxFor(WorkoutExerciseEntity::class.java)
    private val workoutSetBox get() = store.boxFor(WorkoutSetEntity::class.java)

    suspend fun completedSnapshot(): List<HistoryEntry> = withContext(Dispatchers.IO) { completedSnapshotBlocking() }

    /** The caller may use this inside its transaction; no dispatcher switch or independent write occurs. */
    fun completedSnapshotBlocking(): List<HistoryEntry> = store.callInReadTx {
        val workouts = workoutBox.query(WorkoutEntity_.status.equal("completed")).build().use { it.find() }
        if (workouts.isEmpty()) return@callInReadTx emptyList()
        val links = workoutExerciseBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workouts.map { it.uid }.toTypedArray()))
            .build().use { it.find() }
        val sets = if (links.isEmpty()) emptyList() else workoutSetBox.query(
            WorkoutSetEntity_.workoutExerciseUid.oneOf(links.map { it.uid }.toTypedArray()),
        ).build().use { it.find() }
        val exerciseIds = links.map { it.exerciseUid }.distinct().toTypedArray()
        val exercises = if (exerciseIds.isEmpty()) emptyList() else store.boxFor(ExerciseEntity::class.java)
            .query(ExerciseEntity_.uid.oneOf(exerciseIds)).build().use { it.find() }
        val muscles = if (exerciseIds.isEmpty()) emptyList() else store.boxFor(ExerciseMuscleEntity::class.java)
            .query(ExerciseMuscleEntity_.exerciseUid.oneOf(exerciseIds)).build().use { it.find() }
        projectHistory(workouts, links, sets, exercises, muscles)
    }

    suspend fun updateWorkout(
        uid: String,
        startedAt: Long? = null,
        durationSeconds: Int? = null,
        rating: Double? = null,
        updateRating: Boolean = false,
        notes: String? = null,
        updateNotes: Boolean = false,
    ): WorkoutEntity = withContext(Dispatchers.IO) {
        val row = workoutBox.query(WorkoutEntity_.uid.equal(uid)).build().use { it.findFirst() }
            ?: error("Workout not found: $uid")
        startedAt?.let { row.startedAt = it }
        durationSeconds?.let { row.durationSeconds = kotlin.math.max(0, it) }
        if (updateRating) row.rating = rating
        if (updateNotes) row.notes = notes
        row.updatedAt = System.currentTimeMillis()
        workoutBox.put(row)
        row
    }

    suspend fun deleteWorkout(uid: String) = withContext(Dispatchers.IO) {
        deleteWorkoutsBlocking(listOf(uid))
    }

    suspend fun deleteWorkouts(ids: List<String>) = withContext(Dispatchers.IO) {
        deleteWorkoutsBlocking(ids)
    }

    private fun deleteWorkoutsBlocking(ids: List<String>) {
        if (ids.isEmpty()) return
        store.runInTx {
            val rows = workoutBox.query(WorkoutEntity_.uid.oneOf(ids.distinct().toTypedArray()))
                .build().use { it.find() }
            if (rows.isEmpty()) return@runInTx
            val rowUids = rows.map { it.uid }.toTypedArray()
            val exercises = workoutExerciseBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(rowUids))
                .build().use { it.find() }
            val exerciseUids = exercises.map { it.uid }.toTypedArray()
            val sets = if (exerciseUids.isEmpty()) emptyList() else {
                workoutSetBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(exerciseUids))
                    .build().use { it.find() }
            }
            workoutSetBox.remove(sets)
            workoutExerciseBox.remove(exercises)
            workoutBox.remove(rows)
        }
    }
}
