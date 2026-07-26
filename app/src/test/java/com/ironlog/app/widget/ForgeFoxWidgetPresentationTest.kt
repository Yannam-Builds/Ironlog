package com.ironlog.app.widget

import com.ironlog.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ForgeFoxWidgetPresentationTest {
    @Test
    fun `layout resolver classifies canonical widget sizes`() {
        assertEquals(
            ForgeWidgetLayoutClass.SMALL,
            resolveForgeWidgetLayoutClass(110f, 110f, ForgeWidgetLayoutClass.MEDIUM),
        )
        assertEquals(
            ForgeWidgetLayoutClass.MEDIUM,
            resolveForgeWidgetLayoutClass(180f, 180f, ForgeWidgetLayoutClass.SMALL),
        )
        assertEquals(
            ForgeWidgetLayoutClass.TALL,
            resolveForgeWidgetLayoutClass(220f, 320f, ForgeWidgetLayoutClass.MEDIUM),
        )
        assertEquals(
            ForgeWidgetLayoutClass.WIDE,
            resolveForgeWidgetLayoutClass(320f, 160f, ForgeWidgetLayoutClass.MEDIUM),
        )
    }

    @Test
    fun `streak icon uses the single official drawable resource`() {
        assertEquals(R.drawable.ic_forge_streak_dumbbell, ForgeFoxWidgetAssets.streakIcon)
    }

    @Test
    fun `presentation keeps short labels for constrained widgets`() {
        val active = forgePresentationFor(WidgetVisualState.ACTIVE_STREAK, null)
        val atRisk = forgePresentationFor(WidgetVisualState.AT_RISK, null)

        assertEquals("DAY STREAK", active.title)
        assertEquals("AT RISK", atRisk.title)
        assertEquals("Don't let it break!", atRisk.message)
    }
}
