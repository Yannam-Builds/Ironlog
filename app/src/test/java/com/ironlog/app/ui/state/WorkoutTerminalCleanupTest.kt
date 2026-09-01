package com.ironlog.app.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutTerminalCleanupTest {
    @Test fun `ancillary failures still run remaining cleanup and complete callback once`() = runBlocking {
        val events = mutableListOf<String>()
        afterWorkoutCommit(
            cleanup = listOf(
                { events += "streak"; error("read failed") },
                { events += "service"; error("stop failed") },
                { events += "widget" },
            ),
            onDone = { events += "done" },
        )
        assertEquals(listOf("streak", "service", "widget", "done"), events)
    }

    @Test fun `cancellation after durable success completes callback once then propagates`() = runBlocking {
        val events = mutableListOf<String>()
        val error = runCatching {
            afterWorkoutCommit(
                cleanup = listOf(
                    { throw CancellationException("cancelled after commit") },
                    { events += "remaining cleanup" },
                ),
                onDone = { events += "done" },
            )
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(listOf("remaining cleanup", "done"), events)
    }

    @Test fun `caller cancellation after durable success cannot strand terminal cleanup`() = runBlocking {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            afterWorkoutCommit(
                cleanup = listOf(
                    {
                        cleanupStarted.complete(Unit)
                        releaseCleanup.await()
                        events += "service stopped"
                    },
                    { events += "notification removed" },
                ),
                onDone = { events += "done" },
            )
        }

        cleanupStarted.await()
        job.cancel()
        releaseCleanup.complete(Unit)
        job.cancelAndJoin()

        assertEquals(listOf("service stopped", "notification removed", "done"), events)
    }

    @Test fun `terminal database commit finishes when caller is cancelled`() = runBlocking {
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            commitWorkoutTerminalMutation {
                commitStarted.complete(Unit)
                releaseCommit.await()
                events += "committed"
            }
            events += "returned"
        }

        commitStarted.await()
        job.cancel()
        releaseCommit.complete(Unit)
        job.cancelAndJoin()

        assertEquals(listOf("committed", "returned"), events)
    }
}
