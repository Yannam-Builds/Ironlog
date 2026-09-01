package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyBonusMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `existing legacy event without marker is preserved and marker is restored`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val events = store.boxFor(IronLedgerEventEntity::class.java)
            events.put(IronLedgerEventEntity(eventId = "legacy:bonus-v1", sourceType = "bonus", xpDelta = 25, invalidated = true))
            migrateLegacyBonusXpBlocking(store, profileXp = 500L, ledgerXp = 100L, nowEpochMs = 123L)
            assertEquals(1L, events.count()); assertTrue(events.all.single().invalidated)
            assertEquals(25, events.all.single().xpDelta)
            assertEquals("true", SettingsRepository(store.boxFor(AppSettingEntity::class.java)).getStringBlocking("gamification_bonus_events_v2_migrated"))
        }
    }
    @Test fun `workout derived XP is not converted into a bonus and transaction failure rolls back marker`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            assertTrue(runCatching {
                store.runInTx {
                    migrateLegacyBonusXpBlocking(store, 100, 100, 123)
                    error("injected write failure")
                }
            }.isFailure)
            assertEquals(0L, store.boxFor(AppSettingEntity::class.java).count())
            assertEquals(0L, store.boxFor(IronLedgerEventEntity::class.java).count())
            migrateLegacyBonusXpBlocking(store, 100, 100, 123)
            assertEquals(0L, store.boxFor(IronLedgerEventEntity::class.java).count())
        }
    }

    @Test fun `first refresh normalizes legacy profile before bonus marker can be committed`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val profiles = store.boxFor(GamificationProfileEntity::class.java)
            profiles.put(GamificationProfileEntity(offlineUserId = "legacy-user", totalXp = 500L))

            recomputeGamificationAtomically(store) {
                val canonical = profiles.query(GamificationProfileEntity_.offlineUserId.equal("local"))
                    .build().use { it.findFirst() }
                assertNotNull("normalization must precede refresh derivation", canonical)
                migrateLegacyBonusXpBlocking(store, canonical!!.totalXp, ledgerXp = 100L, nowEpochMs = 123L)
            }

            assertEquals(listOf("local"), profiles.all.map { it.offlineUserId })
            assertEquals(500L, profiles.all.single().totalXp)
            val bonus = store.boxFor(IronLedgerEventEntity::class.java).all.single()
            assertEquals(400, bonus.xpDelta)
            val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            assertEquals("true", settings.getStringBlocking("workout_import_provenance_v1"))
            assertEquals("true", settings.getStringBlocking("gamification_bonus_events_v2_migrated"))
        }
    }
}
