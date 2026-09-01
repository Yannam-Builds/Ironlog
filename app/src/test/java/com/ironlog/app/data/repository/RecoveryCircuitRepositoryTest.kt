package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecoveryCircuitRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private fun seed(store: io.objectbox.BoxStore, sessions: Int, goal: Int = 3) {
        store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply { key = "ironlog_settings"; value = "{\"weeklyGoalDays\":$goal}"; valueType = "json" })
        val ex = ExerciseEntity().apply { uid = "bench"; name = "Bench Press"; trackingType = "weight_reps" }
        store.boxFor(ExerciseEntity::class.java).put(ex)
        repeat(sessions) { index ->
            val w = WorkoutEntity().apply { uid = "w$index"; status = "completed"; startedAt = clock.millis() - 3_600_000L; durationSeconds = 1800 }
            store.boxFor(WorkoutEntity::class.java).put(w)
            val we = WorkoutExerciseEntity().apply { uid = "we$index"; workoutUid = w.uid; exerciseUid = ex.uid }
            store.boxFor(WorkoutExerciseEntity::class.java).put(we)
            store.boxFor(WorkoutSetEntity::class.java).put(List(8) { WorkoutSetEntity().apply { workoutExerciseUid = we.uid; weight = 60.0; reps = 8.0 } })
        }
    }
    @Test fun `one short of goal records once and does not double award`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            seed(store, 2)
            val repo = RecoveryCircuitRepository(store, clock)
            assertEquals(RecoveryCircuitResult.RECORDED, repo.complete("2026-W36", "core_basic"))
            assertEquals(RecoveryCircuitResult.ALREADY_RECORDED, repo.complete("2026-W36", "core_basic"))
            assertEquals(1L, store.boxFor(IronLedgerEventEntity::class.java).count())
            assertEquals(25, store.boxFor(IronLedgerEventEntity::class.java).all.single().xpDelta)
        }
    }
    @Test fun `ineligible completion never creates profile or completion marker`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            seed(store, 1)
            assertEquals(RecoveryCircuitResult.INELIGIBLE, RecoveryCircuitRepository(store, clock).complete("2026-W36", "core_basic"))
            assertEquals(0L, store.boxFor(GamificationProfileEntity::class.java).count())
            assertEquals(0L, store.boxFor(IronLedgerEventEntity::class.java).count())
        }
    }
    @Test fun `stale week and changed goal are checked at commit time`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            seed(store, 2)
            val repo = RecoveryCircuitRepository(store, clock)
            assertEquals(RecoveryCircuitResult.INELIGIBLE, repo.complete("2026-W35", "core_basic"))
            val box = store.boxFor(AppSettingEntity::class.java)
            box.put(box.all.single().apply { value = "{\"weeklyGoalDays\":4}" })
            assertEquals(RecoveryCircuitResult.INELIGIBLE, repo.complete("2026-W36", "core_basic"))
            assertEquals(0L, store.boxFor(IronLedgerEventEntity::class.java).count())
        }
    }
    @Test fun `stale derived profile cannot erase another committed circuit or badge`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            seed(store, 2)
            val box = store.boxFor(GamificationProfileEntity::class.java)
            box.put(GamificationProfileEntity(offlineUserId = "local"))
            val stale = box.all.single()
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                executor.submit { try {
                    RecoveryCircuitRepository(store, clock).complete("2026-W36", "core_basic")
                    box.put(box.all.single().apply { unlockedBadges = "streak_3"; badgeUnlocksJson = "{\"streak_3\":123}" })
                } finally { store.closeThreadResources() } }.get()
                val result = commitGamificationProfile(store, stale, 100, HistoryRepository(store).completedSnapshotBlocking(), 3, clock)
                assertEquals(125L, result.totalXp)
                assertEquals(1, org.json.JSONObject(result.makeupCompletionsJson).getInt("2026-W36"))
                assertEquals(123L, org.json.JSONObject(result.badgeUnlocksJson!!).getLong("streak_3"))
                assertTrue("streak_3" in result.unlockedBadges)
                assertEquals(com.ironlog.app.domain.badges.BadgeDefinitions.all.first { it.id == "streak_3" }.title, result.activeTitle)
            } finally { executor.shutdownNow() }
        }
    }
}
