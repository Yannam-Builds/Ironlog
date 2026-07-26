package com.ironlog.app.navigation

import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.ui.screens.onboarding.OnboardingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPersistenceTest {
    @Test
    fun `onboarding draft maps into canonical athlete calibration entity and fallback settings`() {
        val draft = OnboardingDraft(
            weeklyGoalDays = 5,
            historicalTrainingDaysPerWeek = 4,
            weightUnit = "kg",
            goalMode = "STRENGTH",
            bodyweightKg = 70,
            trainingAgeMonths = 19,
            hasPastTraining = true,
            hasGymAccess = true,
            baselinePushups = 40,
            baselinePullups = 14,
            baselineBenchKg = 65,
            baselineLatPulldownKg = 110,
            baselineMileRunSeconds = 570,
        )

        val entity = buildCalibrationEntityFromOnboardingDraft(
            draft = draft,
            existing = AthleteCalibrationEntity(),
            updatedAtMs = 1234L,
        )
        val baselineSettings = onboardingBaselineSettingsFromDraft(draft)

        assertEquals("local", entity.offlineUserId)
        assertEquals(19, entity.trainingAgeMonths)
        assertEquals(5, entity.weeklyGoalDays)
        assertEquals(4, entity.historicalTrainingDaysPerWeek)
        assertEquals("kg", entity.weightUnit)
        assertEquals(70.0, entity.bodyweightKg!!, 0.0)
        assertTrue(entity.hasPastTraining)
        assertTrue(entity.hasGymAccess)
        assertEquals(40, entity.baselinePushups)
        assertEquals(14, entity.baselinePullups)
        assertEquals(65, entity.baselineBenchKg)
        assertEquals(110, entity.baselineLatPulldownKg)
        assertEquals(570, entity.baselineMileRunSeconds)
        assertEquals(1234L, entity.updatedAt)

        assertEquals("19", baselineSettings["baseline_training_age_months"])
        assertEquals("4", baselineSettings["baseline_historical_training_days_per_week"])
        assertEquals("70", baselineSettings["baseline_bodyweight_kg"])
        assertEquals("40", baselineSettings["baseline_pushups"])
        assertEquals("14", baselineSettings["baseline_pullups"])
        assertEquals("65", baselineSettings["baseline_bench_kg"])
        assertEquals("110", baselineSettings["baseline_lat_pulldown_kg"])
        assertEquals("570", baselineSettings["baseline_mile_run_seconds"])
        assertEquals("true", baselineSettings["baseline_has_past_training"])
        assertEquals("true", baselineSettings["baseline_has_gym_access"])
    }
}
