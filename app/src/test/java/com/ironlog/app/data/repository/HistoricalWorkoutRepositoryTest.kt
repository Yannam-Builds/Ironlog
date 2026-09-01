package com.ironlog.app.data.repository

import com.ironlog.app.data.model.CompletedExerciseInput
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.HistoricalWorkoutSaveResult
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.objectbox.*
import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoricalWorkoutRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val store by lazy { MyObjectBox.builder().directory(temporaryFolder.newFolder("historical-db")).build() }
    private val repo by lazy { WorkoutRepository(SettingsRepository(store.boxFor(AppSettingEntity::class.java)), store) }
    private val zone = ZoneId.of("Asia/Kolkata")
    @After fun close() { if (!store.isClosed) store.close() }

    @Test fun completedEntryPersistsSetNotesStableIdsAndDropAliasInInjectedStore() = databaseTest {
        seedExercise()
        val saved = repo.saveHistoricalWorkout(input(), zone)
        assertTrue(saved is HistoricalWorkoutSaveResult.Saved)
        val rows = store.boxFor(WorkoutSetEntity::class.java).all
        assertEquals("synthetic-set", rows.single().uid)
        assertEquals("Synthetic set note", rows.single().notes)
        assertTrue(rows.single().isDropset)
        assertEquals(8.5, rows.single().rpe)
        assertEquals(2.0, rows.single().rir)
        assertEquals(1, store.boxFor(ExerciseEntity::class.java).all.size)
        assertEquals("Exercise note", store.boxFor(WorkoutExerciseEntity::class.java).all.single().notes)
        val snapshot = HistoricalExerciseSnapshotCodec.decode(
            store.boxFor(WorkoutExerciseEntity::class.java).all.single().exerciseSnapshotJson,
        )!!
        assertEquals("Old exercise name", snapshot.name)
    }

    @Test fun retryingSameDraftCreatesOneWorkoutAndChangedPayloadIsRejected() = databaseTest {
        seedExercise()
        repo.saveHistoricalWorkout(input(), zone)
        repo.saveHistoricalWorkout(input(), zone)
        assertEquals(1L, store.boxFor(WorkoutEntity::class.java).count())
        assertEquals(1L, store.boxFor(WorkoutSetEntity::class.java).count())
        assertTrue(runCatching { repo.saveHistoricalWorkout(input().copy(notes = "Changed"), zone) }.isFailure)
    }

    @Test fun sameLocalDateNeedsExplicitAddAnotherAndNeverOverwrites() = databaseTest {
        seedExercise()
        repo.saveHistoricalWorkout(input(), zone)
        val second = input().copy(uid = "second-draft", startedAt = Instant.parse("2026-08-31T01:00:00Z").toEpochMilli(),
            exerciseData = emptyList())
        assertTrue(repo.saveHistoricalWorkout(second, zone) is HistoricalWorkoutSaveResult.DateConflict)
        assertEquals(1L, store.boxFor(WorkoutEntity::class.java).count())
        assertTrue(repo.saveHistoricalWorkout(second, zone, allowSameDay = true) is HistoricalWorkoutSaveResult.Saved)
        assertEquals(2L, store.boxFor(WorkoutEntity::class.java).count())
    }

    @Test fun conflictingSetUidRollsBackTheEntireHistoricalWrite() = databaseTest {
        seedExercise()
        repo.saveHistoricalWorkout(input(), zone)
        val second = input().copy(uid = "another-draft", startedAt = input().startedAt - 86_400_000)
        assertTrue(runCatching { repo.saveHistoricalWorkout(second, zone) }.isFailure)
        assertEquals(1L, store.boxFor(WorkoutEntity::class.java).count())
        assertEquals(1L, store.boxFor(WorkoutExerciseEntity::class.java).count())
        assertEquals(1L, store.boxFor(WorkoutSetEntity::class.java).count())
    }

    @Test fun historicalSaveLeavesActiveWorkoutAndCompletionSettingsUntouched() = databaseTest {
        seedExercise()
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply { uid = "active"; status = "active" })
        store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply { key = "active_workout_id"; value = "active" })
        repo.saveHistoricalWorkout(input(), zone)
        assertEquals("active", store.boxFor(WorkoutEntity::class.java).all.first { it.uid == "active" }.status)
        assertEquals(listOf("active_workout_id"), store.boxFor(AppSettingEntity::class.java).all.map { it.key })
        assertFalse(store.boxFor(WorkoutEntity::class.java).all.first { it.uid == "synthetic-draft" }.imported)
    }

    @Test fun invalidInputFailsBeforeAnyRowsAreWritten() = databaseTest {
        seedExercise()
        assertTrue(runCatching { repo.saveHistoricalWorkout(input().copy(durationSeconds = -1), zone) }.isFailure)
        assertEquals(0L, store.boxFor(WorkoutEntity::class.java).count())
    }

    @Test fun historyEditCanExplicitlyClearNotesWithoutAmbiguousNullSemantics() = databaseTest {
        seedExercise()
        repo.saveHistoricalWorkout(input(), zone)
        val history = HistoryRepository(store)

        history.updateWorkout(uid = "synthetic-draft", durationSeconds = 900, rating = 4.0, updateRating = true)
        val preserved = store.boxFor(WorkoutEntity::class.java).all.single()
        assertEquals("Workout note", preserved.notes)
        assertEquals(4.0, preserved.rating)

        history.updateWorkout(uid = "synthetic-draft", notes = null, updateNotes = true, rating = null, updateRating = true)
        val cleared = store.boxFor(WorkoutEntity::class.java).all.single()
        assertNull(cleared.notes)
        assertNull(cleared.rating)
    }

    // Repository methods use blocking IO transactions. Own and close readers on that same IO thread.
    private fun databaseTest(block: suspend () -> Unit) = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        try { block() } finally { store.closeThreadResources() }
    }

    private fun seedExercise() {
        store.boxFor(ExerciseEntity::class.java).put(ExerciseEntity().apply {
            uid = "library-exercise"; name = "Current exercise name"; normalizedName = "current exercise name"
        })
    }

    private fun input() = CreateCompletedWorkoutInput(
        uid = "synthetic-draft", name = "Past workout", startedAt = Instant.parse("2026-08-30T20:00:00Z").toEpochMilli(),
        durationSeconds = 1200, notes = "Workout note",
        exerciseData = listOf(CompletedExerciseInput(exerciseId = "library-exercise", name = "Old exercise name",
            notes = "Exercise note", sets = listOf(SetInput(uid = "synthetic-set", weight = 40.0, reps = 8.0,
                type = "drop", rpe = 8.5, rir = 2.0, notes = "Synthetic set note")))),
    )
}
