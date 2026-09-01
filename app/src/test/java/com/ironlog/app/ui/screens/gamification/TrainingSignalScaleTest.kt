package com.ironlog.app.ui.screens.gamification

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSignalScaleTest {
    @Test fun `scale remains 100 for early profiles`() {
        assertEquals(100, trainingSignalScale(listOf(0, 25, 99)))
    }

    @Test fun `scale expands to a shared readable ceiling`() {
        assertEquals(200, trainingSignalScale(listOf(145, 53, 50, 103, 40, 177, 159)))
    }

    @Test fun `exact quarter step remains exact`() {
        assertEquals(125, trainingSignalScale(listOf(125)))
    }
}
