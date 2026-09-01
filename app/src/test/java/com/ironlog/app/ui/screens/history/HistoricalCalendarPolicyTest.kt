package com.ironlog.app.ui.screens.history

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.ui.screens.workout.getCalendarStreakCount
import com.ironlog.app.ui.screens.workout.calcSessionVolume
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoricalCalendarPolicyTest {
    @Test fun calendarVolumeUsesSharedWorkingSetPolicyInsteadOfRawTimedOrWarmupProducts() {
        val entry = HistoryEntry("volume", "2026-08-30T20:00:00Z", exercises = listOf(
            HistoryExercise(trackingType = "weight_reps", sets = listOf(
                HistoryExerciseSet(weight = 50.0, reps = 10.0, type = "warmup", isWarmup = true),
                HistoryExerciseSet(weight = 40.0, reps = 8.0))),
            HistoryExercise(trackingType = "duration_distance", category = "cardio", sets = listOf(
                HistoryExerciseSet(weight = 2.0, reps = 600.0))),
        ))
        assertEquals(320, calcSessionVolume(entry))
    }
    @Test fun calendarGroupsAllRecordedSessionsByCanonicalLocalDate() {
        val entry = HistoryEntry("summary", "2026-08-30T20:00:00Z")
        assertEquals(setOf("2026-08-31"), calendarSessionsByLocalDate(listOf(entry), ZoneId.of("Asia/Kolkata")).keys)
        assertEquals(setOf("2026-08-30"), calendarSessionsByLocalDate(listOf(entry), ZoneId.of("America/Los_Angeles")).keys)
    }
    @Test fun calendarStreakUsesCreditedProofWithYesterdayGraceAndOneClock() {
        val now = Instant.parse("2026-09-01T04:00:00Z")
        val zone = ZoneId.of("Asia/Kolkata")
        val credited = HistoryEntry("credited", "2026-08-30T20:00:00Z", exercises = listOf(HistoryExercise(
            trackingType = "weight_reps", sets = List(8) { HistoryExerciseSet(weight = 40.0, reps = 8.0) })))
        val metadata = HistoryEntry("summary", "2026-09-01T01:00:00Z")
        assertEquals(1, getCalendarStreakCount(listOf(credited, metadata), now, zone))
        assertEquals(0, getCalendarStreakCount(listOf(metadata), now, zone))
        assertEquals(0, getCalendarStreakCount(listOf(credited), now.plusSeconds(86400), zone))
    }
}
