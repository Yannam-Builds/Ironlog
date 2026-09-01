package com.ironlog.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptedMutationCoordinatorTest {
    @Test
    fun `accepted work is visible immediately and serialized process wide`() = runTest {
        val coordinator = AcceptedMutationCoordinator(this)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()

        coordinator.launch {
            calls += "first-start"
            firstEntered.complete(Unit)
            releaseFirst.await()
            calls += "first-end"
        }
        coordinator.launch {
            calls += "second"
        }

        assertEquals(2, coordinator.inFlightCount.value)
        firstEntered.await()
        assertEquals(listOf("first-start"), calls)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first-start", "first-end", "second"), calls)
        assertEquals(0, coordinator.inFlightCount.value)
    }
}
