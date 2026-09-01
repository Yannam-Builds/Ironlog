package com.ironlog.app.util

import com.ironlog.app.ui.screens.settings.DEFAULT_PLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalcAccuracyTest {

    @Test
    fun `65 kg uses visible 20 and 2 point 5 plates per side`() {
        val result = calculatePlates(65.0, 20.0, DEFAULT_PLATES)

        assertTrue(result.isValid)
        assertEquals(listOf(20.0, 2.5), result.platesPerSide.map { it.weightKg })
        assertEquals(65.0, result.achievedWeightKg, 0.001)
        assertEquals(0.0, result.remainderKg, 0.001)
    }

    @Test
    fun `impossible load reports achieved weight and remainder`() {
        val result = calculatePlates(66.0, 20.0, DEFAULT_PLATES)

        assertEquals(65.0, result.achievedWeightKg, 0.001)
        assertEquals(1.0, result.remainderKg, 0.001)
    }
}
