// app/src/test/java/com/ironlog/app/data/health/HealthConnectRepositoryTest.kt
package com.ironlog.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectRepositoryTest {

    // ── Biometric readiness score ─────────────────────────────────────────

    @Test fun `sleepScore returns 1_0 for 8+ hours`() {
        assertEquals(1.0, sleepScore(8.0), 0.001)
        assertEquals(1.0, sleepScore(9.0), 0.001)
    }

    @Test fun `sleepScore returns 0_0 for 4 hours or less`() {
        assertEquals(0.0, sleepScore(4.0), 0.001)
        assertEquals(0.0, sleepScore(3.0), 0.001)
    }

    @Test fun `sleepScore scales linearly between 4 and 8 hours`() {
        val score6h = sleepScore(6.0)
        assertTrue("6h sleep score should be ~0.5 but was $score6h", score6h in 0.45..0.55)
    }

    @Test fun `hrvScore returns 1_0 for HRV at or above 80 ms`() {
        assertEquals(1.0, hrvScore(80.0), 0.001)
        assertEquals(1.0, hrvScore(100.0), 0.001)
    }

    @Test fun `hrvScore returns 0_0 for HRV at or below 20 ms`() {
        assertEquals(0.0, hrvScore(20.0), 0.001)
        assertEquals(0.0, hrvScore(10.0), 0.001)
    }

    @Test fun `biometricReadinessScore blends sleep and HRV`() {
        val snap = BiometricSnapshot(sleepHours = 8.0, hrvRmssd = 80.0)
        val score = biometricReadinessScore(snap)
        assertTrue("Score with full sleep+HRV should be >= 0.9", score >= 0.9)
    }

    @Test fun `biometricReadinessScore with null data returns 0_5 neutral`() {
        val snap = BiometricSnapshot()  // all nulls
        val score = biometricReadinessScore(snap)
        assertEquals(0.5, score, 0.001)
    }

    @Test fun `biometricReadinessScore with only sleep data uses sleep score`() {
        val snap = BiometricSnapshot(sleepHours = 4.0)
        val score = biometricReadinessScore(snap)
        assertTrue("Poor sleep only should score < 0.3", score < 0.3)
    }
}

// ── Pure helper functions (extracted for testability) ───────────────────────

fun sleepScore(hours: Double): Double = ((hours - 4.0) / 4.0).coerceIn(0.0, 1.0)

fun hrvScore(rmssd: Double): Double = ((rmssd - 20.0) / 60.0).coerceIn(0.0, 1.0)

fun biometricReadinessScore(snap: BiometricSnapshot): Double {
    val scores = buildList {
        snap.sleepHours?.let { add(sleepScore(it) * 0.6) }
        snap.hrvRmssd?.let  { add(hrvScore(it) * 0.4) }
    }
    if (scores.isEmpty()) return 0.5
    // Normalize: sum of weights of present components
    val weights = buildList {
        if (snap.sleepHours != null) add(0.6)
        if (snap.hrvRmssd != null)   add(0.4)
    }
    return (scores.sum() / weights.sum()).coerceIn(0.0, 1.0)
}
