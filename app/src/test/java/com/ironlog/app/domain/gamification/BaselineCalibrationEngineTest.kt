package com.ironlog.app.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineCalibrationEngineTest {
    private val engine = BaselineCalibrationEngine()

    @Test
    fun `calibration reuses the historical seeded curve and exact lifetime session formula`() {
        val result = engine.calculate(richCalibration())

        assertEquals(330, result.estimatedLifetimeSessions)
        assertEquals(6_600L, result.xp)
        assertEquals(IronGrade.TITANIUM, result.grade)
        assertEquals(
            IronLedgerStats(
                strength = 476,
                power = 397,
                hypertrophy = 373,
                endurance = 371,
                agility = 383,
                discipline = 325,
                recovery = 249,
            ),
            result.stats,
        )
        assertEquals(0.5, result.trustScore, 0.0)
    }

    @Test
    fun `only exposure and duration badges supported by onboarding answers are seeded`() {
        val result = engine.calculate(richCalibration())

        assertEquals(
            setOf(
                "first_workout",
                "workouts_10",
                "workouts_50",
                "workouts_100",
                "consistency_4w",
                "member_365",
            ),
            result.supportedBadgeIds,
        )
        setOf(
            "streak_3",
            "streak_30",
            "first_pr",
            "first_rest_timer",
            "first_plan",
            "ai_activated",
            "progressive_streak",
            "volume_milestone",
            "all_goal_modes",
        ).forEach { unsupported ->
            assertFalse("$unsupported requires evidence onboarding does not collect", unsupported in result.supportedBadgeIds)
        }
    }

    @Test
    fun `frequency changes lifetime exposure xp and seeded signals without changing the formula`() {
        val low = engine.calculate(richCalibration().copy(historicalTrainingDaysPerWeek = 1))
        val high = engine.calculate(richCalibration().copy(historicalTrainingDaysPerWeek = 5))

        assertEquals(kotlin.math.round(19 * 4.345 * 1).toInt(), low.estimatedLifetimeSessions)
        assertEquals(kotlin.math.round(19 * 4.345 * 5).toInt(), high.estimatedLifetimeSessions)
        assertTrue(high.xp > low.xp)
        assertTrue(high.stats.discipline > low.stats.discipline)
        assertTrue(high.stats.recovery > low.stats.recovery)
        assertTrue(high.grade.ordinal >= low.grade.ordinal)
    }

    @Test
    fun `extreme self report remains capped at Titanium`() {
        val result = engine.calculate(
            richCalibration().copy(
                trainingAgeMonths = 360,
                historicalTrainingDaysPerWeek = 7,
                baselinePushups = 120,
                baselinePullups = 40,
                baselineBenchKg = 220,
                baselineLatPulldownKg = 220,
                baselineMileRunSeconds = 240,
            ),
        )

        assertEquals(IronGrade.TITANIUM, result.grade)
        assertTrue(result.xp > 0L)
    }

    @Test
    fun `fingerprint is deterministic and changes with calibration inputs`() {
        val first = engine.calculate(richCalibration())
        val retry = engine.calculate(richCalibration())
        val changed = engine.calculate(richCalibration().copy(baselineBenchKg = 70))

        assertTrue(first.fingerprint.isNotBlank())
        assertEquals(first.fingerprint, retry.fingerprint)
        assertNotEquals(first.fingerprint, changed.fingerprint)
    }

    private fun richCalibration() = AthleteCalibration(
        trainingAgeMonths = 19,
        weeklyGoalDays = 5,
        historicalTrainingDaysPerWeek = 4,
        bodyweightKg = 70.0,
        hasPastTraining = true,
        hasGymAccess = true,
        baselinePushups = 40,
        baselinePullups = 14,
        baselineBenchKg = 65,
        baselineLatPulldownKg = 110,
        baselineMileRunSeconds = 570,
    )
}
