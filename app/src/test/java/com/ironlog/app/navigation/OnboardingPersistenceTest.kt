package com.ironlog.app.navigation

import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.domain.intelligence.INTELLIGENCE_MODE_BUILTIN
import com.ironlog.app.domain.intelligence.INTELLIGENCE_MODE_CLOUD_AI
import com.ironlog.app.domain.intelligence.INTELLIGENCE_MODE_GEMINI_NANO
import com.ironlog.app.domain.intelligence.TrainingDayPreferences
import com.ironlog.app.domain.intelligence.canonicalIntelligenceMode
import com.ironlog.app.ui.screens.onboarding.OnboardingDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPersistenceTest {
    @Test
    fun `onboarding modes map to settings modes used by the rest of the app`() {
        assertEquals("strength", canonicalGoalMode("STRENGTH"))
        assertEquals("hypertrophy", canonicalGoalMode("HYPERTROPHY"))
        assertEquals("general_fitness", canonicalGoalMode("PERFORMANCE"))
        assertEquals("general_fitness", canonicalGoalMode("ENDURANCE"))
        assertEquals("conservative", canonicalProgressionStyle("LINEAR"))
        assertEquals("balanced", canonicalProgressionStyle("DOUBLE_PROGRESSION"))
        assertEquals("aggressive", canonicalProgressionStyle("UNDULATING"))
        assertEquals(INTELLIGENCE_MODE_BUILTIN, canonicalIntelligenceMode("LOCAL"))
        assertEquals(INTELLIGENCE_MODE_CLOUD_AI, canonicalIntelligenceMode("AUTO"))
        assertEquals(INTELLIGENCE_MODE_GEMINI_NANO, canonicalIntelligenceMode("gemini_nano"))
    }

    @Test
    fun `onboarding cloud selection and exact weekdays survive settings json round trip`() {
        val merged = mergeOnboardingSettingsJson(
            existingRaw = "{\"theme\":\"Dark\"}",
            draft = OnboardingDraft(
                intelligenceMode = "AUTO",
                weeklyGoalDays = 3,
                selectedDayIndices = setOf(1, 3, 5),
            ),
        )

        assertEquals("Dark", merged.getString("theme"))
        assertEquals(INTELLIGENCE_MODE_CLOUD_AI, merged.getString("intelligenceMode"))
        assertEquals(
            setOf(1, 3, 5),
            TrainingDayPreferences.readFromSettings(merged, fallbackCount = 3),
        )
    }

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
        assertEquals("false", baselineSettings["notifications_enabled"])
    }

    @Test
    fun `notification permission choice is persisted with onboarding`() {
        val granted = onboardingBaselineSettingsFromDraft(OnboardingDraft(notificationsGranted = true))
        val skipped = onboardingBaselineSettingsFromDraft(OnboardingDraft(notificationsGranted = false))

        assertEquals("true", granted["notifications_enabled"])
        assertEquals("false", skipped["notifications_enabled"])
    }
}
