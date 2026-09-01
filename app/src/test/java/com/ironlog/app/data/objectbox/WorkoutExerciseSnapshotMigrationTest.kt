package com.ironlog.app.data.objectbox

import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkoutExerciseSnapshotMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val store by lazy { MyObjectBox.builder().directory(temporary.newFolder("snapshot-migration")).build() }
    @After fun close() { if (!store.isClosed) store.close() }

    @Test fun `migration freezes only completed legacy rows once and leaves active rows for completion`() {
        val exercise = ExerciseEntity().apply {
            uid = "exercise"; name = "Original name"; trackingType = "weight_reps"; primaryMuscle = "Chest"
        }
        store.boxFor(ExerciseEntity::class.java).put(exercise)
        val completed = WorkoutEntity().apply { uid = "completed"; status = "completed" }
        val active = WorkoutEntity().apply { uid = "active"; status = "active" }
        store.boxFor(WorkoutEntity::class.java).put(completed, active)
        val historical = WorkoutExerciseEntity().apply {
            uid = "historical"; workoutUid = completed.uid; exerciseUid = exercise.uid
        }
        val live = WorkoutExerciseEntity().apply {
            uid = "live"; workoutUid = active.uid; exerciseUid = exercise.uid
        }
        store.boxFor(WorkoutExerciseEntity::class.java).put(historical, live)

        WorkoutExerciseSnapshotMigration.run(store)
        val frozen = HistoricalExerciseSnapshotCodec.decode(
            store.boxFor(WorkoutExerciseEntity::class.java).all.first { it.uid == historical.uid }.exerciseSnapshotJson,
        )!!
        assertEquals("Original name", frozen.name)
        assertNull(store.boxFor(WorkoutExerciseEntity::class.java).all.first { it.uid == live.uid }.exerciseSnapshotJson)

        exercise.name = "Edited after migration"
        store.boxFor(ExerciseEntity::class.java).put(exercise)
        WorkoutExerciseSnapshotMigration.run(store)
        val stillFrozen = HistoricalExerciseSnapshotCodec.decode(
            store.boxFor(WorkoutExerciseEntity::class.java).all.first { it.uid == historical.uid }.exerciseSnapshotJson,
        )!!
        assertEquals("Original name", stillFrozen.name)
        assertTrue(store.boxFor(AppSettingEntity::class.java).all.any {
            it.key == WorkoutExerciseSnapshotMigration.MIGRATION_KEY && it.value == "true"
        })
    }
}
