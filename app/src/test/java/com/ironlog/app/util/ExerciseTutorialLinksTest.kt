package com.ironlog.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseTutorialLinksTest {
    @Test fun resolvesDisplayNamesAgainstCompactAssetKeys() {
        val links = mapOf("abcrunchmachine" to "https://example.com/ab", "plank" to "https://example.com/plank")
        assertEquals("https://example.com/ab", exerciseTutorialLink(links, "  Ab Crunch Machine  "))
        assertEquals("https://example.com/ab", exerciseTutorialLink(links, "AbCrunchMachine"))
        assertEquals("https://example.com/plank", exerciseTutorialLink(links, "PLANK"))
        assertNull(exerciseTutorialLink(links, "Athlete custom exercise"))
    }

    @Test fun exactNormalizedKeyTakesPrecedence() {
        val links = mapOf("ab_crunch_machine" to "https://example.com/exact", "abcrunchmachine" to "https://example.com/compact")
        assertEquals("https://example.com/exact", exerciseTutorialLink(links, "Ab Crunch Machine"))
    }
}
