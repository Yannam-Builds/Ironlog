package com.ironlog.app.widget

import kotlinx.serialization.Serializable

/**
 * Canonical Glance widget snapshot sourced from Iron Ledger state.
 */
@Serializable
data class WidgetState(
    // Ledger identity
    val grade: String = "Uncalibrated",
    val level: Int = 1,
    val title: String = "Ledger Initiate",
    val dailyStreakDays: Int = 0,
    val streakWeeks: Int = 0,
    val integrityScore: Double = 1.0,

    // XP progression
    val totalXp: Long = 0L,
    val xpInLevel: Long = 0L,
    val xpForNextLevel: Long = 125L,

    // Training signals
    val signalStrength: Int = 1,
    val signalPower: Int = 1,
    val signalHypertrophy: Int = 1,
    val signalEndurance: Int = 1,
    val signalAgility: Int = 1,
    val signalDiscipline: Int = 1,
    val signalRecovery: Int = 1,

    // Workout recommendation
    val recommendedDayName: String = "No Plan",
    val recommendedDayBlurb: String = "",

    // Weekly progress
    val weekSessionsCount: Int = 0,
    val weeklyGoal: Int = 4,
    val weeklyCompletion: List<Boolean> = emptyList(),

    // Next checkpoint
    val nextGradeLabel: String = "",
    val nextGradeOutstanding: Int = 0,

    // Daily proof surface
    val dailyProofHeadline: String = "Set your proof loop",
    val dailyProofDetail: String = "",
    val primaryActionLabel: String = "Choose a program",
    val primaryActionRoute: String = "ProgramPicker",
    val foxExpressionId: String = "forgefox_20_clipboard",
    val latestBadgeTitle: String = "",
    val todayCompleted: Boolean = false,
    val minutesUntilWorkout: Int? = null,
    val isAtRisk: Boolean = false,
    val isRecoveryDay: Boolean = false,
    val hasNewPb: Boolean = false,
    val visualState: WidgetVisualState = WidgetVisualState.ACTIVE_STREAK,

    val lastUpdatedEpochMs: Long = 0L,
) {
    val xpPercent: Float
        get() = if (xpForNextLevel <= 0L) 0f
        else (xpInLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)

    val weekPercent: Float
        get() = if (weeklyGoal <= 0) 0f
        else (weekSessionsCount.toFloat() / weeklyGoal.toFloat()).coerceIn(0f, 1f)
}
