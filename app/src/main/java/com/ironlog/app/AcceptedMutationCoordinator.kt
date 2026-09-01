package com.ironlog.app

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-owned serialization for user-confirmed work that must outlive its initiating route. */
internal class AcceptedMutationCoordinator(
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _inFlightCount = MutableStateFlow(0)
    val inFlightCount: StateFlow<Int> = _inFlightCount.asStateFlow()

    fun launch(block: suspend () -> Unit): Job {
        _inFlightCount.update { it + 1 }
        val countedDown = AtomicBoolean(false)
        fun countDownOnce() {
            if (countedDown.compareAndSet(false, true)) {
                _inFlightCount.update { count -> (count - 1).coerceAtLeast(0) }
            }
        }

        val job = scope.launch {
            try {
                mutex.withLock { block() }
            } finally {
                countDownOnce()
            }
        }
        // Also covers a scope/job canceled before the launch body gets its first dispatch.
        job.invokeOnCompletion { countDownOnce() }
        return job
    }
}
