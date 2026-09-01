package com.ironlog.app.ui.viewmodel

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.StatsRepository
import com.ironlog.app.ui.model.StatsUiState
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class StatsInvalidationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val now = Instant.parse("2026-09-01T12:00:00Z")

    @Test fun `unrelated settings and active sets do not run stats projection`() = runBlocking {
        val fixture = Fixture("irrelevant")
        val events = Channel<StatsUiState>(Channel.UNLIMITED)
        val collector = launch { fixture.source.observe().collect { events.send(it) } }
        try {
            assertEquals(1, withTimeout(5_000) { events.receive() }.history.size)
            val initial = fixture.projections.get()
            fixture.settings.setString("backup_dirty_at", "123", "number")
            assertNull(withTimeoutOrNull(500) { events.receive() })
            fixture.settings.setString("ironlog_settings", "{\"weightUnit\":\"kg\",\"theme\":\"changed\"}", "json")
            assertNull(withTimeoutOrNull(500) { events.receive() })
            fixture.activeSet.weight = 77.0
            fixture.sets.put(fixture.activeSet)
            assertNull(withTimeoutOrNull(500) { events.receive() })
            assertEquals("no hidden projection followed by output distinct", initial, fixture.projections.get())

            fixture.settings.setString("ironlog_settings", "{\"weightUnit\":\"lb\"}", "json")
            assertEquals("lb", withTimeout(5_000) { events.receive() }.weightUnit)
            assertEquals(initial + 1, fixture.projections.get())
        } finally {
            collector.cancelAndJoin()
            events.close()
            fixture.close()
        }
    }

    @Test fun `completion edits deletions and referenced metadata still project fresh history`() = runBlocking {
        val fixture = Fixture("relevant")
        val events = Channel<StatsUiState>(Channel.UNLIMITED)
        val collector = launch { fixture.source.observe().collect { events.send(it) } }
        suspend fun next() = withTimeout(5_000) { events.receive() }
        try {
            assertEquals(1, next().history.size)
            fixture.active.status = "completed"
            fixture.workouts.put(fixture.active)
            assertEquals(2, next().history.size)

            fixture.completedSet.rir = 1.5
            fixture.completedSet.notes = "Edited without changing timestamp"
            fixture.sets.put(fixture.completedSet)
            val edited = next().history.flatMap { it.exercises }.flatMap { it.sets }.first { it.id == fixture.completedSet.uid }
            assertEquals(1.5, edited.rir)
            assertEquals("Edited without changing timestamp", edited.note)

            fixture.exercise.trackingType = "duration"
            fixture.exercises.put(fixture.exercise)
            assertTrue(next().history.flatMap { it.exercises }.all { it.trackingType == "duration" })

            fixture.store.boxFor(ExerciseMuscleEntity::class.java).put(ExerciseMuscleEntity().apply {
                exerciseUid = fixture.exercise.uid
                muscle = "chest"
                role = "primary"
                contributionFraction = 0.75
            })
            assertTrue(next().history.flatMap { it.exercises }.all { it.muscleContributions["chest"] == 0.75 })

            fixture.sets.remove(fixture.completedSet)
            assertEquals(1, next().history.sumOf { it.sets })
            fixture.workouts.remove(fixture.completed)
            assertEquals(listOf(fixture.active.uid), next().history.map { it.id })
            assertEquals(7, fixture.projections.get())
        } finally {
            collector.cancelAndJoin()
            events.close()
            fixture.close()
        }
    }

    @Test fun `PR reset setting invalidates only PR projection and retains completed history`() = runBlocking {
        val fixture = Fixture("pr-reset-invalidation")
        val events = Channel<StatsUiState>(Channel.UNLIMITED)
        val collector = launch { fixture.source.observe().collect { events.send(it) } }
        try {
            val initial = withTimeout(5_000) { events.receive() }
            assertEquals(1, initial.personalBests.size)
            assertEquals(1, initial.history.size)

            fixture.settings.resetPersonalBests(now.toEpochMilli())
            val reset = withTimeout(5_000) { events.receive() }

            assertTrue(reset.personalBests.isEmpty())
            assertTrue(reset.pb.isEmpty())
            assertEquals(initial.history, reset.history)
            assertEquals(now.toEpochMilli(), reset.prResetAtEpochMs)
        } finally {
            collector.cancelAndJoin()
            events.close()
            fixture.close()
        }
    }

    private inner class Fixture(name: String) {
        val store = MyObjectBox.builder().directory(temporaryFolder.newFolder(name)).build()
        val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
        val workouts = store.boxFor(WorkoutEntity::class.java)
        val exercises = store.boxFor(ExerciseEntity::class.java)
        val sets = store.boxFor(WorkoutSetEntity::class.java)
        val exercise = ExerciseEntity().apply { uid = "bench"; this.name = "Bench Press"; trackingType = "weight_reps" }.also { exercises.put(it) }
        val completed = workout("finished", "completed")
        val active = workout("active", "active")
        val completedSet = set(completed)
        val activeSet = set(active)
        val projections = AtomicInteger()
        val source = RepositoryStatsUiSource(
            settingsRepo = settings,
            statsRepository = StatsRepository(boxStore = store),
            clock = { projections.incrementAndGet(); now },
            zoneId = ZoneOffset.UTC,
        )

        private fun workout(id: String, status: String) = WorkoutEntity().apply {
            uid = id
            this.status = status
            startedAt = now.minusSeconds(3_600).toEpochMilli()
            durationSeconds = 600
        }.also { workouts.put(it) }

        private fun set(parent: WorkoutEntity): WorkoutSetEntity {
            val link = WorkoutExerciseEntity().apply {
                uid = "exercise-${parent.uid}"
                workoutUid = parent.uid
                workout.target = parent
                exerciseUid = this@Fixture.exercise.uid
                exercise.target = this@Fixture.exercise
            }.also { store.boxFor(WorkoutExerciseEntity::class.java).put(it) }
            return WorkoutSetEntity().apply {
                uid = "set-${parent.uid}"
                workoutExerciseUid = link.uid
                workoutExercise.target = link
                weight = 40.0
                reps = 8.0
                setIndex = 1
            }.also { sets.put(it) }
        }

        fun close() { store.closeThreadResources(); store.close() }
    }
}
