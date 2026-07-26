package com.ironlog.app.domain.gamification

import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.ui.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationSummaryTest {
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
            history = listOf(HistoryEntry(id = "today", date = "2026-05-25T10:00:00Z")),
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
            history = listOf(HistoryEntry(id = "old", date = "2026-05-20T10:00:00Z")),
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
}
