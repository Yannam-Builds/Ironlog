package com.ironlog.app.widget

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Serializable
enum class WidgetVisualState {
    ACTIVE_STREAK,
    AT_RISK,
    WORKOUT_SOON,
    WORKOUT_DONE,
    RECOVERY_DAY,
    EARLY_WORKOUT,
    NO_EXCUSES,
    NEW_PB,
    COMEBACK,
    MISSION_COMPLETE,
}

data class WidgetVisualInputs(
    val streakDays: Int,
    val todayCompleted: Boolean,
    val weekSessionsCount: Int,
    val weeklyGoal: Int,
    val minutesUntilWorkout: Int?,
    val scheduledWorkoutHour: Int?,
    val isAtRisk: Boolean,
    val isRecoveryDay: Boolean,
    val hasRecentNewPb: Boolean,
    val returnedAfterGap: Boolean,
)

fun buildWeeklyCompletion(
    workoutDates: Set<LocalDate>,
    weekStart: LocalDate,
): List<Boolean> =
    (0..6).map { offset -> weekStart.plusDays(offset.toLong()) in workoutDates }

fun minutesUntilScheduledTime(now: LocalDateTime, scheduledMinutesOfDay: Int): Int {
    val safeMinutes = scheduledMinutesOfDay.coerceIn(0, 23 * 60 + 59)
    var next = now.toLocalDate().atStartOfDay().plusMinutes(safeMinutes.toLong())
    if (next.isBefore(now)) next = next.plusDays(1)
    return ChronoUnit.MINUTES.between(now, next).toInt().coerceAtLeast(0)
}

fun isRecentWidgetEvent(
    eventEpochMs: Long?,
    nowEpochMs: Long,
    maxAgeHours: Long,
): Boolean {
    val event = eventEpochMs ?: return false
    if (event <= 0L || event > nowEpochMs) return false
    return nowEpochMs - event <= maxAgeHours * 60L * 60L * 1000L
}

fun resolveWidgetVisualState(inputs: WidgetVisualInputs): WidgetVisualState {
    if (inputs.hasRecentNewPb) return WidgetVisualState.NEW_PB
    if (inputs.todayCompleted && inputs.weekSessionsCount >= inputs.weeklyGoal.coerceAtLeast(1)) {
        return WidgetVisualState.MISSION_COMPLETE
    }
    if (inputs.todayCompleted && inputs.returnedAfterGap) return WidgetVisualState.COMEBACK
    if (inputs.todayCompleted) return WidgetVisualState.WORKOUT_DONE
    if (inputs.isRecoveryDay) return WidgetVisualState.RECOVERY_DAY

    val soon = inputs.minutesUntilWorkout?.let { it in 0..90 } == true
    if (soon) {
        return if ((inputs.scheduledWorkoutHour ?: 24) < 9) {
            WidgetVisualState.EARLY_WORKOUT
        } else {
            WidgetVisualState.WORKOUT_SOON
        }
    }

    if (inputs.isAtRisk) return WidgetVisualState.AT_RISK
    if (inputs.streakDays > 0 && (inputs.scheduledWorkoutHour ?: 24) < 12) {
        return WidgetVisualState.NO_EXCUSES
    }
    return WidgetVisualState.ACTIVE_STREAK
}
