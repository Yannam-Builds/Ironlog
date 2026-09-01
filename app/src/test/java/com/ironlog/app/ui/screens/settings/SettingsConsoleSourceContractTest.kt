package com.ironlog.app.ui.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConsoleSourceContractTest {
    private val source = File(
        "src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt",
    ).readText()

    @Test
    fun `settings uses the console and focused detail surface contract`() {
        assertTrue(source.contains("SettingsConsoleLanding("))
        assertTrue(source.contains("SettingsDetailHeader("))
        assertTrue(source.contains("SettingsDangerZone("))
    }

    @Test
    fun `inert performance mode is not presented as a working control`() {
        assertFalse(source.contains("Performance mode"))
        assertFalse(source.contains("PERFORMANCE_MODES"))
        assertFalse(source.contains("copy(performanceMode"))
    }
}
