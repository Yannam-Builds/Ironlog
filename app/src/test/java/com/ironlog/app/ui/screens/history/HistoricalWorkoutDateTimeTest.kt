package com.ironlog.app.ui.screens.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class HistoricalWorkoutDateTimeTest {
    @Test fun explicitLocalTimeAndPickerDateDoNotShiftBetweenZones() {
        assertEquals(Instant.parse("2026-08-31T04:00:00Z"), historicalStartInstant("2026-08-31", "09:30", ZoneId.of("Asia/Kolkata")))
        assertEquals(Instant.parse("2026-08-31T16:30:00Z"), historicalStartInstant("2026-08-31", "09:30", ZoneId.of("America/Los_Angeles")))
        val day = LocalDate.parse("2026-08-31")
        assertEquals(day, historicalPickerDate(historicalPickerMillis(day)))
    }

    @Test fun dstGapIsRejectedAndOverlapNeedsExplicitOffset() {
        val zone = ZoneId.of("America/Los_Angeles")
        assertTrue(runCatching { historicalStartInstant("2026-03-08", "02:30", zone) }.isFailure)
        assertTrue(runCatching { historicalStartInstant("2026-11-01", "01:30", zone) }.isFailure)
        assertEquals(Instant.parse("2026-11-01T09:30:00Z"), historicalStartInstant("2026-11-01", "01:30", zone, ZoneOffset.ofHours(-8)))
    }
}
