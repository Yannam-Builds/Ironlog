package com.ironlog.app.ui.screens

import com.ironlog.app.ui.screens.body.*
import org.junit.Assert.*
import org.junit.Test

class BodyRegionPresentationTest {
    @Test fun `pain flags map broad groups to exactly their canvas regions`() {
        assertEquals(setOf("chest", "shoulders", "rearDelts"), bodyPainRegions(setOf("Push", "Shoulders")))
        assertEquals(setOf("quads", "hamstrings", "calves"), bodyPainRegions(setOf("Legs")))
        assertTrue(bodyPainRegions(setOf("Unknown")).isEmpty())
    }
    @Test fun `missing recovery data stays unknown instead of becoming fully recovered`() {
        assertTrue(buildDisplayReadiness(emptyMap()).isEmpty())
        assertEquals(mapOf("chest" to 0.4), buildDisplayReadiness(mapOf("Push" to 0.4)))
    }
    @Test fun `every visible region has a unique accessible name and source group`() {
        assertEquals(9, BODY_REGIONS.size)
        assertEquals(9, BODY_REGIONS.map { it.label }.toSet().size)
        assertTrue(BODY_REGIONS.all { it.group in setOf("Push", "Pull", "Shoulders", "Arms", "Legs", "Core") })
    }
}
