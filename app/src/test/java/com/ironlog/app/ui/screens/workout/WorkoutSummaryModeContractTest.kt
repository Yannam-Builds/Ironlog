package com.ironlog.app.ui.screens.workout

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryModeContractTest {
    @Test fun `completion receives the selected intelligence mode`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt").readText()
        assertTrue("Summary must receive mode, not just saved credentials", source.contains("intelligenceMode = appSettings.intelligenceMode"))
        assertTrue("Summary must use the mode-scoped debrief", source.contains("WorkoutCloudDebrief("))
    }
    @Test fun `workout observes credential-only changes`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt").readText()
        assertTrue(source.contains("CloudAiKeyStore.revision.collectAsStateWithLifecycle()"))
    }
}
