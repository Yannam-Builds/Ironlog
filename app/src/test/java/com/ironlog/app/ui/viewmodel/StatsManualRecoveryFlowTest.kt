package com.ironlog.app.ui.viewmodel

import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.domain.intelligence.RecoveryCheckInCodec
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class StatsManualRecoveryFlowTest {
    @Test fun `cached checkin expires on clock tick and selected new value restores it`() = runBlocking {
        val start = 1_800_000_000_000L
        val input = ManualRecoveryInput(soreness = 2, sleepQuality = 3, energy = 4, recordedAt = start)
        val raw = MutableStateFlow<String?>(RecoveryCheckInCodec.encode(input, start))
        val time = MutableStateFlow(start)
        val events = Channel<ManualRecoveryInput?>(Channel.UNLIMITED)
        val job = launch { manualRecoveryInputFlow(raw, time).collect { events.send(it) } }
        try {
            assertEquals(input, withTimeout(5_000) { events.receive() })
            time.value = start + RecoveryCheckInCodec.MAX_AGE_MS + 1
            assertNull(withTimeout(5_000) { events.receive() })
            raw.value = RecoveryCheckInCodec.encode(input, time.value)
            assertEquals(time.value, withTimeout(5_000) { events.receive() }?.recordedAt)
            raw.value = null
            assertNull(withTimeout(5_000) { events.receive() })
        } finally {
            job.cancelAndJoin()
            events.close()
        }
    }
}
