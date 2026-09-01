package com.ironlog.app.services

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTimerClockTest {

    @Test
    fun `notification updater sleeps coarsely without an active rest`() {
        assertEquals(30_000L, workoutNotificationUpdaterDelayMs(null, 30_000L))
    }

    @Test
    fun `notification updater wakes only for rest milestones and completion`() {
        assertEquals(5_000L, workoutNotificationUpdaterDelayMs(65_000L, 30_000L))
        assertEquals(15_000L, workoutNotificationUpdaterDelayMs(20_000L, 30_000L))
        assertEquals(1_000L, workoutNotificationUpdaterDelayMs(5_000L, 30_000L))
        assertEquals(250L, workoutNotificationUpdaterDelayMs(250L, 30_000L))
    }

    @Test
    fun `notification updater failure backoff grows and remains bounded`() {
        assertEquals(500L, workoutNotificationUpdaterFailureBackoffMs(1))
        assertEquals(1_000L, workoutNotificationUpdaterFailureBackoffMs(2))
        assertEquals(8_000L, workoutNotificationUpdaterFailureBackoffMs(5))
        assertEquals(30_000L, workoutNotificationUpdaterFailureBackoffMs(Int.MAX_VALUE))
    }

    @Test
    fun `same boot rest countdown ignores manual wall clock changes`() {
        val start = WorkoutClockSnapshot(1_000_000L, 20_000L, 4)
        val deadline = WorkoutTimerClock.deadlineAfter(start, 90_000L)

        val wallMovedForward = WorkoutClockSnapshot(4_600_000L, 50_000L, 4)
        val wallMovedBackward = WorkoutClockSnapshot(10_000L, 50_000L, 4)

        assertEquals(60_000L, deadline.remainingMs(wallMovedForward))
        assertEquals(60_000L, deadline.remainingMs(wallMovedBackward))
    }

    @Test
    fun `malformed huge rest duration saturates instead of wrapping into the past`() {
        val deadline = WorkoutTimerClock.deadlineAfter(
            WorkoutClockSnapshot(Long.MAX_VALUE - 5L, Long.MAX_VALUE - 10L, 4),
            30_000L,
        )

        assertEquals(Long.MAX_VALUE, deadline.wallEndMs)
        assertEquals(Long.MAX_VALUE, deadline.elapsedEndMs)
    }

    @Test
    fun `reboot falls back to the persisted wall deadline`() {
        val deadline = WorkoutTimerDeadline(
            wallEndMs = 1_100_000L,
            elapsedEndMs = 110_000L,
            bootCount = 4,
        )

        assertEquals(
            40_000L,
            deadline.remainingMs(WorkoutClockSnapshot(1_060_000L, 5_000L, 5)),
        )
    }

    @Test
    fun `session chronometer also ignores wall changes on the same boot`() {
        val now = WorkoutClockSnapshot(
            wallTimeMs = 9_000_000L,
            elapsedRealtimeMs = 80_000L,
            bootCount = 7,
        )

        assertEquals(
            60_000L,
            WorkoutTimerClock.elapsedSinceStartMs(
                startWallMs = 1_000_000L,
                startElapsedMs = 20_000L,
                startBootCount = 7,
                now = now,
            ),
        )
        assertEquals(8_940_000L, WorkoutTimerClock.effectiveStartWallMs(
            startWallMs = 1_000_000L,
            startElapsedMs = 20_000L,
            startBootCount = 7,
            now = now,
        ))
    }
}
