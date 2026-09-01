package com.ironlog.app.ui.screens.history

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalTrackingPresentationTest {
    @Test fun historicalLibraryAdapterPreservesBodyweightAndAssistedModes() {
        val relative = "src/main/java/com/ironlog/app/ui/screens/history/HistoricalWorkoutEntryHost.kt"
        val source = listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()
        assertTrue("Bodyweight-only reps need their own presentation", source.contains("TrackingMode.BODYWEIGHT_REPS -> \"bodyweight_reps\""))
        assertTrue("Added load is not an ordinary weight-only exercise", source.contains("TrackingMode.ADDED_LOAD_REPS -> \"bodyweight_plus_weight_reps\""))
        assertTrue("Assistance must remain distinct from added load", source.contains("TrackingMode.ASSISTED_REPS -> \"assisted_bodyweight\""))
    }
}
