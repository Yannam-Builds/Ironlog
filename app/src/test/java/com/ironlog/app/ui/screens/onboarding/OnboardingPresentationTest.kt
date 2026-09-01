package com.ironlog.app.ui.screens.onboarding

import com.ironlog.app.domain.gamification.AthleteCalibration
import com.ironlog.app.domain.gamification.BaselineCalibrationEngine
import com.ironlog.app.ui.screens.home.dailyProofVisualSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPresentationTest {
    @Test
    fun `compact phones and large text use the tighter shared page inset`() {
        val compactPhone = onboardingContentLayoutSpec(widthDp = 320, fontScale = 1f)
        val largeText = onboardingContentLayoutSpec(widthDp = 420, fontScale = 1.5f)
        val regular = onboardingContentLayoutSpec(widthDp = 420, fontScale = 1f)

        assertEquals(18, compactPhone.horizontalPaddingDp)
        assertEquals(18, largeText.horizontalPaddingDp)
        assertEquals(24, regular.horizontalPaddingDp)
        assertTrue(compactPhone.bottomPaddingDp >= 48)
    }

    @Test
    fun `welcome hero always reserves a separate mascot viewport and banner gap`() {
        val compact = welcomeHeroLayoutSpec(availableHeightDp = 640, fontScale = 1.35f)
        val roomy = welcomeHeroLayoutSpec(availableHeightDp = 900, fontScale = 1f)

        assertTrue(compact.isCompact)
        assertTrue(compact.mascotViewportHeightDp >= compact.mascotSizeDp)
        assertTrue(compact.bannerGapDp >= 12)
        assertTrue(compact.mascotSizeDp < roomy.mascotSizeDp)
    }

    @Test
    fun `supporting mascot remains subordinate to the welcome hero on every height`() {
        val compactHero = welcomeHeroLayoutSpec(availableHeightDp = 640, fontScale = 1.35f)
        val roomyHero = welcomeHeroLayoutSpec(availableHeightDp = 900, fontScale = 1f)
        val compactSupporting = supportingMascotSizeDp(availableHeightDp = 640, fontScale = 1.35f)
        val roomySupporting = supportingMascotSizeDp(availableHeightDp = 900, fontScale = 1f)

        assertTrue(compactSupporting in 80 until compactHero.mascotSizeDp)
        assertTrue(roomySupporting in 80 until roomyHero.mascotSizeDp)
        assertTrue(compactSupporting <= roomySupporting)
    }

    @Test
    fun `mascot hierarchy remains consistent with the home daily proof art`() {
        val compactHome = dailyProofVisualSpec(320f).imageSizeDp
        val regularHome = dailyProofVisualSpec(390f).imageSizeDp
        val roomyHome = dailyProofVisualSpec(480f).imageSizeDp
        val compactSupporting = supportingMascotSizeDp(availableHeightDp = 640, fontScale = 1.35f)
        val regularSupporting = supportingMascotSizeDp(availableHeightDp = 800, fontScale = 1f)
        val roomySupporting = supportingMascotSizeDp(availableHeightDp = 900, fontScale = 1f)
        val compactHero = welcomeHeroLayoutSpec(availableHeightDp = 640, fontScale = 1.35f).mascotSizeDp
        val regularHero = welcomeHeroLayoutSpec(availableHeightDp = 800, fontScale = 1f).mascotSizeDp
        val roomyHero = welcomeHeroLayoutSpec(availableHeightDp = 900, fontScale = 1f).mascotSizeDp

        assertTrue(compactHome < compactSupporting && compactSupporting < compactHero)
        assertTrue(regularHome < regularSupporting && regularSupporting < regularHero)
        assertTrue(roomyHome < roomySupporting && roomySupporting < roomyHero)
    }

    @Test
    fun `wheel sheet header keeps three equal single-line touch-safe slots`() {
        val spec = wheelSheetHeaderLayoutSpec()

        assertEquals(spec.actionSlotWeight, spec.titleSlotWeight, 0f)
        assertEquals(1, spec.actionMaxLines)
        assertEquals(1, spec.titleMaxLines)
        assertTrue(spec.minTouchTargetDp >= 48)
    }

    @Test
    fun `wheel rows grow with font scale and keep values on one line`() {
        val regular = wheelRowLayoutSpec(fontScale = 1f)
        val largeText = wheelRowLayoutSpec(fontScale = 2f)

        assertTrue(largeText.heightDp > regular.heightDp)
        assertTrue(largeText.heightDp >= 72)
        assertEquals(1, largeText.valueMaxLines)
    }

    @Test
    fun `baseline picker reserves bounded space for both label and value`() {
        val spec = baselinePickerFieldLayoutSpec()

        assertTrue(spec.labelWeight > 0f)
        assertTrue(spec.valueWeight > 0f)
        assertEquals(1f, spec.labelWeight + spec.valueWeight, 0.001f)
        assertTrue(spec.labelMaxLines >= 2)
        assertEquals(1, spec.valueMaxLines)
    }

    @Test
    fun `training profile preview exposes the authoritative seeded rank stats xp and badges`() {
        val baseline = BaselineCalibrationEngine().calculate(
            AthleteCalibration(
                trainingAgeMonths = 18,
                historicalTrainingDaysPerWeek = 4,
                weeklyGoalDays = 4,
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
        val preview = buildOnboardingTrainingProfilePreview(
            trainingAgeMonths = 18,
            historicalTrainingDaysPerWeek = 4,
            hasPastTraining = true,
            hasGymAccess = true,
            baselineResult = baseline,
        )

        assertEquals("1 yr 6 mo", preview.experienceLabel)
        assertEquals("4 days / week", preview.weeklyRhythmLabel)
        assertEquals("Past training reported", preview.historyLabel)
        assertEquals("Gym equipment available", preview.equipmentLabel)
        assertEquals("Verified workouts refine and build on this baseline", preview.calibrationLabel)
        assertEquals("Titanium", preview.provisionalRankLabel)
        assertEquals(baseline.estimatedLifetimeSessions, preview.estimatedLifetimeSessions)
        assertEquals(baseline.xp, preview.seededXp)
        assertEquals(5, preview.seededLevel)
        assertEquals(baseline.supportedBadgeIds.size, preview.supportedBadgeCount)
        assertEquals(baseline.stats.strength, preview.estimatedStats.getValue("STR"))
        assertEquals(baseline.stats.power, preview.estimatedStats.getValue("PWR"))
        assertEquals(baseline.stats.hypertrophy, preview.estimatedStats.getValue("HYP"))
        assertEquals(baseline.stats.endurance, preview.estimatedStats.getValue("END"))
        assertEquals(baseline.stats.agility, preview.estimatedStats.getValue("AGI"))
        assertEquals(baseline.stats.discipline, preview.estimatedStats.getValue("DISC"))
        assertEquals(baseline.stats.recovery, preview.estimatedStats.getValue("REC"))
    }

    @Test
    fun `new athlete preview is honest about missing history and limited equipment`() {
        val baseline = BaselineCalibrationEngine().calculate(
            AthleteCalibration(
                trainingAgeMonths = 0,
                historicalTrainingDaysPerWeek = 1,
                weeklyGoalDays = 1,
                hasPastTraining = false,
                hasGymAccess = false,
            ),
        )
        val preview = buildOnboardingTrainingProfilePreview(
            trainingAgeMonths = 0,
            historicalTrainingDaysPerWeek = 1,
            hasPastTraining = false,
            hasGymAccess = false,
            baselineResult = baseline,
        )

        assertEquals("New to structured training", preview.experienceLabel)
        assertEquals("1 day / week", preview.weeklyRhythmLabel)
        assertEquals("No prior workout log", preview.historyLabel)
        assertEquals("Bodyweight / limited equipment", preview.equipmentLabel)
        assertEquals(0L, preview.seededXp)
        assertEquals("Uncalibrated", preview.provisionalRankLabel)
    }
}
