package com.ironlog.app.util

import com.ironlog.app.ui.screens.settings.DEFAULT_PLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalcAccuracyTest {

    @Test
    fun `converted pound plates retain precision for repeated pairs`() {
        val fivePounds = 5 * 0.45359237
        val target = 20 + fivePounds * 8
        val result = calculatePlates(target, 20.0, listOf(
            com.ironlog.app.ui.screens.settings.PlateDto(fivePounds, 4)))
        assertTrue(result.isValid)
        assertEquals(4, result.platesPerSide.single().quantity)
        assertEquals(fivePounds, result.platesPerSide.single().weightKg, 0.00000001)
        assertEquals(target, result.achievedWeightKg, 0.00000001)
    }

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
    @Test
    fun `finds exact bounded combination instead of greedy dead end`() {
        val result = calculatePlates(44.0, 20.0, listOf(
            com.ironlog.app.ui.screens.settings.PlateDto(10.0, 1),
            com.ironlog.app.ui.screens.settings.PlateDto(6.0, 2, "#1565C0")))
        assertTrue(result.isValid)
        assertEquals(44.0, result.achievedWeightKg, 0.001)
        assertEquals(2, result.platesPerSide.single().quantity)
        assertEquals("#1565C0", result.platesPerSide.single().color)
    }

    @Test
    fun `empty inventory cannot load plates and duplicate sizes share stock`() {
        assertEquals(20.0, calculatePlates(60.0, 20.0, emptyList()).achievedWeightKg, 0.001)
        val result = calculatePlates(60.0, 20.0, listOf(
            com.ironlog.app.ui.screens.settings.PlateDto(10.0, 1),
            com.ironlog.app.ui.screens.settings.PlateDto(10.0, 1)))
        assertTrue(result.isValid)
        assertEquals(2, result.platesPerSide.single().quantity)
    }

    @Test
    fun `fractional target never overshoots and malformed input is rejected`() {
        val plate = com.ironlog.app.ui.screens.settings.PlateDto(0.01, 1)
        assertEquals(20.0, calculatePlates(20.01, 20.0, listOf(plate)).achievedWeightKg, 0.001)
        org.junit.Assert.assertFalse(calculatePlates(Double.NaN, 20.0, DEFAULT_PLATES).isValid)
        org.junit.Assert.assertFalse(calculatePlates(60.0, 20.0, listOf(plate.copy(weightKg = Double.POSITIVE_INFINITY))).isValid)
    }
}
