package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import io.objectbox.BoxStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class LastExerciseSessionTest {
    @get:Rule val temporary = TemporaryFolder()

    private suspend fun withStore(block: suspend (BoxStore) -> Unit) {
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        try { block(store) } finally { store.closeThreadResources(); store.close() }
    }

    @Test fun `actual completed session date wins over import order future sessions and ten active links`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val now = Instant.parse("2026-09-01T12:00:00Z")
            fun seed(id: String, started: Instant, created: Long, status: String = "completed", warmup: Boolean = false) {
                store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply { uid = id; startedAt = started.toEpochMilli(); this.status = status })
                store.boxFor(WorkoutExerciseEntity::class.java).put(WorkoutExerciseEntity().apply {
                    uid = "link-$id"; workoutUid = id; exerciseUid = "bench"; createdAt = created; notes = "note-$id"
                })
                store.boxFor(WorkoutSetEntity::class.java).put(WorkoutSetEntity().apply {
                    uid = "set-$id"; workoutExerciseUid = "link-$id"; weight = 40.0; reps = 8.0; isWarmup = warmup
                })
            }
            seed("backfilled-old", now.minusSeconds(30 * 86_400), now.toEpochMilli())
            val sourceDate = now.minusSeconds(86_400)
            seed("latest-real", sourceDate, 1L)
            seed("future", now.plusSeconds(86_400), now.toEpochMilli() + 1)
            repeat(12) { seed("active-$it", now.minusSeconds(60), now.toEpochMilli() + it + 2, "active") }
            val repository = WorkoutRepository(SettingsRepository(store.boxFor(AppSettingEntity::class.java)), store)
            val session = checkNotNull(repository.getLastExerciseSession("bench", now))
            assertEquals("latest-real", session.workoutId)
            assertEquals("link-latest-real", session.workoutExerciseId)
            assertEquals(sourceDate.toString(), session.date)
            assertEquals("note-latest-real", session.notes)
            assertEquals(listOf("set-latest-real"), session.sets.map { it.uid })
        }
    }

    @Test fun `warmup only recent session falls back to older performed sets with null legacy timestamp`() = runBlocking(Dispatchers.IO) {
        withStore { store ->
            val now = Instant.parse("2026-08-30T12:00:00Z")
            for ((id, seconds, warmup) in listOf(Triple("older", 3_600L, false), Triple("warmup", 60L, true))) {
                store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply { uid = id; status = "completed"; startedAt = now.minusSeconds(seconds).toEpochMilli() })
                store.boxFor(WorkoutExerciseEntity::class.java).put(WorkoutExerciseEntity().apply { uid = "link-$id"; workoutUid = id; exerciseUid = "bench" })
                store.boxFor(WorkoutSetEntity::class.java).put(WorkoutSetEntity().apply { uid = "set-$id"; workoutExerciseUid = "link-$id"; weight = 40.0; reps = 8.0; isWarmup = warmup; completedAt = null })
            }
            val repository = WorkoutRepository(SettingsRepository(store.boxFor(AppSettingEntity::class.java)), store)
            assertEquals("older", repository.getLastExerciseSession("bench", now)?.workoutId)
            assertEquals(listOf("set-older"), repository.getLastSessionSetsForExercise("bench").map { it.uid })
            assertNull(repository.getLastExerciseSession("missing", now))
        }
    }
}
