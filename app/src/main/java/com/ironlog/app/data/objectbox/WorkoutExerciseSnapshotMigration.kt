package com.ironlog.app.data.objectbox

import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import com.ironlog.app.data.history.captureHistoricalExerciseSnapshot
import io.objectbox.BoxStore

/** Freezes metadata for completed rows created before historical exercise snapshots existed. */
object WorkoutExerciseSnapshotMigration {
    internal const val MIGRATION_KEY = "workout_exercise_snapshot_v1"

    fun run(store: BoxStore) {
        store.runInTx {
            val settingsBox = store.boxFor(AppSettingEntity::class.java)
            val complete = settingsBox.query(AppSettingEntity_.key.equal(MIGRATION_KEY))
                .build().use { it.findFirst() }
                ?.value
                ?.toBooleanStrictOrNull() == true
            if (complete) return@runInTx

            val completedWorkoutIds = store.boxFor(WorkoutEntity::class.java)
                .query(WorkoutEntity_.status.equal("completed"))
                .build().use { it.find() }
                .mapTo(mutableSetOf(), WorkoutEntity::uid)
            val linksBox = store.boxFor(WorkoutExerciseEntity::class.java)
            val legacyLinks = linksBox.all.filter {
                it.workoutUid in completedWorkoutIds && it.exerciseSnapshotJson.isNullOrBlank()
            }
            if (legacyLinks.isNotEmpty()) {
                val exercises = store.boxFor(ExerciseEntity::class.java).all.associateBy(ExerciseEntity::uid)
                val muscles = store.boxFor(ExerciseMuscleEntity::class.java).all.groupBy(ExerciseMuscleEntity::exerciseUid)
                val now = System.currentTimeMillis()
                legacyLinks.forEach { link ->
                    val exercise = exercises[link.exerciseUid] ?: return@forEach
                    link.exerciseSnapshotJson = HistoricalExerciseSnapshotCodec.encode(
                        captureHistoricalExerciseSnapshot(exercise, muscles[exercise.uid].orEmpty()),
                    )
                    link.updatedAt = maxOf(link.updatedAt, now)
                }
                linksBox.put(legacyLinks.filter { !it.exerciseSnapshotJson.isNullOrBlank() })
            }

            settingsBox.put(AppSettingEntity().apply {
                key = MIGRATION_KEY
                value = "true"
                valueType = "boolean"
                updatedAt = System.currentTimeMillis()
            })
        }
    }
}
