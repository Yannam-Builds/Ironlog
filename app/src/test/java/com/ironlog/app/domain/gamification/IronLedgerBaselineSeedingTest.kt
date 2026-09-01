package com.ironlog.app.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IronLedgerBaselineSeedingTest {
    private val engine = IronLedgerEngine()

    @Test
    fun `onboarding calibration never grants earned ledger progress before workout history`() {
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

        assertEquals(IronGrade.UNCALIBRATED, snapshot.grade)
        assertEquals(0L, snapshot.totalXp)
        assertEquals(1, snapshot.level)
        assertEquals(IronLedgerStats(), snapshot.stats)
    }

    @Test
    fun `historical training frequency does not change earned rank or stats`() {
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

        assertEquals(lowFrequency.grade, highFrequency.grade)
        assertEquals(lowFrequency.stats, highFrequency.stats)
        assertEquals(lowFrequency.totalXp, highFrequency.totalXp)
    }
}
