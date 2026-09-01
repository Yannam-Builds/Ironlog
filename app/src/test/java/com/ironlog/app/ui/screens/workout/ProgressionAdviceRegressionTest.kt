package com.ironlog.app.ui.screens.workout

import com.ironlog.app.ui.state.GhostData
import com.ironlog.app.ui.state.GhostSet
import org.junit.Assert.*
import org.junit.Test

class ProgressionAdviceRegressionTest {
    @Test fun `weighted bodyweight normalizer key receives conservative added load progression`() {
        val ghost = GhostData(List(3) { GhostSet(10.0, 8.0, rir = 3.0) })
        val advice = buildProgressionSuggestion(ghost, "kg", "bodyweight_plus_weight_reps", 3, 8)
        assertNotNull(advice)
        assertTrue(advice.orEmpty().contains("9 reps"))
    }
    @Test fun `rep progression follows performed baseline even when plan target is unchanged`() {
        for (performed in listOf(9, 12)) {
            val previous = GhostData(List(3) { GhostSet(0.0, performed.toDouble(), rir = 3.0) })
            assertTrue(buildProgressionSuggestion(previous, "kg", "reps", 3, 8).orEmpty().contains("${performed + 1} reps"))
        }
    }
    @Test fun `missed target holds load despite low effort`() {
        val advice = buildProgressionSuggestion(GhostData(List(3) { GhostSet(100.0, 6.0, rpe = 7.0) }), "kg", "weight_reps", 3, 8)
        assertTrue(advice.orEmpty().startsWith("Repeat"))
    }
    @Test fun `all targets with effort margin permit a small load step in correct units`() {
        val ghost = GhostData(List(3) { GhostSet(100.0, 8.0, rpe = 8.0) })
        assertTrue(buildProgressionSuggestion(ghost, "kg", "weight_reps", 3, 8).orEmpty().contains("102.5 kg"))
        assertTrue(buildProgressionSuggestion(ghost, "lbs", "weight_reps", 3, 8).orEmpty().contains("225 lb"))
    }
    @Test fun `bodyweight and small loads use rep progression without a large weight jump`() {
        val bodyweight = GhostData(List(3) { GhostSet(0.0, 8.0, rir = 3.0) })
        assertTrue(buildProgressionSuggestion(bodyweight, "kg", "reps", 3, 8).orEmpty().contains("9 reps"))
        val lightLoad = GhostData(List(3) { GhostSet(10.0, 8.0, rir = 3.0) })
        assertTrue(buildProgressionSuggestion(lightLoad, "kg", "weight_reps", 3, 8).orEmpty().contains("9 reps"))
    }
    @Test fun `timed exercise does not get a rep prescription`() {
        assertNull(buildProgressionSuggestion(GhostData(listOf(GhostSet(0.0, 30.0))), "kg", "duration", 3, 30))
    }
    @Test fun `missing effort and mixed loads require review`() {
        assertTrue(buildProgressionSuggestion(GhostData(List(3) { GhostSet(50.0, 8.0) }), "kg", "weight_reps", 3, 8).orEmpty().startsWith("Repeat"))
        val mixed = GhostData(listOf(GhostSet(50.0, 8.0, rpe = 7.0), GhostSet(40.0, 8.0, rpe = 7.0)))
        assertTrue(buildProgressionSuggestion(mixed, "kg", "weight_reps", 2, 8).orEmpty().startsWith("Repeat"))
    }
    @Test fun `near failure performance does not force a load increase`() {
        val advice = buildProgressionSuggestion(GhostData(listOf(GhostSet(100.0, 6.0, rpe = 10.0))), "kg")
        assertTrue(advice.orEmpty().contains("Repeat"))
    }
    @Test fun `warmup only data is not a progression baseline`() {
        assertNull(buildProgressionSuggestion(GhostData(listOf(GhostSet(50.0, 8.0, type = "WARMUP"))), "kg"))
    }
    @Test fun `invalid previous performance cannot generate a prescription`() {
        assertNull(buildProgressionSuggestion(GhostData(listOf(GhostSet(Double.NaN, 0.0))), "kg"))
    }
}
