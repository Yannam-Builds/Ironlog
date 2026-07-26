package com.ironlog.app.ui.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutExerciseBindingTest {
    @Test
    fun mapsDuplicateExerciseIdsToDistinctWorkoutRowsInUiOrder() {
        val rows = listOf(
            WorkoutExerciseBinding(workoutExerciseId = "warmup-row", exerciseId = "bench", orderIndex = 0),
            WorkoutExerciseBinding(workoutExerciseId = "working-row", exerciseId = "bench", orderIndex = 1),
            WorkoutExerciseBinding(workoutExerciseId = "curl-row", exerciseId = "curl", orderIndex = 2),
        )

        val result = buildWorkoutExerciseIndexMap(
            exerciseIdsInUiOrder = listOf("bench", "bench", "curl"),
            workoutExerciseRows = rows,
        )

        assertEquals("warmup-row", result[0])
        assertEquals("working-row", result[1])
        assertEquals("curl-row", result[2])
    }
}
