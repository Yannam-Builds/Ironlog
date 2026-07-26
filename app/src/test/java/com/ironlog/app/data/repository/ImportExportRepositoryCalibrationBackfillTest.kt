package com.ironlog.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ImportExportRepositoryCalibrationBackfillTest {

    @Test
    fun `legacy restore backfills missing athlete calibration and gamification profile rows from settings`() {
        val calibration = deriveFallbackAthleteCalibration(
            settings = linkedMapOf(
                "baseline_training_age_months" to ("19" to "string"),
                "baseline_historical_training_days_per_week" to ("4" to "string"),
                "baseline_bodyweight_kg" to ("70" to "string"),
                "baseline_pushups" to ("40" to "string"),
                "baseline_pullups" to ("14" to "string"),
                "baseline_bench_kg" to ("65" to "string"),
                "baseline_lat_pulldown_kg" to ("110" to "string"),
                "baseline_mile_run_seconds" to ("570" to "string"),
                "baseline_has_past_training" to ("true" to "boolean"),
                "baseline_has_gym_access" to ("true" to "boolean"),
                "ledger_imported_history" to ("true" to "boolean"),
                "goalMode" to ("strength" to "string"),
                "weightUnit" to ("kg" to "string"),
                "weeklyGoalDays" to ("5" to "string"),
            ),
            hasImportedWorkouts = true,
            nowMs = 1L,
        )
        val profile = fallbackGamificationProfileRow()

        assertEquals(19, calibration.trainingAgeMonths)
        assertEquals(4, calibration.historicalTrainingDaysPerWeek)
        assertEquals(true, calibration.importedHistory)
        assertEquals("local", calibration.offlineUserId)
        assertEquals("local", profile.offlineUserId)
        assertEquals(0L, profile.totalXp)
        assertEquals("{}", profile.statsJson)
    }

    @Test
    fun `legacy date-only strings preserve their original calendar date`() {
        val parsed = parseImportEpochMillis("2026-05-27", fallback = 99L, zoneId = ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-05-27T00:00:00Z").toEpochMilli(), parsed)
    }

    @Test
    fun `legacy ISO instants and numeric timestamps are both preserved`() {
        val instant = Instant.parse("2026-05-27T18:45:00Z").toEpochMilli()

        assertEquals(instant, parseImportEpochMillis("2026-05-27T18:45:00Z", 99L, ZoneOffset.UTC))
        assertEquals(instant, parseImportEpochMillis(instant, 99L, ZoneOffset.UTC))
    }
}
