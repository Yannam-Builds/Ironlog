package com.ironlog.app.domain.intelligence

import org.junit.Assert.*
import org.junit.Test

class CloudAiKeyPolicyTest {
    @Test fun `legacy key binds only to a recorded provider`() {
        assertNull(legacyKeyMigration("synthetic", "", null))
        assertNull(legacyKeyMigration("synthetic", "", ""))
        assertEquals(LegacyKeyMigration("openai", "synthetic"), legacyKeyMigration(" synthetic ", "", "openai"))
    }
    @Test fun `existing provider key wins over old shared key`() {
        assertEquals(LegacyKeyMigration("gemini", "current"), legacyKeyMigration("old", "current", "gemini"))
        assertNull(legacyKeyMigration("", "current", "gemini"))
    }
}
