package com.ironlog.app.services

import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupMutationCoordinatorTest {
    @Test
    fun `issuing a newer token invalidates the older token synchronously`() {
        val gate = LatestWinsMutationGate()

        val older = gate.issueToken()
        val newer = gate.issueToken()

        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))
    }

    @Test
    fun `serialized mutation lets an in flight write finish before the latest write wins`() = runBlocking {
        val gate = LatestWinsMutationGate()
        val older = gate.issueToken()
        val olderStarted = CompletableDeferred<Unit>()
        val releaseOlder = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async {
            gate.runIfCurrent(older) {
                events += "older-start"
                olderStarted.complete(Unit)
                releaseOlder.await()
                events += "older-end"
            }
        }
        olderStarted.await()
        val newer = gate.issueToken()
        val second = async {
            gate.runIfCurrent(newer) { events += "newer" }
        }
        releaseOlder.complete(Unit)

        assertFalse(first.await())
        assertTrue(second.await())
        assertEquals(listOf("older-start", "older-end", "newer"), events)
    }

    @Test
    fun `backup commit cleanup finishes after caller cancellation`() = runBlocking {
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val events = Collections.synchronizedList(mutableListOf<String>())

        val job = launch {
            commitBackupArtifactWithCleanup(
                commit = {
                    events += "commit-start"
                    commitStarted.complete(Unit)
                    releaseCommit.await()
                    events += "commit-end"
                    "snapshot.json"
                },
                cleanup = { events += "cleanup" },
            )
        }
        commitStarted.await()
        job.cancel()
        releaseCommit.complete(Unit)
        job.join()

        assertEquals(listOf("commit-start", "commit-end", "cleanup"), events)
    }

    @Test
    fun `cleanup failure cannot turn a committed backup into failure`() = runBlocking {
        val cleanupFailures = mutableListOf<Exception>()

        val result = commitBackupArtifactWithCleanup(
            commit = { "snapshot.json" },
            cleanup = { error("notification cleanup failed") },
            onCleanupFailure = cleanupFailures::add,
        )

        assertEquals("snapshot.json", result)
        assertEquals("notification cleanup failed", cleanupFailures.single().message)
    }

    @Test
    fun `cleanup cancellation remains cancellation`() = runBlocking {
        var cancelled = false

        try {
            commitBackupArtifactWithCleanup(
                commit = { "snapshot.json" },
                cleanup = { throw CancellationException("cancelled") },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }
}
