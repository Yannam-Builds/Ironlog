package com.ironlog.app.ui.screens.workout

import com.ironlog.app.ui.components.isValidEffortInput
import org.junit.Assert.*
import org.junit.Test

class EffortInputTest {
    @Test fun `rir accepts whole reps including zero and blank clears`() {
        for (value in listOf("", "0", "2", "10")) assertTrue(value, isValidEffortInput("RIR", value))
        for (value in listOf("-1", "2.5", "11", "NaN", "Infinity", "abc")) assertFalse(value, isValidEffortInput("RIR", value))
    }
    @Test fun `rpe accepts decimal effort within range and blank clears`() {
        for (value in listOf("", "1", "7.5", "7,5", "10")) assertTrue(value, isValidEffortInput("RPE", value))
        for (value in listOf("0", "-1", "10.1", "NaN", "Infinity", "abc")) assertFalse(value, isValidEffortInput("RPE", value))
    }
}
