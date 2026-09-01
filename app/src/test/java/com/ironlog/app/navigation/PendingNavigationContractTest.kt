package com.ironlog.app.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingNavigationContractTest {
    @Test fun `notification Tabs route selects existing Home tab instead of pushing Tabs`() {
        assertEquals(0, pendingTabIndex("Tabs"))
        assertEquals(0, pendingTabIndex("Home"))
        assertEquals(1, pendingTabIndex("Plans"))
        assertNull(pendingTabIndex("RecoveryMap"))
    }

    @Test fun `external pending routes are allowlisted before navigation`() {
        assertTrue(isAllowedPendingStackRoute("BodyWeight"))
        assertTrue(isAllowedPendingStackRoute("ActiveWorkout/synthetic-day"))
        assertFalse(isAllowedPendingStackRoute("UnknownRoute"))
        assertFalse(isAllowedPendingStackRoute("ActiveWorkout/"))
        assertFalse(isAllowedPendingStackRoute("ActiveWorkout/day?redirect=Settings"))
        assertFalse(isAllowedPendingStackRoute("../../Settings"))
        assertTrue(isAllowedPendingRoute("Stats"))
        assertTrue(isAllowedPendingRoute("statusWindow"))
        assertFalse(isAllowedPendingRoute("made-up-destination"))
    }

    @Test fun `nested destination is revealed only after durable pending route write`() = runTest {
        val allowWrite = CompletableDeferred<Unit>()
        var persisted: String? = null
        var revealed = false
        val operation = async {
            persistPendingRouteAndRevealTabs(
                route = "Plans",
                persist = { route -> allowWrite.await(); persisted = route },
                revealTabs = { revealed = true },
            )
        }

        runCurrent()
        assertFalse(revealed)
        allowWrite.complete(Unit)
        operation.await()
        assertEquals("Plans", persisted)
        assertTrue(revealed)
    }
}
