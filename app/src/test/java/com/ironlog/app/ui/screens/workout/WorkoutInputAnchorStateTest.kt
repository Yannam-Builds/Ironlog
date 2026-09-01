package com.ironlog.app.ui.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutInputAnchorStateTest {
    @Test fun `late source publication cannot anchor an input already moved outside the typing viewport`() {
        val state = WorkoutInputAnchorState().apply { setFocused(true) }
        state.recordPlacement(emptyList<String>(), 100f, viewportTop = 16f, viewportBottom = 344f, inputHeight = 64f)
        state.recordPlacement(emptyList<String>(), -260f, enabled = false, viewportTop = 16f, viewportBottom = 344f, inputHeight = 64f)
        state.recordPlacement(emptyList<String>(), -260f, viewportTop = 16f, viewportBottom = 344f, inputHeight = 64f)
        assertNull(state.recordPlacement(listOf("late-set"), -156f, viewportTop = 16f, viewportBottom = 344f, inputHeight = 64f))
    }

    @Test fun `a previously visible input still anchors when the new footer obscures its old position`() {
        val state = WorkoutInputAnchorState().apply { setFocused(true) }
        state.recordPlacement(emptyList<String>(), 256f, viewportTop = 40f, viewportBottom = 344f, inputHeight = 64f)
        val request = state.recordPlacement(listOf("set-1"), 400f, viewportTop = 40f, viewportBottom = 256f, inputHeight = 64f)!!
        assertEquals(208f, state.compensation(request, 400f, minTop = 40f, maxBottom = 256f, inputHeight = 64f)!!, 0f)
    }

    @Test fun `pending visible anchor survives multiple insertions moving the field temporarily outside viewport`() {
        val state = WorkoutInputAnchorState().apply { setFocused(true) }
        state.recordPlacement(emptyList<String>(), 100f, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f)
        state.recordPlacement(listOf("one"), 260f, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f)
        val latest = state.recordPlacement(listOf("one", "two"), 420f, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f)!!
        assertEquals(320f, state.compensation(latest, 420f)!!, 0f)
    }

    @Test fun `returning an input to view allows future anchoring after manual scrolling`() {
        val state = WorkoutInputAnchorState().apply { setFocused(true) }
        state.recordPlacement(emptyList<String>(), -60f, scrolling = true, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f)
        assertNull(state.recordPlacement(listOf("one"), 40f, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f))
        state.recordPlacement(listOf("one"), 120f, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f)
        val request = state.recordPlacement(listOf("one", "two"), 224f, viewportTop = 0f, viewportBottom = 300f, inputHeight = 64f)!!
        assertEquals(104f, state.compensation(request, 224f)!!, 0f)
    }

    @Test fun `rest footer clamps an obscured anchor using actual list and input bounds`() {
        val state = focusedAnchor()
        val request = state.recordPlacement(listOf("set-1"), 220f)!!
        assertEquals(160f, state.compensation(request, 220f, minTop = 16f, maxBottom = 124f, inputHeight = 64f)!!, 0f)
    }

    @Test fun `visible anchor is not moved gratuitously by footer clamping`() {
        val state = focusedAnchor()
        val request = state.recordPlacement(listOf("set-1"), 220f)!!
        assertEquals(120f, state.compensation(request, 220f, minTop = 16f, maxBottom = 300f, inputHeight = 64f)!!, 0f)
    }

    @Test fun `first set and header expansion preserve the measured anchor`() {
        val state = focusedAnchor()
        val request = state.recordPlacement(listOf("set-1"), 260f)!!
        assertEquals(160f, state.compensation(request, 260f)!!, 0f)
    }

    @Test fun `framework relocation is deducted from compensation`() {
        val state = focusedAnchor()
        val request = state.recordPlacement(listOf("set-1"), 260f)!!
        assertEquals(30f, state.compensation(request, 130f)!!, 0f)
    }

    @Test fun `intermediate framework placements retain the original pending anchor`() {
        val state = focusedAnchor()
        val request = state.recordPlacement(listOf("set-1"), 260f)!!
        assertNull(state.recordPlacement(listOf("set-1"), 220f))
        assertNull(state.recordPlacement(listOf("set-1"), 192f))
        assertEquals(92f, state.compensation(request, 192f)!!, 0f)
    }

    @Test fun `rapid logs coalesce against the original anchor and invalidate older jobs`() {
        val state = focusedAnchor()
        val first = state.recordPlacement(listOf("set-1"), 210f)!!
        val second = state.recordPlacement(listOf("set-1", "set-2"), 320f)!!
        assertNull(state.compensation(first, 320f))
        assertEquals(220f, state.compensation(second, 320f)!!, 0f)
    }

    @Test fun `late banner insertion removal and deletion use signed measured deltas`() {
        val state = focusedAnchor(key = listOf("set-1") to null)
        val banner = state.recordPlacement(listOf("set-1") to "PR", 140f)!!
        assertEquals(40f, state.compensation(banner, 140f)!!, 0f)
        state.recordPlacement(listOf("set-1") to "PR", 100f)
        val removal = state.recordPlacement(listOf("set-1") to null, 60f)!!
        assertEquals(-40f, state.compensation(removal, 60f)!!, 0f)
        state.recordPlacement(listOf("set-1") to null, 100f)
        val deletion = state.recordPlacement(emptyList<String>() to null, 0f)!!
        assertEquals(-100f, state.compensation(deletion, 0f)!!, 0f)
    }

    @Test fun `ordinary placement changes do not schedule a scroll`() {
        val state = focusedAnchor()
        assertNull(state.recordPlacement(emptyList<String>(), 70f))
        assertNull(state.recordPlacement(emptyList<String>(), 90f))
    }

    @Test fun `user scroll and superset navigation cancel pending compensation`() {
        val state = focusedAnchor()
        val scrolling = state.recordPlacement(listOf("set-1"), 200f)!!
        assertNull(state.compensation(scrolling, 180f, scrolling = true))
        val superset = state.recordPlacement(listOf("set-1", "set-2"), 300f)!!
        assertNull(state.compensation(superset, 300f, enabled = false))
        assertNull(state.recordPlacement(listOf("set-3"), 400f, scrolling = true))
    }

    @Test fun `ending explicit navigation re-enables later structural anchoring without replaying the old request`() {
        val state = focusedAnchor()
        val old = state.recordPlacement(listOf("set-1"), 200f)!!
        state.cancelPending()
        assertNull(state.compensation(old, 200f))
        assertNull(state.recordPlacement(listOf("set-1"), 140f, enabled = false))
        assertNull(state.recordPlacement(listOf("set-1"), 140f, enabled = true))
        val deletion = state.recordPlacement(emptyList<String>(), 40f)!!
        assertEquals(-100f, state.compensation(deletion, 40f)!!, 0f)
    }

    @Test fun `focus loss or detached coordinates cancel pending compensation`() {
        val state = focusedAnchor()
        val request = state.recordPlacement(listOf("set-1"), 200f)!!
        state.setFocused(false)
        assertNull(state.compensation(request, 200f))
        state.setFocused(true)
        val detached = state.recordPlacement(listOf("set-2"), 300f)!!
        assertNull(state.compensation(detached, null))
    }

    private fun focusedAnchor(key: Any = emptyList<String>()) = WorkoutInputAnchorState().apply {
        setFocused(true)
        recordPlacement(key, 100f)
    }
}
