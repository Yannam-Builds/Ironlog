package com.ironlog.app.data.plan

import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanJsonCodecTest {

    @Test
    fun `canonical wrapper preserves exerciseName notes warmups and snake case`() {
        val raw = """
            {
              "version": 1,
              "type": "ironlog_plan",
              "plan": {
                "name": "Push",
                "notes": "Plan description",
                "days": [{
                  "name": "Day 1",
                  "exercises": [{
                    "exerciseName": "Barbell Bench Press",
                    "exercise_id": "bench-1",
                    "sets": 4,
                    "reps": "6-8",
                    "rest_seconds": 150,
                    "superset_group": "A",
                    "is_warmup": true,
                    "notes": "Two-count pause on the chest."
                  }]
                }]
              }
            }
        """.trimIndent()

        val decoded = PlanJsonCodec.decode(raw).single()
        val exercise = decoded.days.single().exercises.single()

        assertEquals("Plan description", decoded.description)
        assertEquals("Barbell Bench Press", exercise.name)
        assertEquals("bench-1", exercise.exerciseId)
        assertEquals(150, exercise.restSeconds)
        assertEquals("A", exercise.supersetGroup)
        assertEquals(true, exercise.isWarmup)
        assertEquals("Two-count pause on the chest.", exercise.notes)
    }

    @Test
    fun `camel and snake case exercise order aliases are decoded`() {
        val decoded = PlanJsonCodec.decode(
            """{"name":"Ordered","days":[{"exercises":[{"name":"A","orderIndex":7},{"name":"B","order_index":2}]}]}""",
        ).single().days.single().exercises

        assertEquals(7, decoded[0].orderIndex)
        assertEquals(2, decoded[1].orderIndex)
    }

    @Test
    fun `legacy array still decodes and canonical export wraps the plan`() {
        val legacy = """[{"name":"Legacy","days":[{"name":"A","exercises":[{"name":"Dips","sets":3}]}]}]"""
        assertEquals("Dips", PlanJsonCodec.decode(legacy).single().days.single().exercises.single().name)

        val exported = PlanJsonCodec.encode(
            UiPlan(
                id = "p1",
                name = "Exported",
                days = listOf(
                    UiPlanDay("d1", "Day", exercises = listOf(UiPlanExercise("pe1", "ex1", "Dips", 3, "8", 90, notes = "Lean forward"))),
                ),
            ),
            exportedAt = "2026-08-13T00:00:00Z",
        )

        assertEquals("ironlog_plan", exported.getString("type"))
        assertTrue(exported.has("plan"))
        assertEquals("Lean forward", exported.getJSONObject("plan").getJSONArray("days").getJSONObject(0).getJSONArray("exercises").getJSONObject(0).getString("notes"))
    }

    @Test
    fun `canonical export and decode preserve custom exercise identity and tracking metadata`() {
        val exported = PlanJsonCodec.encode(
            UiPlan(
                id = "custom-plan",
                name = "Odd lifts",
                days = listOf(
                    UiPlanDay(
                        id = "day-1",
                        name = "Custom day",
                        exercises = listOf(
                            UiPlanExercise(
                                id = "plan-exercise-1",
                                exerciseId = "custom-cable-pullover",
                                name = "Custom cable pullover",
                                sets = 3,
                                reps = "10-12",
                                restSeconds = 75,
                                notes = "Keep ribs down",
                                isCustom = true,
                                primaryMuscle = "lats",
                                equipment = "cable",
                                category = "strength",
                                trackingType = "weight_reps",
                                isBodyweight = false,
                                movementPattern = "pull",
                            ),
                        ),
                    ),
                ),
            ),
            exportedAt = "2026-09-01T00:00:00Z",
        )

        val encodedExercise = exported.getJSONObject("plan")
            .getJSONArray("days").getJSONObject(0)
            .getJSONArray("exercises").getJSONObject(0)
        val definition = encodedExercise.getJSONObject("exerciseDefinition")
        assertTrue(definition.getBoolean("isCustom"))
        assertEquals("weight_reps", definition.getString("trackingType"))

        val decoded = PlanJsonCodec.decode(exported.toString()).single()
            .days.single().exercises.single()
        assertEquals("custom-cable-pullover", decoded.exerciseId)
        assertEquals("Custom cable pullover", decoded.name)
        assertEquals("Keep ribs down", decoded.notes)
        assertTrue(decoded.definition?.isCustom == true)
        assertEquals("lats", decoded.definition?.primaryMuscle)
        assertEquals("cable", decoded.definition?.equipment)
        assertEquals("weight_reps", decoded.definition?.trackingType)
        assertFalse(decoded.definition?.isBodyweight ?: true)
    }

    @Test
    fun `decode report counts invalid plan and exercise entries instead of silently dropping them`() {
        val decoded = PlanJsonCodec.decodeWithReport(
            """
                {
                  "plans": [
                    {"goal":"Missing a name"},
                    {
                      "name":"Usable",
                      "days":[{
                        "name":"Day",
                        "exercises":[
                          {"name":"Valid movement"},
                          {"sets":3},
                          "not-an-object"
                        ]
                      }]
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(1, decoded.plans.size)
        assertEquals("Valid movement", decoded.plans.single().days.single().exercises.single().name)
        assertEquals(3, decoded.skipped)
    }
}
