package com.ironlog.app.ui.screens.settings

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal enum class HistoryDerivedSurface {
    APP_STATE,
    STALE_REMINDER,
    NOTIFICATION_SCHEDULE,
    WIDGETS,
}

internal data class HistoryDerivedSurfaceResult(
    val refreshed: Set<HistoryDerivedSurface>,
    val failures: Map<HistoryDerivedSurface, Throwable>,
) {
    val fullyRefreshed: Boolean
        get() = failures.isEmpty() && refreshed.size == HistoryDerivedSurface.entries.size
}

internal data class HistoryMutationOutcome<T>(
    val value: T?,
    val mutationError: Throwable?,
    val surfaces: HistoryDerivedSurfaceResult,
) {
    val mutationSucceeded: Boolean get() = mutationError == null
}

/**
 * Once a destructive/import action is accepted, finish its terminal reconciliation even if the
 * Settings route leaves. Each derived surface is attempted independently, including after a
 * mutation error because import formats may have committed validated rows before failing.
 */
internal suspend fun <T> runHistoryMutationTerminally(
    mutation: suspend () -> T,
    refreshAppState: suspend () -> Unit,
    dismissStaleReminder: suspend () -> Unit,
    reconcileNotifications: suspend () -> Unit,
    enqueueWidgets: suspend () -> Unit,
): HistoryMutationOutcome<T> = withContext(NonCancellable) {
    var value: T? = null
    var mutationError: Throwable? = null
    try {
        value = mutation()
    } catch (error: Exception) {
        mutationError = error
    }

    val refreshed = linkedSetOf<HistoryDerivedSurface>()
    val failures = linkedMapOf<HistoryDerivedSurface, Throwable>()

    suspend fun attempt(surface: HistoryDerivedSurface, action: suspend () -> Unit) {
        try {
            action()
            refreshed += surface
        } catch (error: Exception) {
            failures[surface] = error
        }
    }

    attempt(HistoryDerivedSurface.APP_STATE, refreshAppState)
    attempt(HistoryDerivedSurface.STALE_REMINDER, dismissStaleReminder)
    attempt(HistoryDerivedSurface.NOTIFICATION_SCHEDULE, reconcileNotifications)
    attempt(HistoryDerivedSurface.WIDGETS, enqueueWidgets)

    HistoryMutationOutcome(
        value = value,
        mutationError = mutationError,
        surfaces = HistoryDerivedSurfaceResult(refreshed, failures),
    )
}
