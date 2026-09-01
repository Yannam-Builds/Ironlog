package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GamificationRefreshTransactionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `refresh derives from canonical history and rolls back all derived writes on failure`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val workouts = store.boxFor(WorkoutEntity::class.java)
            workouts.put(WorkoutEntity().apply { uid = "canonical"; status = "completed"; startedAt = 1_000L })
            val profile = store.boxFor(GamificationProfileEntity::class.java)
            val events = store.boxFor(IronLedgerEventEntity::class.java)
            val settings = store.boxFor(AppSettingEntity::class.java)
            assertTrue(runCatching {
                recomputeGamificationAtomically(store) { history ->
                    assertEquals(listOf("canonical"), history.map { it.id })
                    profile.put(GamificationProfileEntity(offlineUserId = "local", totalXp = 999))
                    events.put(IronLedgerEventEntity(eventId = "new-event", xpDelta = 999))
                    settings.put(AppSettingEntity().apply { key = "gamification_bonus_events_v2_migrated"; value = "true" })
                    error("injected mid-event failure")
                }
            }.isFailure)
            assertEquals(0L, profile.count()); assertEquals(0L, events.count()); assertEquals(0L, settings.count())
            workouts.removeAll()
            assertTrue(recomputeGamificationAtomically(store) { it.isEmpty() })
        }
    }

    @Test fun `concurrent history deletion cannot interleave with profile and event derivation`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val workouts = store.boxFor(WorkoutEntity::class.java)
            workouts.put(WorkoutEntity().apply { uid = "w"; status = "completed"; startedAt = 1_000L })
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val refresh = executor.submit<Int> { try {
                    recomputeGamificationAtomically(store) { rows -> entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); rows.size }
                } finally { store.closeThreadResources() } }
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                val deletion = executor.submit { try { workouts.removeAll() } finally { store.closeThreadResources() } }
                assertFalse(deletion.isDone)
                release.countDown()
                assertEquals(1, refresh.get().toInt()); deletion.get()
                assertEquals(0, recomputeGamificationAtomically(store) { it.size })
            } finally { release.countDown(); executor.shutdownNow() }
        }
    }
}
