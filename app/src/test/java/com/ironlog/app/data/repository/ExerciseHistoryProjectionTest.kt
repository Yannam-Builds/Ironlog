package com.ironlog.app.data.repository

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.*
import org.junit.Test

class ExerciseHistoryProjectionTest {
    @Test fun `exercise progress filters shared history without discarding modality or flags`() {
        val selected = HistoryExercise(
            id = "performed", exerciseId = "library", name = "Synthetic hold", trackingType = "duration_weight",
            secondaryMuscles = listOf("Core"), requiresExternalLoad = true,
            sets = listOf(HistoryExerciseSet(id = "set", weight = 20.0, reps = 60.0, rir = 1.5, isAmrap = true, toFailure = true)),
        )
        val other = selected.copy(id = "other-performed", exerciseId = "other-library")
        val newer = HistoryEntry(id = "newer", date = "2026-09-01T10:30:00Z", planDayUid = "day", exercises = listOf(selected, other))
        val older = newer.copy(id = "older", date = "2026-08-30T10:30:00Z")
        val result = projectExerciseHistory(listOf(newer, older), "library")
        assertEquals(listOf("older", "newer"), result.map { it.id })
        assertEquals(selected, result.last().exercises.single())
        assertEquals("day", result.last().planDayUid)
        assertTrue(projectExerciseHistory(listOf(newer), "missing").isEmpty())
    }
}
