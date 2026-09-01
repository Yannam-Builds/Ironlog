package com.ironlog.app.ui.state

import org.junit.Assert.*
import org.junit.Test

class WorkoutEstimatePolicyTest {
    @Test fun `added bodyweight and warmup sets never display an invented one rep max`() {
        listOf(LoggedSet(weight = 10.0, reps = 8.0, trackingType = "bodyweight_plus_weight_reps"),
            LoggedSet(weight = 60.0, reps = 8.0, type = "warmup")).forEach { set ->
            val state = workoutReducer(WorkoutState(), WorkoutAction.LogSet(0, set))
            assertEquals(0.0, state.setLog[0]!!.single().orm, 0.0)
        }
    }
    @Test fun `changing retained working set to warmup clears its estimate`() {
        val logged = workoutReducer(WorkoutState(), WorkoutAction.LogSet(0, LoggedSet(weight = 60.0, reps = 8.0)))
        val warmup = workoutReducer(logged, WorkoutAction.SetType(0, 0, "warmup"))
        assertEquals(0.0, warmup.setLog[0]!!.single().orm, 0.0)
    }
}
