package com.ironlog.app.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectRepositoryTest {

    @Test
    fun `one authorized health context permission is enough to attempt partial reads`() {
        val supported = setOf("sleep", "resting_hr", "hrv")

        assertTrue(canReadAnyHealthContext(setOf("sleep"), supported))
        assertTrue(canReadAnyHealthContext(setOf("hrv", "unrelated"), supported))
        assertFalse(canReadAnyHealthContext(emptySet(), supported))
        assertFalse(canReadAnyHealthContext(setOf("unrelated"), supported))
    }

    @Test
    fun `display evidence keeps plausible finite readings without interpreting them`() {
        val evidence = BiometricSnapshot(
            sleepHours = 7.5,
            restingHrBpm = 58,
            hrvRmssd = 42.0,
        ).validatedForDisplay()

        assertEquals(7.5, evidence.sleepHours!!, 0.001)
        assertEquals(58L, evidence.restingHrBpm)
        assertEquals(42.0, evidence.hrvRmssd!!, 0.001)
        assertTrue(evidence.hasAnySignal())
    }

    @Test
    fun `display evidence quarantines impossible or non finite readings`() {
        val evidence = BiometricSnapshot(
            sleepHours = Double.NaN,
            restingHrBpm = 400,
            hrvRmssd = Double.POSITIVE_INFINITY,
        ).validatedForDisplay()

        assertNull(evidence.sleepHours)
        assertNull(evidence.restingHrBpm)
        assertNull(evidence.hrvRmssd)
        assertFalse(evidence.hasAnySignal())
    }

    @Test
    fun `display evidence does not invent a weight signal`() {
        val evidence = BiometricSnapshot(weightKg = 80.0).validatedForDisplay()

        assertFalse(evidence.hasAnySignal())
    }
}
