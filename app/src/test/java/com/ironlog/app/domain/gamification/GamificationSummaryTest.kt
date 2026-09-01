package com.ironlog.app.domain.gamification

import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationSummaryTest {
    @Test fun `pain overrides high readiness stale rhythm and first proof prompts`() {
        val now = 1_779_696_000_000L
        listOf(emptyList(), listOf(credited("old", "2026-05-20T10:00:00Z"))).forEach { history ->
            val summary = buildDailyProofSummary(history, true, null, 99, now, painFlags = setOf("Push"))
            assertEquals(DailyProofStatus.RECOVER_SMART, summary.status)
            assertEquals("RecoveryMap", summary.primaryRoute)
            assertEquals("Review recovery", summary.primaryActionLabel)
            org.junit.Assert.assertTrue(summary.detail.contains("Push"))
        }
    }

    @Test fun `pain keeps active workout resumable without encouraging more load`() {
        val summary = buildDailyProofSummary(emptyList(), true, "Push", 99, painFlags = setOf("Push"))
        assertEquals("ActiveWorkout", summary.primaryRoute)
        org.junit.Assert.assertTrue(summary.detail.contains("pain", ignoreCase = true))
    }

    private fun credited(id: String, date: String) = HistoryEntry(
        id = id,
        date = date,
        duration = 45 * 60,
        exercises = listOf(HistoryExercise(id = "$id-ex", name = "Bench Press", sets = List(8) { HistoryExerciseSet(weight = 60.0, reps = 8.0) })),
    )
    @Test
    fun `active workout state resumes current session`() {
        val summary = buildDailyProofSummary(
            history = emptyList(),
            hasActivePlan = true,
            activeWorkoutDayName = "Push Day",
            readinessScore = 82,
            nowEpochMs = 1_779_696_000_000L,
        )

        assertEquals(DailyProofStatus.ACTIVE_WORKOUT, summary.status)
        assertEquals("Resume workout", summary.primaryActionLabel)
        assertEquals("ActiveWorkout", summary.primaryRoute)
        assertEquals(ForgeFoxExpression.Determined.id, summary.foxExpressionId)
    }

    @Test
    fun `fresh user without plan is routed to setup`() {
        val summary = buildDailyProofSummary(
            history = emptyList(),
            hasActivePlan = false,
            activeWorkoutDayName = null,
            readinessScore = null,
            nowEpochMs = 1_779_696_000_000L,
        )

        assertEquals(DailyProofStatus.SETUP, summary.status)
        assertEquals("Choose a program", summary.primaryActionLabel)
        assertEquals("ProgramPicker", summary.primaryRoute)
        assertEquals(ForgeFoxExpression.Clipboard.id, summary.foxExpressionId)
    }

    @Test
    fun `training completed today becomes saved proof state`() {
        val summary = buildDailyProofSummary(
            history = listOf(credited("today", "2026-05-25T07:00:00Z")),
            hasActivePlan = true,
            activeWorkoutDayName = null,
            readinessScore = 91,
            nowEpochMs = 1_779_696_000_000L,
        )

        assertEquals(DailyProofStatus.PROOF_LOGGED, summary.status)
        assertEquals("Open Iron Ledger", summary.primaryActionLabel)
        assertEquals("statusWindow", summary.primaryRoute)
        assertEquals(ForgeFoxExpression.Proud.id, summary.foxExpressionId)
    }

    @Test
    fun `stale training history becomes at risk state`() {
        val summary = buildDailyProofSummary(
            history = listOf(credited("old", "2026-05-20T10:00:00Z")),
            hasActivePlan = true,
            activeWorkoutDayName = null,
            readinessScore = 61,
            nowEpochMs = 1_779_696_000_000L,
        )

        assertEquals(DailyProofStatus.AT_RISK, summary.status)
        assertEquals("Train today", summary.primaryActionLabel)
        assertEquals("Home", summary.primaryRoute)
        assertEquals(ForgeFoxExpression.CheckingWatch.id, summary.foxExpressionId)
    }

    @Test
    fun `fresh user with a plan waits for first proof instead of being at risk`() {
        val summary = buildDailyProofSummary(emptyList(), true, null, 50, 1_779_696_000_000L)
        assertEquals(DailyProofStatus.FIRST_PROOF, summary.status)
        assertEquals("First proof awaits", summary.headline)
    }
}
