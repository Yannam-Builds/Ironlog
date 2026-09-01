package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
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
import java.time.Instant
import java.time.ZoneOffset

class PersonalBestResetTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val store by lazy { MyObjectBox.builder().directory(temporaryFolder.newFolder("pr-reset-db")).build() }
    private val settings by lazy { SettingsRepository(store.boxFor(AppSettingEntity::class.java)) }

    @After fun close() {
        if (!store.isClosed) {
            store.closeThreadResources()
            store.close()
        }
    }

    @Test fun `reset persists cutoff and clears manual and legacy PR maps atomically`() = runBlocking {
        settings.setString("ironlog_pb", "{\"Bench Press\":120.0}", "json")
        settings.setString("pr_index_v1", "{\"bench\":[{\"e1rm\":120.0}]}", "json")
        val cutoff = Instant.parse("2026-09-01T12:00:00Z")

        settings.resetPersonalBests(cutoff.toEpochMilli())

        assertEquals(cutoff, settings.getPersonalBestResetAt())
        assertEquals("{}", settings.getString("ironlog_pb"))
        assertEquals("{}", settings.getString("pr_index_v1"))
    }

    @Test fun `missing or malformed reset cutoff is treated as no reset`() = runBlocking {
        assertNull(settings.getPersonalBestResetAt())
        settings.setString(PR_RESET_AT_KEY, "not-a-timestamp", "number")
        assertNull(settings.getPersonalBestResetAt())
    }

    @Test fun `direct PR repository APIs honor reset and accept later completed sets`() = runBlocking {
        val now = Instant.parse("2026-09-01T12:00:00Z")
        val cutoff = Instant.parse("2026-09-01T10:00:00Z")
        seedCompletedSet("old", now.minusSeconds(10_800), 120.0)
        val repository = StatsRepository(
            clock = { now },
            zoneId = ZoneOffset.UTC,
            boxStore = store,
            settingsRepository = settings,
        )
        assertEquals(1, repository.getPRsSnapshot().size)
        assertEquals(160.0, repository.getExerciseEstimatedOneRepMaxSnapshot("bench"), 0.0)

        settings.resetPersonalBests(cutoff.toEpochMilli())
        assertTrue(repository.getPRsSnapshot().isEmpty())
        assertEquals(0.0, repository.getExerciseEstimatedOneRepMaxSnapshot("bench"), 0.0)

        seedCompletedSet("new", now.minusSeconds(1_800), 90.0)
        assertEquals(1, repository.getPRsSnapshot().size)
        assertEquals(120.0, repository.getExerciseEstimatedOneRepMaxSnapshot("bench"), 0.0)
    }

    private fun seedCompletedSet(id: String, occurredAt: Instant, weight: Double) {
        val exerciseBox = store.boxFor(ExerciseEntity::class.java)
        val exercise = exerciseBox.all.firstOrNull { it.uid == "bench" } ?: ExerciseEntity().apply {
            uid = "bench"
            name = "Bench Press"
            trackingType = "weight_reps"
        }.also(exerciseBox::put)
        val workout = WorkoutEntity().apply {
            uid = "workout-$id"
            name = "Push"
            status = "completed"
            startedAt = occurredAt.toEpochMilli()
            completedAt = occurredAt.plusSeconds(600).toEpochMilli()
            durationSeconds = 600
        }.also { store.boxFor(WorkoutEntity::class.java).put(it) }
        val link = WorkoutExerciseEntity().apply {
            uid = "link-$id"
            workoutUid = workout.uid
            this.workout.target = workout
            exerciseUid = exercise.uid
            this.exercise.target = exercise
        }.also { store.boxFor(WorkoutExerciseEntity::class.java).put(it) }
        WorkoutSetEntity().apply {
            uid = "set-$id"
            workoutExerciseUid = link.uid
            workoutExercise.target = link
            this.weight = weight
            reps = 10.0
            setIndex = 1
            completedAt = occurredAt.plusSeconds(300).toEpochMilli()
        }.also { store.boxFor(WorkoutSetEntity::class.java).put(it) }
    }
}
