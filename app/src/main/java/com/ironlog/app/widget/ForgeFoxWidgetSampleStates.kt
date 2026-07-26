package com.ironlog.app.widget

internal object ForgeFoxWidgetSampleStates {
    val all: List<WidgetState> = WidgetVisualState.entries.mapIndexed { index, visualState ->
        WidgetState(
            dailyStreakDays = when (visualState) {
                WidgetVisualState.AT_RISK -> 210
                WidgetVisualState.EARLY_WORKOUT -> 196
                WidgetVisualState.NEW_PB -> 35
                else -> 23 + index
            },
            weeklyGoal = 4,
            weekSessionsCount = if (visualState == WidgetVisualState.MISSION_COMPLETE) 4 else 3,
            weeklyCompletion = listOf(true, true, true, visualState == WidgetVisualState.MISSION_COMPLETE, false, false, false),
            todayCompleted = visualState in setOf(
                WidgetVisualState.WORKOUT_DONE,
                WidgetVisualState.NEW_PB,
                WidgetVisualState.COMEBACK,
                WidgetVisualState.MISSION_COMPLETE,
            ),
            minutesUntilWorkout = if (visualState in setOf(
                    WidgetVisualState.WORKOUT_SOON,
                    WidgetVisualState.EARLY_WORKOUT,
                )
            ) 30 else null,
            isAtRisk = visualState == WidgetVisualState.AT_RISK,
            isRecoveryDay = visualState == WidgetVisualState.RECOVERY_DAY,
            hasNewPb = visualState == WidgetVisualState.NEW_PB,
            visualState = visualState,
            primaryActionRoute = when (visualState) {
                WidgetVisualState.RECOVERY_DAY -> "RecoveryMap"
                WidgetVisualState.WORKOUT_DONE,
                WidgetVisualState.NEW_PB,
                WidgetVisualState.COMEBACK,
                WidgetVisualState.MISSION_COMPLETE -> "statusWindow"
                else -> "Home"
            },
        )
    }
}
