package com.ironlog.app.ui.state

import org.junit.Assert.*
import org.junit.Test

class WorkoutDraftWriteGateTest {
    @Test fun `older asynchronous draft cannot overwrite latest lifecycle snapshot`() {
        val gate = WorkoutDraftWriteGate()
        val older = checkNotNull(gate.capture())
        val latest = checkNotNull(gate.capture())
        var saved = ""
        gate.writeIfCurrent(latest) { saved = "latest" }
        gate.writeIfCurrent(older) { saved = "old" }
        assertEquals("latest", saved)
    }

    @Test fun `terminal pause invalidates pending writes and failure resumes only fresh snapshots`() {
        val gate = WorkoutDraftWriteGate()
        val old = checkNotNull(gate.capture())
        gate.pause()
        assertNull(gate.capture())
        var writes = 0
        gate.writeIfCurrent(old) { writes++ }
        gate.resume()
        gate.writeIfCurrent(old) { writes++ }
        assertEquals(0, writes)
        gate.writeIfCurrent(checkNotNull(gate.capture())) { writes++ }
        assertEquals(1, writes)
    }
}
