package com.ironlog.app.ui.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppearanceSaveQueue(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    fun enqueue(block: suspend () -> Unit): Job = scope.launch { mutex.withLock { block() } }
}

/** Process-owned queue: leaving Settings does not cancel an accepted save. Disk work stays on IO. */
object AppearancePersistence {
    private val queue = AppearanceSaveQueue(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    fun enqueue(block: suspend () -> Unit): Job = queue.enqueue(block)
}
