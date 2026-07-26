package com.ironlog.app.ui.screens.onboarding

import com.ironlog.app.domain.gamification.AthleteCalibration
import com.ironlog.app.domain.gamification.IronLedgerEngine
import com.ironlog.app.domain.gamification.IronLedgerSnapshot
import com.ironlog.app.domain.gamification.IronLedgerStats

internal fun onboardingCalibrationFromDraft(draft: OnboardingDraft): AthleteCalibration = AthleteCalibration(
    trainingAgeMonths = draft.trainingAgeMonths.coerceAtLeast(0),
    historicalTrainingDaysPerWeek = draft.historicalTrainingDaysPerWeek.coerceIn(1, 7),
    weeklyGoalDays = draft.weeklyGoalDays.coerceIn(1, 7),
    bodyweightKg = draft.bodyweightKg.takeIf { it > 0 }?.toDouble(),
    hasPastTraining = draft.hasPastTraining,
    hasGymAccess = draft.hasGymAccess,
    baselinePushups = draft.baselinePushups.coerceAtLeast(0),
    baselinePullups = draft.baselinePullups.coerceAtLeast(0),
    baselineBenchKg = draft.baselineBenchKg.coerceAtLeast(0),
    baselineLatPulldownKg = draft.baselineLatPulldownKg.coerceAtLeast(0),
    baselineMileRunSeconds = draft.baselineMileRunSeconds.coerceAtLeast(0),
)

internal fun buildOnboardingLedgerSnapshot(draft: OnboardingDraft): IronLedgerSnapshot =
    IronLedgerEngine().rebuild(
        history = emptyList(),
        weeklyGoal = draft.weeklyGoalDays.coerceIn(1, 7),
        calibration = onboardingCalibrationFromDraft(draft),
    )

internal fun onboardingPreviewStats(stats: IronLedgerStats): Map<String, Int> = linkedMapOf(
    "STR" to stats.strength,
    "PWR" to stats.power,
    "HYP" to stats.hypertrophy,
    "END" to stats.endurance,
    "AGI" to stats.agility,
    "DISC" to stats.discipline,
    "REC" to stats.recovery,
)
