package com.ironlog.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutClearExerciseNotesTest {
    @Test
    fun `clear exercise notes preserves set session and previous-session data`() {
        val set = LoggedSet(id = "set", note = "Set note")
        val ghost = GhostData(previousNote = "Previous-session note")
        val initial = WorkoutState(
            setLog = mapOf(0 to listOf(set)),
            ghostData = mapOf(0 to ghost),
            exerciseNotes = mapOf(0 to "Plan cue", 1 to "Session cue"),
            supersetGroups = mapOf(0 to "A"),
        )

        val cleared = workoutReducer(initial, WorkoutAction.ClearExerciseNotes)

        assertTrue(cleared.exerciseNotes.isEmpty())
        assertEquals(initial.setLog, cleared.setLog)
        assertEquals(initial.ghostData, cleared.ghostData)
        assertEquals(initial.supersetGroups, cleared.supersetGroups)
    }
}
