package com.ironlog.app.navigation

import org.junit.Assert.*
import org.junit.Test

class LiquidGlassGeometryTest {
    @Test fun `settled lens preserves original five dp insets`() {
        val lens = liquidGlassLens(320f, 64f, 5, 2f, 2, 1f)
        assertEquals(133f, lens.left, .001f)
        assertEquals(5f, lens.top, .001f)
        assertEquals(54f, lens.width, .001f)
        assertEquals(54f, lens.height, .001f)
    }
    @Test fun `travel stretches only drawing and stays inside the bar`() {
        for (density in listOf(1f, 2.625f, 4f)) for (offset in listOf(-1f, 0f, .5f, 2.4f, 4f, 5f, Float.NaN)) {
            val lens = liquidGlassLens(288f * density, 64f * density, 5, offset, 4, density)
            assertTrue(lens.left >= 5 * density)
            assertTrue(lens.right <= 283 * density + .001f)
            assertTrue(lens.top >= 5 * density)
            assertTrue(lens.bottom <= 59 * density)
            assertTrue(lens.width.isFinite() && lens.height.isFinite())
        }
    }
}
