package com.ironlog.app.ui.screens.home

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDateProjectionTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-08-31T12:00:00Z").toEpochMilli()

    @Test fun `home streak and weekly count follow the athlete's local dates`() {
        val history = listOf(
            credited("today", "2026-08-30T18:45:00Z"),
            credited("yesterday", "2026-08-29T19:00:00Z"),
            HistoryEntry("invalid", "bad-date"),
        )
        assertEquals(2, getStreak(history, now, zone))
        assertEquals(1, countSessionsThisWeek(history, LocalDate.of(2026, 8, 31), zone))
        assertEquals(getWeekKey("2026-08-31", zone), getWeekKey(history.first().date, zone))
    }

    private fun credited(id: String, date: String) = HistoryEntry(
        id = id,
        date = date,
        duration = 45 * 60,
        exercises = listOf(HistoryExercise(id = "$id-ex", name = "Bench Press", sets = List(8) {
            HistoryExerciseSet(weight = 60.0, reps = 8.0)
        })),
    )
}
