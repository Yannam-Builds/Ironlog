package com.ironlog.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanExerciseNotesPreferencesTest {
    @Test
    fun `visibility defaults on and only explicit false hides notes`() {
        assertTrue(parsePlanExerciseNotesVisible(null))
        assertTrue(parsePlanExerciseNotesVisible(""))
        assertTrue(parsePlanExerciseNotesVisible("true"))
        assertTrue(parsePlanExerciseNotesVisible("unexpected"))
        assertFalse(parsePlanExerciseNotesVisible("false"))
        assertFalse(parsePlanExerciseNotesVisible(" FALSE "))
    }
}
