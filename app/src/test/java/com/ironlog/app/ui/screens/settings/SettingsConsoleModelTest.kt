package com.ironlog.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConsoleModelTest {

    @Test
    fun `console exposes the approved destination order`() {
        assertEquals(
            listOf(
                SettingsDestination.TRAINING,
                SettingsDestination.INTELLIGENCE,
                SettingsDestination.APPEARANCE,
                SettingsDestination.NOTIFICATIONS,
                SettingsDestination.DATA_PRIVACY,
                SettingsDestination.ABOUT,
            ),
            settingsConsoleDestinations.map { it.destination },
        )
    }

    @Test
    fun `search matches titles descriptions and useful keywords without case sensitivity`() {
        assertEquals(
            listOf(SettingsDestination.INTELLIGENCE),
            filterSettingsConsoleDestinations("CLOUD API").map { it.destination },
        )
        assertEquals(
            listOf(SettingsDestination.TRAINING),
            filterSettingsConsoleDestinations("barbell").map { it.destination },
        )
        assertEquals(
            listOf(SettingsDestination.DATA_PRIVACY),
            filterSettingsConsoleDestinations("delete history").map { it.destination },
        )
    }

    @Test
    fun `blank search returns every destination and an unknown search returns none`() {
        assertEquals(settingsConsoleDestinations, filterSettingsConsoleDestinations("   "))
        assertTrue(filterSettingsConsoleDestinations("unrelated phrase").isEmpty())
    }

    @Test
    fun `destructive actions are explicitly isolated from ordinary data tools`() {
        assertEquals(
            listOf(SettingsDangerAction.CLEAR_HISTORY, SettingsDangerAction.RESET_PERSONAL_RECORDS),
            settingsDangerActions,
        )
    }

    @Test
    fun `console status copy is honest about engines and notification delivery`() {
        assertEquals("Built-in intelligence", intelligenceModeLabel("builtin"))
        assertEquals("APEX on-device", intelligenceModeLabel("gemini_nano"))
        assertEquals("Cloud AI", intelligenceModeLabel("cloud_ai"))

        assertEquals("Blocked by Android", notificationConsoleStatus(false, true, true))
        assertEquals("Smart notifications off", notificationConsoleStatus(true, false, true))
        assertEquals("On · workout reminders enabled", notificationConsoleStatus(true, true, true))
        assertEquals("On · workout reminders off", notificationConsoleStatus(true, true, false))
    }
}
