package com.ironlog.app.ui.screens.plans

import com.ironlog.app.ui.model.HistoryEntry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramInsightsMetricsTest {
    @Test
    fun `missing calendar weeks reduce adherence and consistency`() {
        val zone = ZoneId.systemDefault()
        val entries = listOf(
            HistoryEntry("old", LocalDate.now().minusWeeks(4).atStartOfDay(zone).toInstant().toString()),
            HistoryEntry("now", LocalDate.now().atStartOfDay(zone).toInstant().toString()),
        )

        val metrics = computeInsightsMetrics(entries, weeklyGoalDays = 1)

        assertEquals(5, metrics.weekCount)
        assertEquals(40, metrics.consistencyPct)
        assertTrue(metrics.adherencePct < 100)
    }

    @Test
    fun `invalid dates are excluded from both numerator and denominator`() {
        val metrics = computeInsightsMetrics(listOf(HistoryEntry("bad", "not-a-date")), weeklyGoalDays = 3)
        assertEquals(0, metrics.weekCount)
        assertEquals(0, metrics.adherencePct)
    }
}
