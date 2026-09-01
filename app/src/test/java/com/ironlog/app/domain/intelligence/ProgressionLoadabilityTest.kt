package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.screens.settings.PlateDto
import org.junit.Assert.*
import org.junit.Test

class ProgressionLoadabilityTest {
    @Test fun `suggested barbell step respects actual plate inventory`() {
        assertEquals(2.5, progressionBarbellStep(100.0, 20.0, listOf(PlateDto(20.0, 2), PlateDto(1.25, 1))), .0001)
        assertEquals(5.0, progressionBarbellStep(100.0, 20.0, listOf(PlateDto(20.0, 2), PlateDto(2.5, 1))), .0001)
    }
    @Test fun `unloadable baseline or missing plates does not suggest a fictitious increase`() {
        assertEquals(0.0, progressionBarbellStep(101.0, 20.0, listOf(PlateDto(20.0, 2), PlateDto(2.5, 1))), 0.0)
        assertEquals(0.0, progressionBarbellStep(100.0, 20.0, emptyList()), 0.0)
        assertEquals(0.0, progressionBarbellStep(Double.NaN, 20.0, listOf(PlateDto(2.5, 1))), 0.0)
    }
}
