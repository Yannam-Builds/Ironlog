package com.ironlog.app.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class InfiniteWheelPickerTest {
    @Test
    fun `virtual start maps back to selected value`() {
        for (size in listOf(7, 46, 145)) {
            for (selected in 0 until size) {
                assertEquals(selected, virtualWheelStart(size, selected).mod(size))
            }
        }
    }
}
