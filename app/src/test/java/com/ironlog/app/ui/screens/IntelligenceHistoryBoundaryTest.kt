package com.ironlog.app.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceHistoryBoundaryTest {
    @Test fun `weekly insight screen uses completed history rather than legacy draft json`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/intelligence/TrainingIntelligenceScreen.kt").readText()
        assertFalse(source.contains("loadActiveDraftContribution"))
        assertFalse(source.contains("active_workout_draft_"))
        assertTrue(source.contains("Completed workouts only"))
    }
}
