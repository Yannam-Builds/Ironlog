package com.ironlog.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSetInputTest {
    @Test fun `distance stays distance in pounds while actual load converts to kilograms`() {
        assertEquals(5.0, canonicalSetLoad(5.0, "duration_distance", "lb"), 0.0)
        assertEquals(5.0, canonicalSetLoad(5.0, "cardio", "lb"), 0.0)
        assertEquals(45.359, canonicalSetLoad(100.0, "weight_reps", "lbs"), 0.00001)
        assertEquals(45.359, canonicalSetLoad(100.0, "duration_weight", "lbs"), 0.00001)
        assertEquals(100.0, canonicalSetLoad(100.0, "weight_reps", "kg"), 0.0)
    }

    @Test fun `legacy dropset and current drop save the same canonical type`() {
        assertEquals("drop", canonicalSetType("dropset"))
        assertEquals("drop", canonicalSetType("DROP"))
        assertEquals("warmup", canonicalSetType(" WARMUP "))
    }
}
