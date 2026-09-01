package com.ironlog.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLibraryDeleteFailureTest {
    @Test
    fun `referenced custom exercise rejection is surfaced to the user`() {
        assertEquals(
            "This exercise is used by a plan or workout and cannot be deleted. Edit it instead.",
            customExerciseDeleteFailureMessage(
                IllegalStateException("This exercise is used by a plan or workout and cannot be deleted. Edit it instead."),
            ),
        )
    }

    @Test
    fun `blank failures receive an actionable fallback`() {
        assertEquals(
            "This exercise could not be deleted. Try again.",
            customExerciseDeleteFailureMessage(IllegalStateException()),
        )
    }
}
