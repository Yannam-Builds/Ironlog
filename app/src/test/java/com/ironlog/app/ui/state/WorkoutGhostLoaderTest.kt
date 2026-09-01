package com.ironlog.app.ui.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutGhostLoaderTest {
    @Test fun `replacement with no history clears prior exercise ghost`() = runTest {
        var current = listOf(GhostLoadTarget("row", "A"))
        var published = emptyMap<Int, GhostData>()
        val loader = WorkoutGhostLoader(this, { id ->
            if (id == "A") GhostData(date = "A-date") else null
        }, { it == current }, { published = it })

        loader.load(current)
        runCurrent()
        assertEquals("A-date", published[0]?.date)
        current = listOf(GhostLoadTarget("row", "B"))
        loader.load(current)
        runCurrent()

        assertEquals(emptyMap<Int, GhostData>(), published)
    }

    @Test fun `delayed superseded generation cannot overwrite replacement`() = runTest {
        val releaseA = CompletableDeferred<Unit>()
        var current = listOf(GhostLoadTarget("row", "A"))
        var published = emptyMap<Int, GhostData>()
        val loader = WorkoutGhostLoader(this, { id ->
            if (id == "A") withContext(NonCancellable) {
                releaseA.await()
                GhostData(date = "stale-A")
            } else GhostData(date = "current-B")
        }, { it == current }, { published = it })

        loader.load(current)
        runCurrent()
        current = listOf(GhostLoadTarget("row", "B"))
        loader.load(current)
        runCurrent()
        assertEquals("current-B", published[0]?.date)

        releaseA.complete(Unit)
        runCurrent()
        assertEquals("current-B", published[0]?.date)
    }

    @Test fun `unbound initial request does not consume the later bound generation`() = runTest {
        var current = listOf(GhostLoadTarget("row", "A"))
        var published = emptyMap<Int, GhostData>()
        val loader = WorkoutGhostLoader(this, { GhostData(date = "loaded") },
            { it == current }, { published = it })

        loader.load(listOf(GhostLoadTarget("", "A")))
        runCurrent()
        assertEquals(emptyMap<Int, GhostData>(), published)
        loader.load(current)
        runCurrent()
        assertEquals("loaded", published[0]?.date)
    }

    @Test fun `current load failure clears stale ghost and reports error`() = runTest {
        var current = listOf(GhostLoadTarget("row", "A"))
        var published = mapOf(0 to GhostData(date = "stale"))
        var reported: Exception? = null
        val failure = IllegalStateException("query failed")
        val loader = WorkoutGhostLoader(this, { throw failure }, { it == current },
            { published = it }, { reported = it })

        loader.load(current)
        runCurrent()

        assertEquals(emptyMap<Int, GhostData>(), published)
        assertEquals(failure, reported)
    }
}
