package com.ironlog.app.ui.screens.onboarding

import com.ironlog.app.domain.badges.BadgeDefinitions
import com.ironlog.app.domain.gamification.BaselineCalibrationResult
import com.ironlog.app.domain.gamification.IronLedgerEngine

internal data class OnboardingContentLayoutSpec(
    val horizontalPaddingDp: Int,
    val topPaddingDp: Int,
    val bottomPaddingDp: Int,
)

internal fun onboardingContentLayoutSpec(
    widthDp: Int,
    fontScale: Float,
): OnboardingContentLayoutSpec {
    val needsExtraWidth = widthDp < 360 || fontScale >= 1.3f
    return OnboardingContentLayoutSpec(
        horizontalPaddingDp = if (needsExtraWidth) 18 else 24,
        topPaddingDp = if (needsExtraWidth) 20 else 24,
        bottomPaddingDp = 56,
    )
}

internal data class WelcomeHeroLayoutSpec(
    val isCompact: Boolean,
    val mascotSizeDp: Int,
    val mascotViewportHeightDp: Int,
    val bannerGapDp: Int,
)

internal fun welcomeHeroLayoutSpec(
    availableHeightDp: Int,
    fontScale: Float,
): WelcomeHeroLayoutSpec {
    val compact = availableHeightDp < 720 || fontScale >= 1.25f
    val roomy = availableHeightDp >= 860 && fontScale < 1.15f
    return when {
        compact -> WelcomeHeroLayoutSpec(
            isCompact = true,
            mascotSizeDp = 124,
            mascotViewportHeightDp = 140,
            bannerGapDp = 12,
        )
        roomy -> WelcomeHeroLayoutSpec(
            isCompact = false,
            mascotSizeDp = 176,
            mascotViewportHeightDp = 196,
            bannerGapDp = 14,
        )
        else -> WelcomeHeroLayoutSpec(
            isCompact = false,
            mascotSizeDp = 150,
            mascotViewportHeightDp = 168,
            bannerGapDp = 12,
        )
    }
}

/**
 * Supporting onboarding art stays visibly quieter than the welcome hero and
 * shrinks on compact or large-text layouts instead of crowding the content.
 */
internal fun supportingMascotSizeDp(
    availableHeightDp: Int,
    fontScale: Float,
): Int = when {
    availableHeightDp < 720 || fontScale >= 1.25f -> 88
    availableHeightDp >= 860 && fontScale < 1.15f -> 112
    else -> 100
}

internal data class WheelSheetHeaderLayoutSpec(
    val actionSlotWeight: Float = 1f,
    val titleSlotWeight: Float = 1f,
    val actionMaxLines: Int = 1,
    val titleMaxLines: Int = 1,
    val minTouchTargetDp: Int = 48,
)

internal fun wheelSheetHeaderLayoutSpec(): WheelSheetHeaderLayoutSpec =
    WheelSheetHeaderLayoutSpec()

internal data class WheelRowLayoutSpec(
    val heightDp: Int,
    val visibleRowCount: Int,
    val valueMaxLines: Int = 1,
)

internal fun wheelRowLayoutSpec(fontScale: Float): WheelRowLayoutSpec {
    val safeFontScale = fontScale.coerceAtLeast(1f)
    return WheelRowLayoutSpec(
        heightDp = kotlin.math.ceil(36f * safeFontScale).toInt().coerceAtLeast(54),
        visibleRowCount = if (safeFontScale >= 1.7f) 3 else 5,
    )
}

internal data class BaselinePickerFieldLayoutSpec(
    val labelWeight: Float = 0.44f,
    val valueWeight: Float = 0.56f,
    val labelMaxLines: Int = 2,
    val valueMaxLines: Int = 1,
)

internal fun baselinePickerFieldLayoutSpec(): BaselinePickerFieldLayoutSpec =
    BaselinePickerFieldLayoutSpec()

data class OnboardingTrainingProfilePreview(
    val experienceLabel: String,
    val weeklyRhythmLabel: String,
    val historyLabel: String,
    val equipmentLabel: String,
    val calibrationLabel: String = "Verified workouts refine and build on this baseline",
    val provisionalRankLabel: String,
    val estimatedLifetimeSessions: Int,
    val estimatedStats: Map<String, Int>,
    val seededLevel: Int,
    val seededXp: Long,
    val supportedBadgeLabels: List<String>,
    val supportedBadgeCount: Int,
)

internal fun buildOnboardingTrainingProfilePreview(
    trainingAgeMonths: Int,
    historicalTrainingDaysPerWeek: Int,
    hasPastTraining: Boolean,
    hasGymAccess: Boolean,
    baselineResult: BaselineCalibrationResult,
): OnboardingTrainingProfilePreview = OnboardingTrainingProfilePreview(
    experienceLabel = formatTrainingExperience(trainingAgeMonths.coerceAtLeast(0)),
    weeklyRhythmLabel = historicalTrainingDaysPerWeek.coerceIn(1, 7).let { days ->
        if (days == 1) "1 day / week" else "$days days / week"
    },
    historyLabel = if (hasPastTraining) "Past training reported" else "No prior workout log",
    equipmentLabel = if (hasGymAccess) "Gym equipment available" else "Bodyweight / limited equipment",
    provisionalRankLabel = baselineResult.grade.label,
    estimatedLifetimeSessions = baselineResult.estimatedLifetimeSessions,
    estimatedStats = onboardingPreviewStats(baselineResult.stats),
    seededLevel = IronLedgerEngine().levelFromTotalXp(baselineResult.xp),
    seededXp = baselineResult.xp,
    supportedBadgeLabels = BadgeDefinitions.all
        .filter { it.id in baselineResult.supportedBadgeIds }
        .map { it.title },
    supportedBadgeCount = baselineResult.supportedBadgeIds.size,
)

private fun formatTrainingExperience(months: Int): String = when {
    months <= 0 -> "New to structured training"
    months < 12 -> if (months == 1) "1 month" else "$months months"
    months % 12 == 0 -> if (months == 12) "1 yr" else "${months / 12} yrs"
    else -> "${months / 12} yr ${months % 12} mo"
}
