package com.ironlog.app.domain.intelligence

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class RecoveryCheckInCodecTest {
    private val now = 1_788_242_400_000L

    @Test fun `check in survives normal and legacy double encoded storage`() {
        val input = ManualRecoveryInput(soreness = 2, sleepQuality = 4, energy = 3, notes = "Easy day")
        val raw = RecoveryCheckInCodec.encode(input, now)
        assertEquals(input.copy(recordedAt = now), RecoveryCheckInCodec.decode(raw, now))
        assertEquals(input.copy(recordedAt = now), RecoveryCheckInCodec.decode(JSONObject.quote(raw), now))
    }

    @Test fun `expired future malformed and out of range inputs are not active signals`() {
        val raw = RecoveryCheckInCodec.encode(ManualRecoveryInput(energy = 3), now)
        assertNotNull(RecoveryCheckInCodec.decode(raw, now + 48 * 3_600_000L))
        assertNull(RecoveryCheckInCodec.decode(raw, now + 48 * 3_600_000L + 1))
        assertNull(RecoveryCheckInCodec.decode(raw, now - 300_001L))
        assertNull(RecoveryCheckInCodec.decode("not JSON", now))
        assertNull(RecoveryCheckInCodec.decode("{\"energy\":9,\"recordedAt\":$now}", now))
        assertNull(RecoveryCheckInCodec.decode("{\"energy\":3}", now))
    }

    @Test fun `bad input is rejected before save`() {
        assertThrows(IllegalArgumentException::class.java) { RecoveryCheckInCodec.encode(ManualRecoveryInput(soreness = -1), now) }
    }
}
