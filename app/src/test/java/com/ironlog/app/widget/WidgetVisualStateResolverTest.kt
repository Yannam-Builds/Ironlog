package com.ironlog.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetVisualStateResolverTest {

    @Test
    fun `weekly completion is Monday through Sunday for current week`() {
        val weekStart = LocalDate.of(2026, 6, 1)

        val completion = buildWeeklyCompletion(
            workoutDates = setOf(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 5, 31),
            ),
            weekStart = weekStart,
        )

        assertEquals(listOf(true, false, true, false, false, false, true), completion)
    }

    @Test
    fun `workout soon uses real reminder time and distinguishes early workouts`() {
        val early = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = 5,
                todayCompleted = false,
                weekSessionsCount = 2,
                weeklyGoal = 4,
                minutesUntilWorkout = 25,
                scheduledWorkoutHour = 6,
                isAtRisk = false,
                isRecoveryDay = false,
                hasRecentNewPb = false,
                returnedAfterGap = false,
            )
        )
        val later = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = 5,
                todayCompleted = false,
                weekSessionsCount = 2,
                weeklyGoal = 4,
                minutesUntilWorkout = 25,
                scheduledWorkoutHour = 18,
                isAtRisk = false,
                isRecoveryDay = false,
                hasRecentNewPb = false,
                returnedAfterGap = false,
            )
        )

        assertEquals(WidgetVisualState.EARLY_WORKOUT, early)
        assertEquals(WidgetVisualState.WORKOUT_SOON, later)
    }

    @Test
    fun `completion states are prioritized from real workout outcomes`() {
        val missionComplete = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = 12,
                todayCompleted = true,
                weekSessionsCount = 4,
                weeklyGoal = 4,
                minutesUntilWorkout = null,
                scheduledWorkoutHour = null,
                isAtRisk = false,
                isRecoveryDay = false,
                hasRecentNewPb = false,
                returnedAfterGap = false,
            )
        )
        val newPb = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = 12,
                todayCompleted = true,
                weekSessionsCount = 3,
                weeklyGoal = 4,
                minutesUntilWorkout = null,
                scheduledWorkoutHour = null,
                isAtRisk = false,
                isRecoveryDay = false,
                hasRecentNewPb = true,
                returnedAfterGap = false,
            )
        )
        val comeback = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = 1,
                todayCompleted = true,
                weekSessionsCount = 1,
                weeklyGoal = 4,
                minutesUntilWorkout = null,
                scheduledWorkoutHour = null,
                isAtRisk = false,
                isRecoveryDay = false,
                hasRecentNewPb = false,
                returnedAfterGap = true,
            )
        )

        assertEquals(WidgetVisualState.MISSION_COMPLETE, missionComplete)
        assertEquals(WidgetVisualState.NEW_PB, newPb)
        assertEquals(WidgetVisualState.COMEBACK, comeback)
    }

    @Test
    fun `safe incomplete streak remains active instead of coach mode`() {
        val state = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = 23,
                todayCompleted = false,
                weekSessionsCount = 2,
                weeklyGoal = 4,
                minutesUntilWorkout = null,
                scheduledWorkoutHour = null,
                isAtRisk = false,
                isRecoveryDay = false,
                hasRecentNewPb = false,
                returnedAfterGap = false,
            )
        )

        assertEquals(WidgetVisualState.ACTIVE_STREAK, state)
    }

    @Test
    fun `recent pb hook expires instead of becoming a permanent fake state`() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            true,
            isRecentWidgetEvent(
                eventEpochMs = now.minusHours(2).atZone(zone).toInstant().toEpochMilli(),
                nowEpochMs = nowMs,
                maxAgeHours = 24,
            )
        )
        assertEquals(
            false,
            isRecentWidgetEvent(
                eventEpochMs = now.minusDays(3).atZone(zone).toInstant().toEpochMilli(),
                nowEpochMs = nowMs,
                maxAgeHours = 24,
            )
        )
    }
}
