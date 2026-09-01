package com.ironlog.app.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class GhostLoadTarget(val rowUid: String, val exerciseId: String)

/** Publishes one complete ghost snapshot and never publishes a superseded row/exercise generation. */
internal class WorkoutGhostLoader(
    private val scope: CoroutineScope,
    private val loadOne: suspend (String) -> GhostData?,
    private val isCurrent: (List<GhostLoadTarget>) -> Boolean,
    private val publish: (Map<Int, GhostData>) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
) {
    private var generation = 0L
    private var job: Job? = null

    fun load(targets: List<GhostLoadTarget>) {
        // Binding is the ownership token. An early Compose pass must not consume a generation
        // before durable rows have been bound; bindWorkoutExerciseRows schedules the retry.
        if (targets.any { it.rowUid.isBlank() || it.exerciseId.isBlank() }) return
        generation += 1
        val requestedGeneration = generation
        job?.cancel()
        job = scope.launch {
            try {
                val result = targets.map { target -> target to loadOne(target.exerciseId) }
                coroutineContext.ensureActive()
                if (requestedGeneration != generation || !isCurrent(targets)) return@launch
                publish(result.mapIndexedNotNull { index, (_, ghost) -> ghost?.let { index to it } }.toMap())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestedGeneration == generation && isCurrent(targets)) {
                    publish(emptyMap())
                    onFailure(failure)
                }
            }
        }
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
    }
}
