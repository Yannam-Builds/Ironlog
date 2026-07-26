package com.ironlog.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutReducerRemoveExerciseTest {

    @Test
    fun removingBaseExerciseTracksOriginalBaseIndexAndReindexesSessionMaps() {
        val state = WorkoutState(
            inputs = mapOf(0 to WorkoutInput("100", "8"), 1 to WorkoutInput("80", "10"), 2 to WorkoutInput("50", "12")),
            setLog = mapOf(
                0 to listOf(LoggedSet(weight = 100.0, reps = 8.0)),
                1 to listOf(LoggedSet(weight = 80.0, reps = 10.0)),
                2 to listOf(LoggedSet(weight = 50.0, reps = 12.0)),
            ),
            supersetGroups = mapOf(0 to "A", 1 to "A", 2 to null),
        )

        val next = workoutReducer(
            state,
            WorkoutAction.RemoveExercise(exIndex = 1, baseExercisesCount = 3, removedBaseIndex = 4),
        )

        assertTrue(next.removedBaseExerciseIndices.contains(4))
        assertEquals(2, next.inputs.size)
        assertEquals("50", next.inputs[1]?.weight)
        assertEquals(1, next.setLog[1]?.size)
        assertEquals(50.0, next.setLog[1]?.first()?.weight ?: 0.0, 0.0)
    }

    @Test
    fun removingAddedExerciseDoesNotMarkBaseExerciseRemoved() {
        val state = WorkoutState(
            addedExercises = listOf(
                AddedExerciseEntry(exerciseId = "added-a", name = "Added A"),
                AddedExerciseEntry(exerciseId = "added-b", name = "Added B"),
            ),
        )

        val next = workoutReducer(
            state,
            WorkoutAction.RemoveExercise(exIndex = 3, baseExercisesCount = 2),
        )

        assertTrue(next.removedBaseExerciseIndices.isEmpty())
        assertEquals(listOf("Added A"), next.addedExercises.map { it.name })
    }
}
