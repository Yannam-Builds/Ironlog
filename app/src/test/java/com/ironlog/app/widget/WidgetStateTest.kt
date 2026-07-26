package com.ironlog.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {

    @Test fun `default WidgetState has safe defaults`() {
        val state = WidgetState()
        assertEquals("Uncalibrated", state.grade)
        assertEquals(1, state.level)
        assertEquals(0, state.dailyStreakDays)
        assertEquals(0, state.streakWeeks)
        assertEquals("No Plan", state.recommendedDayName)
        assertEquals("Choose a program", state.primaryActionLabel)
        assertEquals("ProgramPicker", state.primaryActionRoute)
        assertTrue(state.xpPercent in 0f..1f)
    }

    @Test fun `xpPercent is clamped to 0-1`() {
        val state = WidgetState(xpInLevel = 200L, xpForNextLevel = 100L)
        assertEquals(1.0f, state.xpPercent, 0.001f)
    }

    @Test fun `xpPercent is 0 when xpForNextLevel is 0`() {
        val state = WidgetState(xpInLevel = 0L, xpForNextLevel = 0L)
        assertEquals(0.0f, state.xpPercent, 0.001f)
    }

    @Test fun `xpPercent computes correctly for partial level`() {
        val state = WidgetState(xpInLevel = 50L, xpForNextLevel = 200L)
        assertEquals(0.25f, state.xpPercent, 0.001f)
    }
}
