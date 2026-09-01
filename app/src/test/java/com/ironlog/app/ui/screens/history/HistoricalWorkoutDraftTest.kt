package com.ironlog.app.ui.screens.history

import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.WorkoutAction
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoricalWorkoutDraftTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-09-01T12:00:00Z")
    private fun draft() = HistoricalWorkoutDraft(id = "draft", date = "2026-08-31", time = "09:30", durationMinutes = "20",
        exercises = listOf(HistoricalExerciseDraft(id = "row", exerciseId = "exercise", name = "Bench",
            sets = listOf(LoggedSet(id = "A", weight = 100.0, reps = 8.0, type = "drop", rpe = 8.0, rir = 2, note = "Pause"),
                LoggedSet(id = "B", weight = 90.0, reps = 10.0)))))

    @Test fun localSetActionsPreserveIdsAndNotesAndClearEffort() {
        val edited = updateHistoricalSet(draft(), "row", "A", WorkoutAction.SetRpe(0, 0, null), "kg")
        val input = edited.toCompletedInput(zone, now)
        assertEquals("draft", input.uid)
        assertEquals("A", input.exerciseData.single().sets.first().uid)
        assertNull(input.exerciseData.single().sets.first().rpe)
        assertEquals("Pause", input.exerciseData.single().sets.first().notes)
        assertEquals("drop", input.exerciseData.single().sets.first().type)
        val removed = updateHistoricalSet(edited, "row", "A", WorkoutAction.DeleteSet(0, 0), "kg")
        assertEquals(listOf("B"), removed.exercises.single().sets.map { it.id })
    }

    @Test fun poundsEditsConvertDisplayLoadExactlyOnce() {
        val edited = updateHistoricalSet(draft(), "row", "A", WorkoutAction.UpdateSet(0, 0, 220.462262185, 8.0), "lbs")
        assertEquals(100.0, edited.exercises.single().sets.first().weight, 0.001)
    }

    @Test fun draftRoundTripRetainsStableIdsAndUnsubmittedText() {
        val original = draft().copy(notes = "Draft note", exercises = draft().exercises.map { it.copy(loadInput = "12.", repsInput = "") })
        assertEquals(original, decodeHistoricalDraft(encodeHistoricalDraft(original)))
    }

    @Test fun nonfiniteEditedSetSurvivesRecreationAndStillBlocksSave() {
        val edited = updateHistoricalSet(draft(), "row", "A", WorkoutAction.UpdateSet(0, 0, Double.NaN, Double.POSITIVE_INFINITY), "kg")
        val restored = decodeHistoricalDraft(encodeHistoricalDraft(edited))
        assertTrue(restored.exercises.single().sets.first().weight.isNaN())
        assertEquals(Double.POSITIVE_INFINITY, restored.exercises.single().sets.first().reps, 0.0)
        assertTrue(runCatching { restored.toCompletedInput(zone, now) }.isFailure)
    }

    @Test fun invalidNumbersEmptyDetailedEntryAndFutureDateBlockSave() {
        assertTrue(runCatching { draft().copy(durationMinutes = "-1").toCompletedInput(zone, now) }.isFailure)
        assertTrue(runCatching { draft().copy(exercises = emptyList()).toCompletedInput(zone, now) }.isFailure)
        assertTrue(runCatching { draft().copy(date = "2030-01-01").toCompletedInput(zone, now) }.isFailure)
        assertTrue(runCatching { draft().copy(exercises = listOf(draft().exercises.single().copy(sets = listOf(LoggedSet(weight = Double.NaN, reps = 8.0))))).toCompletedInput(zone, now) }.isFailure)
        assertTrue(draft().copy(detailed = false, exercises = emptyList()).toCompletedInput(zone, now).exerciseData.isEmpty())
    }
}
