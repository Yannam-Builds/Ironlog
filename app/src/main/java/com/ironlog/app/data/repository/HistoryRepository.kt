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

class HistoryRepository {
    private val workoutBox get() = ObjectBox.store.boxFor(WorkoutEntity::class.java)
    private val workoutExerciseBox get() = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
    private val workoutSetBox get() = ObjectBox.store.boxFor(WorkoutSetEntity::class.java)

    suspend fun updateWorkout(
        uid: String,
        startedAt: Long? = null,
        durationSeconds: Int? = null,
        rating: Double? = null,
        notes: String? = null,
    ): WorkoutEntity = withContext(Dispatchers.IO) {
        val row = workoutBox.query(WorkoutEntity_.uid.equal(uid)).build().use { it.findFirst() }
            ?: error("Workout not found: $uid")
        startedAt?.let { row.startedAt = it }
        durationSeconds?.let { row.durationSeconds = kotlin.math.max(0, it) }
        rating?.let { row.rating = it }
        if (notes != null) row.notes = notes
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
        ObjectBox.store.runInTx {
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
