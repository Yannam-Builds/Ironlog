package com.ironlog.app.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A user-confirmed terminal write must finish even if its screen leaves composition mid-commit. */
internal suspend fun <T> commitWorkoutTerminalMutation(commit: suspend () -> T): T =
    withContext(NonCancellable) { commit() }

/** Called only after durable success: ancillary failures cannot turn a saved session into a retry. */
internal suspend fun afterWorkoutCommit(
    cleanup: List<suspend () -> Unit>,
    onDone: () -> Unit,
    onCleanupFailure: (Exception) -> Unit = {},
) {
    // The domain transaction has already committed. Navigation, configuration changes, or
    // ViewModel disposal must not strand a foreground service/notification after that point.
    withContext(NonCancellable) {
        var deferredCancellation: CancellationException? = null
        try {
            for (action in cleanup) {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    deferredCancellation = deferredCancellation ?: cancelled
                } catch (error: Exception) {
                    onCleanupFailure(error)
                }
            }
        } finally {
            onDone()
        }
        deferredCancellation?.let { throw it }
    }
}
