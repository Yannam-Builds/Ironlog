package com.ironlog.app.ui.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExerciseTrackingTest {
    @Test
    fun `bodyweight strength movement records optional added load`() {
        assertEquals(
            "bodyweight_plus_weight_reps",
            resolveSessionTrackingType("weight_reps", isBodyweight = true),
        )
    }

    @Test
    fun `barbell and duration tracking stay unchanged`() {
        assertEquals("weight_reps", resolveSessionTrackingType("weight_reps", isBodyweight = false))
        assertEquals("duration_distance", resolveSessionTrackingType("duration_distance", isBodyweight = false))
    }
}
