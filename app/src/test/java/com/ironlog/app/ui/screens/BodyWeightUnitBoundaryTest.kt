package com.ironlog.app.ui.screens

import com.ironlog.app.ui.screens.body.bodyWeightInputTextFromKg
import com.ironlog.app.ui.screens.body.bodyWeightInputToKg
import com.ironlog.app.ui.screens.body.formatSigned
import com.ironlog.app.ui.screens.body.formatWeightValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyWeightUnitBoundaryTest {
    @Test
    fun `kilogram input round trips through canonical persistence and labels`() {
        val storedKg = bodyWeightInputToKg(82.4, "kg")

        assertEquals(82.4, storedKg ?: Double.NaN, 0.0001)
        assertEquals("82.4", bodyWeightInputTextFromKg(storedKg, "kg"))
        assertEquals("82.4 kg", formatWeightValue(storedKg, "kg"))
        assertEquals("+1.2 kg", formatSigned(1.2, "kg"))
    }

    @Test
    fun `pound input round trips through canonical kg and uses pound labels`() {
        val storedKg = bodyWeightInputToKg(154.3, "lbs")

        assertEquals(69.989, storedKg ?: Double.NaN, 0.0001)
        assertEquals("154.3", bodyWeightInputTextFromKg(storedKg, "lbs"))
        assertEquals("154 lb", formatWeightValue(70.0, "lbs"))
        assertEquals("-2 lb", formatSigned(-1.0, "lbs"))
    }

    @Test
    fun `singular pound alias follows the same canonical boundary`() {
        val storedKg = bodyWeightInputToKg(220.5, "lb")

        assertEquals(100.017, storedKg ?: Double.NaN, 0.0001)
        assertEquals("220.5", bodyWeightInputTextFromKg(storedKg, "lb"))
        assertEquals("220 lb", formatWeightValue(100.0, "lb"))
    }
}
