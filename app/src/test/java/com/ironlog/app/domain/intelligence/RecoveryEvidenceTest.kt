package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecoveryEvidenceTest {
    @Test fun `single mapped fatigued region is not described as most recovered`() {
        val advice = RecoveryReadinessEngine.suggestions(mapOf("Push" to .4)).joinToString(" ")
        assertFalse(advice.contains("most recovered"))
        assertTrue(advice.contains("reduce volume"))
    }
    @Test fun `single recovered region does not imply full body evidence`() {
        val advice = RecoveryReadinessEngine.suggestions(mapOf("Push" to .95)).joinToString(" ")
        assertFalse(advice.contains("most recovered"))
        assertTrue(advice.contains("Push"))
    }
    private val now = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli()
    @Test fun `no workload with positive checkin is unknown not ready`() {
        val regions = RecoveryReadinessEngine.readinessByRegion(emptyList(), nowEpochMs = now)
        assertTrue(regions.isEmpty())
        val score = RecoveryReadinessEngine.score(regions, ManualRecoveryInput(1, 5, 5, recordedAt = now), now)
        assertEquals("unknown", score.state)
        assertNull(score.scoreOrNull)
    }
    @Test fun `unknown region pain remains explicit without inventing other region evidence`() {
        val snapshot = RecoveryReadinessEngine.snapshot(emptyList(), setOf("Push"), nowEpochMs = now)
        assertEquals(mapOf("Push" to 0.0), snapshot.readiness)
        assertTrue(snapshot.workloadEvidence.isEmpty())
        assertEquals("pain flagged", snapshot.score.state)
    }
    @Test fun `snapshot explanations only include valid mapped working sets`() {
        val ex = HistoryExercise(name = "Custom chest", primaryMuscle = "chest", sets = listOf(
            HistoryExerciseSet(weight = 30.0, reps = 10.0), HistoryExerciseSet(weight = 90.0, reps = 10.0, isWarmup = true)))
        val history = listOf(HistoryEntry("source", "2026-09-01T09:00:00Z", exercises = listOf(ex)))
        val snapshot = RecoveryReadinessEngine.snapshot(history, nowEpochMs = now)
        assertTrue("Chest workload is mapped", "Push" in snapshot.readiness)
        assertFalse("No leg workload was recorded", "Legs" in snapshot.readiness)
        val evidence = snapshot.workloadEvidence.getValue("Push").single()
        assertEquals("source", evidence.workoutId)
        assertEquals(1, evidence.workingSets)
        assertEquals("Custom chest", evidence.exerciseName)
    }
    @Test fun `date only workload uses explicitly selected timezone`() {
        val history = listOf(HistoryEntry("local", "2026-09-01", exercises = listOf(
            HistoryExercise(name = "Bench Press", sets = listOf(HistoryExerciseSet(weight = 60.0, reps = 8.0))))))
        val instant = Instant.parse("2026-08-31T20:00:00Z").toEpochMilli()
        assertTrue(RecoveryReadinessEngine.snapshot(history, nowEpochMs = instant, zoneId = ZoneId.of("Asia/Kolkata")).readiness.isNotEmpty())
        assertTrue(RecoveryReadinessEngine.snapshot(history, nowEpochMs = instant, zoneId = ZoneId.of("America/Los_Angeles")).readiness.isEmpty())
    }
}
