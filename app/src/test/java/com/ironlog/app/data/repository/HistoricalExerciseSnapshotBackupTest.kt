package com.ironlog.app.data.repository

import com.ironlog.app.data.history.HistoricalExerciseSnapshot
import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoricalExerciseSnapshotBackupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `full backup preserves snapshots and legacy backup restores create them`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("snapshot-backup")).build()
        try {
            val exercise = ExerciseEntity().apply { uid = "exercise"; name = "Current"; trackingType = "weight_reps" }
            val workout = WorkoutEntity().apply { uid = "workout"; status = "completed" }
            store.boxFor(ExerciseEntity::class.java).put(exercise)
            store.boxFor(WorkoutEntity::class.java).put(workout)
            val row = WorkoutExerciseEntity().apply {
                uid = "row"; workoutUid = workout.uid; exerciseUid = exercise.uid
                exerciseSnapshotJson = HistoricalExerciseSnapshotCodec.encode(
                    HistoricalExerciseSnapshot(name = "Original", trackingType = "duration_weight"),
                )
            }
            store.boxFor(WorkoutExerciseEntity::class.java).put(row)
            val repository = ImportExportRepository(store, temporary.newFolder("backup-recovery"))

            val exported = repository.exportDatabase()
            val exportedSnapshot = exported.getJSONObject("data").getJSONArray("workout_exercises")
                .getJSONObject(0).getString("exercise_snapshot_json")
            assertEquals("Original", HistoricalExerciseSnapshotCodec.decode(exportedSnapshot)?.name)

            val legacyPayload = org.json.JSONObject(exported.toString())
            legacyPayload.getJSONObject("data").getJSONArray("workout_exercises")
                .getJSONObject(0).remove("exercise_snapshot_json")
            val legacy = legacyPayload.toString()
            val secondStore = MyObjectBox.builder().directory(temporary.newFolder("legacy-restore")).build()
            try {
                val restoredRepository = ImportExportRepository(secondStore, temporary.newFolder("legacy-recovery"))
                restoredRepository.runConfirmedImport(legacy)
                val restored = secondStore.boxFor(WorkoutExerciseEntity::class.java).all.single()
                assertNotNull(restored.exerciseSnapshotJson)
                assertEquals("Current", HistoricalExerciseSnapshotCodec.decode(restored.exerciseSnapshotJson)?.name)
            } finally {
                secondStore.close()
            }
        } finally {
            store.close()
        }
    }
}
