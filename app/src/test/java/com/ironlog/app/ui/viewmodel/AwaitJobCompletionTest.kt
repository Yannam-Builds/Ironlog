package com.ironlog.app.ui.viewmodel

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitJobCompletionTest {
    @Test
    fun `refresh task consumes root launch failure and propagates it to awaited caller`() = runTest {
        val expected = IllegalStateException("snapshot failed")
        val task = launchHandledRefreshTask {
            throw expected
        }
        joinAll(task.job)

        var actual: Throwable? = null
        try {
            task.completion.await()
        } catch (error: Throwable) {
            actual = error
        }

        assertFalse(task.job.isCancelled)
        assertEquals(expected::class, actual?.let { it::class })
        assertEquals(expected.message, actual?.message)
    }

    @Test
    fun `successful job completion returns normally`() = runTest {
        val task = launchHandledRefreshTask { Unit }

        task.completion.await()
    }

    @Test
    fun `cancellation before launch body starts completes awaited signal`() = runTest {
        val deferredDispatcher = StandardTestDispatcher(testScheduler)
        val deferredScope = CoroutineScope(SupervisorJob() + deferredDispatcher)
        val task = deferredScope.launchHandledRefreshTask {
            error("body must not start")
        }

        task.job.cancel()
        advanceUntilIdle()

        assertTrue(task.completion.isCompleted)
        assertTrue(task.completion.isCancelled)
    }
}
