package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CreditedProofValidityTest {
    private val now = Instant.parse("2026-09-01T10:00:00Z")
    private fun session(ex: HistoryExercise) = HistoryEntry("test", "2026-09-01T09:00:00Z", duration = 1800, exercises = listOf(ex))
    @Test fun `weight without repetitions is not proof`() {
        assertFalse(CreditedProof.qualifies(session(HistoryExercise(name = "Bench Press", sets = List(8) { HistoryExerciseSet(weight = 60.0, reps = 0.0) })), now))
    }
    @Test fun `warmup cardio cannot earn proof`() {
        assertFalse(CreditedProof.qualifies(session(HistoryExercise(name = "Treadmill", category = "Cardio", sets = listOf(HistoryExerciseSet(reps = 600.0, type = "warmup")))), now))
    }
    @Test fun `crunch repetitions are never interpreted as running seconds`() {
        assertFalse(CreditedProof.qualifies(session(HistoryExercise(name = "Crunch", sets = listOf(HistoryExerciseSet(reps = 600.0)))), now))
    }
}
