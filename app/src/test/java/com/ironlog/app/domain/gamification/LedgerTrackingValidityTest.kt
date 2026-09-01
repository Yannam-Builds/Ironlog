package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LedgerTrackingValidityTest {
    @Test fun `timed holds and added bodyweight loads are not strength one rep maxes`() {
        val clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneId.of("Asia/Kolkata"))
        listOf("duration_weight", "bodyweight_plus_weight_reps").forEach { tracking ->
            val history = listOf(HistoryEntry("w", "2026-09-01T09:00:00Z", duration = 1800, exercises = listOf(
                HistoryExercise(name = "Movement", trackingType = tracking, sets = List(8) { HistoryExerciseSet(weight = 20.0, reps = 8.0) })
            )))
            assertEquals(0, IronLedgerEngine(clock.zone, clock).rebuild(history, 3, AthleteCalibration()).stats.strength)
            assertEquals(1, StatEngine().compute(history, 0, 1).str)
        }
    }
}
