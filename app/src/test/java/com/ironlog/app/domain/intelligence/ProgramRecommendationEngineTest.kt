package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.seed.PROGRAM_TEMPLATES
import com.ironlog.app.data.seed.ProgramTemplate
import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.PlanExerciseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRecommendationEngineTest {

    @Test
    fun `no gym access keeps home friendly plans at the top`() {
        val ranked = ProgramRecommendationEngine.rank(
            PROGRAM_TEMPLATES,
            ProgramRecommendationProfile(
                goalMode = "general_fitness",
                weeklyDays = 3,
                trainingAgeMonths = 2,
                hasPastTraining = false,
                hasGymAccess = false,
            ),
        )

        assertTrue(ranked.take(3).all { ProgramRecommendationEngine.isHomeFriendly(it.template) })
    }

    @Test
    fun `new three day athlete gets a three day foundation option`() {
        val best = ProgramRecommendationEngine.rank(
            PROGRAM_TEMPLATES,
            ProgramRecommendationProfile(
                goalMode = "strength",
                weeklyDays = 3,
                trainingAgeMonths = 1,
                hasPastTraining = false,
                hasGymAccess = true,
            ),
        ).first()

        assertEquals(3, best.template.days.size)
        assertEquals("Foundation", best.experienceLabel)
    }

    @Test
    fun `experienced hypertrophy athlete gets matching high frequency plan`() {
        val best = ProgramRecommendationEngine.rank(
            PROGRAM_TEMPLATES,
            ProgramRecommendationProfile(
                goalMode = "hypertrophy",
                weeklyDays = 5,
                trainingAgeMonths = 30,
                hasPastTraining = true,
                hasGymAccess = true,
            ),
        ).first()

        assertEquals(5, best.template.days.size)
        assertTrue(best.template.category in setOf("HYPERTROPHY", "AESTHETIC"))
        assertTrue(best.sessionLengthLabel.startsWith("~"))
    }

    @Test fun `minimalist gym program is not home equipment`() {
        val template = PROGRAM_TEMPLATES.first { it.id == "minimalist_full_body_23" }
        assertTrue(!ProgramRecommendationEngine.isHomeFriendly(template))
        val recommendation = ProgramRecommendationEngine.rank(listOf(template), ProgramRecommendationProfile(hasGymAccess = false)).single()
        assertTrue(!recommendation.reason.contains("home-friendly"))
        assertTrue(recommendation.equipmentLabel.contains("Gym"))
    }

    @Test fun `home title cannot hide cable or barbell requirements`() {
        listOf("Cable Row", "Barbell Squat", "Leg Press", "Smith Press").forEach { exercise ->
            assertTrue(exercise, !ProgramRecommendationEngine.isHomeFriendly(template("Home Minimal", exercise)))
        }
    }

    @Test fun `portable equipment is recognized from exercises without marketing keywords`() {
        assertTrue(ProgramRecommendationEngine.isHomeFriendly(template("Routine A", "Dumbbell Row", "Push-Up")))
        assertTrue(ProgramRecommendationEngine.isHomeFriendly(template("Routine B", "Band Row", "Band Squat")))
    }

    @Test fun `unknown and empty exercise requirements are not certified home friendly`() {
        assertTrue(!ProgramRecommendationEngine.isHomeFriendly(template("Home Minimal", "Mystery Movement")))
        assertTrue(!ProgramRecommendationEngine.isHomeFriendly(template("Home Minimal")))
    }

    @Test fun `home recommendations disclose needed equipment`() {
        val recommendation = ProgramRecommendationEngine.rank(
            listOf(template("Routine", "Dumbbell Bench Press", "Pull-Up", "Band Row")),
            ProgramRecommendationProfile(hasGymAccess = false),
        ).single()
        assertTrue(recommendation.equipmentLabel.contains("Dumbbells", true))
        assertTrue(recommendation.equipmentLabel.contains("bench", true))
        assertTrue(recommendation.equipmentLabel.contains("pull-up", true))
        assertTrue(recommendation.equipmentLabel.contains("bands", true))
        assertTrue(recommendation.reason.contains("if you have", true))
    }

    @Test fun `mismatched frequency explains actual days without claiming closest fit`() {
        val recommendation = ProgramRecommendationEngine.rank(
            listOf(template("Routine", "Push-Up")), ProgramRecommendationProfile(weeklyDays = 4),
        ).single()
        assertTrue(!recommendation.reason.contains("Closest fit"))
        assertTrue(recommendation.reason.contains("1-day"))
        assertTrue(recommendation.reason.contains("4-day"))
    }

    @Test fun `reason describes template focus not requested goal`() {
        val recommendation = ProgramRecommendationEngine.rank(
            listOf(template("Routine", "Push-Up").copy(category = "STRENGTH")),
            ProgramRecommendationProfile(goalMode = "hypertrophy"),
        ).single()
        assertTrue(recommendation.reason.contains("strength", true))
        assertTrue(!recommendation.reason.contains("built for muscle growth", true))
    }

    @Test fun `experience does not add an arbitrary high frequency bonus`() {
        val oneDay = template("Routine", "Push-Up").days.single()
        val templates = listOf(
            template("Routine Four", "Push-Up").copy(days = List(4) { oneDay }),
            template("Routine Five", "Push-Up").copy(days = List(5) { oneDay }),
        )
        fun scores(months: Int) = ProgramRecommendationEngine.rank(templates,
            ProgramRecommendationProfile(trainingAgeMonths = months, hasPastTraining = true, weeklyDays = 4),
        ).associate { it.template.name to it.score }
        assertEquals(scores(12), scores(24))
    }

    private fun template(name: String, vararg exercises: String) = ProgramTemplate(
        id = name, name = name, category = "HYPERTROPHY", description = "",
        days = listOf(FullPlanDay("Day", "#FFFFFF", exercises.map { PlanExerciseInput(name = it, sets = 3) })),
    )
}
