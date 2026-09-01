package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RecoveryValidityTest {
    private val now = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli()
    private fun readiness(ex: HistoryExercise, at: Long = now - 3600000) = RecoveryReadinessEngine.readinessByRegion(
        listOf(HistoryEntry("w", Instant.ofEpochMilli(at).toString(), exercises = listOf(ex))), nowEpochMs = now)
    private val empty get() = RecoveryReadinessEngine.readinessByRegion(emptyList(), nowEpochMs = now)
    @Test fun `unmapped custom movement cannot fabricate Core fatigue`() {
        assertEquals(empty, readiness(HistoryExercise(name = "Custom movement Z", sets = List(8) { HistoryExerciseSet(weight = 20.0, reps = 8.0) })))
    }
    @Test fun `invalid rows and independent warmup flag cannot create fatigue`() {
        val ex = HistoryExercise(name = "Bench Press", sets = listOf(HistoryExerciseSet(weight = 60.0, reps = 0.0), HistoryExerciseSet(weight = 60.0, reps = 8.0, isWarmup = true)))
        assertEquals(empty, readiness(ex))
    }
    @Test fun `future workload is ignored even within clock skew margin`() {
        assertEquals(empty, readiness(HistoryExercise(name = "Bench Press", sets = List(8) { HistoryExerciseSet(weight = 60.0, reps = 8.0) }), now + 60000))
    }
}
