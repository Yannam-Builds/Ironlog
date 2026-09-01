package com.ironlog.app.ui.screens.home

import com.ironlog.app.data.repository.ProgressionPolicySnapshot
import com.ironlog.app.domain.intelligence.ProgressionAction
import com.ironlog.app.domain.intelligence.ProgressionPolicyResolver
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveProgressionPolicyTest {
    @Test
    fun `home adaptive target uses the same resolved exercise policy as active workout`() {
        val conservative = ProgressionPolicyResolver.resolve(null, null, "conservative")
        val aggressive = ProgressionPolicyResolver.resolve(null, null, "aggressive")
        val snapshot = ProgressionPolicySnapshot(
            fallback = conservative,
            byPlanExerciseId = mapOf("plan-row-1" to aggressive),
        )
        val day = UiPlanDay(
            id = "day-1",
            name = "Push",
            exercises = listOf(
                UiPlanExercise("plan-row-1", "bench", "Bench Press", 3, "8", 120),
            ),
        )
        val history = listOf(
            HistoryEntry(
                id = "workout-1",
                date = "2026-09-01T10:00:00Z",
                exercises = listOf(
                    HistoryExercise(
                        exerciseId = "bench",
                        name = "Bench Press",
                        trackingType = "weight_reps",
                        sets = List(3) { HistoryExerciseSet(weight = 100.0, reps = 8.0, rir = 1.0) },
                    ),
                ),
            ),
        )

        val target = buildAdaptiveTargets(day, history, snapshot).single()

        assertEquals(ProgressionAction.ADD_LOAD, target.advice?.action)
        assertEquals(aggressive.id, target.advice?.policyId)
    }
}
