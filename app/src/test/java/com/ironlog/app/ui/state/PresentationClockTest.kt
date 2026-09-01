package com.ironlog.app.ui.state

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PresentationClockTest {
    @Test fun `clock is cold cancels with collection and refreshes immediately when collected again`() = runTest {
        var reads = 0
        var time = 100L
        val clock = object : Clock() {
            override fun getZone() = ZoneId.of("Asia/Kolkata")
            override fun withZone(zone: ZoneId) = this
            override fun instant(): Instant { reads++; return Instant.ofEpochMilli(time) }
        }
        val flow = presentationClock(clock, 1000)
        assertEquals(0, reads)
        val values = mutableListOf<Long>()
        val job = launch { flow.collect { values += it } }
        runCurrent()
        assertEquals(listOf(100L), values)
        job.cancel(); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, reads)
        time = 5000
        val resumed = launch { flow.collect { values += it } }
        runCurrent()
        assertEquals(listOf(100L, 5000L), values)
        resumed.cancel()
    }
}
