package com.ironlog.app.ui.screens.stats

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryCalendarProjectionTest {
    @Test fun `session counts use local calendar day across UTC midnight`() {
        val counts = countSessionsByLocalDate(
            listOf("2026-08-30T18:45:00Z", "2026-08-31", "not-a-date"),
            ZoneId.of("Asia/Kolkata"),
        )
        assertEquals(mapOf(LocalDate.of(2026, 8, 31) to 2), counts)
    }

    @Test fun `westward offsets keep early UTC sessions on the previous day`() {
        val counts = countSessionsByLocalDate(
            listOf("2026-09-01T06:30:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )
        assertEquals(mapOf(LocalDate.of(2026, 8, 31) to 1), counts)
    }
}
