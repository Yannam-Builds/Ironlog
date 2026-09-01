package com.ironlog.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStateStabilityTest {

    @Test
    fun `logging preserves caller supplied stable id`() {
        val logged = LoggedSet(id = "set-stable-1", weight = 65.0, reps = 8.0)

        val result = workoutReducer(WorkoutState(), WorkoutAction.LogSet(0, logged))

        assertEquals("set-stable-1", result.setLog.getValue(0).single().id)
    }

    @Test
    fun `warmup suggestions stay pending until explicitly logged`() {
        val suggestions = listOf(
            PendingWarmup(id = "warmup-1", weightKg = 20.0, reps = 10),
            PendingWarmup(id = "warmup-2", weightKg = 40.0, reps = 5),
        )

        val queued = workoutReducer(WorkoutState(), WorkoutAction.QueueWarmups(0, suggestions))

        assertTrue(queued.setLog[0].isNullOrEmpty())
        assertEquals(suggestions, queued.pendingWarmups[0])

        val logged = workoutReducer(queued, WorkoutAction.LogPendingWarmup(0, "warmup-1"))
        assertEquals(listOf("warmup-1"), logged.setLog.getValue(0).map { it.id })
        assertEquals("warmup", logged.setLog.getValue(0).single().type)
        assertEquals(listOf("warmup-2"), logged.pendingWarmups.getValue(0).map { it.id })
    }

    @Test
    fun `hydration restores draft target overrides and pending warmups`() {
        val draft = WorkoutState(
            targetOverrides = mapOf(2 to TargetOverride(5, 3)),
            pendingWarmups = mapOf(2 to listOf(PendingWarmup("wu", 30.0, 5))),
        )

        val result = workoutReducer(WorkoutState(), WorkoutAction.HydrateState(draft))

        assertEquals(draft.targetOverrides, result.targetOverrides)
        assertEquals(draft.pendingWarmups, result.pendingWarmups)
        assertTrue(result.setLog.isEmpty())

        // Repeated lifecycle recreation must not promote suggestions into completed sets.
        val reopened = workoutReducer(WorkoutState(), WorkoutAction.HydrateState(result))
        assertEquals(draft.pendingWarmups, reopened.pendingWarmups)
        assertTrue(reopened.setLog.isEmpty())
    }

    @Test
    fun `adding time while paused updates the held remainder without restarting`() {
        val state = WorkoutState(restTimer = RestTimerState(
            active = true,
            endTime = 10_000L,
            endElapsedTime = 20_000L,
            bootCount = 3,
            total = 60,
            paused = true,
            pausedRemainingMs = 12_000L,
        ))

        val result = workoutReducer(state, WorkoutAction.Add30s)

        assertTrue(result.restTimer.paused)
        assertEquals(42_000L, result.restTimer.pausedRemainingMs)
        assertEquals(90, result.restTimer.total)
    }

    @Test
    fun `receiver sync mirrors the authoritative dual deadline without readding time`() {
        val state = WorkoutState(restTimer = RestTimerState(active = true, total = 90))

        val result = workoutReducer(state, WorkoutAction.SyncRestDeadline(
            endTime = 50_000L,
            endElapsedTime = 70_000L,
            bootCount = 8,
        ))

        assertEquals(50_000L, result.restTimer.endTime)
        assertEquals(70_000L, result.restTimer.endElapsedTime)
        assertEquals(8, result.restTimer.bootCount)
        assertEquals(90, result.restTimer.total)
    }

    @Test
    fun `persisted paused rest sync survives process recreation without restarting`() {
        val state = WorkoutState(restTimer = RestTimerState(total = 90))

        val result = workoutReducer(
            state,
            WorkoutAction.SyncPausedRest(remainingMs = 42_000L),
        )

        assertTrue(result.restTimer.active)
        assertTrue(result.restTimer.paused)
        assertEquals(42_000L, result.restTimer.pausedRemainingMs)
        assertEquals(null, result.restTimer.endTime)
        assertEquals(null, result.restTimer.endElapsedTime)
        assertEquals(90, result.restTimer.total)
    }
}
