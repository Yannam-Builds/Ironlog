package com.ironlog.app.ui.state

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutHydrationGateTest {
    @Test fun `exercise list appearing before workout ID cannot consume successful hydration`() = runBlocking {
        val gate = WorkoutHydrationGate()
        var count = 0
        gate.reconcile(null, listOf("bench")) { count++; true }
        gate.reconcile("workout", listOf("bench")) { count++; true }
        gate.reconcile("workout", listOf("bench")) { count++; true }
        assertEquals(1, count)
    }
    @Test fun `failure is retryable and a second workout with same exercises hydrates`() = runBlocking {
        val gate = WorkoutHydrationGate()
        var count = 0
        gate.reconcile("one", listOf("bench")) { count++; false }
        gate.reconcile("one", listOf("bench")) { count++; true }
        gate.reconcile("two", listOf("bench")) { count++; true }
        assertEquals(3, count)
    }
    @Test fun `adding a repeated exercise row requires another hydration`() = runBlocking {
        val gate = WorkoutHydrationGate()
        var count = 0
        gate.reconcile("one", listOf("bench")) { count++; true }
        gate.reconcile("one", listOf("bench", "bench")) { count++; true }
        assertEquals(2, count)
    }
}
