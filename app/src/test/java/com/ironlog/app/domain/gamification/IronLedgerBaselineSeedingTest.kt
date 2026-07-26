package com.ironlog.app.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IronLedgerBaselineSeedingTest {
    private val engine = IronLedgerEngine()

    @Test
    fun `onboarding calibration seeds starting ledger grade and stats before workout history`() {
        val snapshot = engine.rebuild(
            history = emptyList(),
            weeklyGoal = 5,
            calibration = AthleteCalibration(
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
            ),
        )

        assertEquals(IronGrade.TITANIUM, snapshot.grade)
        assertTrue(snapshot.stats.strength > 1)
        assertTrue(snapshot.stats.power > 1)
        assertTrue(snapshot.stats.endurance > 1)
        assertTrue(snapshot.stats.agility > 1)
        assertTrue(snapshot.stats.discipline > 1)
    }

    @Test
    fun `historical training frequency changes seeded baseline rank and stats`() {
        val lowFrequency = engine.rebuild(
            history = emptyList(),
            weeklyGoal = 5,
            calibration = AthleteCalibration(
                trainingAgeMonths = 24,
                weeklyGoalDays = 5,
                historicalTrainingDaysPerWeek = 1,
                bodyweightKg = 70.0,
                hasPastTraining = true,
                hasGymAccess = true,
                baselinePushups = 18,
                baselinePullups = 5,
                baselineBenchKg = 45,
                baselineLatPulldownKg = 60,
            ),
        )
        val highFrequency = engine.rebuild(
            history = emptyList(),
            weeklyGoal = 5,
            calibration = AthleteCalibration(
                trainingAgeMonths = 24,
                weeklyGoalDays = 5,
                historicalTrainingDaysPerWeek = 5,
                bodyweightKg = 70.0,
                hasPastTraining = true,
                hasGymAccess = true,
                baselinePushups = 18,
                baselinePullups = 5,
                baselineBenchKg = 45,
                baselineLatPulldownKg = 60,
            ),
        )

        assertTrue(highFrequency.grade.ordinal >= lowFrequency.grade.ordinal)
        assertTrue(highFrequency.stats.discipline > lowFrequency.stats.discipline)
        assertTrue(highFrequency.stats.recovery > lowFrequency.stats.recovery)
    }
}
