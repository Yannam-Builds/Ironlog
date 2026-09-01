package com.ironlog.app.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanNoteMutationCoordinatorTest {
    @Test
    fun `accepted note write completes before a later clear`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val coordinator = PlanNoteMutationCoordinator()
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        var persistedNote = "original"

        coordinator.launch(scope) {
            updateStarted.complete(Unit)
            releaseUpdate.await()
            persistedNote = "stale in-flight note"
        }

        assertTrue("accepted writes must enter the serialized queue before launch returns", updateStarted.isCompleted)
        val clear = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.run {
                persistedNote = ""
            }
        }
        assertFalse("clear must wait for the already accepted note write", clear.isCompleted)

        releaseUpdate.complete(Unit)
        advanceUntilIdle()

        assertEquals("", persistedNote)
        assertTrue(clear.isCompleted)
        scope.cancel()
    }
}
