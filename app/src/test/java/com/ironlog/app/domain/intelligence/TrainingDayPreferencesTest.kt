package com.ironlog.app.domain.intelligence

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingDayPreferencesTest {
    @Test
    fun `settings json preserves exact selected weekdays in canonical order`() {
        val json = JSONObject()
        TrainingDayPreferences.writeToSettings(json, setOf(5, 1, 3))

        assertEquals("[1,3,5]", json.getJSONArray(TrainingDayPreferences.SETTINGS_KEY).toString())
        assertEquals(setOf(1, 3, 5), TrainingDayPreferences.readFromSettings(json, fallbackCount = 3))
    }

    @Test
    fun `missing weekday setting derives an evenly distributed schedule from weekly goal`() {
        assertEquals(setOf(0, 2, 4, 6), TrainingDayPreferences.readFromSettings(JSONObject(), fallbackCount = 4))
    }

    @Test
    fun `home schedule status identifies today and the next protected day`() {
        val monday = LocalDate.of(2026, 8, 31)
        val offDay = TrainingDayPreferences.status(setOf(0, 2, 4), monday.plusDays(1))
        val trainingDay = TrainingDayPreferences.status(setOf(0, 2, 4), monday.plusDays(2))

        assertFalse(offDay.isTrainingDay)
        assertEquals(monday.plusDays(2), offDay.nextTrainingDate)
        assertTrue(trainingDay.isTrainingDay)
        assertEquals(monday.plusDays(2), trainingDay.nextTrainingDate)
    }
}
