package com.ironlog.app.ui.components

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CloudSummaryState(val loading: Boolean, val text: String?, val regenerate: () -> Unit)

/** Shared, configuration-keyed request lifecycle. Removing the host cancels pending work. */
@Composable
internal fun CloudSummaryHost(enabled: Boolean, requestKey: Any?, load: suspend () -> String): CloudSummaryState {
    if (!enabled) return CloudSummaryState(false, null, {})
    return key(requestKey) {
        var text by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(true) }
        var revision by remember { mutableIntStateOf(0) }
        val currentLoad by rememberUpdatedState(load)
        LaunchedEffect(revision) {
            loading = true
            try {
                val result = currentLoad()
                // Legacy engine helpers can swallow cancellation; don't publish their late response.
                currentCoroutineContext().ensureActive()
                text = result.takeIf { it.isNotBlank() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                text = null
            } finally {
                loading = false
            }
        }
        CloudSummaryState(loading, text) { revision++ }
    }
}
