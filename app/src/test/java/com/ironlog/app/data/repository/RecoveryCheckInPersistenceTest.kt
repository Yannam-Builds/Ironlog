package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.domain.intelligence.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecoveryCheckInPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `one save persists checkin and complete pain replacement in injected database`() = runBlocking {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val repo = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            repo.setString("pain_flag_Pull", "true")
            repo.saveRecoveryCheckIn(ManualRecoveryInput(2, 4, 3), setOf("Push"), 123456789L)
            val rows = repo.observeStrings(setOf("manual_recovery_input") + RECOVERY_REGIONS.map { "pain_flag_$it" }).first()
            assertEquals(ManualRecoveryInput(2, 4, 3, recordedAt = 123456789L), RecoveryCheckInCodec.decode(rows["manual_recovery_input"], 123456789L))
            assertEquals("true", rows["pain_flag_Push"])
            assertEquals("false", rows["pain_flag_Pull"])
            assertEquals(7, store.boxFor(AppSettingEntity::class.java).count().toInt())
        }
    }
    @Test fun `invalid checkin cannot partially replace previous pain state`() = runBlocking {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val repo = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
            repo.setString("pain_flag_Pull", "true")
            assertTrue(runCatching { repo.saveRecoveryCheckIn(ManualRecoveryInput(8, 3, 3), setOf("Push"), 123456789L) }.isFailure)
            assertEquals("true", repo.getString("pain_flag_Pull"))
            assertNull(repo.getString("manual_recovery_input"))
            assertNull(repo.getString("pain_flag_Push"))
        }
    }
}
