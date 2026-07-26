package com.ironlog.app.ui.screens.body

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMapTransformTest {
    @Test
    fun `top anchored body transform centers path without screen-size bias`() {
        val canvasWidth = 600f
        val viewBox = ViewBox(x = 0f, y = 0f, width = 600f, height = 1200f)

        val transform = computeBodyTransform(
            canvasW = canvasWidth,
            canvasH = 1200f,
            viewBox = viewBox,
            verticalAnchor = VerticalAnchor.TOP,
        )

        assertEquals(0.93f, transform.scale, 0.0001f)
        assertEquals(21f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
    }

    @Test
    fun `canonical fit uses shared dimensions so front and back render at same scale`() {
        val frontViewBox = ViewBox(x = 0f, y = 0f, width = 620f, height = 1200f)
        val backViewBox = ViewBox(x = 0f, y = 0f, width = 560f, height = 1180f)

        val front = computeBodyTransform(
            canvasW = 300f,
            canvasH = 600f,
            viewBox = frontViewBox,
            fitWidth = 620f,
            fitHeight = 1200f,
            verticalAnchor = VerticalAnchor.TOP,
        )
        val back = computeBodyTransform(
            canvasW = 300f,
            canvasH = 600f,
            viewBox = backViewBox,
            fitWidth = 620f,
            fitHeight = 1200f,
            verticalAnchor = VerticalAnchor.TOP,
        )

        assertEquals(front.scale, back.scale, 0.0001f)
        assertEquals((300f - backViewBox.width * back.scale) / 2f, back.offsetX, 0.0001f)
    }
}
