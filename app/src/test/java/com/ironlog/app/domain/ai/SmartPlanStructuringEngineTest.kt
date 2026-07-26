package com.ironlog.app.domain.ai

import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.PlanExerciseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlanStructuringEngineTest {
    @Test
    fun addsWarmupForFirstCompoundWhenMissing() {
        val plan = FullPlanObject(
            name = "P",
            days = listOf(
                FullPlanDay(
                    name = "Push",
                    exercises = listOf(
                        PlanExerciseInput(exerciseId = "bench", name = "Barbell Bench Press", sets = 4, reps = "6-8", restSeconds = 20, isWarmup = false),
                        PlanExerciseInput(exerciseId = "lat", name = "Cable Lateral Raise", sets = 4, reps = "12-15", restSeconds = 0, isWarmup = false),
                    ),
                )
            ),
        )
        val lib = listOf(
            exercise("bench", "Barbell Bench Press", "push", "Barbell", "Chest", "weight_reps"),
            exercise("lat", "Cable Lateral Raise", "isolation", "Cable", "Shoulders", "weight_reps"),
        )

        val out = SmartPlanStructuringEngine.structure(plan, lib)
        val day = out.plan.days.first()
        assertTrue(out.insertedWarmupRows >= 1)
        assertTrue(day.exercises.first().isWarmup == true)
        assertEquals(60, day.exercises.first().restSeconds)
        assertEquals(120, day.exercises[1].restSeconds) // normalized compound rest (clamped from 20)
    }

    @Test
    fun suggestsSupersetForAdjacentIsolationRows() {
        val plan = FullPlanObject(
            name = "P",
            days = listOf(
                FullPlanDay(
                    name = "Pull",
                    exercises = listOf(
                        PlanExerciseInput(exerciseId = "curl", name = "Incline Dumbbell Curl", sets = 4, reps = "10-12", restSeconds = 75, isWarmup = false),
                        PlanExerciseInput(exerciseId = "pushdown", name = "Rope Pushdown", sets = 4, reps = "10-12", restSeconds = 75, isWarmup = false),
                    ),
                )
            ),
        )
        val lib = listOf(
            exercise("curl", "Incline Dumbbell Curl", "isolation", "Dumbbell", "Biceps", "weight_reps"),
            exercise("pushdown", "Rope Pushdown", "isolation", "Cable", "Triceps", "weight_reps"),
        )

        val out = SmartPlanStructuringEngine.structure(plan, lib)
        val rows = out.plan.days.first().exercises
        assertEquals(rows[0].supersetGroup, rows[1].supersetGroup)
        assertTrue(rows[0].supersetGroup == "A")
    }

    @Test
    fun keepsTimeBasedRowsOutOfSupersetAndNormalizesRest() {
        val plan = FullPlanObject(
            name = "P",
            days = listOf(
                FullPlanDay(
                    name = "Core",
                    exercises = listOf(
                        PlanExerciseInput(exerciseId = "plank", name = "Plank", sets = 3, reps = "60s", restSeconds = 200, isWarmup = false),
                        PlanExerciseInput(exerciseId = "crunch", name = "Ab Crunch Machine", sets = 4, reps = "12-15", restSeconds = 5, isWarmup = false),
                    ),
                )
            ),
        )
        val lib = listOf(
            exercise("plank", "Plank", "isolation", "Bodyweight", "Core", "duration"),
            exercise("crunch", "Ab Crunch Machine", "isolation", "Machine", "Core", "weight_reps"),
        )

        val out = SmartPlanStructuringEngine.structure(plan, lib)
        val rows = out.plan.days.first().exercises
        assertEquals(75, rows[0].restSeconds)
        assertEquals(60, rows[1].restSeconds)
        assertTrue(rows.all { it.supersetGroup.isNullOrBlank() })
    }

    private fun exercise(
        id: String,
        name: String,
        movementPattern: String,
        equipment: String,
        primaryMuscle: String,
        trackingType: String,
    ) = LegacyExerciseShape(
        id = id,
        exerciseId = id,
        name = name,
        primaryMuscles = listOf(primaryMuscle),
        primaryMuscle = primaryMuscle,
        secondaryMuscles = emptyList(),
        equipment = equipment,
        category = "strength",
        trackingType = trackingType,
        isCustom = false,
        aliases = emptyList(),
        isBodyweight = equipment.equals("bodyweight", ignoreCase = true),
        movementPattern = movementPattern,
        difficulty = "intermediate",
        apparatus = null,
        equipmentDetail = null,
        sourceTags = emptyList(),
        notes = "",
    )
}
