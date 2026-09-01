package com.ironlog.app.ui.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutForegroundResumePolicyTest {
    @Test
    fun `resume syncs a durable timer started active workout even without a pending start failure`() {
        assertTrue(
            shouldReconcileWorkoutForegroundService(
                currentWorkoutId = "workout-1",
                timerStarted = true,
                currentStartMs = 1_725_000_000_000L,
                durableWorkoutId = "workout-1",
                durableStartMs = 1_725_000_000_000L,
            ),
        )
    }

    @Test
    fun `resume does not restart a stale or timerless workout service`() {
        assertFalse(
            shouldReconcileWorkoutForegroundService(
                currentWorkoutId = "workout-1",
                timerStarted = true,
                currentStartMs = 1_725_000_000_000L,
                durableWorkoutId = "workout-2",
                durableStartMs = 1_725_000_000_000L,
            ),
        )
        assertFalse(
            shouldReconcileWorkoutForegroundService(
                currentWorkoutId = "workout-1",
                timerStarted = false,
                currentStartMs = null,
                durableWorkoutId = "workout-1",
                durableStartMs = null,
            ),
        )
        assertFalse(
            shouldReconcileWorkoutForegroundService(
                currentWorkoutId = "workout-1",
                timerStarted = true,
                currentStartMs = 1_725_000_000_000L,
                durableWorkoutId = "workout-1",
                durableStartMs = 1_725_000_000_001L,
            ),
        )
    }
}
