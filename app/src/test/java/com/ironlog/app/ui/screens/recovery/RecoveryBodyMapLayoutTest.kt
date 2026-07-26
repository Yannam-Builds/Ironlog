package com.ironlog.app.ui.screens.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBodyMapLayoutTest {
    @Test
    fun `recovery map page leaves top breathing room and does not fill viewport`() {
        val layout = computeRecoveryMapPageLayout(
            maxWidthDp = 360f,
            maxHeightDp = 470f,
            aspect = 0.55f,
        )

        assertEquals(12f, layout.topPaddingDp, 0.001f)
        assertTrue(layout.canvasHeightDp < 430f)
        assertTrue(layout.canvasWidthDp < 300f)
    }
}
