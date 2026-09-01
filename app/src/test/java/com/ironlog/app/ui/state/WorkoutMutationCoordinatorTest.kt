package com.ironlog.app.ui.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutMutationCoordinatorTest {
    @Test fun `initialization is single flight and queued mutation waits for restoration`() = runTest {
        val coordinator = WorkoutMutationCoordinator(this)
        val restored = CompletableDeferred<Unit>()
        var loads = 0
        val first = coordinator.initialize { loads++; restored.await() }
        val second = coordinator.initialize { error("must join original initialization") }
        assertSame(first, second)
        val events = mutableListOf<String>()
        val mutation = launch { coordinator.commit<String>({ events += "write"; "row" }, { events += "publish:$it" }) }
        runCurrent()
        assertEquals(1, loads)
        assertTrue(events.isEmpty())
        assertFalse(coordinator.initialization.value.ready)
        restored.complete(Unit)
        mutation.join()
        assertTrue(coordinator.initialization.value.ready)
        assertEquals(listOf("write", "publish:row"), events)
    }

    @Test fun `commit serializes delayed write before publication and next mutation`() = runTest {
        val coordinator = WorkoutMutationCoordinator(this)
        coordinator.initialize { }.await().getOrThrow()
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch { coordinator.commit<String>({ events += "add"; gate.await(); "added" }, { events += it }) }
        val second = launch { coordinator.commit<String>({ events += "log"; "logged" }, { events += it }) }
        runCurrent()
        assertEquals(listOf("add"), events)
        gate.complete(Unit)
        first.join(); second.join()
        assertEquals(listOf("add", "added", "log", "logged"), events)
    }

    @Test fun `write failure never publishes and next mutation remains usable`() = runTest {
        val coordinator = WorkoutMutationCoordinator(this)
        coordinator.initialize { }.await().getOrThrow()
        var published = "original"
        val result = runCatching { coordinator.commit<String>({ error("disk failure") }, { published = it }) }
        assertTrue(result.isFailure)
        assertEquals("original", published)
        coordinator.commit<String>({ "retry" }, { published = it })
        assertEquals("retry", published)
    }

    @Test fun `initialization failure is visible and explicit retry unblocks writes`() = runTest {
        val coordinator = WorkoutMutationCoordinator(this)
        assertTrue(coordinator.initialize { error("restore failed") }.await().isFailure)
        assertEquals("restore failed", coordinator.initialization.value.error)
        assertTrue(runCatching { coordinator.awaitReady() }.isFailure)
        coordinator.initialize { }.await().getOrThrow()
        assertNull(coordinator.initialization.value.error)
        var saved = false
        coordinator.commit({ Unit }, { saved = true })
        assertTrue(saved)
    }

    @Test fun `canceling waiting action does not cancel initialization or publish it later`() = runTest {
        val coordinator = WorkoutMutationCoordinator(this)
        val gate = CompletableDeferred<Unit>()
        val initialization = coordinator.initialize { gate.await() }
        var published = false
        val mutation = launch { coordinator.commit({ "value" }, { published = true }) }
        runCurrent()
        mutation.cancelAndJoin()
        gate.complete(Unit)
        initialization.await().getOrThrow()
        assertFalse(published)
        assertTrue(coordinator.initialization.value.ready)
    }

    @Test fun `terminal commit rejects queued later edits before they write`() = runTest {
        val coordinator = WorkoutMutationCoordinator(this)
        coordinator.initialize { }.await().getOrThrow()
        val gate = CompletableDeferred<Unit>()
        val terminal = launch { coordinator.commit({ gate.await() }, { coordinator.close() }) }
        var wrote = false
        val queued = async { runCatching { coordinator.commit({ wrote = true }, {}) } }
        runCurrent()
        gate.complete(Unit)
        terminal.join()
        assertTrue(queued.await().isFailure)
        assertFalse(wrote)
    }

    @Test fun `postcreation restore failure retries persisted session without creating another`() = runTest {
        var persisted: String? = null
        var creations = 0
        var failRestore = true
        suspend fun load() = loadOrCreateWorkout(
            readActiveId = { persisted },
            resume = { it },
            create = { "workout-${++creations}".also { persisted = it } },
            restore = { if (failRestore) error("read failed after durable creation") },
        )
        val coordinator = WorkoutMutationCoordinator(this)
        assertTrue(coordinator.initialize { load() }.await().isFailure)
        failRestore = false
        coordinator.initialize { assertEquals("workout-1", load()) }.await().getOrThrow()
        assertEquals(1, creations)
    }
}
