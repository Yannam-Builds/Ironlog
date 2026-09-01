package com.ironlog.app.domain.training

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** Existing public history API: these assertions must fail before the typed policy is wired. */
class TrainingSetPolicyRegressionTest {
    @Test fun `legacy uppercase warmup never contributes external load volume`() {
        val history = history(HistoryExerciseSet(weight = 100.0, reps = 10.0, type = "WARMUP"))
        assertEquals(0.0, history.volume, 0.0)
    }

    @Test fun `invalid set does not poison valid history volume`() {
        val history = history(
            HistoryExerciseSet(weight = Double.NaN, reps = 10.0),
            HistoryExerciseSet(weight = 100.0, reps = 5.0),
        )
        assertEquals(500.0, history.volume, 0.0)
    }

    @Test fun `negative reps do not subtract from completed volume`() {
        val history = history(HistoryExerciseSet(weight = 100.0, reps = -5.0))
        assertEquals(0.0, history.volume, 0.0)
    }

    private fun history(vararg sets: HistoryExerciseSet) = HistoryEntry(
        id = "synthetic-workout", date = "2026-08-31T18:00:00Z",
        exercises = listOf(HistoryExercise(name = "Synthetic press", sets = sets.toList())),
    )
}
