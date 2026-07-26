package com.ironlog.app.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExportRepositoryBackupSchemaTest {

    @Test
    fun `full backup schema includes Iron Ledger and athlete state sections`() {
        assertTrue(FULL_BACKUP_DATA_SECTIONS.contains("athlete_calibrations"))
        assertTrue(FULL_BACKUP_DATA_SECTIONS.contains("gamification_profiles"))
        assertTrue(FULL_BACKUP_DATA_SECTIONS.contains("iron_ledger_events"))
    }
}
