package com.ironlog.app.ui.screens.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryMutationCoordinatorTest {
    @Test
    fun `route cancellation cannot strand a committed mutation before reconciliation`() = runTest {
        val committed = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()

        val job = launch {
            runHistoryMutationTerminally(
                mutation = {
                    calls += "mutation"
                    committed.complete(Unit)
                    releaseMutation.await()
                    3
                },
                refreshAppState = { calls += "app" },
                dismissStaleReminder = { calls += "reminder" },
                reconcileNotifications = { calls += "schedule" },
                enqueueWidgets = { calls += "widgets" },
            )
        }

        committed.await()
        job.cancel()
        releaseMutation.complete(Unit)
        job.cancelAndJoin()

        assertEquals(listOf("mutation", "app", "reminder", "schedule", "widgets"), calls)
    }

    @Test
    fun `one failed surface cannot prevent the others`() = runTest {
        val calls = mutableListOf<String>()

        val outcome = runHistoryMutationTerminally(
            mutation = { 4 },
            refreshAppState = { calls += "app" },
            dismissStaleReminder = {
                calls += "reminder"
                error("notification manager unavailable")
            },
            reconcileNotifications = { calls += "schedule" },
            enqueueWidgets = { calls += "widgets" },
        )

        assertTrue(outcome.mutationSucceeded)
        assertFalse(outcome.surfaces.fullyRefreshed)
        assertTrue(HistoryDerivedSurface.STALE_REMINDER in outcome.surfaces.failures)
        assertEquals(listOf("app", "reminder", "schedule", "widgets"), calls)
    }

    @Test
    fun `possible partial mutation failure still reconciles every derived surface`() = runTest {
        val calls = mutableListOf<String>()

        val outcome = runHistoryMutationTerminally<Unit>(
            mutation = {
                calls += "mutation"
                error("second imported row failed")
            },
            refreshAppState = { calls += "app" },
            dismissStaleReminder = { calls += "reminder" },
            reconcileNotifications = { calls += "schedule" },
            enqueueWidgets = { calls += "widgets" },
        )

        assertFalse(outcome.mutationSucceeded)
        assertTrue(outcome.surfaces.fullyRefreshed)
        assertEquals(listOf("mutation", "app", "reminder", "schedule", "widgets"), calls)
    }
}
